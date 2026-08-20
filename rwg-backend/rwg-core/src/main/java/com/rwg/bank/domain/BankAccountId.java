package com.rwg.bank.domain;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Khóa chính composite (id, created_at) của bank_accounts — partition-ready
 * theo thời gian (DECISIONS.md mục b). Dùng kèm {@code @IdClass}.
 */
public class BankAccountId implements Serializable {

    private UUID id;
    private Instant createdAt;

    public BankAccountId() {
        // cho JPA
    }

    public BankAccountId(UUID id, Instant createdAt) {
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
        if (!(o instanceof BankAccountId that)) return false;
        return Objects.equals(id, that.id) && Objects.equals(createdAt, that.createdAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, createdAt);
    }
}
