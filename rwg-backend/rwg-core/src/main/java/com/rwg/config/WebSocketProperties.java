package com.rwg.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Cấu hình WebSocket (prefix rwg.websocket).
 * allowedOriginPatterns theo profile: dev đặt ["*"], môi trường khác PHẢI cấu hình
 * danh sách origin cụ thể (không hard-code "*" trong code).
 */
@ConfigurationProperties(prefix = "rwg.websocket")
public record WebSocketProperties(
        List<String> allowedOriginPatterns
) {
    public WebSocketProperties {
        allowedOriginPatterns = allowedOriginPatterns == null
                ? List.of()
                : List.copyOf(allowedOriginPatterns);
    }
}
