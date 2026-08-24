package com.rwg.game;

import com.rwg.common.money.Money;
import com.rwg.game.domain.BetType;
import com.rwg.game.service.BaccaratEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Hoa hồng cửa Nhà băng phải tính trên TIỀN LỜI, theo DECISIONS.md M6.
 *
 * Vì sao cần bộ test riêng: công thức cũ trừ trên stake, và với odds mặc định 1 thì
 * `stake * 5%` bằng đúng `profit * 5%` — nên mọi phép kiểm ở mức odds chung đều đạt với cả
 * hai công thức. Chỉ khi odds khác 1 (quản trị đặt tỷ lệ riêng) sai lệch mới lộ ra.
 * Các trường hợp ở đây cố tình dùng odds khác 1.
 */
class BaccaratCommissionTest {

    private static final Money STAKE_100 = Money.of("100");

    @Test
    @DisplayName("Ví dụ trong DECISIONS.md M6: cược 10 thắng Nhà băng nhận 19.50")
    void matchesDecisionsExample() {
        Money stake = Money.of("10");
        Money payout = BaccaratEngine.payout(BetType.BANKER, "BANKER", false, false, stake);
        Money commission = BaccaratEngine.commissionFor(BetType.BANKER, "BANKER", stake, null);

        // M6: 10 + (10 x 1) - 5% x 10 = 19.50
        assertThat(payout.amount().subtract(commission.amount()))
                .isEqualByComparingTo("19.50");
    }

    @Test
    @DisplayName("Hoa hồng tính trên tiền lời, KHÔNG trên stake")
    void chargedOnProfitNotStake() {
        // odds 2 -> tiền lời 200 -> hoa hồng 10.
        // Công thức cũ (stake * 5%) cho 5, tức nhà cái thu thiếu một nửa.
        Money commission = BaccaratEngine.commissionFor(
                BetType.BANKER, "BANKER", STAKE_100, new BigDecimal("2"));
        assertThat(commission.amount()).isEqualByComparingTo("10");

        // odds 0.5 -> tiền lời 50 -> hoa hồng 2.5.
        // Công thức cũ cho 5, tức người chơi bị thu gấp đôi mức đáng phải trả.
        Money low = BaccaratEngine.commissionFor(
                BetType.BANKER, "BANKER", STAKE_100, new BigDecimal("0.5"));
        assertThat(low.amount()).isEqualByComparingTo("2.5");
    }

    @Test
    @DisplayName("Ở odds mặc định 1, hai công thức trùng nhau — đây là lý do bug bị che")
    void identicalAtDefaultOdds() {
        Money onProfit = BaccaratEngine.commissionFor(
                BetType.BANKER, "BANKER", STAKE_100, BigDecimal.ONE);
        Money onStake = STAKE_100.multiply(BaccaratEngine.BANKER_COMMISSION_RATE);

        assertThat(onProfit.amount()).isEqualByComparingTo(onStake.amount());
        assertThat(onProfit.amount()).isEqualByComparingTo("5");
    }

    @Test
    @DisplayName("odds null dùng mức chung của cửa đó")
    void nullOddsFallsBackToDefault() {
        Money withNull = BaccaratEngine.commissionFor(BetType.BANKER, "BANKER", STAKE_100, null);
        Money withOne = BaccaratEngine.commissionFor(
                BetType.BANKER, "BANKER", STAKE_100, BigDecimal.ONE);

        // Cược đặt TRƯỚC khi có tính năng tỷ lệ riêng có bets.odds = NULL.
        assertThat(withNull.amount()).isEqualByComparingTo(withOne.amount());
    }

