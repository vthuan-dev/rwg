package com.rwg.game.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Bàn chơi cấu hình (game_tables). KHÔNG phải bảng volume -> PK đơn.
 * nameI18n lưu JSON chuỗi {"en","vi","zh","ja"} (DECISIONS.md: cấu hình JSON).
 */
@Entity
@Table(name = "game_tables")
public class GameTable {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "game_type", nullable = false, length = 16)
    private String gameType;

    /**
     * MySQL: cột JSON (bind dạng varchar nhờ @JdbcTypeCode LONGVARCHAR - cùng quy ước
     * với audit_log.details). H2 test (MODE=MySQL): cột JSON - tương thích cả hai.
     */
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "name_i18n", nullable = false)
    private String nameI18n;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private GameTableStatus status = GameTableStatus.ACTIVE;

    @Column(name = "min_bet", nullable = false)
    private BigDecimal minBet = BigDecimal.ONE;

    @Column(name = "max_bet", nullable = false)
    private BigDecimal maxBet = new BigDecimal("10000");

    @Column(name = "currency", nullable = false, length = 8)
    private String currency = "USD";

    @Column(name = "created_at", nullable = false, updatable = false, insertable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    protected GameTable() {
        // cho JPA
    }

    public GameTable(UUID id, String gameType, String nameI18n, BigDecimal minBet, BigDecimal maxBet) {
        this.id = id;
        this.gameType = gameType;
        this.nameI18n = nameI18n;
        this.minBet = minBet;
        this.maxBet = maxBet;
    }

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
    }

    public UUID getId() { return id; }
    public String getGameType() { return gameType; }
    public String getNameI18n() { return nameI18n; }
    public GameTableStatus getStatus() { return status; }
    public BigDecimal getMinBet() { return minBet; }
    public BigDecimal getMaxBet() { return maxBet; }
    public String getCurrency() { return currency; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
