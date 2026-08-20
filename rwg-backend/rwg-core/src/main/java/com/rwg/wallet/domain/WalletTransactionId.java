package com.rwg.wallet.domain;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Khóa chính composite (id, created_at) của wallet_transactions — partition-ready
 * theo thời gian (DECISIONS.md mục b). Dùng kèm {@code @IdClass}.
 */
public class WalletTransactionId implements Serializable {

    private UUID id;
    private Instant createdAt;

    public WalletTransactionId() {
        // cho JPA
    }

    public WalletTransactionId(UUID id, Instant createdAt) {
        this.id = id;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WalletTransactionId that)) return false;
        return Objects.equals(id, that.id) && Objects.equals(createdAt, that.createdAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, createdAt);
    }
}
