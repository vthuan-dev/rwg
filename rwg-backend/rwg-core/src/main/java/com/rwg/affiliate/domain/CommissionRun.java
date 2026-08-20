package com.rwg.affiliate.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Chứng từ chi hoa hồng cho một đại lý, cho MỘT ngày, ở MỘT cấp
 * (map bảng V20260820_09.commission_runs).
 *
 * uq_commission_runs_agent_period_level là chốt an toàn quan trọng nhất của
 * nghiệp vụ hoa hồng: job chạy lại (retry / deploy trùng instance / admin bấm
 * tay) đều KHÔNG thể trả hoa hồng hai lần cho cùng (đại lý, ngày, cấp).
 *
 * Lưu cả {@code rate} và {@code turnover} tại thời điểm chốt (không chỉ số tiền)
 * để đối soát về sau vẫn tái dựng được con số, kể cả khi admin đã đổi cấu hình %.
 *
 * Tiền dùng BigDecimal — CẤM float/double (ArchUnit enforce).
 */
@Entity
@Table(name = "commission_runs")
public class CommissionRun {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "agent_id", nullable = false)
    private UUID agentId;

    /** Ngày (UTC) được chốt hoa hồng — KHÔNG phải thời điểm chạy job. */
    @Column(name = "period_date", nullable = false)
    private LocalDate periodDate;

    @Column(name = "level", nullable = false)
    private short level;

    @Column(name = "turnover", nullable = false)
    private BigDecimal turnover;

    @Column(name = "rate", nullable = false)
    private BigDecimal rate;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CommissionRun() {
        // cho JPA
    }

    public CommissionRun(UUID agentId, LocalDate periodDate, int level,
                         BigDecimal turnover, BigDecimal rate, BigDecimal amount,
                         String idempotencyKey) {
        this.agentId = agentId;
        this.periodDate = periodDate;
        this.level = (short) level;
        this.turnover = turnover;
        this.rate = rate;
        this.amount = amount;
        this.idempotencyKey = idempotencyKey;
    }

    /**
     * Khóa idempotency dùng khi credit ví. Theo (đại lý, ngày, cấp) nên trùng
     * khóa nghĩa là ĐÚNG nghiệp vụ đó đã chi — wallet_ledger_guard sẽ chặn.
     */
    public static String idempotencyKeyFor(UUID agentId, LocalDate periodDate, int level) {
        return "COMMISSION:" + agentId + ":" + periodDate + ":L" + level;
    }

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getAgentId() { return agentId; }
    public LocalDate getPeriodDate() { return periodDate; }
    public short getLevel() { return level; }
    public BigDecimal getTurnover() { return turnover; }
    public BigDecimal getRate() { return rate; }
    public BigDecimal getAmount() { return amount; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Instant getCreatedAt() { return createdAt; }
}
