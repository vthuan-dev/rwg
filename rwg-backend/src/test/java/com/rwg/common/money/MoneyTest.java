package com.rwg.common.money;

import com.rwg.common.ApiException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit test quy ước tiền tệ DECISIONS.md: BigDecimal, scale 8, HALF_UP, cấm float/double.
 */
class MoneyTest {

    @Test
    void normalizesScaleTo8() {
        assertThat(Money.of("10").amount().scale()).isEqualTo(8);
        assertThat(Money.of("10.5").amount()).isEqualByComparingTo(new BigDecimal("10.50000000"));
    }

    @Test
    void roundsHalfUp() {
        // 1.234567895 -> làm tròn HALF_UP ở scale 8 -> 1.23456790
        assertThat(Money.of("1.234567895").amount().toPlainString()).isEqualTo("1.23456790");
    }

    @Test
    void rejectsFloatAndDouble() {
        assertThatThrownBy(() -> Money.of(10.5d))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("float/double");
        assertThatThrownBy(() -> Money.of(10.5f))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void addsAndSubtracts() {
        assertThat(Money.of("10").add(Money.of("5")).amount())
                .isEqualByComparingTo("15");
        assertThat(Money.of("10").subtract(Money.of("4")).amount())
                .isEqualByComparingTo("6");
    }

    @Test
    void winningPayoutIsStakePlusProfitAtOdds() {
        // Cược $10 thắng odds 35:1 (Straight) -> nhận $360 (350 lời + 10 hoàn stake)
        Money stake = Money.of("10");
        Money payout = stake.winningPayoutAtOdds(new BigDecimal("35"));
        assertThat(payout.amount()).isEqualByComparingTo("360");
        assertThat(stake.profitAtOdds(new BigDecimal("35")).amount())
                .isEqualByComparingTo("350");
    }

    @Test
    void comparesUsingCompareTo() {
        assertThat(Money.of("10").compareAmountTo(Money.of("10.00"))).isZero();
        assertThat(Money.of("9.99").compareAmountTo(Money.of("10"))).isNegative();
        assertThat(Money.of("10").isPositive()).isTrue();
        assertThat(Money.zero().isNotNegative()).isTrue();
    }
}
