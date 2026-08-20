package com.rwg.wallet.domain;

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
 * Một dòng ledger double-entry (map bảng V1.wallet_transactions). Mỗi dòng CHỈ debit
 * HOẶC credit (CHECK chk_wallet_tx_single_direction). PK composite (id, created_at).
 * idempotency_key đảm bảo một nghiệp vụ tiền không ghi sổ 2 lần.
 */
@Entity
@Table(name = "wallet_transactions")
@IdClass(WalletTransactionId.class)
public class WalletTransaction {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Id
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "wallet_id", nullable = false)
    private UUID walletId;

    @Column(name = "debit", nullable = false)
    private BigDecimal debit = BigDecimal.ZERO;

    @Column(name = "credit", nullable = false)
    private BigDecimal credit = BigDecimal.ZERO;

    @Column(name = "balance_after", nullable = false)
    private BigDecimal balanceAfter;

    @Enumerated(EnumType.STRING)
    @Column(name = "ref_type", nullable = false, length = 32)
    private WalletRefType refType;

    @Column(name = "ref_id", nullable = false, length = 64)
    private String refId;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private WalletTxStatus status = WalletTxStatus.SETTLED;

    @Column(name = "description", length = 255)
    private String description;

    protected WalletTransaction() {
        // cho JPA
    }

    /** Dòng debit (tiền ra). */
    public static WalletTransaction debit(UUID walletId, BigDecimal amount, BigDecimal balanceAfter,
                                          WalletRefType refType, String refId, String idempotencyKey) {
        WalletTransaction tx = new WalletTransaction();
        tx.walletId = walletId;
        tx.debit = amount;
        tx.credit = BigDecimal.ZERO;
        tx.balanceAfter = balanceAfter;
        tx.refType = refType;
        tx.refId = refId;
        tx.idempotencyKey = idempotencyKey;
        tx.status = WalletTxStatus.SETTLED;
        return tx;
    }

    /** Dòng credit (tiền vào). */
    public static WalletTransaction credit(UUID walletId, BigDecimal amount, BigDecimal balanceAfter,
                                           WalletRefType refType, String refId, String idempotencyKey) {
        WalletTransaction tx = new WalletTransaction();
        tx.walletId = walletId;
        tx.debit = BigDecimal.ZERO;
        tx.credit = amount;
        tx.balanceAfter = balanceAfter;
        tx.refType = refType;
        tx.refId = refId;
        tx.idempotencyKey = idempotencyKey;
        tx.status = WalletTxStatus.SETTLED;
        return tx;
    }

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public UUID getId() { return id; }
    public Instant getCreatedAt() { return createdAt; }
    public UUID getWalletId() { return walletId; }
    public BigDecimal getDebit() { return debit; }
    public BigDecimal getCredit() { return credit; }
    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public WalletRefType getRefType() { return refType; }
    public String getRefId() { return refId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public WalletTxStatus getStatus() { return status; }
    public String getDescription() { return description; }

    public void setDescription(String description) { this.description = description; }
}
