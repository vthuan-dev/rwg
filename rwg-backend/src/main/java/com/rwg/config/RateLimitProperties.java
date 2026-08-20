package com.rwg.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Cấu hình rate-limit đăng nhập (prefix rwg.rate-limit).
 * Hai bucket chạy song song:
 * - Bucket IP+identifier: capacity = lockThreshold, refill toàn bộ mỗi loginWindow.
 *   Vượt captchaThreshold lần sai -> cờ captcha; chạm lockThreshold -> khóa.
 * - Bucket CHỈ identifier (khóa tài khoản toàn cục, bất kể IP):
 *   capacity = accountLockThreshold.
 * Khi chạm ngưỡng khóa, ghi LOCK MARKER riêng có TTL đúng loginWindow
 * (không dựa thời điểm refill bucket) để khóa đủ 15 phút thật.
 */
@ConfigurationProperties(prefix = "rwg.rate-limit")
public record RateLimitProperties(
        Duration loginWindow,
        int captchaThreshold,
        int lockThreshold,
        int accountLockThreshold
) {
}
