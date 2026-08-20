package com.rwg.payment.service;

/**
 * Kết quả khởi tạo lệnh phía provider.
 * - providerTxnId: mã giao dịch phía provider (khóa idempotency cho webhook).
 * - approved: provider duyệt ngay (stub: luôn true sau delay).
 */
public record GatewayResult(
        String providerTxnId,
        boolean approved
) {
}
