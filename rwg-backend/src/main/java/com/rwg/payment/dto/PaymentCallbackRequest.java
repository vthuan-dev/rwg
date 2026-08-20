package com.rwg.payment.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Webhook từ provider thanh toán (POST /api/v1/payments/callback).
 * Idempotent theo providerTxnId — lệnh đã ở trạng thái cuối thì KHÔNG xử lý lại.
 * TODO chặng sau: xác thực chữ ký provider trước khi tin payload.
 */
public record PaymentCallbackRequest(
        @NotBlank(message = "{validation.payment_callback.provider_txn_id.not_blank}")
        String providerTxnId,

        @NotBlank(message = "{validation.payment_callback.status.not_blank}")
        String status
) {
}
