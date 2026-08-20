package com.rwg.affiliate.service;

import com.rwg.common.money.Money;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Tính toán hoa hồng THUẦN — KHÔNG chạm DB, KHÔNG side effect.
 *
 * Tách riêng khỏi {@link CommissionJob} để phần công thức (dễ sai nhất, dính tiền)
 * kiểm thử được bằng unit test nhanh, không cần Spring context hay database.
 *
 * Công thức (đặc tả mục 5.2): hoa hồng = tổng cược hợp lệ của tuyến dưới × % cấp.
 * Dùng BigDecimal scale 8 HALF_UP qua {@link Money} — CẤM float/double.
 */
public final class CommissionCalculator {

    private CommissionCalculator() {
    }

    /**
     * Kết quả tính cho một đại lý ở một cấp.
     *
     * @param payable false khi số tiền làm tròn về 0 — KHÔNG ghi chứng từ và
     *                KHÔNG gọi credit ví, tránh sinh dòng ledger 0 đồng vô nghĩa.
     */
    public record Result(UUID agentId, int level, BigDecimal turnover, BigDecimal rate,
                         BigDecimal amount, boolean payable) {
    }

    /**
     * Tính hoa hồng từ turnover và tỷ lệ.
     *
     * Turnover âm là dữ liệu không hợp lệ (stake luôn dương) -> coi như 0 thay vì
     * tạo hoa hồng âm sẽ trừ tiền đại lý.
     */
    public static Result calculate(UUID agentId, int level, BigDecimal turnover, BigDecimal rate) {
        if (turnover == null || turnover.signum() <= 0 || rate == null || rate.signum() <= 0) {
            return new Result(agentId, level,
                    turnover == null ? BigDecimal.ZERO : turnover,
                    rate == null ? BigDecimal.ZERO : rate,
                    BigDecimal.ZERO, false);
        }
        BigDecimal amount = Money.of(turnover).multiply(rate).amount();
        // Làm tròn scale 8 có thể cho 0 khi turnover rất nhỏ; khi đó không chi.
        return new Result(agentId, level, turnover, rate, amount, amount.signum() > 0);
    }
}
