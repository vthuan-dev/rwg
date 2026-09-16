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
        Long roundSeq,
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
        String payout,
        String balanceAfter,
        Instant serverTime) {

    public static PlayerWinPayload of(String tableId, String roundId, int winningNumber,
                                      String payout, String balanceAfter) {
        return of(tableId, roundId, null, winningNumber, payout, balanceAfter);
    }

    public static PlayerWinPayload of(String tableId, String roundId, Long roundSeq, int winningNumber,
                                      String payout, String balanceAfter) {
        return new PlayerWinPayload("PLAYER_WIN", tableId, roundId, roundSeq, winningNumber,
                null, null, null, null, null, null, null, null, null,
                null, null, payout, balanceAfter, Instant.now());
    }

    public static PlayerWinPayload baccarat(String tableId, String roundId,
                                            String playerCards, String bankerCards,
                                            int playerScore, int bankerScore,
                                            boolean playerPair, boolean bankerPair, String result,
                                            String payout, String balanceAfter) {
        return baccarat(tableId, roundId, null, playerCards, bankerCards, playerScore, bankerScore,
                playerPair, bankerPair, result, payout, balanceAfter);
    }

    public static PlayerWinPayload baccarat(String tableId, String roundId, Long roundSeq,
                                            String playerCards, String bankerCards,
                                            int playerScore, int bankerScore,
                                            boolean playerPair, boolean bankerPair, String result,
                                            String payout, String balanceAfter) {
        return new PlayerWinPayload("PLAYER_WIN", tableId, roundId, roundSeq, null,
                playerCards, bankerCards, playerScore, bankerScore, playerPair, bankerPair, result,
                null, null, null, null, payout, balanceAfter, Instant.now());
    }

    public static PlayerWinPayload kl28(String tableId, String roundId,
                                        String kl28Numbers, int kl28Sum,
                                        String payout, String balanceAfter) {
        return kl28(tableId, roundId, null, kl28Numbers, kl28Sum, payout, balanceAfter);
    }

    public static PlayerWinPayload kl28(String tableId, String roundId, Long roundSeq,
                                        String kl28Numbers, int kl28Sum,
                                        String payout, String balanceAfter) {
        return new PlayerWinPayload("PLAYER_WIN", tableId, roundId, roundSeq, null,
                null, null, null, null, null, null, null,
                kl28Numbers, kl28Sum, null, null, payout, balanceAfter, Instant.now());
    }

    public static PlayerWinPayload xocDia(String tableId, String roundId,
                                         String xocDiaCoins, int xocDiaRedCount,
                                         String payout, String balanceAfter) {
        return xocDia(tableId, roundId, null, xocDiaCoins, xocDiaRedCount, payout, balanceAfter);
    }

    public static PlayerWinPayload xocDia(String tableId, String roundId, Long roundSeq,
                                         String xocDiaCoins, int xocDiaRedCount,
                                         String payout, String balanceAfter) {
        return new PlayerWinPayload("PLAYER_WIN", tableId, roundId, roundSeq, null,
                null, null, null, null, null, null, null,
                null, null, xocDiaCoins, xocDiaRedCount, payout, balanceAfter, Instant.now());
    }
}
