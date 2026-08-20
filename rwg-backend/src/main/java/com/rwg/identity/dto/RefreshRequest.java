package com.rwg.identity.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Yêu cầu xoay vòng refresh token (rotation: token cũ bị thu hồi ngay).
 */
public record RefreshRequest(
        @NotBlank(message = "{validation.refresh.refresh_token.not_blank}")
        String refreshToken
) {
}
