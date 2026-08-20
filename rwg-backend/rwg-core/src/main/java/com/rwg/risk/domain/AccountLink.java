package com.rwg.risk.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Liên kết hai tài khoản bị nghi là cùng một người (map bảng account_links).
 *
 * ===== CẶP LUÔN ĐƯỢC SẮP XẾP =====
 * {@code userAId < userBId} về thứ tự chuỗi. Constructor là {@code private} và chỉ
 * tạo được qua {@link #of}, nên KHÔNG có đường nào tạo được dòng sai chiều. Nếu để
 * tự do thì (A,B) và (B,A) là hai dòng khác nhau: UNIQUE ở DB vô hiệu, và người
 * vận hành sẽ thấy cùng một liên kết hai lần với hai trạng thái có thể ngược nhau.
 *
 * ===== KHÔNG KHOÁ TÀI KHOẢN =====
 * Entity này KHÔNG chạm tới trạng thái user. Tác dụng duy nhất của nó là loại
 * turnover khỏi cơ sở tính hoa hồng (xem {@link #blocksCommission()}).
 */
@Entity
@Table(name = "account_links")
public class AccountLink {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    /** Luôn là id NHỎ HƠN trong cặp. */
    @Column(name = "user_a_id", nullable = false)
    private UUID userAId;

    /** Luôn là id LỚN HƠN trong cặp. */
    @Column(name = "user_b_id", nullable = false)
    private UUID userBId;

    @Enumerated(EnumType.STRING)
    @Column(name = "link_type", nullable = false, length = 16)
    private AccountLinkType linkType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private AccountLinkStatus status = AccountLinkStatus.SUSPECTED;

    /** Bằng chứng dạng JSON (fingerprint/IP đã khớp, số tài khoản trong chùm). */
    @Column(name = "evidence")
    private String evidence;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "note", length = 255)
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AccountLink() {
        // cho JPA
    }

    /**
     * Tạo liên kết, TỰ SẮP XẾP cặp theo id. Gọi với thứ tự nào cũng ra cùng một dòng.
     *
     * @throws IllegalArgumentException nếu hai id trùng nhau (tự liên kết chính mình)
     */
    public static AccountLink of(UUID first, UUID second, AccountLinkType type, String evidence) {
        if (first.equals(second)) {
            // Tự liên kết chính mình là vô nghĩa và sẽ vi phạm CHECK ở DB.
            throw new IllegalArgumentException("Không thể liên kết một tài khoản với chính nó");
        }
        AccountLink link = new AccountLink();
        boolean firstIsSmaller = first.toString().compareTo(second.toString()) < 0;
        link.userAId = firstIsSmaller ? first : second;
        link.userBId = firstIsSmaller ? second : first;
        link.linkType = type;
        link.status = AccountLinkStatus.SUSPECTED;
        link.evidence = evidence;
        return link;
    }

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    /** Người vận hành kết luận. */
    public void review(AccountLinkStatus decision, UUID adminId, String note) {
        if (decision == AccountLinkStatus.SUSPECTED) {
            // Không có đường quay lại SUSPECTED: kết luận của người thắng máy.
            throw new IllegalArgumentException("Không thể đưa liên kết về lại trạng thái SUSPECTED");
        }
        this.status = decision;
        this.reviewedBy = adminId;
        this.reviewedAt = Instant.now();
        this.note = note;
    }

    /**
     * Liên kết này có giữ hoa hồng hay không — LUẬT DUY NHẤT, đặt trong domain để
     * job hoa hồng và API admin không thể hiểu khác nhau.
     *
     * <pre>
     * CONFIRMED (mọi loại)      -> GIỮ: người thật đã xác nhận cùng một người.
     * SHARED_DEVICE + SUSPECTED -> GIỮ: tín hiệu mạnh, thà giữ rồi trả sau còn hơn
     *                                   mất tiền rồi mới phát hiện.
     * SHARED_IP + SUSPECTED     -> KHÔNG: trùng IP là tín hiệu yếu (NAT nhà mạng,
     *                                   cùng wifi), giữ tiền sẽ chặn oan người
     *                                   giới thiệu thật. Chỉ vào hàng đợi.
     * CLEARED                   -> KHÔNG: đã gỡ oan.
     * </pre>
     */
    public boolean blocksCommission() {
        return switch (status) {
            case CONFIRMED -> true;
            case SUSPECTED -> linkType != AccountLinkType.SHARED_IP;
            case CLEARED -> false;
        };
    }

    /** Id còn lại trong cặp. */
    public UUID otherThan(UUID userId) {
        return userAId.equals(userId) ? userBId : userAId;
    }

    public UUID getId() { return id; }
    public UUID getUserAId() { return userAId; }
    public UUID getUserBId() { return userBId; }
    public AccountLinkType getLinkType() { return linkType; }
    public AccountLinkStatus getStatus() { return status; }
    public String getEvidence() { return evidence; }
    public UUID getReviewedBy() { return reviewedBy; }
    public Instant getReviewedAt() { return reviewedAt; }
    public String getNote() { return note; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
