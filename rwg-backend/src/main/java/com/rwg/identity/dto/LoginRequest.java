package com.rwg.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Yêu cầu đăng nhập: identifier là username HOẶC email.
 * captchaToken để trống khi server chưa yêu cầu captcha; sau >= captchaThreshold
 * lần sai, server ENFORCE captcha (thiếu token hợp lệ -> 429 CAPTCHA_REQUIRED,
 * kể cả khi đúng mật khẩu).
 */
public record LoginRequest(
        @NotBlank(message = "{validation.login.identifier.not_blank}")
        @Size(max = 255, message = "{validation.login.identifier.size}")
        String identifier,

        @NotBlank(message = "{validation.login.password.not_blank}")
        @Size(max = 72, message = "{validation.login.password.size}")
        String password,

        String captchaToken
) {
}
