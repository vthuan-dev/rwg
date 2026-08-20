package com.rwg.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Người dùng RWG. KHÔNG lộ entity này ra API (luôn map sang DTO ở tầng api).
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "username", nullable = false, unique = true, length = 32)
    private String username;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    /** BCrypt strength 12. */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    /** Hash riêng cho mật khẩu rút tiền; null cho tới khi user đặt. */
    @Column(name = "withdrawal_password_hash", length = 100)
    private String withdrawalPasswordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 16)
    private UserRole role = UserRole.PLAYER;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private UserStatus status = UserStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_level", nullable = false, length = 16)
    private KycLevel kycLevel = KycLevel.NONE;

    /** Ngôn ngữ hiển thị ưu tiên cho user (en/vi/zh/ja) — i18n chặng 2. */
    @Column(name = "locale", nullable = false, length = 8)
    private String locale = "en";

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected User() {
        // cho JPA
    }

    public User(String username, String email, String passwordHash) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getWithdrawalPasswordHash() { return withdrawalPasswordHash; }
    public UserRole getRole() { return role; }
    public UserStatus getStatus() { return status; }
    public KycLevel getKycLevel() { return kycLevel; }
    public String getLocale() { return locale; }
    public Instant getLastLoginAt() { return lastLoginAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setWithdrawalPasswordHash(String withdrawalPasswordHash) {
        this.withdrawalPasswordHash = withdrawalPasswordHash;
    }

    /** Đổi mật khẩu đăng nhập (hash BCrypt mới). */
    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void setLastLoginAt(Instant lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }

    public void setKycLevel(KycLevel kycLevel) {
        this.kycLevel = kycLevel;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }
}
