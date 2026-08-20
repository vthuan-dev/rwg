package com.rwg.wallet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Ví chính của user (map bảng V1.wallets). Mỗi user đúng 1 ví (uq_wallets_user_id).
 * Số dư dùng BigDecimal DECIMAL(20,8) — CẤM float/double (ArchUnit enforce package ..wallet..).
 * Cột version để optimistic-locking; nghiệp vụ debit/credit dùng UPDATE điều kiện
 * (balance >= amt) nên KHÔNG dùng @Version của JPA ở đây.
 */
@Entity
@Table(name = "wallets")
public class Wallet {

    public static final String DEFAULT_CURRENCY = "USD";

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "balance", nullable = false)
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(name = "currency", nullable = false, length = 8)
    private String currency = DEFAULT_CURRENCY;

    @Column(name = "version", nullable = false)
    private Long version = 0L;

    /** Mốc claim nạp tiền đầu tiên (fix review M5) — set bởi conditional UPDATE nguyên tử. */
    @Column(name = "first_deposit_at")
    private Instant firstDepositAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Wallet() {
        // cho JPA
    }

    public Wallet(UUID userId) {
        this.userId = userId;
        this.balance = BigDecimal.ZERO;
        this.currency = DEFAULT_CURRENCY;
    }

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (version == null) version = 0L;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public BigDecimal getBalance() { return balance; }
    public String getCurrency() { return currency; }
    public Long getVersion() { return version; }
    public Instant getFirstDepositAt() { return firstDepositAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
