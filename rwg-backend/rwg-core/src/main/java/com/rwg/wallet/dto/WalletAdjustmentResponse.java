package com.rwg.wallet.dto;

import java.time.Instant;

/**
 * Kết quả điều chỉnh ví thủ công. Tiền là String — CẤM float/double.
 * Trả cả balanceBefore và balanceAfter để admin đối chiếu ngay trên UI.
 */
public record WalletAdjustmentResponse(
        String userId,
        String direction,
        String amount,
        String balanceBefore,
        String balanceAfter,
        String reason,
        String idempotencyKey,
        Instant adjustedAt
) {
}
