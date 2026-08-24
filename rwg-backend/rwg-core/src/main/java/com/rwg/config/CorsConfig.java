package com.rwg.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Nguồn cấu hình CORS cho SecurityConfig.
 *
 * Trước đây CORS bị tắt hoàn toàn (.cors(disable)), nên frontend chạy ở origin khác
 * (localhost:3000) không gọi được API: trình duyệt chặn ngay ở bước preflight và
 * request thật chưa bao giờ tới server.
 *
 * Nguyên tắc thu hẹp áp dụng ở đây:
 * - Origin: chỉ những gì khai báo tường minh trong rwg.cors.allowed-origins.
 * - Header: chỉ Authorization + Content-Type, không mở "*".
 * - Method: chỉ các method thực dùng, KHÔNG có PUT hay TRACE.
 * - allowCredentials = false: hệ thống dùng Bearer token trong header, KHÔNG dùng
 *   cookie. Bật credentials sẽ cho phép trình duyệt đính kèm cookie phiên vào
 *   request chéo origin — mở đúng cửa cho CSRF mà CSRF protection đang tắt.
 */
@Configuration
public class CorsConfig {

    /** Chỉ mở CORS cho API và endpoint media, không mở cho toàn bộ đường dẫn. */
    private static final String API_PATTERN = "/api/**";
    private static final String UPLOADS_PATTERN = "/uploads/**";

    /** Cache preflight 30 phút để trình duyệt không hỏi lại mỗi request. */
    private static final long PREFLIGHT_MAX_AGE_SECONDS = 1800L;

    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties props) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        if (!props.enabled()) {
            // Không khai báo origin nào -> không đăng ký mapping nào. Hành vi giống như
            // trước: mọi request chéo origin bị chặn. An toàn làm mặc định cho production
            // khi frontend và backend cùng domain.
            return source;
        }

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(props.allowedOrigins());
        config.setAllowedMethods(List.of(
                HttpMethod.GET.name(),
                HttpMethod.POST.name(),
                HttpMethod.PATCH.name(),
                HttpMethod.DELETE.name(),
                HttpMethod.OPTIONS.name()));
        config.setAllowedHeaders(List.of(
                HttpHeaders.AUTHORIZATION,
                HttpHeaders.CONTENT_TYPE,
                HttpHeaders.ACCEPT_LANGUAGE,
                "X-Device-Id"));
        config.setAllowCredentials(false);
        config.setMaxAge(PREFLIGHT_MAX_AGE_SECONDS);

        source.registerCorsConfiguration(API_PATTERN, config);
        source.registerCorsConfiguration(UPLOADS_PATTERN, config);
        return source;
    }
}
