package com.rwg.game.dto;

import java.time.Instant;

/** Vòng hiện tại của bàn (GET /api/v1/games/tables/{id}/rounds/current). */
public record RoundResponse(
        String roundId,
        String tableId,
        long roundSeq,
        String phase,
        String status,
        Integer winningNumber,
        String baccaratPlayerCards,
        String baccaratBankerCards,
        Integer baccaratPlayerScore,
        Integer baccaratBankerScore,
        Boolean baccaratPlayerPair,
        Boolean baccaratBankerPair,
        String baccaratResult,
        String kl28Numbers,
        Integer kl28Sum,
        Instant serverTime) {
}
