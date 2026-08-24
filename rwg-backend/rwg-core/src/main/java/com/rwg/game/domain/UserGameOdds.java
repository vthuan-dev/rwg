package com.rwg.game.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Tỷ lệ cược riêng của MỘT người chơi ở MỘT bàn, cho MỘT loại cược.
 *
 * Không có bản ghi nghĩa là người chơi dùng tỷ lệ mặc định của engine. Cách này thay cho
 * việc chèn sẵn đủ bản ghi cho mọi người: 6 bàn nhân 5 loại cược nhân số người chơi sẽ
 * phình rất nhanh, mà đa số người chơi dùng mức chung.
 *
 * Khoá duy nhất (user_id, table_id, bet_type) nằm ở tầng cơ sở dữ liệu, không chỉ ở tầng
 * ứng dụng: hai yêu cầu đặt tỷ lệ cùng lúc cho cùng một tổ hợp sẽ có một cái thất bại
 * thay vì tạo ra hai dòng mà không biết dòng nào có hiệu lực.
 */
@Entity
@Table(name = "user_game_odds")
public class UserGameOdds {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "table_id", nullable = false)
    private UUID tableId;

    @Enumerated(EnumType.STRING)
    @Column(name = "bet_type", nullable = false, length = 16)
    private BetType betType;

    /** Odds lợi, cùng quy ước engine: 0.98 nghĩa là cược 100 thắng nhận 198. */
    @Column(name = "odds", nullable = false)
    private BigDecimal odds;

    /**
     * Lý do điều chỉnh, ĐƯỢC PHÉP null.
     *
     * Không bắt buộc vì người vận hành thường chỉ nhích tỷ lệ vài phần trăm; bắt gõ lý do
     * chỉ sinh ra những câu vô nghĩa cho qua ràng buộc. Dấu vết thật nằm ở audit
     * `ADMIN_USER_ODDS_CHANGED`: người thực hiện, thời điểm, IP, tỷ lệ trước/sau.
     *
     * Service chuẩn hoá chuỗi rỗng thành null, nên chỉ có MỘT cách biểu diễn "không có lý do".
     */
    @Column(name = "reason", length = 255)
    private String reason;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserGameOdds() {
        // cho JPA
    }

    public UserGameOdds(UUID userId, UUID tableId, BetType betType, BigDecimal odds,
                        String reason, UUID createdBy) {
        this.userId = userId;
        this.tableId = tableId;
        this.betType = betType;
        this.odds = odds;
        this.reason = reason;
        this.createdBy = createdBy;
    }

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    /**
     * Đổi tỷ lệ của bản ghi đã có.
     *
     * Ghi lại cả người thực hiện và lý do MỖI LẦN đổi, không chỉ lần tạo: nếu chỉ giữ
     * thông tin lần tạo thì sau vài lần chỉnh sẽ không biết ai đặt ra con số đang có
     * hiệu lực.
     */
    public void update(BigDecimal odds, String reason, UUID updatedBy) {
        this.odds = odds;
        this.reason = reason;
        this.createdBy = updatedBy;
        this.updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getTableId() { return tableId; }
    public BetType getBetType() { return betType; }
    public BigDecimal getOdds() { return odds; }
    public String getReason() { return reason; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
