package com.rwg.game.domain;

/**
 * Trạng thái cuối của vòng: OPEN (đang chạy), SETTLED (đã trả thưởng),
 * VOIDED (hủy — đã refund toàn bộ cược).
 */
public enum RoundStatus {
    OPEN,
    SETTLED,
    VOIDED
}
