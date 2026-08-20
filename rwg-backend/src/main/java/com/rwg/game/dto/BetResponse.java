package com.rwg.game.dto;

/** Kết quả đặt cược (POST /api/v1/games/tables/{id}/bets). */
public record BetResponse(
        String id,
        String roundId,
        String betType,
        String selection,
        String stake,
        String status,
        String balanceAfter) {
}
