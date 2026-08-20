package com.rwg.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cấu hình captcha (prefix rwg.captcha).
 * - enforced: bật/tắt việc enforce captcha phía server (theo profile).
 * - provider: "dev" = stub chấp nhận token khác rỗng; sau này thêm provider thật.
 */
@ConfigurationProperties(prefix = "rwg.captcha")
public record CaptchaProperties(
        boolean enforced,
        String provider
) {
}
