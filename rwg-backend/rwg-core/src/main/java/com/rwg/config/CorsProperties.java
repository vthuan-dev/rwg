package com.rwg.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Cấu hình CORS (prefix rwg.cors).
 *
 * CỐ TÌNH KHÔNG hỗ trợ "*": frontend gửi header Authorization nên request là
 * credentialed, và trình duyệt TỪ CHỐI wildcard origin với request loại này. Khai
 * báo "*" ở đây chỉ tạo ra cấu hình trông như đã mở nhưng thực tế vẫn bị chặn, rất
 * mất thời gian để lần ra. Phải liệt kê origin cụ thể.
 */
@ConfigurationProperties(prefix = "rwg.cors")
public record CorsProperties(
        List<String> allowedOrigins
) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }

    /** CORS chỉ được bật khi có ít nhất một origin được khai báo tường minh. */
    public boolean enabled() {
        return !allowedOrigins.isEmpty();
    }
}
