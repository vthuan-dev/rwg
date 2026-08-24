package com.rwg.game.service;

import com.rwg.common.money.Money;
import com.rwg.game.domain.BetType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kiểm tra tỷ lệ chỉ định có được engine dùng đúng không.
 *
 * Đây là phần dễ sai nhất của tính năng tỷ lệ riêng: nếu odds chỉ định bị bỏ qua thì
 * người chơi thấy một con số trên màn hình và nhận tiền theo con số khác, mà không có gì
 * báo lỗi.
 */
class EngineOddsOverrideTest {

    private static final Money STAKE = Money.of("100");

    @Test
    @DisplayName("Lucky 28: odds chỉ định được dùng thay mức chung")
    void kl28UsesOverride() {
        // Tổng 20 -> Lớn thắng. Mức chung 0.98 nên trả 198.
        Money standard = Kl28Engine.payout(BetType.KL28_BIG, "", 20, STAKE);
        assertThat(standard.amount()).isEqualByComparingTo("198");

        // Odds riêng 1.5 -> trả 250.
        Money boosted = Kl28Engine.payout(BetType.KL28_BIG, "", 20, STAKE, new BigDecimal("1.5"));
        assertThat(boosted.amount()).isEqualByComparingTo("250");
    }

    @Test
    @DisplayName("Lucky 28: odds chỉ định KHÔNG làm cược thua thành thắng")
    void kl28OverrideDoesNotChangeOutcome() {
        // Tổng 10 -> Lớn THUA. Odds cao mấy cũng không được trả đồng nào.
        Money lost = Kl28Engine.payout(BetType.KL28_BIG, "", 10, STAKE, new BigDecimal("99"));
        assertThat(lost.amount()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Lucky 28: odds null rơi về mức chung, không phải 0")
    void kl28NullOverrideFallsBack() {
        // Cược đặt TRƯỚC khi có tính năng tỷ lệ riêng có cột odds null. Nếu coi null là 0
        // thì mọi cược cũ đang chờ sẽ trả trắng cho người thắng.
        Money fallback = Kl28Engine.payout(BetType.KL28_BIG, "", 20, STAKE, null);
        assertThat(fallback.amount()).isEqualByComparingTo("198");
    }

    @Test
    @DisplayName("Lucky 28: cược đúng tổng vẫn dùng bảng riêng theo từng số khi không chỉ định")
    void kl28NumberUsesPerSumTable() {
        // Tổng 14 có odds 12 -> cược 100 nhận 1300.
        Money win14 = Kl28Engine.payout(BetType.KL28_NUMBER, "14", 14, STAKE);
        assertThat(win14.amount()).isEqualByComparingTo("1300");

        // Tổng 0 có odds 999 -> cược 100 nhận 100000.
        Money win0 = Kl28Engine.payout(BetType.KL28_NUMBER, "0", 0, STAKE);
        assertThat(win0.amount()).isEqualByComparingTo("100000");
    }

    @Test
    @DisplayName("Roulette: odds chỉ định được dùng thay mức chung")
    void rouletteUsesOverride() {
        // Số 3 là ĐỎ. Mức chung 1:1 nên trả 200.
        Money standard = RouletteEngine.payout(BetType.RED, "", 3, STAKE);
        assertThat(standard.amount()).isEqualByComparingTo("200");

        Money boosted = RouletteEngine.payout(BetType.RED, "", 3, STAKE, new BigDecimal("1.2"));
        assertThat(boosted.amount()).isEqualByComparingTo("220");
    }

    @Test
    @DisplayName("Baccarat: odds chỉ định được dùng, nhưng ván hoà vẫn hoàn đúng tiền gốc")
    void baccaratOverrideDoesNotAffectTieRefund() {
        Money standard = BaccaratEngine.payout(BetType.PLAYER, "PLAYER", false, false, STAKE);
        assertThat(standard.amount()).isEqualByComparingTo("200");

        Money boosted = BaccaratEngine.payout(BetType.PLAYER, "PLAYER", false, false, STAKE,
                new BigDecimal("1.5"));
        assertThat(boosted.amount()).isEqualByComparingTo("250");

        // Ván HOÀ hoàn lại đúng tiền gốc. Tỷ lệ riêng không được phép làm người chơi nhận
        // về nhiều hay ít hơn số đã đặt, vì hoàn cược không phải một mức trả thưởng.
        Money tieRefund = BaccaratEngine.payout(BetType.PLAYER, "TIE", false, false, STAKE,
                new BigDecimal("1.5"));
        assertThat(tieRefund.amount()).isEqualByComparingTo("100");
    }

    @Test
    @DisplayName("Bảng odds mặc định mở ra ngoài khớp với giá trị engine tự dùng")
    void defaultOddsAccessorsMatchEngineBehaviour() {
        // Nếu hai đường này lệch nhau thì màn hình hiện một tỷ lệ và thanh toán dùng tỷ lệ
        // khác — đúng loại lỗi mà tính năng này phải tránh.
        assertThat(Kl28Engine.defaultOddsFor(BetType.KL28_BIG, null))
                .isEqualByComparingTo("0.98");
        assertThat(Kl28Engine.defaultOddsFor(BetType.KL28_NUMBER, "14"))
                .isEqualByComparingTo("12");
        assertThat(RouletteEngine.oddsFor(BetType.STRAIGHT)).isEqualByComparingTo("35");
        assertThat(BaccaratEngine.oddsFor(BetType.TIE)).isEqualByComparingTo("8");
        // BANKER là 1 chứ không phải 0.95: hoa hồng 5% thu riêng qua sổ cái.
        assertThat(BaccaratEngine.oddsFor(BetType.BANKER)).isEqualByComparingTo("1");
    }
}
