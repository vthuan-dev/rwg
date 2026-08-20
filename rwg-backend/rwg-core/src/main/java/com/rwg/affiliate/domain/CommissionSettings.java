package com.rwg.affiliate.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Cấu hình % hoa hồng (map bảng V20260820_09.commission_settings).
 *
 * Lưu DB thay vì file config để admin sửa được lúc chạy — đặc tả mục 6.2 coi
 * "thay đổi % hoa hồng" là hành vi cần giám sát, hàm ý đây là thao tác admin
 * chứ không phải hằng số biên dịch.
 *
 * Chỉ tồn tại ĐÚNG 1 dòng (id = 1, enforce bằng CHECK ở DB).
 */
@Entity
@Table(name = "commission_settings")
public class CommissionSettings {

    /** Id cố định của dòng singleton. */
    public static final short SINGLETON_ID = 1;

    @Id
    @Column(name = "id", nullable = false)
    private short id = SINGLETON_ID;

    @Column(name = "level1_rate", nullable = false)
    private BigDecimal level1Rate;

    @Column(name = "level2_rate", nullable = false)
    private BigDecimal level2Rate;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    protected CommissionSettings() {
        // cho JPA
    }

    @PrePersist
    void onCreate() {
        if (updatedAt == null) updatedAt = Instant.now();
    }

    /** Tỷ lệ theo cấp; cấp ngoài 1..2 là lỗi lập trình, không phải lỗi dữ liệu. */
    public BigDecimal rateForLevel(int level) {
        return switch (level) {
            case 1 -> level1Rate;
            case 2 -> level2Rate;
            default -> throw new IllegalArgumentException("Cấp hoa hồng không hỗ trợ: " + level);
        };
    }

    public void update(BigDecimal level1Rate, BigDecimal level2Rate, UUID adminId) {
        this.level1Rate = level1Rate;
        this.level2Rate = level2Rate;
        this.updatedBy = adminId;
        this.updatedAt = Instant.now();
    }

    public short getId() { return id; }
    public BigDecimal getLevel1Rate() { return level1Rate; }
    public BigDecimal getLevel2Rate() { return level2Rate; }
    public Instant getUpdatedAt() { return updatedAt; }
    public UUID getUpdatedBy() { return updatedBy; }
}
