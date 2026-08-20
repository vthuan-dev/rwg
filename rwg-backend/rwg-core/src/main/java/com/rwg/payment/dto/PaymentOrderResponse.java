package com.rwg.payment.dto;

import com.rwg.payment.domain.PaymentOrder;

import java.time.Instant;

/**
 * Response lệnh thanh toán (nạp/rút). amount dạng STRING — CẤM float/double.
 * KHÔNG trả providerTxnId về client (fix review M4): đó là mã nội bộ phía
 * provider, attacker đoán được có thể dựng callback giả.
 */
public record PaymentOrderResponse(
        String id,
        String type,
        String amount,
        String currency,
        String status,
        String bankAccountId,
        Instant createdAt
) {

    public static PaymentOrderResponse from(PaymentOrder o) {
        return new PaymentOrderResponse(
                o.getId().toString(),
                o.getType().name(),
                o.getAmount().toPlainString(),
                o.getCurrency(),
                o.getStatus().name(),
                o.getBankAccountId() == null ? null : o.getBankAccountId().toString(),
                o.getCreatedAt());
    }
}