    @Test
    @DisplayName("Chỉ cửa Nhà băng chịu hoa hồng")
    void onlyBankerIsCharged() {
        for (BetType type : new BetType[]{
                BetType.PLAYER, BetType.TIE, BetType.PLAYER_PAIR, BetType.BANKER_PAIR}) {
            assertThat(BaccaratEngine.hasCommission(type))
                    .as("%s không chịu hoa hồng", type)
                    .isFalse();
            assertThat(BaccaratEngine.commissionFor(type, "BANKER", STAKE_100, null).amount())
                    .as("%s hoa hồng phải bằng 0", type)
                    .isEqualByComparingTo("0");
        }
        assertThat(BaccaratEngine.hasCommission(BetType.BANKER)).isTrue();
    }

    @Test
    @DisplayName("Nhà băng THUA hoặc ván HOÀ thì không thu hoa hồng")
    void noCommissionUnlessBankerWins() {
        // Thua: không có tiền lời.
        assertThat(BaccaratEngine.commissionFor(BetType.BANKER, "PLAYER", STAKE_100, null).amount())
                .isEqualByComparingTo("0");

        // Hoà: cược được HOÀN. Thu hoa hồng lúc này là lấy bớt tiền gốc của người chơi.
        assertThat(BaccaratEngine.commissionFor(BetType.BANKER, "TIE", STAKE_100, null).amount())
                .isEqualByComparingTo("0");

        Money refund = BaccaratEngine.payout(BetType.BANKER, "TIE", false, false, STAKE_100);
        assertThat(refund.amount())
                .as("ván hoà hoàn đúng tiền gốc")
                .isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("Cược 0 hoặc âm không sinh hoa hồng")
    void nonPositiveStakeYieldsZero() {
        assertThat(BaccaratEngine.commissionFor(BetType.BANKER, "BANKER", Money.zero(), null).amount())
                .isEqualByComparingTo("0");
        assertThat(BaccaratEngine.commissionFor(BetType.BANKER, "BANKER", null, null).amount())
                .isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("netOddsFor cho ra con số người chơi thực nhận")
    void netOddsReflectsWhatPlayerReceives() {
        // Mức chung: odds lợi 1 -> thực nhận tương đương 0.95 -> hệ số 1.95, KHÔNG phải 2.
        assertThat(BaccaratEngine.netOddsFor(BetType.BANKER, BigDecimal.ONE))
                .isEqualByComparingTo("0.95");

        // Cửa không có hoa hồng giữ nguyên odds.
        assertThat(BaccaratEngine.netOddsFor(BetType.PLAYER, BigDecimal.ONE))
                .isEqualByComparingTo("1");
        assertThat(BaccaratEngine.netOddsFor(BetType.TIE, new BigDecimal("8")))
                .isEqualByComparingTo("8");
    }

    @Test
    @DisplayName("netOddsFor khớp số tiền thực trả ở nhiều mức odds")
    void netOddsMatchesActualPayout() {
        for (String oddsText : new String[]{"0.5", "0.75", "1", "1.5", "2", "3"}) {
            BigDecimal odds = new BigDecimal(oddsText);

            Money payout = BaccaratEngine.payout(
                    BetType.BANKER, "BANKER", false, false, STAKE_100, odds);
            Money commission = BaccaratEngine.commissionFor(
                    BetType.BANKER, "BANKER", STAKE_100, odds);
            BigDecimal actualNet = payout.amount().subtract(commission.amount());

            // Con số API gửi cho người chơi: hệ số thực nhận x tiền cược.
            BigDecimal netMultiplier = BaccaratEngine.netOddsFor(BetType.BANKER, odds)
                    .add(BigDecimal.ONE);
            BigDecimal predicted = STAKE_100.amount().multiply(netMultiplier);

            assertThat(actualNet)
                    .as("odds %s: hệ số hiển thị phải khớp tiền thực trả", oddsText)
                    .isEqualByComparingTo(predicted);
        }
    }

    @Test
    @DisplayName("Tỷ lệ hoa hồng đúng 5% theo M6")
    void rateIsFivePercent() {
        assertThat(BaccaratEngine.BANKER_COMMISSION_RATE).isEqualByComparingTo("0.05");
    }
}
