package com.rwg.identity.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Yêu cầu đăng xuất: thu hồi refresh token phía client đang giữ.
 */
public record LogoutRequest(
        @NotBlank(message = "{validation.logout.refresh_token.not_blank}")
        String refreshToken
) {
}
