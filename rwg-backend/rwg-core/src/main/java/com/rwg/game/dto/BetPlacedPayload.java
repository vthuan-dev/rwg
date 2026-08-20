package com.rwg.game.dto;

import java.time.Instant;

/**
 * Gói BET_PLACED AGGREGATE (chống bão sự kiện): gom mọi cược của bàn trong cửa sổ
 * 250ms thành 1 gói duy nhất (số lệnh + tổng stake) phát trên /topic/game/table/{tableId}.
 */
public record BetPlacedPayload(
        String type,
        String tableId,
        String roundId,
        long betCount,
        String totalStake,
        Instant serverTime) {

    public static BetPlacedPayload of(String tableId, String roundId, long betCount, String totalStake) {
        return new BetPlacedPayload("BET_PLACED", tableId, roundId, betCount, totalStake, Instant.now());
    }
}
