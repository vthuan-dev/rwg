package com.rwg.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Yêu cầu Admin tự đổi mật khẩu đăng nhập (cấp 1) cho người chơi.
 */
public record AdminOverridePasswordRequest(
    @NotBlank(message = "Mật khẩu mới không được để trống")
    @Size(min = 6, max = 100, message = "Mật khẩu phải từ 6 đến 100 ký tự")
    String newPassword
) {}
