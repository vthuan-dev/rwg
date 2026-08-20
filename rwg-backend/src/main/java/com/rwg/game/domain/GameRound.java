package com.rwg.game.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Một vòng chơi (rounds) — bảng volume PK composite (id, created_at)
 * partition-ready (DECISIONS.md mục b). Single-writer: chỉ RoundScheduler
 * của bàn mới ghi vòng này.
 */
@Entity
@Table(name = "rounds")
@IdClass(GameRoundId.class)
public class GameRound {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Id
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "table_id", nullable = false)
    private UUID tableId;

    @Column(name = "round_seq", nullable = false)
    private long roundSeq;

    @Enumerated(EnumType.STRING)
    @Column(name = "phase", nullable = false, length = 16)
    private RoundPhase phase = RoundPhase.BETTING_OPEN;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private RoundStatus status = RoundStatus.OPEN;

    @Column(name = "winning_number")
    private Integer winningNumber;

    @Column(name = "baccarat_player_cards", length = 64)
    private String baccaratPlayerCards;

    @Column(name = "baccarat_banker_cards", length = 64)
    private String baccaratBankerCards;

    @Column(name = "baccarat_player_score")
    private Integer baccaratPlayerScore;

    @Column(name = "baccarat_banker_score")
    private Integer baccaratBankerScore;

    @Column(name = "baccarat_player_pair")
    private Boolean baccaratPlayerPair;

    @Column(name = "baccarat_banker_pair")
    private Boolean baccaratBankerPair;

    @Column(name = "baccarat_result", length = 16)
    private String baccaratResult;

    @Column(name = "kl28_numbers", length = 16)
    private String kl28Numbers;

    @Column(name = "kl28_sum")
    private Integer kl28Sum;

    @Column(name = "result_at")
    private Instant resultAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected GameRound() {
        // cho JPA
    }

    public GameRound(UUID tableId, long roundSeq) {
        this.tableId = tableId;
        this.roundSeq = roundSeq;
        this.phase = RoundPhase.BETTING_OPEN;
        this.status = RoundStatus.OPEN;
    }

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    public void setPhase(RoundPhase phase) { this.phase = phase; }
    public void setStatus(RoundStatus status) { this.status = status; }
    public void setWinningNumber(Integer winningNumber) { this.winningNumber = winningNumber; }
    public void setResultAt(Instant resultAt) { this.resultAt = resultAt; }
    public void touch() { this.updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS); }

    public void setBaccaratPlayerCards(String baccaratPlayerCards) { this.baccaratPlayerCards = baccaratPlayerCards; }
    public void setBaccaratBankerCards(String baccaratBankerCards) { this.baccaratBankerCards = baccaratBankerCards; }
    public void setBaccaratPlayerScore(Integer baccaratPlayerScore) { this.baccaratPlayerScore = baccaratPlayerScore; }
    public void setBaccaratBankerScore(Integer baccaratBankerScore) { this.baccaratBankerScore = baccaratBankerScore; }
    public void setBaccaratPlayerPair(Boolean baccaratPlayerPair) { this.baccaratPlayerPair = baccaratPlayerPair; }
    public void setBaccaratBankerPair(Boolean baccaratBankerPair) { this.baccaratBankerPair = baccaratBankerPair; }
    public void setBaccaratResult(String baccaratResult) { this.baccaratResult = baccaratResult; }
    public void setKl28Numbers(String kl28Numbers) { this.kl28Numbers = kl28Numbers; }
    public void setKl28Sum(Integer kl28Sum) { this.kl28Sum = kl28Sum; }

    public UUID getId() { return id; }
    public Instant getCreatedAt() { return createdAt; }
    public UUID getTableId() { return tableId; }
    public long getRoundSeq() { return roundSeq; }
    public RoundPhase getPhase() { return phase; }
    public RoundStatus getStatus() { return status; }
    public Integer getWinningNumber() { return winningNumber; }
    public String getBaccaratPlayerCards() { return baccaratPlayerCards; }
    public String getBaccaratBankerCards() { return baccaratBankerCards; }
    public Integer getBaccaratPlayerScore() { return baccaratPlayerScore; }
    public Integer getBaccaratBankerScore() { return baccaratBankerScore; }
    public Boolean getBaccaratPlayerPair() { return baccaratPlayerPair; }
    public Boolean getBaccaratBankerPair() { return baccaratBankerPair; }
    public String getBaccaratResult() { return baccaratResult; }
    public String getKl28Numbers() { return kl28Numbers; }
    public Integer getKl28Sum() { return kl28Sum; }
    public Instant getResultAt() { return resultAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
