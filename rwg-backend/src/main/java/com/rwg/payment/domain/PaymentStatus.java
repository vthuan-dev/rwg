package com.rwg.payment.domain;

/**
 * Trạng thái lệnh thanh toán (payment_orders.status).
 * - Nạp (DEPOSIT):   PENDING -> SUCCESS | FAILED.
 * - Rút (WITHDRAWAL): PENDING -> SETTLED (admin duyệt) | VOIDED (admin từ chối, hoàn tiền).
 */
public enum PaymentStatus {
    PENDING, SUCCESS, FAILED, SETTLED, VOIDED;

    /** Trạng thái cuối — không chuyển đổi nữa (dùng cho idempotency webhook/duyệt). */
    public boolean isTerminal() {
        return this != PENDING;
    }
}
