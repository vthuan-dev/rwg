package com.rwg.affiliate;

import com.rwg.affiliate.service.CommissionCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test THUẦN cho công thức hoa hồng — không Spring context, không DB.
 *
 * Công thức dính tiền nên các biên phải được khẳng định rõ ràng: turnover/rate
 * bằng 0 hoặc âm, và trường hợp làm tròn scale 8 về 0.
 */
class CommissionCalculatorTest {

    private static final UUID AGENT = UUID.randomUUID();

    @Test
    @DisplayName("Hoa hồng = turnover × rate, scale 8")
    void computesCommissionFromTurnoverAndRate() {
        CommissionCalculator.Result result = CommissionCalculator.calculate(
                AGENT, 1, new BigDecimal("1000"), new BigDecimal("0.005"));

        assertThat(result.payable()).isTrue();
        assertThat(result.amount()).isEqualByComparingTo("5");
        assertThat(result.amount().scale()).isEqualTo(8);
        assertThat(result.level()).isEqualTo(1);
        assertThat(result.agentId()).isEqualTo(AGENT);
    }

    @Test
    @DisplayName("Giữ nguyên turnover và rate đã dùng để đối soát về sau")
    void keepsInputsForAudit() {
        CommissionCalculator.Result result = CommissionCalculator.calculate(
                AGENT, 2, new BigDecimal("12345.67891234"), new BigDecimal("0.002"));

        assertThat(result.turnover()).isEqualByComparingTo("12345.67891234");
        assertThat(result.rate()).isEqualByComparingTo("0.002");
        assertThat(result.amount()).isEqualByComparingTo("24.69135782");
    }

    @Test
    @DisplayName("Turnover 0 -> không chi (tránh dòng ledger 0 đồng)")
    void zeroTurnoverIsNotPayable() {
        CommissionCalculator.Result result = CommissionCalculator.calculate(
                AGENT, 1, BigDecimal.ZERO, new BigDecimal("0.005"));

        assertThat(result.payable()).isFalse();
        assertThat(result.amount()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Rate 0 (cấp bị tắt) -> không chi")
    void zeroRateIsNotPayable() {
        CommissionCalculator.Result result = CommissionCalculator.calculate(
                AGENT, 2, new BigDecimal("1000"), BigDecimal.ZERO);

        assertThat(result.payable()).isFalse();
        assertThat(result.amount()).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("Turnover âm (dữ liệu bẩn) -> không chi, KHÔNG sinh hoa hồng âm trừ tiền đại lý")
    void negativeTurnoverNeverProducesNegativeCommission() {
        CommissionCalculator.Result result = CommissionCalculator.calculate(
                AGENT, 1, new BigDecimal("-5000"), new BigDecimal("0.005"));

        assertThat(result.payable()).isFalse();
        assertThat(result.amount().signum()).isNotNegative();
    }

    @Test
    @DisplayName("Null an toàn -> không chi thay vì NullPointerException")
    void nullInputsAreNotPayable() {
        assertThat(CommissionCalculator.calculate(AGENT, 1, null, new BigDecimal("0.005")).payable())
                .isFalse();
        assertThat(CommissionCalculator.calculate(AGENT, 1, new BigDecimal("100"), null).payable())
                .isFalse();
    }

    @Test
    @DisplayName("Turnover cực nhỏ làm tròn về 0 -> không chi")
    void amountRoundedToZeroIsNotPayable() {
        // 0.000000001 × 0.005 = 5e-12 -> scale 8 HALF_UP = 0
        CommissionCalculator.Result result = CommissionCalculator.calculate(
                AGENT, 1, new BigDecimal("0.000000001"), new BigDecimal("0.005"));

        assertThat(result.amount()).isEqualByComparingTo("0");
        assertThat(result.payable()).isFalse();
    }
}
