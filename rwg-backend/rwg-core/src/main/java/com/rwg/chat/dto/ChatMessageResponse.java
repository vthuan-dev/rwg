package com.rwg.chat.dto;

import com.rwg.chat.domain.ChatMessage;

import java.time.Instant;

/**
 * Một tin nhắn trả về cho client.
 *
 * `body` với tin của PLAYER/STAFF là văn bản thô do người gõ; với tin SYSTEM nó là
 * KHOÁ DỊCH (vd "chat.system.assigned"). Client phân biệt qua `senderType` —
 * cùng cách {@code NotificationResponse} trả khoá dịch để client tự dịch theo ngôn
 * ngữ đang hiển thị.
 *
 * `clientMsgId` được trả LẠI cho client: giao diện hiện tin ngay lúc bấm gửi ở
 * trạng thái mờ, rồi cần đối chiếu để biết bản ghi nào từ server ứng với tin nào
 * đang chờ. Không có nó thì gói WebSocket về trước response HTTP sẽ tạo ra tin
 * hiện hai lần.
 *
 * Bốn trường `attachment*` cùng null hoặc cùng có giá trị (DB có CHECK bảo đảm cặp
 * url + type). `body` có thể rỗng khi tin CHỈ có ảnh.
 *
 * `withdrawal` chỉ có giá trị với THẺ DUYỆT LỆNH RÚT, và chỉ trên đường đọc của khu
 * quản trị. Đường của người chơi không bao giờ trả các tin đó — chúng bị lọc ngay
 * trong truy vấn (xem {@code findPageBeforeVisibleToPlayer}).
 */
public record ChatMessageResponse(
        String id,
        String conversationId,
        /** PLAYER | STAFF | SYSTEM. */
        String senderType,
        /** null với tin của hệ thống. */
        String senderId,
        /** Tên chụp lại lúc gửi; null với tin của hệ thống. */
        String senderUsername,
        /** Có thể rỗng/null khi tin chỉ có ảnh. */
        String body,
        /** Đường dẫn ảnh đính kèm; null nếu tin chỉ có chữ. */
        String attachmentUrl,
        /** IMAGE; null nếu không có đính kèm. */
        String attachmentType,
        /** Tên tệp gốc, để hiện cho người nhận và dùng khi tải về. */
        String attachmentName,
        /** Dung lượng byte, để giao diện hiện kích cỡ mà không cần gọi HEAD. */
        Long attachmentSize,
        /** null = phía đối diện chưa xem. */
        Instant readAt,
        /** null nếu client không gửi kèm. */
        String clientMsgId,
        Instant createdAt,
        /**
         * Thông tin lệnh rút của thẻ duyệt; null với mọi tin thường.
         *
         * {@link #from} luôn đặt null, {@link #withWithdrawal} gắn vào sau — xem lý do
         * ở hai hàm đó.
         */
        ChatWithdrawalCardResponse withdrawal
) {

    /**
     * Dựng DTO từ entity.
     *
     * LUÔN ĐẶT {@code withdrawal} = null. Thông tin lệnh rút nằm ở bảng khác, và tra nó
     * tại đây nghĩa là mỗi tin nhắn trong trang thành một truy vấn phụ. Nhân sự tải lịch
     * sử 30 tin là 30 lượt gọi DB thêm, trên màn hình được mở lại liên tục. Chỗ gọi nạp
     * theo LÔ rồi gắn qua {@link #withWithdrawal}.
     */
    public static ChatMessageResponse from(ChatMessage m) {
        return new ChatMessageResponse(
                m.getId().toString(),
                m.getConversationId().toString(),
                m.getSenderType().name(),
                m.getSenderId() == null ? null : m.getSenderId().toString(),
                m.getSenderUsername(),
                m.getBody(),
                m.getAttachmentUrl(),
                m.getAttachmentType() == null ? null : m.getAttachmentType().name(),
                m.getAttachmentName(),
                m.getAttachmentSize(),
                m.getReadAt(),
                m.getClientMsgId() == null ? null : m.getClientMsgId().toString(),
                m.getCreatedAt(),
                null);
    }

    /**
     * Bản sao có gắn thông tin lệnh rút.
     *
     * Trả BẢN MỚI thay vì đặt vào bản cũ: record là bất biến, và đó là điều mong muốn ở
     * đây — cùng một tin nhắn đi ra hai đường (khu quản trị có thẻ, người chơi không có),
     * nên một đối tượng chung mà sửa được sẽ là đường để thông tin nội bộ rò sang đường
     * còn lại.
     */
    public ChatMessageResponse withWithdrawal(ChatWithdrawalCardResponse card) {
        return new ChatMessageResponse(id, conversationId, senderType, senderId, senderUsername,
                body, attachmentUrl, attachmentType, attachmentName, attachmentSize,
                readAt, clientMsgId, createdAt, card);
    }
}
