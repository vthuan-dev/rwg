package com.rwg.affiliate.dto;

import com.rwg.affiliate.domain.CommissionRun;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Một khoản hoa hồng NHÌN TỪ PHÍA ĐẠI LÝ (GET /api/v1/affiliate/me/commissions).
 *
 * Khác {@link CommissionRunResponse} của admin ở hai điểm, đều có chủ ý:
 * - KHÔNG trả {@code agentId}: người chơi đã biết đó là mình, để lộ UUID nội bộ
 *   ra API công khai là không cần thiết.
 * - KHÔNG trả {@code turnover} và {@code rate}: turnover là tổng cược của tuyến
 *   dưới — dữ liệu cá nhân của người khác. Đại lý chỉ cần biết mình nhận bao nhiêu.
 */
public record MyCommissionResponse(
        LocalDate periodDate,
        int level,
        String amount,
        Instant createdAt
) {
    public static MyCommissionResponse from(CommissionRun run) {
        return new MyCommissionResponse(
                run.getPeriodDate(),
                run.getLevel(),
                run.getAmount().toPlainString(),
                run.getCreatedAt());
    }
}
