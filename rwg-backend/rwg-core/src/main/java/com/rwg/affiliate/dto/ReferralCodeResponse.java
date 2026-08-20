package com.rwg.affiliate.dto;

/**
 * Mã giới thiệu của chính người chơi (GET /api/v1/affiliate/me/code).
 *
 * Trả kèm đường dẫn đăng ký sẵn để người chơi copy gửi bạn bè — tránh việc họ tự
 * ghép link sai rồi mất công giới thiệu mà không được tính hoa hồng.
 */
public record ReferralCodeResponse(
        String code,
        String registerPath
) {
    public static ReferralCodeResponse of(String code) {
        return new ReferralCodeResponse(code, "/register?ref=" + code);
    }
}
