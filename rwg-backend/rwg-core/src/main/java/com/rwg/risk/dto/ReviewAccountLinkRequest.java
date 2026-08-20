package com.rwg.risk.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Kết luận của người vận hành về một liên kết
 * (PATCH /api/v1/admin/risk/links/{id}).
 *
 * Chỉ nhận CONFIRMED hoặc CLEARED — không có đường quay lại SUSPECTED vì một khi
 * người đã xem thì kết luận của người thắng máy.
 *
 * Lý do BẮT BUỘC: liên kết là căn cứ giữ tiền hoa hồng của người khác, nên phải
 * truy được ai kết luận và vì sao.
 */
public record ReviewAccountLinkRequest(
        @NotBlank
        @Pattern(regexp = "CONFIRMED|CLEARED")
        String status,

        @NotBlank
        @Size(max = 255)
        String note
) {
}
