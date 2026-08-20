package com.rwg.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Admin nâng/hạ quyền tài khoản (PLAYER | ADMIN). */
public record ChangeUserRoleRequest(
        @NotBlank(message = "{validation.admin.role.not_blank}")
        String role,

        @Size(max = 255, message = "{validation.admin.reason.size}")
        String reason
) {
}
