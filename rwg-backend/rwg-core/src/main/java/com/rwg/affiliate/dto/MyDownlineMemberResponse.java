package com.rwg.affiliate.dto;

import java.time.Instant;

/**
 * Một thành viên tuyến dưới NHÌN TỪ PHÍA ĐẠI LÝ (GET /api/v1/affiliate/me/downline).
 *
 * CHE username: chỉ hiện vài ký tự đầu (vd {@code ng***}). Đại lý cần biết mình có
 * bao nhiêu tuyến dưới và họ vào khi nào, nhưng KHÔNG cần danh tính đầy đủ của
 * người khác — trả username đầy đủ sẽ tạo ra một kênh thu thập danh sách tài khoản.
 * Cũng KHÔNG trả userId vì cùng lý do.
 */
public record MyDownlineMemberResponse(
        String maskedUsername,
        int level,
        Instant joinedAt
) {
    /** Giữ 2 ký tự đầu, phần còn lại thay bằng {@code ***}. */
    public static String mask(String username) {
        if (username == null || username.isBlank()) {
            return "***";
        }
        String trimmed = username.trim();
        if (trimmed.length() <= 2) {
            return trimmed.charAt(0) + "***";
        }
        return trimmed.substring(0, 2) + "***";
    }
}
