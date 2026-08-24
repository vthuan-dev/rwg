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
        List<String> allowedOriginPatterns,

        /**
         * Nhóm người dùng được phép mở phiên STOMP trên tiến trình NÀY.
         *
         * VÌ SAO CẦN: hai app dùng CHUNG {@code JWT_SECRET} và chung {@code issuer},
         * nên một token hợp lệ ở app này cũng hợp lệ ở app kia. Trước khi có thuộc
         * tính này, {@code WsAuthChannelInterceptor} chấp nhận mọi JWT giải mã được —
         * nghĩa là token của PLAYER mở được phiên trên broker của khu quản trị.
         *
         * PLAYER : app người chơi (8080) — chỉ vai trò PLAYER được kết nối.
         * STAFF  : app quản trị (8081) — chỉ vai trò quản trị được kết nối.
         *
         * Mặc định PLAYER: app người chơi là app công khai, nên nếu ai quên khai báo
         * thì hệ quả là "nhân sự không kết nối được" (lộ ra ngay lúc dùng) chứ không
         * phải "người chơi vào được broker quản trị" (không ai thấy cho tới khi bị lợi dụng).
         */
        Audience audience
) {
    public WebSocketProperties {
        allowedOriginPatterns = allowedOriginPatterns == null
                ? List.of()
                : List.copyOf(allowedOriginPatterns);
        if (audience == null) {
            audience = Audience.PLAYER;
        }
    }

    /** Nhóm người dùng được phép kết nối STOMP vào tiến trình này. */
    public enum Audience {
        PLAYER,
        STAFF
    }
}
