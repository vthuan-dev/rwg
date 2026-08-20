package com.rwg.identity.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Thông tin user trả về API. KHÔNG lộ password hash.
 */
public record UserResponse(
        UUID id,
        String username,
        String email,
        String role,
        String status,
        String kycLevel,
        boolean hasWithdrawalPassword,
        String locale,
        Instant createdAt
) {
}
