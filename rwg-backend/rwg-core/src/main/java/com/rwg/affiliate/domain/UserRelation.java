package com.rwg.affiliate.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Quan hệ đại lý (map bảng V20260820_09.user_relations).
 *
 * Lưu PHẲNG THEO CẤP, không lưu cây đệ quy: mỗi user mới sinh tối đa 2 dòng
 * (level 1 = người giới thiệu trực tiếp, level 2 = người giới thiệu của người đó).
 * Nhờ vậy job hoa hồng chỉ cần JOIN phẳng theo ancestorId, không truy vấn đệ quy.
 */
@Entity
@Table(name = "user_relations")
public class UserRelation {

    /** Cấp tối đa hỗ trợ — đặc tả giới hạn 2 cấp để bảo toàn hiệu năng. */
    public static final int MAX_LEVEL = 2;

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    /** Tuyến trên — đại lý nhận hoa hồng. */
    @Column(name = "ancestor_id", nullable = false)
    private UUID ancestorId;

    /** Tuyến dưới — người chơi tạo ra turnover. */
    @Column(name = "descendant_id", nullable = false)
    private UUID descendantId;

    @Column(name = "level", nullable = false)
    private short level;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected UserRelation() {
        // cho JPA
    }

    public UserRelation(UUID ancestorId, UUID descendantId, int level) {
        this.ancestorId = ancestorId;
        this.descendantId = descendantId;
        this.level = (short) level;
    }

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getAncestorId() { return ancestorId; }
    public UUID getDescendantId() { return descendantId; }
    public short getLevel() { return level; }
    public Instant getCreatedAt() { return createdAt; }
}
