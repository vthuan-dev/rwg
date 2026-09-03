package com.rwg.presence.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Gắn {@link PlayerPresenceInterceptor} vào chuỗi xử lý request.
 *
 * ĐĂNG KÝ CHO MỌI ĐƯỜNG DẪN, không lọc theo tiền tố: người chơi bấm quanh ứng dụng sẽ gọi
 * vào rất nhiều nhóm điểm cuối khác nhau (ví, cược, chat, thông báo, hồ sơ). Liệt kê từng
 * tiền tố thì mỗi nhóm điểm cuối thêm về sau đều là một chỗ có thể quên, và hệ quả là
 * người chơi im lặng bị coi như đã rời đi.
 *
 * Việc lọc vai trò nằm trong chính interceptor, nên request của nhân sự đi qua đây cũng
 * không ghi gì.
 *
 * Bean này bị loại khỏi app quản trị cùng với interceptor — xem
 * {@code AdminApplication.excludeFilters}.
 */
@Configuration
public class PresenceWebConfig implements WebMvcConfigurer {

    private final PlayerPresenceInterceptor presenceInterceptor;

    public PresenceWebConfig(PlayerPresenceInterceptor presenceInterceptor) {
        this.presenceInterceptor = presenceInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(presenceInterceptor).addPathPatterns("/**");
    }
}
