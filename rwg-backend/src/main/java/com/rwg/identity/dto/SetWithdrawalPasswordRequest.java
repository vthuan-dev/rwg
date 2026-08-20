package com.rwg.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Đặt/đổi mật khẩu rút tiền. BẮT BUỘC xác nhận lại mật khẩu đăng nhập (loginPassword).
 */
public record SetWithdrawalPasswordRequest(
        @NotBlank(message = "{validation.withdrawal.login_password.not_blank}")
        String loginPassword,

        @NotBlank(message = "{validation.withdrawal.new_password.not_blank}")
        @Size(min = 6, max = 72, message = "{validation.withdrawal.new_password.size}")
        String newWithdrawalPassword
) {
}
