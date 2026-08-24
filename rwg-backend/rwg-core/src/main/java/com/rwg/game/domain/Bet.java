package com.rwg.game.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Một lệnh cược (bets) — bảng volume PK composite (id, created_at).
 * Stake đã TRỪ ví khi đặt (M1); payout stake-inclusive (M2) ghi khi settle.
 * Idempotent theo idempotency_key "BET:{roundId}:{userId}:{seq}".
 */
@Entity
@Table(name = "bets")
@IdClass(BetId.class)
public class Bet {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Id
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "round_id", nullable = false)
    private UUID roundId;

    @Column(name = "table_id", nullable = false)
    private UUID tableId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "bet_type", nullable = false, length = 16)
    private BetType betType;

    @Column(name = "selection", nullable = false, length = 64)
    private String selection = "";

    @Column(name = "stake", nullable = false)
    private BigDecimal stake;

    /**
     * Odds lợi đã chốt lúc nhận cược, theo cùng quy ước engine (0.98 = cược 100 nhận 198).
     *
     * Chốt vào đây chứ không tra lại lúc thanh toán: người chơi đồng ý với con số họ
     * thấy lúc đặt. Nếu thanh toán tra lại bảng tỷ lệ thì đổi tỷ lệ sau khi đã biết kết
     * quả vẫn ảnh hưởng được tới cược cũ.
     *
     * NULL = cược đặt trước khi có tính năng tỷ lệ riêng. Thanh toán rơi về mặc định
     * engine, nên các cược đang chờ lúc triển khai vẫn trả đúng như cũ.
     */
    @Column(name = "odds")
    private BigDecimal odds;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private BetStatus status = BetStatus.PENDING;

    @Column(name = "payout", nullable = false)
    private BigDecimal payout = BigDecimal.ZERO;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Bet() {
        // cho JPA
    }

    public Bet(UUID roundId, UUID tableId, UUID userId, BetType betType, String selection,
               BigDecimal stake, String idempotencyKey) {
        this(roundId, tableId, userId, betType, selection, stake, idempotencyKey, null);
    }

    /**
     * Dạng đầy đủ có chốt odds.
     *
     * Dạng cũ ở trên được giữ lại và ủy quyền xuống đây với `odds = null`, để các chỗ
     * đang tạo cược trong test không phải sửa đồng loạt.
     *
     * @param odds odds lợi hiệu lực, hoặc null để thanh toán dùng mặc định engine
     */
    public Bet(UUID roundId, UUID tableId, UUID userId, BetType betType, String selection,
               BigDecimal stake, String idempotencyKey, BigDecimal odds) {
        this.roundId = roundId;
        this.tableId = tableId;
        this.userId = userId;
        this.betType = betType;
        this.selection = selection == null ? "" : selection;
        this.stake = stake;
        this.idempotencyKey = idempotencyKey;
        this.odds = odds;
        this.status = BetStatus.PENDING;
        this.payout = BigDecimal.ZERO;
    }

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    public void settle(BigDecimal payout) {
        this.payout = payout;
        this.status = BetStatus.SETTLED;
        touch();
    }

    public void markVoided() {
        this.status = BetStatus.VOIDED;
        this.payout = BigDecimal.ZERO;
        touch();
    }

    public void touch() { this.updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS); }

    public UUID getId() { return id; }
    public Instant getCreatedAt() { return createdAt; }
    public UUID getRoundId() { return roundId; }
    public UUID getTableId() { return tableId; }
    public UUID getUserId() { return userId; }
    public BetType getBetType() { return betType; }
    public String getSelection() { return selection; }
    public BigDecimal getStake() { return stake; }
    public BigDecimal getOdds() { return odds; }
    public BetStatus getStatus() { return status; }
    public BigDecimal getPayout() { return payout; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Instant getUpdatedAt() { return updatedAt; }
}
