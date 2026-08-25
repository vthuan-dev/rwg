package com.rwg.chat.dto;

import java.time.Instant;

/**
 * Gói sự kiện chat gửi qua WebSocket, VÀ qua Redis pub/sub để bắc cầu giữa hai app.
 *
 * MỘT gói dùng cho cả hai đường đi. Cầu Redis chỉ cần chuyển tiếp đúng gói này
 * xuống broker cục bộ của app kia mà không phải dựng lại — nếu dùng hai định dạng
 * riêng thì mỗi lần thêm một trường phải sửa hai chỗ và chúng sẽ lệch nhau.
 *
 * `type` để client phân biệt việc cần làm:
 * - MESSAGE         : có tin mới, hiện vào luồng.
 * - READ            : phía đối diện đã xem (đổi dấu tích, không thêm bong bóng).
 * - CONVERSATION    : trạng thái luồng đổi (được nhận việc / bị đóng).
 * - MESSAGES_DELETED: một hay nhiều tin bị xóa bởi nhân sự — client xóa khỏi màn hình ngay.
 *
 * `targetUserId` cho biết gói này thuộc về người chơi nào. App player dùng nó để
 * unicast tới đúng người; app admin dùng để biết dòng nào trong hộp thư cần làm mới.
 *
 * `message` null với gói READ và CONVERSATION — hai loại đó không mang tin nhắn nào.
 */
public record ChatEventPayload(
        String type,
        String conversationId,
        String targetUserId,
        ChatMessageResponse message,
        /** OPEN | CLOSED; chỉ có nghĩa với type = CONVERSATION. */
        String status,
        /**
         * Gói này CHỈ dành cho nhân sự, không được đẩy xuống người chơi.
         *
         * Cần cờ tường minh thay vì suy ra từ nội dung gói (ví dụ "có withdrawal thì là
         * nội bộ"): {@code ChatEventPublisher} là điểm duy nhất quyết định gửi đi đâu, và
         * nó không nên phải hiểu ý nghĩa nghiệp vụ của từng loại tin để làm việc đó. Suy
         * đoán như vậy cũng sẽ sai ngay khi có loại tin nội bộ thứ hai không mang lệnh rút.
         */
        boolean staffOnly,
        /**
         * Danh sách id của tin bị xóa; chỉ có nghĩa với type = MESSAGES_DELETED.
         *
         * Null với mọi loại khác. Lựa chọn kiểu String[] thay vì List: Jackson dùng
         * array cho cả hai chiều và JSON cũng gọn hơn khi đi qua Redis.
         */
        String[] deletedMessageIds,
        Instant serverTime
) {

    public static final String TYPE_MESSAGE = "MESSAGE";
    public static final String TYPE_READ = "READ";
    public static final String TYPE_CONVERSATION = "CONVERSATION";
    public static final String TYPE_MESSAGES_DELETED = "MESSAGES_DELETED";

    public static ChatEventPayload message(String conversationId, String targetUserId,
                                           ChatMessageResponse message) {
        return new ChatEventPayload(TYPE_MESSAGE, conversationId, targetUserId, message,
                null, false, null, Instant.now());
    }

    /**
     * Tin mới CHỈ hiện ở khu quản trị.
     *
     * Vẫn mang {@code targetUserId} dù không unicast tới người đó: hộp thư của nhân sự
     * dùng trường này để biết dòng nào cần làm mới. Bỏ trống sẽ khiến thẻ hiện trong
     * luồng đang mở nhưng dòng tương ứng trong danh sách bên trái không đổi gì.
     */
    public static ChatEventPayload staffOnlyMessage(String conversationId, String targetUserId,
                                                    ChatMessageResponse message) {
        return new ChatEventPayload(TYPE_MESSAGE, conversationId, targetUserId, message,
                null, true, null, Instant.now());
    }

    public static ChatEventPayload read(String conversationId, String targetUserId) {
        return new ChatEventPayload(TYPE_READ, conversationId, targetUserId, null,
                null, false, null, Instant.now());
    }

    public static ChatEventPayload conversation(String conversationId, String targetUserId,
                                               String status) {
        return new ChatEventPayload(TYPE_CONVERSATION, conversationId, targetUserId, null,
                status, false, null, Instant.now());
    }

    /**
     * Thông báo một số tin đã bị xóa — cả admin và player đều nhận.
     *
     * Client nhận gói này và xóa các bong bóng tương ứng khỏi màn hình ngay lập tức.
     */
    public static ChatEventPayload messagesDeleted(String conversationId, String targetUserId,
                                                   String[] deletedMessageIds) {
        return new ChatEventPayload(TYPE_MESSAGES_DELETED, conversationId, targetUserId, null,
                null, false, deletedMessageIds, Instant.now());
    }
}
