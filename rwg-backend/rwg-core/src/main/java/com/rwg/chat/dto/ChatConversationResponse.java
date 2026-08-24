package com.rwg.chat.dto;

import com.rwg.chat.domain.ChatConversation;

import java.time.Instant;

/**
 * Luồng hội thoại của CHÍNH người chơi đang đăng nhập.
 *
 * KHÔNG trả `unreadForAdmin` và `assignedAdminId`: người chơi không cần biết hàng
 * đợi nội bộ của sàn dài bao nhiêu hay ai đang phụ trách mình. Trả về những thông
 * tin đó là lộ dữ liệu vận hành ra ngoài mà không đổi lại được gì cho giao diện.
 */
public record ChatConversationResponse(
        String id,
        /** OPEN | CLOSED. */
        String status,
        /** Số tin nhân sự gửi mà người chơi chưa xem. */
        int unreadCount,
        /** null khi luồng chưa có tin nào. */
        Instant lastMessageAt,
        Instant createdAt
) {

    public static ChatConversationResponse from(ChatConversation c) {
        return new ChatConversationResponse(
                c.getId().toString(),
                c.getStatus().name(),
                c.getUnreadForPlayer(),
                c.getLastMessageAt(),
                c.getCreatedAt());
    }
}
