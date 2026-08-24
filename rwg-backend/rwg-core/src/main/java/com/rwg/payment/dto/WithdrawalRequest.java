package com.rwg.payment.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Yêu cầu rút tiền. amount dạng STRING (parse BigDecimal trong service).
 * withdrawalPassword so khớp hash đã đặt (bắt buộc trước khi debit).
 *
 * bankAccountId KHÔNG bắt buộc: bỏ trống thì service dùng tài khoản MẶC ĐỊNH, giữ đúng
 * hành vi cũ nên các lời gọi đã có (và bộ test) không phải sửa. Có giá trị thì service
 * BẮT BUỘC kiểm tài khoản đó thuộc chính người đang đăng nhập — thiếu bước kiểm này thì
 * bất cứ ai cũng gửi được id tài khoản của người khác để chuyển tiền ra ngoài.
 */
public record WithdrawalRequest(
        @NotBlank(message = "{validation.withdrawal.amount.not_blank}")
        String amount,

        @NotBlank(message = "{validation.withdrawal.withdrawal_password.not_blank}")
        String withdrawalPassword,

        /** null = dùng tài khoản nhận tiền mặc định. */
        String bankAccountId
) {
}
