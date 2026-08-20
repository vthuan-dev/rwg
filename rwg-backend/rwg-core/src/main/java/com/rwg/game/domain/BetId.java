package com.rwg.game.domain;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Khóa chính composite (id, created_at) của bets — dùng kèm {@code @IdClass}. */
public class BetId implements Serializable {

    private UUID id;
    private Instant createdAt;

    public BetId() {
        // cho JPA
    }

    public BetId(UUID id, Instant createdAt) {
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
        if (!(o instanceof BetId that)) return false;
        return Objects.equals(id, that.id) && Objects.equals(createdAt, that.createdAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, createdAt);
    }
}
