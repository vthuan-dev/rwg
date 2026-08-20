package com.rwg.payment.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Yêu cầu rút tiền. amount dạng STRING (parse BigDecimal trong service).
 * withdrawalPassword so khớp hash đã đặt (bắt buộc trước khi debit).
 */
public record WithdrawalRequest(
        @NotBlank(message = "{validation.withdrawal.amount.not_blank}")
        String amount,

        @NotBlank(message = "{validation.withdrawal.withdrawal_password.not_blank}")
        String withdrawalPassword
) {
}
