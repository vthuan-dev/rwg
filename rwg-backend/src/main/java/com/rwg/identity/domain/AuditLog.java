package com.rwg.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Audit trail APPEND-ONLY: chỉ INSERT, KHÔNG UPDATE/DELETE.
 * Mọi sự kiện login/register/đổi mật khẩu/giao dịch đều ghi qua AuditTrailService.
 * Cột details lưu JSON (MySQL dùng JSON, H2 test dùng JSON ở MODE=MySQL).
 */
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** NULL với sự kiện chưa đăng nhập (register, login fail). */
    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "actor_username", length = 32)
    private String actorUsername;

    @Column(name = "action", nullable = false, length = 64)
    private String action;

    @Column(name = "target_type", length = 32)
    private String targetType;

    @Column(name = "target_id", length = 64)
    private String targetId;

    /**
     * Chuỗi JSON; KHÔNG chứa mật khẩu thô.
     * MySQL: cột JSON (bind dạng varchar nhờ @JdbcTypeCode LONGVARCHAR).
     * H2 test (MODE=MySQL): cột JSON - cùng cách bind nên tương thích cả hai.
     */
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "details")
    private String details;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AuditLog() {
        // cho JPA
    }

    public AuditLog(UUID actorId, String actorUsername, String action,
                    String targetType, String targetId, String details, String ipAddress) {
        this.actorId = actorId;
        this.actorUsername = actorUsername;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.details = details;
        this.ipAddress = ipAddress;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public UUID getActorId() { return actorId; }
    public String getActorUsername() { return actorUsername; }
    public String getAction() { return action; }
    public String getTargetType() { return targetType; }
    public String getTargetId() { return targetId; }
    public String getDetails() { return details; }
    public String getIpAddress() { return ipAddress; }
    public Instant getCreatedAt() { return createdAt; }
}
