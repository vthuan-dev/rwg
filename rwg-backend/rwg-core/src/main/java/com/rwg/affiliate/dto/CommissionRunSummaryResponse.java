package com.rwg.affiliate.dto;

/**
 * Kết quả một đợt chi hoa hồng do admin chạy tay.
 *
 * {@code skipped} gộp cả hai lý do: đã chi trước đó (idempotent no-op) và chi lỗi.
 * Log server phân biệt rõ hai trường hợp; response chỉ cần cho admin biết có gì
 * chưa xử lý được để đi soi log.
 */
public record CommissionRunSummaryResponse(
        String periodDate,
        int agentsProcessed,
        int runsCreated,
        int skipped,
        String totalPaid
) {
}
