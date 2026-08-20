package com.rwg.game.dto;

import java.time.Instant;

/** Gói broadcast vòng bị hủy (VOIDED + refund) — 1 gói/bàn trên /topic/game/table/{tableId}. */
public record RoundVoidedPayload(
        String type,
        String tableId,
        String roundId,
        long roundSeq,
        Instant serverTime) {

    public static RoundVoidedPayload of(String tableId, String roundId, long roundSeq) {
        return new RoundVoidedPayload("ROUND_VOIDED", tableId, roundId, roundSeq, Instant.now());
    }
}
