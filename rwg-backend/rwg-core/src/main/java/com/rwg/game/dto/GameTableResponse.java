package com.rwg.game.dto;

import java.util.Map;

/** Thông tin bàn chơi (GET /api/v1/games/tables). */
public record GameTableResponse(
        String id,
        String gameType,
        Map<String, String> nameI18n,
        String status,
        String minBet,
        String maxBet,
        String currency) {
}
