package com.rwg.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Cấu hình JWT / session (prefix rwg.security).
 */
@ConfigurationProperties(prefix = "rwg.security")
public record SecurityProperties(
        String jwtSecret,
        String issuer,
        Duration accessTokenTtl,
        Duration refreshTokenTtl
) {

    public SecretKey hmacKey() {
        byte[] bytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("rwg.security.jwt-secret phải dài tối thiểu 32 ký tự (HS256)");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }
}
