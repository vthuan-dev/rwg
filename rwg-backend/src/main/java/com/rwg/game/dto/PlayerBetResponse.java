package com.rwg.game.dto;

import java.time.Instant;

/** Cược của user trong vòng (GET /api/v1/games/me/bets?roundId=). */
public record PlayerBetResponse(
        String id,
        String roundId,
        String tableId,
        String betType,
        String selection,
        String stake,
        String status,
        String payout,
        Instant createdAt) {
}
