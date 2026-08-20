package com.rwg.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Guard idempotency cho ledger (map bảng wallet_ledger_guard, migration 04).
 * PK THUẦN trên idempotency_key — đảm bảo duy nhất THỰC SỰ ở tầng DB, chặn
 * double credit/debit khi 2 transaction song song cùng key (fix review C1).
 * Đây là bảng guard, KHÔNG phải bảng partition-ready nên PK không kèm created_at.
 */
@Entity
@Table(name = "wallet_ledger_guard")
public class WalletLedgerGuard {

    @Id
    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected WalletLedgerGuard() {
        // cho JPA
    }

    public WalletLedgerGuard(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public String getIdempotencyKey() { return idempotencyKey; }
    public Instant getCreatedAt() { return createdAt; }
}
