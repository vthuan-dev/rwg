package com.rwg.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Yêu cầu xóa một hoặc nhiều tin nhắn trong cuộc hội thoại.
 *
 * Chuyển sang class thông thường để tương thích hoàn toàn với Jackson 3 trong môi trường test/build.
 */
public class DeleteChatMessagesRequest {

    @NotEmpty
    @Size(max = 100, message = "Tối đa 100 tin nhắn mỗi lần xóa")
    private List<UUID> messageIds;

    @NotBlank(message = "Mã xác nhận bảo mật là bắt buộc")
    private String confirmPin;

    public DeleteChatMessagesRequest() {
    }

    public DeleteChatMessagesRequest(List<UUID> messageIds, String confirmPin) {
        this.messageIds = messageIds;
        this.confirmPin = confirmPin;
    }

    public List<UUID> getMessageIds() {
        return messageIds;
    }

    public void setMessageIds(List<UUID> messageIds) {
        this.messageIds = messageIds;
    }

    public String getConfirmPin() {
        return confirmPin;
    }

    public void setConfirmPin(String confirmPin) {
        this.confirmPin = confirmPin;
    }
}
