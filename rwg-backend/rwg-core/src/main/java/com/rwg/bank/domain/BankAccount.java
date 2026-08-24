package com.rwg.bank.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Phương thức nhận tiền của user (map bảng bank_accounts).
 *
 * Chỉ hỗ trợ tài khoản ngân hàng (BANK) theo yêu cầu mới nhất của dự án.
 *
 * Số tài khoản được lưu dạng mã hoá (ciphertext + iv).
 *
 * PK composite (id, created_at) theo DECISIONS.md mục (b).
 */
@Entity
@Table(name = "bank_accounts")
@IdClass(BankAccountId.class)
public class BankAccount {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Id
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** Mã ngân hàng. Bắt buộc. */
    @Column(name = "bank_code", nullable = false, length = 32)
    private String bankCode;

    /** Số tài khoản ĐÃ MÃ HÓA AES-256-GCM (base64) — KHÔNG plaintext. */
    @Column(name = "account_number_ciphertext", nullable = false, length = 512)
    private String accountNumberCiphertext;

    /** IV của lần mã hóa (base64) — GCM cần IV riêng để giải mã. */
    @Column(name = "account_number_iv", nullable = false, length = 64)
    private String accountNumberIv;

    /** 4 ký tự cuối để hiển thị (vd "1234"). */
    @Column(name = "masked_last4", nullable = false, length = 4)
    private String maskedLast4;

    /** Tên chủ tài khoản. Bắt buộc. */
    @Column(name = "holder_name", nullable = false, length = 128)
    private String holderName;

    @Column(name = "is_default", nullable = false)
    private Boolean isDefault = Boolean.FALSE;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private BankAccountStatus status = BankAccountStatus.ACTIVE;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BankAccount() {
        // cho JPA
    }

    /**
     * Tạo phương thức nhận tiền dạng TÀI KHOẢN NGÂN HÀNG.
     */
    public static BankAccount createBank(UUID userId, String bankCode, String ciphertext, String iv,
                                         String maskedLast4, String holderName, boolean isDefault) {
        BankAccount ba = new BankAccount();
        ba.userId = userId;
        ba.bankCode = bankCode;
        ba.accountNumberCiphertext = ciphertext;
        ba.accountNumberIv = iv;
        ba.maskedLast4 = maskedLast4;
        ba.holderName = holderName;
        ba.isDefault = isDefault;
        ba.status = BankAccountStatus.ACTIVE;
        return ba;
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
    public String getBankCode() { return bankCode; }
    public String getAccountNumberCiphertext() { return accountNumberCiphertext; }
    public String getAccountNumberIv() { return accountNumberIv; }
    public String getMaskedLast4() { return maskedLast4; }
    public String getHolderName() { return holderName; }
    public Boolean getIsDefault() { return isDefault; }
    public BankAccountStatus getStatus() { return status; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setIsDefault(Boolean isDefault) { this.isDefault = isDefault; }
    public void setStatus(BankAccountStatus status) { this.status = status; }
}
