package com.rwg.identity.dto;

/**
 * Cặp token trả về sau login/refresh.
 * refreshToken là opaque token (không phải JWT) lưu phía server với TTL.
 */
public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long accessTokenExpiresIn
) {
    public TokenResponse(String accessToken, String refreshToken, long accessTokenExpiresIn) {
        this(accessToken, refreshToken, "Bearer", accessTokenExpiresIn);
    }
}
