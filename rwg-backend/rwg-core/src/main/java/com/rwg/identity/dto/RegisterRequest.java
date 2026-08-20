package com.rwg.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Yêu cầu đăng ký tài khoản PLAYER.
 */
public record RegisterRequest(
        @NotBlank(message = "{validation.register.username.not_blank}")
        @Size(min = 3, max = 32, message = "{validation.register.username.size}")
        @Pattern(regexp = "^[a-zA-Z0-9_.]+$", message = "{validation.register.username.pattern}")
        String username,

        @NotBlank(message = "{validation.register.email.not_blank}")
        @Email(message = "{validation.register.email.invalid}")
        @Size(max = 255, message = "{validation.register.email.size}")
        String email,

        @NotBlank(message = "{validation.register.password.not_blank}")
        @Size(min = 8, max = 72, message = "{validation.register.password.size}")
        String password,

        /**
         * Mã giới thiệu — TÙY CHỌN (null/rỗng đều hợp lệ).
         *
         * Mã sai KHÔNG làm đăng ký thất bại: người dùng không nên bị chặn tạo tài
         * khoản vì gõ sai mã của người khác. Mọi lần bỏ qua đều được ghi audit
         * (xem ReferralService.attachReferral).
         */
        @Size(max = 16, message = "{validation.register.referral_code.size}")
        @Pattern(regexp = "^$|^[A-Za-z0-9]{4,16}$",
                message = "{validation.register.referral_code.pattern}")
        String referralCode
) {
}
