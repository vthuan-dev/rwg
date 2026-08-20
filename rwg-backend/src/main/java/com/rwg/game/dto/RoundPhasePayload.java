package com.rwg.game.dto;

import java.time.Instant;

/**
 * Gói broadcast chuyển pha vòng chơi — 1 gói/bàn trên /topic/game/table/{tableId}.
 * Kèm serverTime để client TỰ countdown (không spam gói từ server).
 */
public record RoundPhasePayload(
        String type,
        String tableId,
        String roundId,
        long roundSeq,
        String phase,
        Instant serverTime) {

    public static RoundPhasePayload of(String tableId, String roundId, long roundSeq, String phase) {
        return new RoundPhasePayload("ROUND_PHASE", tableId, roundId, roundSeq, phase, Instant.now());
    }
}
