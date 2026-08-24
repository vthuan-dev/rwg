package com.rwg.bank.dto;

import java.time.Instant;

/**
 * Số tài khoản ĐẦY ĐỦ, trả về cho khu quản trị.
 */
public record RevealedPayoutAddressResponse(
        String id,
        String payoutType,
        /** Số tài khoản ngân hàng — ĐẦY ĐỦ. */
        String fullAddress,
        String bankCode,
        String holderName,
        /** Thời điểm giải mã, đối chiếu được với dòng audit tương ứng. */
        Instant revealedAt
) {
}
