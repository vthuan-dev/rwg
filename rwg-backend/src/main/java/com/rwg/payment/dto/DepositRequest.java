package com.rwg.payment.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Yêu cầu nạp tiền. amount dạng STRING (parse sang BigDecimal trong service) —
 * CẤM float/double cho tiền tệ (DECISIONS.md). Min $10 / max $50,000 theo KE-HOACH.
 */
public record DepositRequest(
        @NotBlank(message = "{validation.deposit.amount.not_blank}")
        String amount
) {
}
