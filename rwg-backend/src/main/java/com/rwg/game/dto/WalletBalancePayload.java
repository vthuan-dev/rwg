package com.rwg.game.dto;

import java.time.Instant;

/** Gói unicast số dư mới cho user: /user/queue/wallet (sau thắng/refund). */
public record WalletBalancePayload(
        String type,
        String balance,
        Instant serverTime) {

    public static WalletBalancePayload of(String balance) {
        return new WalletBalancePayload("WALLET_BALANCE", balance, Instant.now());
    }
}
