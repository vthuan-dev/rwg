package com.rwg.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Cấu hình chat hỗ trợ (prefix rwg.chat).
 *
 * Mọi giá trị đều có mặc định trong constructor: thiếu khối `rwg.chat` trong
 * application.yml thì app vẫn khởi động với giá trị hợp lý, thay vì ném
 * NullPointerException lúc gửi tin nhắn đầu tiên.
 */
@ConfigurationProperties(prefix = "rwg.chat")
public record ChatProperties(

        /**
         * Số tin tối đa một người chơi gửi được trong {@link #rateWindow}.
         *
         * VÌ SAO CẦN: mỗi tin là một dòng INSERT cộng một gói WebSocket đẩy tới mọi
         * nhân sự đang mở hộp thư. Không có hạn mức thì một script giữ phím Enter đủ
         * làm hộp thư của cả đội hỗ trợ không dùng được.
         *
         * 20 tin / phút cao hơn hẳn tốc độ gõ của người thật (một người gõ nhanh gửi
         * chừng 6–8 tin ngắn mỗi phút), nên người dùng bình thường không bao giờ chạm
         * ngưỡng — đúng mục tiêu của một hạn mức chống lạm dụng.
         */
        int rateLimitPerWindow,

        /** Cửa sổ tính hạn mức gửi tin. */
        Duration rateWindow,

        /** Số tin mỗi trang khi tải lịch sử. */
        int pageSize,

        /**
         * Kênh Redis pub/sub bắc cầu sự kiện chat giữa app player (8080) và app admin (8081).
         *
         * CẦN vì broker STOMP đang là {@code enableSimpleBroker} — in-memory, không
         * relay giữa hai JVM. Không có cầu này thì tin nhân sự gửi ở app admin không
         * bao giờ tới được trình duyệt người chơi đang nối vào app player.
         */
        String relayChannel
) {

    public ChatProperties {
        if (rateLimitPerWindow <= 0) rateLimitPerWindow = 20;
        if (rateWindow == null) rateWindow = Duration.ofMinutes(1);
        if (pageSize <= 0) pageSize = 30;
        if (relayChannel == null || relayChannel.isBlank()) relayChannel = "rwg:chat:events";
    }
}
