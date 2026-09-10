package com.rwg.game.dto;

import java.time.Instant;

/** Gói broadcast kết quả vòng (số trúng) — 1 gói/bàn trên /topic/game/table/{tableId}. */
public record RoundResultPayload(
        String type,
        String tableId,
        String roundId,
        long roundSeq,
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
        String xocDiaCoins,
        Integer xocDiaRedCount,
        String xocDiaSeed,
        String xocDiaSeedHash,
        Instant serverTime) {

    public static RoundResultPayload of(String tableId, String roundId, long roundSeq, int winningNumber) {
        return new RoundResultPayload("ROUND_RESULT", tableId, roundId, roundSeq, winningNumber,
                null, null, null, null, null, null, null, null, null, null, null, null, null, Instant.now());
    }

    public static RoundResultPayload baccarat(String tableId, String roundId, long roundSeq,
                                             String playerCards, String bankerCards,
                                             int playerScore, int bankerScore,
                                             boolean playerPair, boolean bankerPair, String result) {
        return new RoundResultPayload("ROUND_RESULT", tableId, roundId, roundSeq, null,
                playerCards, bankerCards, playerScore, bankerScore, playerPair, bankerPair, result,
                null, null, null, null, null, null, Instant.now());
    }

    public static RoundResultPayload kl28(String tableId, String roundId, long roundSeq,
                                         String kl28Numbers, int kl28Sum) {
        return new RoundResultPayload("ROUND_RESULT", tableId, roundId, roundSeq, null,
                null, null, null, null, null, null, null,
                kl28Numbers, kl28Sum, null, null, null, null, Instant.now());
    }

    public static RoundResultPayload xocDia(String tableId, String roundId, long roundSeq,
                                           String xocDiaCoins, int xocDiaRedCount,
                                           String xocDiaSeed, String xocDiaSeedHash) {
        return new RoundResultPayload("ROUND_RESULT", tableId, roundId, roundSeq, null,
                null, null, null, null, null, null, null,
                null, null, xocDiaCoins, xocDiaRedCount, xocDiaSeed, xocDiaSeedHash, Instant.now());
    }
}
