package com.rwg.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Cấu hình theo dõi người chơi đang có mặt (prefix rwg.presence).
 *
 * Mọi giá trị đều có mặc định trong constructor: thiếu khối `rwg.presence` trong
 * application.yml thì app vẫn khởi động với giá trị hợp lý thay vì ném
 * NullPointerException ở request đầu tiên của người chơi.
 */
@ConfigurationProperties(prefix = "rwg.presence")
public record PresenceProperties(

        /**
         * Khoảng lặng tối đa vẫn được coi là "đang online".
         *
         * VÌ SAO KHÔNG NGẮN HƠN: người chơi ngồi đọc một trang không phát ra request nào,
         * và việc làm mới định kỳ qua WebSocket chạy mỗi {@link #refreshInterval}. Ngưỡng
         * xấp xỉ ba lần chu kỳ đó chịu được vài lần trượt mạng liên tiếp mà không nháy
         * qua lại giữa online và offline.
         *
         * VÌ SAO KHÔNG DÀI HƠN: đây chính là độ trễ để một người đóng tab hiện thành
         * offline. Dài hơn thì người vận hành thấy "đang online" cho người đã rời đi.
         */
        Duration onlineWindow,

        /**
         * Thời gian giữ mốc hoạt động cuối, tức TTL của khoá Redis.
         *
         * Dài hơn hẳn {@link #onlineWindow} là CÓ CHỦ Ý: hết cửa sổ online thì mốc đó
         * chuyển vai, từ "đang ở đây" thành "thấy lần cuối lúc nào". Đặt TTL bằng cửa sổ
         * online sẽ làm thông tin lần cuối biến mất đúng lúc nó bắt đầu có ích.
         */
        Duration retention,

        /**
         * Chu kỳ quét các phiên WebSocket đang mở để làm mới mốc hoạt động.
         *
         * CẦN vì một người chơi có thể ngồi im trên trang game hàng chục phút, chỉ nhận
         * kết quả qua WebSocket mà không gọi REST nào — chỉ dựa vào request HTTP thì họ
         * bị coi là đã rời đi.
         */
        Duration refreshInterval
) {

    public PresenceProperties {
        if (onlineWindow == null) onlineWindow = Duration.ofSeconds(90);
        if (retention == null) retention = Duration.ofDays(30);
        if (refreshInterval == null) refreshInterval = Duration.ofSeconds(30);
    }
}
