package com.rwg.game;

import com.rwg.common.money.Money;
import com.rwg.game.domain.BetType;
import com.rwg.game.service.XocDiaEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class XocDiaEngineTest {

    private static final Money STAKE = Money.of("100");

    @ParameterizedTest
    @CsvSource({
            "XOC_DIA_EVEN, 0, true",
            "XOC_DIA_EVEN, 1, false",
            "XOC_DIA_EVEN, 2, true",
            "XOC_DIA_EVEN, 3, false",
            "XOC_DIA_EVEN, 4, true",
            "XOC_DIA_ODD, 0, false",
            "XOC_DIA_ODD, 1, true",
            "XOC_DIA_ODD, 2, false",
            "XOC_DIA_ODD, 3, true",
            "XOC_DIA_ODD, 4, false",
            "XOC_DIA_FOUR_RED, 4, true",
            "XOC_DIA_FOUR_RED, 3, false",
            "XOC_DIA_FOUR_WHITE, 0, true",
            "XOC_DIA_FOUR_WHITE, 1, false",
            "XOC_DIA_THREE_RED, 3, true",
            "XOC_DIA_THREE_RED, 4, false",
            "XOC_DIA_THREE_WHITE, 1, true",
            "XOC_DIA_THREE_WHITE, 0, false"
    })
    void isWin_checksAllCasesCorrectly(BetType betType, int redCount, boolean expectedWin) {
        assertThat(XocDiaEngine.isWin(betType, redCount)).isEqualTo(expectedWin);
    }

    @Test
    void payout_evenOdd_winsAtOdds0_98() {
        // Cược 100 thắng Chẵn (odds 0.98) -> nhận 100 + 100*0.98 = 198
        Money payout = XocDiaEngine.payout(BetType.XOC_DIA_EVEN, 2, STAKE);
        assertThat(payout.amount()).isEqualByComparingTo(new BigDecimal("198.00000000"));

        // Thua -> nhận 0
        Money losingPayout = XocDiaEngine.payout(BetType.XOC_DIA_EVEN, 3, STAKE);
        assertThat(losingPayout.compareAmountTo(Money.zero())).isZero();
    }

    @Test
    void payout_fourOfAKind_winsAtOdds15() {
        // Cược 100 thắng 4 Đỏ (odds 15) -> nhận 100 + 100*15 = 1600 (1 ăn 16)
        Money payoutRed = XocDiaEngine.payout(BetType.XOC_DIA_FOUR_RED, 4, STAKE);
        assertThat(payoutRed.amount()).isEqualByComparingTo(new BigDecimal("1600.00000000"));

        // Cược 100 thắng 4 Trắng (odds 15) -> nhận 1600
        Money payoutWhite = XocDiaEngine.payout(BetType.XOC_DIA_FOUR_WHITE, 0, STAKE);
        assertThat(payoutWhite.amount()).isEqualByComparingTo(new BigDecimal("1600.00000000"));
    }

    @Test
    void payout_threeOfAKind_winsAtOdds3() {
        // Cược 100 thắng 3 Đỏ 1 Trắng (odds 3) -> nhận 100 + 100*3 = 400 (1 ăn 4)
        Money payoutRed = XocDiaEngine.payout(BetType.XOC_DIA_THREE_RED, 3, STAKE);
        assertThat(payoutRed.amount()).isEqualByComparingTo(new BigDecimal("400.00000000"));

        // Cược 100 thắng 3 Trắng 1 Đỏ (odds 3) -> nhận 400
        Money payoutWhite = XocDiaEngine.payout(BetType.XOC_DIA_THREE_WHITE, 1, STAKE);
        assertThat(payoutWhite.amount()).isEqualByComparingTo(new BigDecimal("400.00000000"));
    }

    @Test
    void payout_withCustomOddsOverride() {
        // Admin override odds cho Chẵn từ 0.98 lên 1.0 (1 ăn 2)
        BigDecimal override = new BigDecimal("1.0");
        Money payout = XocDiaEngine.payout(BetType.XOC_DIA_EVEN, 2, STAKE, override);
        assertThat(payout.amount()).isEqualByComparingTo(new BigDecimal("200.00000000"));
    }

    @Test
    void playRound_generatesValidResultAndSeed() {
        Random r = new Random(12345L);
        XocDiaEngine.RoundResult result = XocDiaEngine.playRound(r);

        assertThat(result.getCoins()).hasSize(4);
        for (int c : result.getCoins()) {
            assertThat(c).isIn(0, 1);
        }
        assertThat(result.getRedCount()).isBetween(0, 4);
        assertThat(result.getWhiteCount()).isEqualTo(4 - result.getRedCount());
        assertThat(result.getSeed()).hasSize(64);
        assertThat(result.getSeedHash()).hasSize(64);
    }

    @Test
    void fromCoins_formatsCorrectly() {
        XocDiaEngine.RoundResult result = XocDiaEngine.fromCoins(List.of(1, 1, 0, 1), "test-seed");
        assertThat(result.getRedCount()).isEqualTo(3);
        assertThat(result.getWhiteCount()).isEqualTo(1);
        assertThat(result.isOdd()).isTrue();
        assertThat(result.isEven()).isFalse();
        assertThat(result.formattedCoins()).isEqualTo("RED,RED,WHITE,RED");
    }
}
