package com.rwg.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Một thông báo gửi cho người chơi (map bảng notifications).
 *
 * Hai dạng:
 * - **Cá nhân**: {@code userId} có giá trị. Sinh tự động từ nghiệp vụ tiền.
 * - **Tin chung**: {@code userId} NULL, hiện cho mọi người. Do admin viết.
 *
 * Nội dung KHÔNG lưu câu đã dịch mà lưu {@code titleKey} + {@code paramsJson} để mỗi lần
 * xem đều dịch theo ngôn ngữ hiện tại của người dùng. Xem chú thích ở migration.
 *
 * PK composite (id, created_at) theo DECISIONS.md mục (b).
 */
@Entity
@Table(name = "notifications")
@IdClass(NotificationId.class)
public class Notification {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Id
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** NULL = tin chung cho mọi người. */
    @Column(name = "user_id")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    private NotificationType type;

    /** Khoá dịch, vd "notification.admin_credit". */
    @Column(name = "title_key", nullable = false, length = 64)
    private String titleKey;

    /** Tham số cho khoá dịch, JSON phẳng. NULL khi khoá không cần tham số. */
    @Column(name = "params_json", length = 512)
    private String paramsJson;

    /** Nội dung tự do của tin chung. NULL với thông báo sinh tự động. */
    @Column(name = "body", length = 1024)
    private String body;

    @Column(name = "read_at")
    private Instant readAt;

    protected Notification() {
        // cho JPA
    }

    private Notification(UUID userId, NotificationType type, String titleKey,
                         String paramsJson, String body) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.type = type;
        this.titleKey = titleKey;
        this.paramsJson = paramsJson;
        this.body = body;
    }

    /** Thông báo cá nhân sinh tự động từ nghiệp vụ tiền. */
    public static Notification personal(UUID userId, NotificationType type,
                                        String titleKey, String paramsJson) {
        if (userId == null) {
            throw new IllegalArgumentException("userId bắt buộc với thông báo cá nhân");
        }
        return new Notification(userId, type, titleKey, paramsJson, null);
    }

    /** Tin chung cho mọi người ({@code userId} NULL). */
    public static Notification broadcast(String titleKey, String body) {
        return new Notification(null, NotificationType.ANNOUNCEMENT, titleKey, null, body);
    }

    /**
     * Đánh dấu đã đọc.
     *
     * KHÔNG ghi đè {@code readAt} nếu đã có: mốc đọc LẦN ĐẦU mới là thứ có giá trị khi phải
     * tra cứu khiếu nại "tôi chưa từng được thông báo". Ghi đè mỗi lần mở lại danh sách sẽ
     * đẩy mốc đó trôi dần về hiện tại và mất hẳn thông tin gốc.
     */
    public void markRead(Instant now) {
        if (readAt == null) {
            readAt = now.truncatedTo(ChronoUnit.MICROS);
        }
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            // Cắt xuống micro giây cho khớp DATETIME(6) của MySQL: Instant của Java có độ
            // phân giải nano, nên không cắt thì giá trị đọc lại từ DB sẽ khác giá trị vừa
            // ghi và mọi so sánh bằng đều sai.
            createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        }
    }

    public UUID getId() { return id; }
    public Instant getCreatedAt() { return createdAt; }
    public UUID getUserId() { return userId; }
    public NotificationType getType() { return type; }
    public String getTitleKey() { return titleKey; }
    public String getParamsJson() { return paramsJson; }
    public String getBody() { return body; }
    public Instant getReadAt() { return readAt; }

    /** Tin chung: hiện cho mọi người, và KHÔNG có trạng thái đọc riêng theo từng người. */
    public boolean isBroadcast() {
        return userId == null;
    }
}
