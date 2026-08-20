package com.rwg.identity.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Chi tiết user cho khu quản trị: thông tin tài khoản + ảnh chụp tài chính.
 * Tiền là String (BigDecimal.toPlainString) — CẤM float/double.
 * KHÔNG lộ password hash / withdrawal password hash.
 */
public record AdminUserDetailResponse(
        UUID id,
        String username,
        String email,
        String role,
        String status,
        String kycLevel,
        boolean hasWithdrawalPassword,
        String locale,
        Instant lastLoginAt,
        Instant createdAt,
        String walletBalance,
        String currency,
        String totalDeposited,
        String totalWithdrawn,
        long pendingWithdrawals
) {
}
