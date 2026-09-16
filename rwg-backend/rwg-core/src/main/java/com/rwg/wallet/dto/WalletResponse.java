package com.rwg.wallet.dto;

/**
 * Thông tin ví trả về API. Số dư dạng String (toPlainString) — KHÔNG lộ float/double.
 */
public record WalletResponse(
        String walletId,
        String userId,
        String balance,
        String currency,
        String totalDeposited,
        String todayProfit
) {
    public WalletResponse(String walletId, String userId, String balance, String currency) {
        this(walletId, userId, balance, currency, "0.00", "0.00");
    }
}
