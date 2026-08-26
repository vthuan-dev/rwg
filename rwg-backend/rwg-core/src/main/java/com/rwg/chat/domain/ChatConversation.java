package com.rwg.chat.domain;

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
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Luồng hội thoại hỗ trợ của MỘT người chơi (map bảng chat_conversations).
 *
 * Mỗi người chơi đúng một luồng, dùng lại mãi. Xem chú thích ở migration để biết
 * vì sao không dùng mô hình ticket.
 *
 * BỐN CỘT PHI CHUẨN HOÁ ({@code lastMessageAt}, {@code lastMessagePreview},
 * {@code unreadForAdmin}, {@code unreadForPlayer}) chỉ được cập nhật qua
 * {@link #appendPlayerMessage}, {@link #appendStaffMessage} và {@link #markReadBy}.
 * ĐỪNG đặt trực tiếp từ service: chúng phải đổi CÙNG NHAU với việc chèn tin nhắn,
 * và mỗi chỗ gọi tự cập nhật từng cột là cách chắc chắn nhất để chúng lệch khỏi
 * bảng tin nhắn.
 */
@Entity
@Table(name = "chat_conversations")
public class ChatConversation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ChatConversationStatus status = ChatConversationStatus.OPEN;

    /** Nhân sự đang phụ trách; null = còn trong hàng đợi chung. */
    @Column(name = "assigned_admin_id")
    private UUID assignedAdminId;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    @Column(name = "last_message_preview", length = ChatMessage.PREVIEW_LENGTH)
    private String lastMessagePreview;

    @Column(name = "unread_for_admin", nullable = false)
    private int unreadForAdmin;

    @Column(name = "unread_for_player", nullable = false)
    private int unreadForPlayer;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // ===== VỊ TRÍ ĐỊA LÝ SUY TỪ IP =====
    //
    // ĐẶT TRÊN LUỒNG, KHÔNG ĐẶT TRÊN TỪNG TIN NHẮN: nhân sự cần biết "khách này ở
    // đâu" một lần ở đầu đoạn chat, không phải vị trí của từng câu. Lưu theo tin nhắn
    // sẽ nhân số dòng dữ liệu lên hàng nghìn lần mà không thêm thông tin nào dùng được.

    /** IP gần nhất thấy khách dùng. */
    @Column(name = "last_ip", length = 45)
    private String lastIp;

    @Column(name = "geo_country_code", length = 2)
    private String geoCountryCode;

    @Column(name = "geo_country_name", length = 64)
    private String geoCountryName;

    @Column(name = "geo_region", length = 96)
    private String geoRegion;

    @Column(name = "geo_city", length = 96)
    private String geoCity;

    @Column(name = "geo_isp", length = 128)
    private String geoIsp;

    /** Lần tra IP gần nhất. null = chưa tra bao giờ. */
    @Column(name = "geo_resolved_at")
    private Instant geoResolvedAt;

    /** IP đã được tra. Khác {@link #lastIp} nghĩa là kết quả hiện tại đã cũ. */
    @Column(name = "geo_resolved_ip", length = 45)
    private String geoResolvedIp;

    protected ChatConversation() {
        // cho JPA
    }

    public static ChatConversation openFor(UUID userId) {
        ChatConversation conversation = new ChatConversation();
        conversation.userId = userId;
        conversation.status = ChatConversationStatus.OPEN;
        return conversation;
    }

    /**
     * Ghi nhận một tin của NGƯỜI CHƠI vào luồng.
     *
     * TỰ MỞ LẠI luồng đã đóng. Nếu không, người chơi gửi tin vào một luồng CLOSED
     * và tin đó không xuất hiện trong hàng đợi của nhân sự — họ nói vào chỗ không
     * ai nghe mà vẫn thấy tin mình đã gửi đi.
     */
    public void appendPlayerMessage(ChatMessage message) {
        touchLastMessage(message);
        unreadForAdmin++;
        if (status == ChatConversationStatus.CLOSED) {
            status = ChatConversationStatus.OPEN;
        }
    }

    /** Ghi nhận một tin của NHÂN SỰ vào luồng. */
    public void appendStaffMessage(ChatMessage message) {
        touchLastMessage(message);
        unreadForPlayer++;
    }

    /**
     * Ghi nhận một dòng thông báo của hệ thống.
     *
     * CỐ TÌNH KHÔNG tăng bộ đếm chưa đọc — xem lý do ở {@link ChatSenderType#SYSTEM}.
     * Vẫn cập nhật {@code lastMessageAt} để hộp thư sắp xếp đúng theo hoạt động
     * gần nhất.
     */
    public void appendSystemMessage(ChatMessage message) {
        touchLastMessage(message);
    }

    private void touchLastMessage(ChatMessage message) {
        this.lastMessageAt = message.getCreatedAt();
        this.lastMessagePreview = message.preview();
    }

    /**
     * Một phía vừa đọc hết: đưa bộ đếm của CHÍNH phía đó về 0.
     *
     * Nhận vào loại người gửi của những tin ĐƯỢC đọc, chứ không phải phía đang đọc.
     * Nghe ngược nhưng đúng: khi nhân sự mở luồng, họ đọc các tin của PLAYER, nên
     * bộ đếm cần xoá là {@code unreadForAdmin}. Truyền vào "ai đang đọc" thì mỗi
     * chỗ gọi lại phải tự nhớ phép đổi này.
     */
    public void markReadBy(ChatSenderType readMessagesFrom) {
        if (readMessagesFrom == ChatSenderType.PLAYER) {
            unreadForAdmin = 0;
        } else if (readMessagesFrom == ChatSenderType.STAFF) {
            unreadForPlayer = 0;
        }
    }

    /**
     * Giao luồng cho một nhân sự.
     *
     * @return true nếu người phụ trách thực sự đổi — chỗ gọi dùng nó để quyết định
     *         có chèn dòng thông báo hệ thống hay không. Bấm "nhận việc" hai lần
     *         không nên tạo ra hai dòng "đã tiếp nhận" y hệt nhau.
     */
    public boolean assignTo(UUID adminId) {
        if (adminId != null && adminId.equals(assignedAdminId)) {
            return false;
        }
        this.assignedAdminId = adminId;
        return true;
    }

    /**
     * Ghi nhận IP khách đang dùng.
     *
     * @return true nếu cần TRA LẠI vị trí — IP mới, hoặc kết quả cũ hơn {@code cacheTtl}.
     *         Chỗ gọi dùng giá trị này để quyết định có gọi dịch vụ ngoài hay không.
     *
     * ĐỂ ENTITY TỰ TRẢ LỜI CÂU "CÓ CẦN TRA LẠI KHÔNG" thay vì để service so sánh các
     * cột: điều kiện này đụng tới ba cột ({@code lastIp}, {@code geoResolvedIp},
     * {@code geoResolvedAt}) và viết lại ở mọi chỗ gọi là cách chắc chắn nhất để chúng
     * lệch nhau — cùng lý do bốn cột phi chuẩn hoá ở trên chỉ được đổi qua phương thức.
     */
    public boolean recordIp(String ip, Instant now, java.time.Duration cacheTtl) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        this.lastIp = ip;

        if (!ip.equals(geoResolvedIp)) {
            return true;
        }
        return geoResolvedAt == null || geoResolvedAt.plus(cacheTtl).isBefore(now);
    }

    /**
     * Lưu kết quả tra IP.
     *
     * GHI {@code geoResolvedAt} VÀ {@code geoResolvedIp} KỂ CẢ KHI KHÔNG TRA ĐƯỢC GÌ.
     * Không ghi thì {@link #recordIp} luôn báo "cần tra lại", và hệ thống sẽ gọi dịch vụ
     * ngoài mỗi lần khách gửi tin cho những IP chắc chắn không bao giờ ra kết quả.
     */
    public void applyGeo(String ip, String countryCode, String countryName,
                         String region, String city, String isp, Instant now) {
        this.geoCountryCode = countryCode;
        this.geoCountryName = countryName;
        this.geoRegion = region;
        this.geoCity = city;
        this.geoIsp = isp;
        this.geoResolvedIp = ip;
        this.geoResolvedAt = now;
    }

    /**
     * Đóng luồng.
     *
     * @return true nếu trạng thái thực sự đổi (chống bấm hai lần).
     */
    public boolean close() {
        if (status == ChatConversationStatus.CLOSED) {
            return false;
        }
        status = ChatConversationStatus.CLOSED;
        return true;
    }

    @PrePersist
    void onCreate() {
        // Cắt xuống micro giây cho khớp DATETIME(6) của MySQL — xem ChatMessage.onCreate.
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public ChatConversationStatus getStatus() { return status; }
    public UUID getAssignedAdminId() { return assignedAdminId; }
    public Instant getLastMessageAt() { return lastMessageAt; }
    public String getLastMessagePreview() { return lastMessagePreview; }
    public int getUnreadForAdmin() { return unreadForAdmin; }
    public int getUnreadForPlayer() { return unreadForPlayer; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getLastIp() { return lastIp; }
    public String getGeoCountryCode() { return geoCountryCode; }
    public String getGeoCountryName() { return geoCountryName; }
    public String getGeoRegion() { return geoRegion; }
    public String getGeoCity() { return geoCity; }
    public String getGeoIsp() { return geoIsp; }
    public Instant getGeoResolvedAt() { return geoResolvedAt; }
    public String getGeoResolvedIp() { return geoResolvedIp; }
}
