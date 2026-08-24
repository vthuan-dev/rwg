package com.rwg.identity.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Thông tin user trả về API. KHÔNG lộ password hash.
 */
public record UserResponse(
        UUID id,
        String username,
        String email,
        String role,
        String status,
        String kycLevel,
        boolean hasWithdrawalPassword,
        String locale,

        /** Họ tên người dùng tự khai; null khi chưa khai. */
        String fullName,

        /**
         * Mã quốc gia ISO 3166-1 alpha-2, ví dụ "VN"; null khi chưa khai.
         *
         * Trả MÃ chứ không trả tên nước: tên hiển thị phụ thuộc ngôn ngữ đang xem nên
         * thuộc về tầng giao diện, còn API phải trả một giá trị ổn định.
         */
        String countryCode,

        /** Số điện thoại người dùng tự khai; null khi chưa khai. */
        String phone,

        Instant createdAt
) {
}
