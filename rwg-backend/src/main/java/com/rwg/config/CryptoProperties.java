package com.rwg.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cấu hình mã hóa dữ liệu nhạy cảm (prefix rwg.crypto).
 * - bankEncKey: khóa mã số tài khoản ngân hàng (AES-256-GCM, env RWG_BANK_ENC_KEY).
 *
 * Pattern giống JWT_SECRET: base KHÔNG có default -> thiếu biến môi trường thì
 * fail-fast khi khởi động ở MỌI profile; chỉ profile dev được đặt default dev
 * (ghi rõ CẤM dùng cho môi trường thật).
 */
@ConfigurationProperties(prefix = "rwg.crypto")
public record CryptoProperties(
        String bankEncKey
) {
}
