package com.rwg.chat.dto;

/**
 * Số tin chưa đọc, cho viên tròn đỏ trên giao diện.
 *
 * Trả về một record thay vì {@code Map.of("count", n)} như
 * {@code NotificationController} đang làm: hai phía dùng con số này theo hai nghĩa
 * khác nhau ({@code conversations} chỉ có nghĩa ở khu quản trị), và một Map không
 * ghi lại được điều đó ở đâu cả — client phải đọc code server mới biết có những
 * khoá nào.
 */
public record ChatUnreadResponse(

        /** Tổng số TIN chưa đọc. */
        long messages,

        /**
         * Số LUỒNG đang chờ trả lời. Luôn 0 ở phía người chơi (họ chỉ có một luồng).
         *
         * Cần riêng con số này ở khu quản trị vì "40 tin chưa đọc" và "40 người đang
         * chờ" là hai mức độ cấp bách hoàn toàn khác nhau — 40 tin có thể chỉ từ một
         * người đang gõ liên tục.
         */
        long conversations
) {

    public static ChatUnreadResponse of(long messages, long conversations) {
        return new ChatUnreadResponse(messages, conversations);
    }
}
