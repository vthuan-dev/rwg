package com.rwg.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Đề nghị thao tác admin cần phê duyệt bởi người thứ hai (quy trình 4 mắt).
 * Map bảng V20260820_10.admin_approval_requests.
 *
 * Tồn tại để chặn một admin đơn độc chuyển tiền ra khỏi sàn: thao tác vượt ngưỡng
 * chỉ tạo đề nghị ở trạng thái PENDING, TIỀN CHƯA CHUYỂN. Phải có admin THỨ HAI
 * phê duyệt thì {@code AdminApprovalService} mới gọi WalletService.credit/debit.
 *
 * Người tạo không được tự duyệt — chặn ở cả service và CHECK constraint của DB.
 *
 * Tiền dùng BigDecimal — CẤM float/double (ArchUnit enforce).
 */
@Entity
@Table(name = "admin_approval_requests")
public class AdminApprovalRequest {

    public static final String TYPE_WALLET_ADJUSTMENT = "WALLET_ADJUSTMENT";
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "type", nullable = false, length = 32)
    private String type;

    @Column(name = "status", nullable = false, length = 16)
    private String status = STATUS_PENDING;

    @Column(name = "target_user_id", nullable = false)
    private UUID targetUserId;

    @Column(name = "direction", nullable = false, length = 8)
    private String direction;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "reason", nullable = false, length = 255)
    private String reason;

    @Column(name = "maker_id", nullable = false)
    private UUID makerId;

    @Column(name = "checker_id")
    private UUID checkerId;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_note", length = 255)
    private String decisionNote;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AdminApprovalRequest() {
        // cho JPA
    }

    public AdminApprovalRequest(String type, UUID targetUserId, String direction,
                                BigDecimal amount, String reason, UUID makerId,
                                String idempotencyKey) {
        this.type = type;
        this.targetUserId = targetUserId;
        this.direction = direction;
        this.amount = amount;
        this.reason = reason;
        this.makerId = makerId;
        this.idempotencyKey = idempotencyKey;
    }

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }

    public boolean isPending() {
        return STATUS_PENDING.equals(status);
    }

    /**
     * Ghi nhận quyết định. KHÔNG tự kiểm tra maker != checker ở đây — việc đó do
     * service kiểm trước (để trả đúng mã lỗi i18n) và CHECK constraint của DB chốt lại.
     */
    public void decide(String newStatus, UUID checkerId, String note) {
        this.status = newStatus;
        this.checkerId = checkerId;
        this.decisionNote = note;
        this.decidedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getType() { return type; }
    public String getStatus() { return status; }
    public UUID getTargetUserId() { return targetUserId; }
    public String getDirection() { return direction; }
    public BigDecimal getAmount() { return amount; }
    public String getReason() { return reason; }
    public UUID getMakerId() { return makerId; }
    public UUID getCheckerId() { return checkerId; }
    public Instant getDecidedAt() { return decidedAt; }
    public String getDecisionNote() { return decisionNote; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Instant getCreatedAt() { return createdAt; }
}
