package com.rwg.game.dto;

import java.time.Instant;
import java.util.List;

/** Goi broadcast khi no hu Tu Quy: ca ban thay winner + tien + xuc xac. */
public record JackpotWonPayload(
        String type,
        String tableId,
        String roundId,
        long roundSeq,
        String door,
        List<Integer> diceQuad,
        String winnerName,
        String amount,
        Instant serverTime) {

    public static JackpotWonPayload of(String tableId, String roundId, long roundSeq,
                                       String door, List<Integer> diceQuad,
                                       String winnerName, String amount) {
        return new JackpotWonPayload("JACKPOT_WON", tableId, roundId, roundSeq,
                door, diceQuad, winnerName, amount, Instant.now());
    }
}
