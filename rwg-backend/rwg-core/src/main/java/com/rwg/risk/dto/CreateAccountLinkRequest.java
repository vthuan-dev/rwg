package com.rwg.risk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Người vận hành tự nối hai tài khoản (POST /api/v1/admin/risk/links).
 *
 * Cần thiết vì gian lận thật thường lộ qua dấu hiệu máy KHÔNG thấy: cùng số tài
 * khoản ngân hàng, cùng kiểu cược, giờ hoạt động trùng khớp.
 *
 * Liên kết tạo tay có {@code linkType = MANUAL} và {@code status = CONFIRMED} ngay —
 * người vận hành đã điều tra rồi mới nối, không cần khâu xem lại. Nghĩa là nó GIỮ
 * TIỀN ngay từ kỳ hoa hồng kế tiếp.
 */
public record CreateAccountLinkRequest(
        @NotBlank
        String userAId,

        @NotBlank
        String userBId,

        @NotBlank
        @Size(max = 255)
        String note
) {
}
