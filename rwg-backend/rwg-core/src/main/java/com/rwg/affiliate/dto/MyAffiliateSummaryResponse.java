package com.rwg.affiliate.dto;

/**
 * Tổng quan hệ đại lý của chính người chơi (GET /api/v1/affiliate/me/summary).
 *
 * CỐ TÌNH KHÔNG trả turnover của tuyến dưới: đó là dữ liệu cá nhân của người khác.
 * Đại lý chỉ cần biết mình được trả bao nhiêu, không cần biết từng người chơi bao nhiêu.
 */
public record MyAffiliateSummaryResponse(
        String code,
        long level1Count,
        long level2Count,
        String totalCommissionEarned,
        String level1Rate,
        String level2Rate
) {
}
