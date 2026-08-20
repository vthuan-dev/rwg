package com.rwg.affiliate.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Mã giới thiệu của một user (map bảng V20260820_09.referral_codes).
 * PK là chính mã code -> tra cứu lúc đăng ký chỉ cần 1 lookup theo khóa chính.
 * Mỗi user đúng 1 mã (uq_referral_codes_user_id).
 */
@Entity
@Table(name = "referral_codes")
public class ReferralCode {

    @Id
    @Column(name = "code", nullable = false, length = 16)
    private String code;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ReferralCode() {
        // cho JPA
    }

    public ReferralCode(String code, UUID userId) {
        this.code = code;
        this.userId = userId;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public String getCode() { return code; }
    public UUID getUserId() { return userId; }
    public Instant getCreatedAt() { return createdAt; }
}
