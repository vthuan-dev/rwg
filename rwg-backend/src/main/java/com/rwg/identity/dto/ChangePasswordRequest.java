package com.rwg.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Đổi mật khẩu đăng nhập. newPassword validate ký tự 8-72 ở DTO;
 * service kiểm thêm BYTE UTF-8 <= 72 (BCrypt truncation).
 */
public record ChangePasswordRequest(
        @NotBlank(message = "{validation.change_password.old_password.not_blank}")
        String oldPassword,

        @NotBlank(message = "{validation.change_password.new_password.not_blank}")
        @Size(min = 8, max = 72, message = "{validation.change_password.new_password.size}")
        String newPassword
) {
}
