package com.rwg.bank.dto;

import com.rwg.bank.domain.BankAccount;

import java.time.Instant;

/**
 * Response phương thức nhận tiền — CHỈ lộ thông tin ĐÃ CHE,
 * KHÔNG BAO GIỜ lộ số tài khoản đầy đủ.
 */
public record BankAccountResponse(
        String id,
        String bankCode,
        /** Giữ cho client: "****1234". */
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
