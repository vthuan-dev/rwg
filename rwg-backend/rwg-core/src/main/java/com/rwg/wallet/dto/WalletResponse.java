package com.rwg.wallet.dto;

/**
 * Thông tin ví trả về API. Số dư dạng String (toPlainString) — KHÔNG lộ float/double.
 */
public record WalletResponse(
        String walletId,
        String userId,
        String balance,
        String currency
) {
}
