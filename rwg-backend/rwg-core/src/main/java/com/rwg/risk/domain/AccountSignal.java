package com.rwg.risk.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Dấu vết kỹ thuật lúc đăng ký (map bảng account_signals). Mỗi user đúng một dòng
 * nên {@code userId} làm khoá chính luôn.
 *
 * IP lưu PLAINTEXT, User-Agent thì HASH — có lý do: {@code audit_log.ip_address} đã
 * lưu plaintext nên hash IP ở đây không giảm được mức phơi bày dữ liệu, mà lại làm
 * người điều tra không đối chiếu được với audit log. User-Agent thì dài, không ai
 * đọc thủ công, và chỉ dùng để so khớp bằng nhau -> hash là đủ.
 */
@Entity
@Table(name = "account_signals")
public class AccountSignal {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "registration_ip", nullable = false, length = 64)
    private String registrationIp;

    /** SHA-256 của header X-Device-Id; null khi client không gửi. */
    @Column(name = "device_fingerprint", length = 128)
    private String deviceFingerprint;

    @Column(name = "user_agent_hash", length = 64)
    private String userAgentHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AccountSignal() {
        // cho JPA
    }

    public AccountSignal(UUID userId, String registrationIp,
                         String deviceFingerprint, String userAgentHash) {
        this.userId = userId;
        this.registrationIp = registrationIp;
        this.deviceFingerprint = deviceFingerprint;
        this.userAgentHash = userAgentHash;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getUserId() { return userId; }
    public String getRegistrationIp() { return registrationIp; }
    public String getDeviceFingerprint() { return deviceFingerprint; }
    public String getUserAgentHash() { return userAgentHash; }
    public Instant getCreatedAt() { return createdAt; }
}
