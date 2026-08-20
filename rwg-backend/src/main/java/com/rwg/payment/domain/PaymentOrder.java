package com.rwg.payment.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Lệnh thanh toán (map bảng payment_orders). Dùng chung cho DEPOSIT và WITHDRAWAL.
 * Số dư/amount dùng BigDecimal DECIMAL(20,8) — KHÔNG dùng float/double.
 */
@Entity
@Table(name = "payment_orders")
@IdClass(PaymentOrderId.class)
public class PaymentOrder {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Id
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "provider", nullable = false, length = 32)
    private String provider;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 16)
    private PaymentType type;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 8)
    private String currency = "USD";

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "provider_txn_id", length = 128)
    private String providerTxnId;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "bank_account_id")
    private UUID bankAccountId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PaymentOrder() {
        // cho JPA
    }

    public static PaymentOrder deposit(UUID userId, String provider, BigDecimal amount, String idempotencyKey) {
        PaymentOrder o = new PaymentOrder();
        o.userId = userId;
        o.provider = provider;
        o.type = PaymentType.DEPOSIT;
        o.amount = amount;
        o.status = PaymentStatus.PENDING;
        o.idempotencyKey = idempotencyKey;
        return o;
    }

    public static PaymentOrder withdrawal(UUID userId, String provider, BigDecimal amount,
                                          UUID bankAccountId, String idempotencyKey) {
        PaymentOrder o = new PaymentOrder();
        o.userId = userId;
        o.provider = provider;
        o.type = PaymentType.WITHDRAWAL;
        o.amount = amount;
        o.status = PaymentStatus.PENDING;
        o.bankAccountId = bankAccountId;
        o.idempotencyKey = idempotencyKey;
        return o;
    }

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public UUID getId() { return id; }
    public Instant getCreatedAt() { return createdAt; }
    public UUID getUserId() { return userId; }
    public String getProvider() { return provider; }
    public PaymentType getType() { return type; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public PaymentStatus getStatus() { return status; }
    public String getProviderTxnId() { return providerTxnId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public UUID getBankAccountId() { return bankAccountId; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setStatus(PaymentStatus status) { this.status = status; }
    public void setProviderTxnId(String providerTxnId) { this.providerTxnId = providerTxnId; }
}
