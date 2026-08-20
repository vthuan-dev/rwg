package com.rwg.bank.dto;

import com.rwg.bank.domain.BankAccount;

import java.time.Instant;

/**
 * Response tài khoản ngân hàng — CHỈ lộ thông tin ĐÃ MASK (****1234),
 * KHÔNG BAO GIỜ lộ số tài khoản đầy đủ.
 */
public record BankAccountResponse(
        String id,
        String bankCode,
        String maskedAccountNumber,
        String holderName,
        boolean isDefault,
        String status,
        Instant createdAt
) {

    public static BankAccountResponse from(BankAccount ba) {
        return new BankAccountResponse(
                ba.getId().toString(),
                ba.getBankCode(),
                "****" + ba.getMaskedLast4(),
                ba.getHolderName(),
                Boolean.TRUE.equals(ba.getIsDefault()),
                ba.getStatus().name(),
                ba.getCreatedAt());
    }
}
