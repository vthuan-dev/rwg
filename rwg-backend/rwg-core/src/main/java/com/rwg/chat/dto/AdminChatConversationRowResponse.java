package com.rwg.chat.dto;

import com.rwg.chat.domain.ChatConversation;

import java.time.Instant;

/**
 * Một dòng trong hộp thư của khu quản trị.
 *
 * Hậu tố `Row` trong tên (khớp {@code AdminWithdrawalRowResponse} đang có) báo rõ
 * đây là dữ liệu cho MỘT DÒNG BẢNG, không phải toàn bộ hội thoại — nó không mang
 * theo tin nhắn nào.
 *
 * `username` được ghép từ bảng users ở tầng service. Cố tình đưa vào DTO thay vì
 * để giao diện tự gọi thêm API lấy tên theo từng userId: một hộp thư 20 dòng sẽ
 * thành 20 request phụ.
 */
public record AdminChatConversationRowResponse(
        String id,
        String userId,
        String username,
        /** OPEN | CLOSED. */
        String status,
        /** null = chưa ai nhận, còn trong hàng đợi chung. */
        String assignedAdminId,
        /** Tên nhân sự đang phụ trách; null khi chưa ai nhận. */
        String assignedAdminUsername,
        /** Số tin người chơi gửi mà chưa nhân sự nào đọc. */
        int unreadCount,
        /** Đoạn xem trước tin cuối; null khi luồng chưa có tin. */
        String lastMessagePreview,
        Instant lastMessageAt,
        Instant createdAt
) {

    public static AdminChatConversationRowResponse from(ChatConversation c,
                                                       String username,
                                                       String assignedAdminUsername) {
        return new AdminChatConversationRowResponse(
                c.getId().toString(),
                c.getUserId().toString(),
                username,
                c.getStatus().name(),
                c.getAssignedAdminId() == null ? null : c.getAssignedAdminId().toString(),
                assignedAdminUsername,
                c.getUnreadForAdmin(),
                c.getLastMessagePreview(),
                c.getLastMessageAt(),
                c.getCreatedAt());
    }
}
