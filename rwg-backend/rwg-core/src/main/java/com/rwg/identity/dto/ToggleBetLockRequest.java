package com.rwg.identity.dto;

/**
 * Admin yêu cầu bật/tắt cấm đặt cược ngầm (Stealth Bet Lock) cho người chơi.
 */
public record ToggleBetLockRequest(
        boolean locked
) {
}
