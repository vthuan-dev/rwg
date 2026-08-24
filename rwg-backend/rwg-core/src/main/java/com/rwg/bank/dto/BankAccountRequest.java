package com.rwg.bank.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Thêm phương thức nhận tiền: tài khoản ngân hàng.
 *
 * Chỉ hỗ trợ ngân hàng (BANK), gỡ bỏ hoàn toàn crypto (USDT/TRC20/...).
 *
 * Service mã hóa AES-256-GCM trước khi lưu — KHÔNG log/trả plaintext.
 */
public record BankAccountRequest(
        /** Mã ngân hàng. Bắt buộc. */
        @NotBlank(message = "{validation.bank.bank_code.not_blank}")
        String bankCode,

        /**
         * Số tài khoản ngân hàng.
         */
        @NotBlank(message = "{validation.bank.account_number.not_blank}")
        String accountNumber,

        /** Tên chủ tài khoản. Bắt buộc. */
        @NotBlank(message = "{validation.bank.holder_name.not_blank}")
        String holderName,

        /** null = tự động: phương thức ĐẦU TIÊN tự thành default; true = đặt làm default. */
        Boolean setDefault,

        /**
         * Mật khẩu rút tiền, để xác nhận lại danh tính.
         *
         * VÌ SAO BẮT BUỘC: đây là thao tác nhạy cảm nhất sau khi ai đó chiếm được phiên
         * đăng nhập đang mở — thêm được một tài khoản nhận tiền rồi đặt nó làm mặc định là
         * chuyển hướng được toàn bộ tiền rút của nạn nhân. Mật khẩu đăng nhập một mình
         * không đủ: người dùng có thể để máy mở, hoặc token bị lấy qua XSS.
         */
        @NotBlank(message = "{validation.bank.withdrawal_password.not_blank}")
        String withdrawalPassword
) {
}
