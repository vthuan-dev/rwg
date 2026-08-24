package com.rwg.game.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Admin đặt tỷ lệ cược riêng cho một người chơi ở một bàn.
 *
 * `odds` là String rồi parse sang BigDecimal ở service, giống `AdjustWalletRequest`: nhận
 * trực tiếp vào `double` sẽ làm 0.98 thành 0.9800000000000000266, và con số đó dùng để
 * tính tiền thật.
 *
 * `reason` KHÔNG bắt buộc. Trước đây bắt buộc, nhưng người vận hành thường chỉ nhích tỷ lệ
 * vài phần trăm và phải gõ một câu vô nghĩa cho qua ràng buộc — thứ đó làm nhật ký khó đọc
 * hơn là để trống. Việc truy vết vẫn đủ: audit `ADMIN_USER_ODDS_CHANGED` ghi người thực
 * hiện, thời điểm, IP, và tỷ lệ trước/sau.
 *
 * Vẫn giới hạn 255 ký tự để khớp cột `user_game_odds.reason`.
 */
public record SetUserOddsRequest(
        @NotBlank(message = "{validation.admin.odds.table.not_blank}")
        String tableId,

        @NotBlank(message = "{validation.admin.odds.bet_type.not_blank}")
        String betType,

        @NotBlank(message = "{validation.admin.odds.value.not_blank}")
        String odds,

        @Size(max = 255, message = "{validation.admin.reason.size}")
        String reason
) {
}
