package com.rwg.presence.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Trạng thái có mặt của một người chơi.
 *
 * `online` là kết luận đã tính sẵn ở server, KHÔNG để phía hiển thị tự so `lastSeenAt` với
 * hiện tại: đồng hồ của máy người vận hành có thể lệch, và ngưỡng "bao lâu thì coi là
 * offline" là một quyết định nghiệp vụ nằm trong cấu hình
 * ({@code rwg.presence.online-window}). Đặt nó ở giao diện sẽ có hai nơi cùng định nghĩa
 * một khái niệm.
 *
 * `lastSeenAt` null nghĩa là KHÔNG RÕ — mốc đã hết hạn hoặc người này chưa hoạt động kể từ
 * khi tính năng được bật. Khác hẳn "vừa mới ở đây", nên phía hiển thị phải lùi về dùng
 * `lastLoginAt` chứ không được vẽ như đã rời đi từ lâu.
 */
public record PresenceEntryResponse(
        UUID userId,
        boolean online,
        Instant lastSeenAt
) {
}
