package com.rwg.notification.dto;

import com.rwg.notification.domain.Notification;

import java.time.Instant;

/**
 * Một thông báo trả về cho client.
 *
 * TRẢ KHOÁ DỊCH + THAM SỐ, không trả câu đã dịch: client dịch theo ngôn ngữ đang hiển thị.
 * Nhờ vậy người dùng đổi ngôn ngữ thì các thông báo cũ cũng đổi theo, thay vì đóng băng ở
 * ngôn ngữ lúc chúng được tạo ra.
 *
 * `paramsJson` giữ nguyên dạng chuỗi JSON thay vì tự phân tích thành Map: nội dung do backend
 * tạo ra và client cũng chỉ cần đọc nguyên khối, nên chuyển đổi thêm một lần ở giữa không đem
 * lại gì. Client tự `JSON.parse`.
 */
public record NotificationResponse(
        String id,
        String type,
        String titleKey,
        /** JSON phẳng, vd {"amount":"500.00"}. null khi khoá không cần tham số. */
        String paramsJson,
        /** Nội dung tự do của tin chung. null với thông báo sinh tự động. */
        String body,
        /** true = tin chung cho mọi người. */
        boolean broadcast,
        /** null = chưa đọc. */
        Instant readAt,
        Instant createdAt
) {

    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.getId().toString(),
                n.getType().name(),
                n.getTitleKey(),
                n.getParamsJson(),
                n.getBody(),
                n.isBroadcast(),
                n.getReadAt(),
                n.getCreatedAt());
    }
}
