package com.rwg.bank.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Thêm tài khoản ngân hàng liên kết. Số tài khoản validate 6-32 chữ số;
 * service mã hóa AES-256-GCM trước khi lưu — KHÔNG log/trả plaintext.
 */
public record BankAccountRequest(
        @NotBlank(message = "{validation.bank.bank_code.not_blank}")
        String bankCode,

        @NotBlank(message = "{validation.bank.account_number.not_blank}")
        @Pattern(regexp = "\\d{6,32}", message = "{validation.bank.account_number.invalid}")
        String accountNumber,

        @NotBlank(message = "{validation.bank.holder_name.not_blank}")
        String holderName,

        /** null = tự động: tài khoản ĐẦU TIÊN tự thành default; true = đặt làm default. */
        Boolean setDefault
) {
}
