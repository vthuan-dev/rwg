package com.rwg.wallet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Admin điều chỉnh số dư ví THỦ CÔNG.
 *
 * - amount là String, parse sang BigDecimal ở service (CẤM float/double).
 * - direction: CREDIT (cộng, vd hoàn tiền sự cố / thưởng sự kiện) hoặc
 *   DEBIT (trừ, vd thu hồi tiền gian lận). KHÔNG có chế độ set số dư tuyệt đối —
 *   mọi thay đổi đều là delta để ledger luôn tái dựng được số dư.
 * - reason BẮT BUỘC: đây là thao tác tạo/hủy tiền, phải truy vết được.
 */
public record AdjustWalletRequest(
        @NotBlank(message = "{validation.admin.adjust.amount.not_blank}")
        String amount,

        @NotBlank(message = "{validation.admin.adjust.direction.not_blank}")
        String direction,

        @NotBlank(message = "{validation.admin.adjust.reason.not_blank}")
        @Size(max = 255, message = "{validation.admin.reason.size}")
        String reason
) {
}
