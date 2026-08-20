package com.rwg.wallet.domain;

/**
 * Trạng thái một dòng ledger (V1.wallet_transactions.status).
 * LOCKED: giữ chỗ (đặt cược M1); SETTLED: đã chốt; VOIDED: đã hủy/hoàn.
 */
public enum WalletTxStatus {
    LOCKED, SETTLED, VOIDED
}
