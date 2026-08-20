package com.rwg.game.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Yêu cầu đặt cược (POST /api/v1/games/tables/{id}/bets).
 * stake dạng STRING (parse BigDecimal trong service — CẤM float/double).
 * seq do client tăng dần trong vòng -> idempotency key BET:{roundId}:{userId}:{seq}.
 */
public record BetRequest(
        @NotBlank(message = "{validation.bet.bet_type.not_blank}")
        String betType,

        String selection,

        @NotBlank(message = "{validation.bet.stake.not_blank}")
        String stake,

        @NotNull(message = "{validation.bet.seq.not_null}")
        Integer seq) {
}
