package com.rwg.bank.dto;

import com.rwg.bank.domain.BankAccount;

import java.time.Instant;

/**
 * Phương thức nhận tiền của một user, dùng cho khu quản trị.
 *
 * Chỉ hỗ trợ BANK.
 */
public record AdminPayoutMethodResponse(
        String id,
        String payoutType,
        String bankCode,
        String maskedAddress,
        String holderName,
        boolean isDefault,
        String status,
        Instant createdAt
) {

    public static AdminPayoutMethodResponse from(BankAccount ba) {
        return new AdminPayoutMethodResponse(
                ba.getId().toString(),
                "BANK",
                ba.getBankCode(),
                "****" + ba.getMaskedLast4(),
                ba.getHolderName(),
                Boolean.TRUE.equals(ba.getIsDefault()),
                ba.getStatus().name(),
                ba.getCreatedAt());
    }
}
