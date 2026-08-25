package com.rwg.chat.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Yêu cầu xóa một hoặc nhiều tin nhắn trong cuộc hội thoại.
 *
 * Admin chọn tin trên màn hình rồi ấn xóa một lần: danh sách gửi tất cả cùng một request
 * thay vì từng request riêng. Giới hạn 100 tin mỗi lần để tránh timeout và lock bảng.
 */
public record DeleteChatMessagesRequest(

        @NotEmpty
        @Size(max = 100, message = "Tối đa 100 tin nhắn mỗi lần xóa")
        List<UUID> messageIds
) {
}
