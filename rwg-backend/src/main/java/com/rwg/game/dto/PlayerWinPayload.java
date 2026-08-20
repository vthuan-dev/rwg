package com.rwg.game.dto;

import java.time.Instant;

/**
 * Gói unicast kết quả thắng của user: /user/queue/game/results
 * (payout stake-inclusive M2 + số dư mới sau credit).
 */
public record PlayerWinPayload(
        String type,
        String tableId,
        String roundId,
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
        String payout,
        String balanceAfter,
        Instant serverTime) {

    public static PlayerWinPayload of(String tableId, String roundId, int winningNumber,
                                      String payout, String balanceAfter) {
        return new PlayerWinPayload("PLAYER_WIN", tableId, roundId, winningNumber,
                null, null, null, null, null, null, null, null, null,
                payout, balanceAfter, Instant.now());
    }

    public static PlayerWinPayload baccarat(String tableId, String roundId,
                                            String playerCards, String bankerCards,
                                            int playerScore, int bankerScore,
                                            boolean playerPair, boolean bankerPair, String result,
                                            String payout, String balanceAfter) {
        return new PlayerWinPayload("PLAYER_WIN", tableId, roundId, null,
                playerCards, bankerCards, playerScore, bankerScore, playerPair, bankerPair, result,
                null, null, payout, balanceAfter, Instant.now());
    }

    public static PlayerWinPayload kl28(String tableId, String roundId,
                                        String kl28Numbers, int kl28Sum,
                                        String payout, String balanceAfter) {
        return new PlayerWinPayload("PLAYER_WIN", tableId, roundId, null,
                null, null, null, null, null, null, null,
                kl28Numbers, kl28Sum, payout, balanceAfter, Instant.now());
    }
}
