package com.rwg.wallet.dto;

import java.time.Instant;

/**
 * Một dòng lịch sử giao dịch ví. Các trường tiền dạng String (toPlainString).
 */
public record WalletTransactionResponse(
        String id,
        Instant createdAt,
        String debit,
        String credit,
        String balanceAfter,
        String refType,
        String refId,
        String status
) {
}
