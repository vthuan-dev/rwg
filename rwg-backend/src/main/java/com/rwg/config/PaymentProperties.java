package com.rwg.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Cấu hình cổng thanh toán (prefix rwg.payment).
 * - provider: "stub" = gateway giả lập auto-success (tích hợp provider thật ở chặng sau).
 * - successDelay: độ trễ giả lập trước khi gateway báo SUCCESS (test đặt PT0S).
 * - callbackSecret: shared-secret xác thực webhook callback (fix review M4);
 *   base KHÔNG có default -> thiếu RWG_PAYMENT_CALLBACK_SECRET thì fail-fast.
 */
@ConfigurationProperties(prefix = "rwg.payment")
public record PaymentProperties(
        String provider,
        Duration successDelay,
        String callbackSecret
) {
}
