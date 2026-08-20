package com.rwg.game.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Bật/tắt bàn chơi (PATCH /api/v1/admin/games/tables/{id}/status).
 *
 * Bắt buộc có lý do: tắt bàn là thao tác ảnh hưởng mọi người chơi đang ở bàn đó,
 * nên phải truy được ai tắt và vì sao — cùng quy ước với đổi trạng thái người dùng.
 */
public record UpdateTableStatusRequest(
        @NotBlank
        @Pattern(regexp = "ACTIVE|DISABLED")
        String status,

        @NotBlank
        @Size(max = 255)
        String reason
) {
}
