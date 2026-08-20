package com.rwg.game.domain;

/**
 * Trạng thái cược: PENDING (chờ settle), SETTLED (đã chốt lời/thua),
 * VOIDED (vòng bị hủy — đã refund stake).
 */
public enum BetStatus {
    PENDING,
    SETTLED,
    VOIDED
}
