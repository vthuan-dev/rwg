package com.rwg.game.domain;

/**
 * Pha của một vòng chơi (docs/round-lifecycle.md). Thời lượng từng pha cấu hình
 * qua rwg.game.round.* để test có thể rút ngắn.
 */
public enum RoundPhase {
    BETTING_OPEN,
    BETTING_CLOSED,
    SPINNING,
    RESULT,
    SETTLE
}
