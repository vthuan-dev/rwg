package com.rwg.chat.domain;

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
 * Một tin nhắn trong luồng hỗ trợ (map bảng chat_messages).
 *
 * PK composite (id, created_at) theo DECISIONS.md mục (b).
 *
 * KHÔNG có quan hệ {@code @ManyToOne} tới {@link ChatConversation}: JPA sẽ tải cả
 * hội thoại mỗi lần đọc một tin nhắn, và khi tải một trang 30 tin thì đó là 30 lần
 * tải lại đúng một hội thoại. Giữ {@code conversationId} thô, service tự tải hội
 * thoại một lần khi cần.
 */
@Entity
@Table(name = "chat_messages")
@IdClass(ChatMessageId.class)
public class ChatMessage {

    /** Giới hạn độ dài nội dung, khớp VARCHAR(2000) của migration. */
    public static final int MAX_BODY_LENGTH = 2000;

    /** Độ dài đoạn xem trước trong hộp thư, khớp VARCHAR(160). */
    public static final int PREVIEW_LENGTH = 160;

    /**
     * Khoá dịch dùng làm đoạn xem trước cho tin chỉ có ảnh.
     *
     * Hộp thư của nhân sự sắp xếp và hiển thị theo cột {@code last_message_preview}.
     * Tin chỉ có ảnh thì {@code body} rỗng, nên không có khoá này thì dòng hộp thư
     * hiện TRỐNG — nhân sự không biết là có tin mới hay dữ liệu bị lỗi.
     *
     * Trả KHOÁ chứ không phải câu đã dịch, cùng lý do với tin SYSTEM: nhân sự đổi
     * ngôn ngữ thì cả hộp thư phải đổi theo, thay vì đóng băng ở ngôn ngữ của người
     * đã gửi.
     */
    public static final String PREVIEW_KEY_IMAGE = "chat.preview.image";

    /**
     * Khoá dịch dùng làm đoạn xem trước cho thẻ duyệt lệnh rút.
     *
     * Thẻ là tin MỚI NHẤT của luồng ngay sau khi người chơi gửi lệnh, nên nó chiếm
     * dòng xem trước trong hộp thư. Để nguyên body thì dòng đó hiện một câu chung chung,
     * không cho biết đây là việc tiền — mà đó đúng là thứ nhân sự cần thấy khi quét danh
     * sách để chọn luồng xử lý trước.
     */
    public static final String PREVIEW_KEY_WITHDRAWAL = "chat.preview.withdrawal";

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Id
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "sender_type", nullable = false, length = 8)
    private ChatSenderType senderType;

    /** NULL với tin của hệ thống. */
    @Column(name = "sender_id")
    private UUID senderId;

    /** Tên hiển thị chụp lại lúc gửi; NULL với tin của hệ thống. */
    @Column(name = "sender_username", length = 32)
    private String senderUsername;

    /** NULL với tin không đính kèm; có thể rỗng khi tin CHỈ có ảnh. */
    @Column(name = "body", length = MAX_BODY_LENGTH)
    private String body;

    /** Đường dẫn công khai của tệp đính kèm; NULL nếu tin chỉ có chữ. */
    @Column(name = "attachment_url", length = 512)
    private String attachmentUrl;

    /** IMAGE. NULL khi không có đính kèm — DB có CHECK buộc đi cặp với url. */
    @Enumerated(EnumType.STRING)
    @Column(name = "attachment_type", length = 16)
    private ChatAttachmentType attachmentType;

    /** Tên gốc do người gửi đặt; tệp trên đĩa mang tên UUID. */
    @Column(name = "attachment_name", length = 255)
    private String attachmentName;

    @Column(name = "attachment_size")
    private Long attachmentSize;

    @Column(name = "read_at")
    private Instant readAt;

    /** Id do client sinh để chống gửi trùng; NULL với tin của nhân sự/hệ thống. */
    @Column(name = "client_msg_id")
    private UUID clientMsgId;

    /**
     * Lệnh rút mà tin này là thẻ duyệt cho; NULL với mọi tin thường.
     *
     * CHỈ LƯU MÃ, không lưu số tiền hay trạng thái. Chúng được đọc từ bảng lệnh mỗi lần
     * tải luồng — xem lý do ở migration V20260824_02.
     */
    @Column(name = "withdrawal_order_id")
    private UUID withdrawalOrderId;

    /**
     * Ai đọc được tin này.
     *
     * ĐẶT MẶC ĐỊNH TẠI ĐÂY chứ không dựa vào DEFAULT của cột: JPA ghi tường minh mọi
     * cột trong câu INSERT, nên DEFAULT của DB không bao giờ được dùng từ đường này và
     * một trường null sẽ thành lỗi vi phạm NOT NULL.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "visible_to", nullable = false, length = 8)
    private ChatVisibility visibleTo = ChatVisibility.ALL;

    /**
     * Thời điểm tin bị xóa; null = chưa xóa.
     *
     * Dùng soft-delete thay vì DELETE khỏi bảng: mỗi tin nhắn là căn cứ khi có khiếu nại
     * về tiền. Xóa hẳn là xóa mất bằng chứng. Với cả hai phía trên màn hình, kết quả
     * giống hệt xóa thật: tin biến mất và không bao giờ hiện lại.
     */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    /** Mã UUID của người xóa (nhân sự khu quản trị). */
    @Column(name = "deleted_by")
    private UUID deletedBy;

    /**
     * Tên đăng nhập của người xóa, chụp lại lúc thực hiện.
     *
     * Không chỉ lưu mã UUID: người xóa có thể đã rời việc và bị đổi tên sau đó. Khi tra
     * nhật ký cần biết đây là ai ngay, không phải chạy thêm truy vấn JOIN.
     */
    @Column(name = "deleted_by_username", length = 50)
    private String deletedByUsername;

    protected ChatMessage() {
        // cho JPA
    }

    private ChatMessage(UUID conversationId, ChatSenderType senderType, UUID senderId,
                        String senderUsername, String body, UUID clientMsgId) {
        this.id = UUID.randomUUID();
        this.conversationId = conversationId;
        this.senderType = senderType;
        this.senderId = senderId;
        this.senderUsername = senderUsername;
        this.body = body;
        this.clientMsgId = clientMsgId;
    }

    /**
     * Gắn tệp đính kèm vào tin.
     *
     * Là một bước RIÊNG thay vì thêm bốn tham số vào mọi factory: phần lớn tin nhắn
     * không có đính kèm, và bốn tham số null rải ở mọi chỗ gọi là chỗ dễ truyền lệch
     * thứ tự nhất — hai tham số String cạnh nhau (url, name) tráo nhau thì trình biên
     * dịch không báo gì.
     */
    public ChatMessage withAttachment(String url, ChatAttachmentType type,
                                      String originalName, long sizeBytes) {
        this.attachmentUrl = url;
        this.attachmentType = type;
        this.attachmentName = originalName;
        this.attachmentSize = sizeBytes;
        return this;
    }

    /** Tin do người chơi gửi. {@code clientMsgId} có thể null (client cũ không gửi). */
    public static ChatMessage fromPlayer(UUID conversationId, UUID senderId, String senderUsername,
                                         String body, UUID clientMsgId) {
        return new ChatMessage(conversationId, ChatSenderType.PLAYER, senderId, senderUsername,
                body, clientMsgId);
    }

    /** Tin do nhân sự quản trị gửi. */
    public static ChatMessage fromStaff(UUID conversationId, UUID senderId, String senderUsername,
                                        String body, UUID clientMsgId) {
        return new ChatMessage(conversationId, ChatSenderType.STAFF, senderId, senderUsername,
                body, clientMsgId);
    }

    /**
     * Dòng thông báo do hệ thống chèn.
     *
     * Nội dung là KHOÁ DỊCH (vd "chat.system.assigned"), không phải câu đã dịch —
     * cùng lý do với {@code notifications.title_key}: người chơi đổi ngôn ngữ thì
     * những dòng cũ cũng phải đổi theo, thay vì đóng băng ở ngôn ngữ lúc được tạo.
     */
    public static ChatMessage fromSystem(UUID conversationId, String bodyKey) {
        return new ChatMessage(conversationId, ChatSenderType.SYSTEM, null, null, bodyKey, null);
    }

    /**
     * Thẻ duyệt một lệnh rút, chèn vào luồng của chính người gửi lệnh.
     *
     * LÀ TIN SYSTEM, KHÔNG PHẢI STAFF: không có nhân sự nào "nói" câu này, và SYSTEM là
     * loại duy nhất không làm tăng bộ đếm chưa đọc của người chơi (xem
     * {@link ChatConversation#appendSystemMessage}). Dùng STAFF sẽ làm viên đỏ báo tin
     * mới nhảy trên máy người chơi cho một tin họ không bao giờ đọc được — họ mở chat
     * ra và không thấy gì mới.
     *
     * Body là KHOÁ DỊCH theo đúng quy ước của tin SYSTEM.
     */
    public static ChatMessage withdrawalCard(UUID conversationId, UUID withdrawalOrderId) {
        ChatMessage card = fromSystem(conversationId, "chat.system.withdrawal_requested");
        card.withdrawalOrderId = withdrawalOrderId;
        card.visibleTo = ChatVisibility.STAFF;
        return card;
    }

    /**
     * Đánh dấu đã đọc.
     *
     * KHÔNG ghi đè mốc đã có: mốc xem LẦN ĐẦU mới là thứ có giá trị khi tra soát
     * "tôi hỏi mà không ai trả lời". Ghi đè mỗi lần mở lại luồng sẽ đẩy mốc đó
     * trôi dần về hiện tại và mất hẳn thông tin gốc.
     */
    public void markRead(Instant now) {
        if (readAt == null) {
            readAt = now.truncatedTo(ChronoUnit.MICROS);
        }
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            // Cắt xuống micro giây cho khớp DATETIME(6) của MySQL: Instant của Java
            // có độ phân giải nano, không cắt thì giá trị đọc lại từ DB khác giá trị
            // vừa ghi và mọi so sánh bằng đều sai.
            createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        }
    }

    public UUID getId() { return id; }
    public Instant getCreatedAt() { return createdAt; }
    public UUID getConversationId() { return conversationId; }
    public ChatSenderType getSenderType() { return senderType; }
    public UUID getSenderId() { return senderId; }
    public String getSenderUsername() { return senderUsername; }
    public String getBody() { return body; }
    public Instant getReadAt() { return readAt; }
    public UUID getClientMsgId() { return clientMsgId; }
    public String getAttachmentUrl() { return attachmentUrl; }
    public ChatAttachmentType getAttachmentType() { return attachmentType; }
    public String getAttachmentName() { return attachmentName; }
    public Long getAttachmentSize() { return attachmentSize; }
    public UUID getWithdrawalOrderId() { return withdrawalOrderId; }
    public ChatVisibility getVisibleTo() { return visibleTo; }
    public Instant getDeletedAt() { return deletedAt; }
    public UUID getDeletedBy() { return deletedBy; }
    public String getDeletedByUsername() { return deletedByUsername; }
    public boolean isDeleted() { return deletedAt != null; }

    /**
     * Đánh dấu tin bị xóa bởi nhân sự. Sau khi save, tin sẽ bị lọc khỏi mọi query
     * thông thường (xem {@code ChatMessageRepository}) và một sự kiện MESSAGES_DELETED
     * được phát qua WebSocket để client xóa tin khỏi màn hình ngay.
     */
    public void markDeleted(UUID staffId, String staffUsername) {
        this.deletedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        this.deletedBy = staffId;
        this.deletedByUsername = staffUsername;
    }

    /**
     * Đoạn xem trước cho hộp thư quản trị.
     *
     * Gộp mọi khoảng trắng liên tiếp thành một dấu cách: người chơi dán vào đoạn
     * văn bản nhiều dòng thì đoạn xem trước sẽ có ký tự xuống dòng, và một ô trên
     * bảng hộp thư đột nhiên cao gấp ba dòng làm cả bảng nhảy.
     *
     * Tin CHỈ có ảnh trả về {@link #PREVIEW_KEY_IMAGE} để hộp thư hiện "[Hình ảnh]"
     * thay vì một dòng trống.
     */
    public String preview() {
        // Kiểm TRƯỚC body: thẻ duyệt có body là khoá dịch của tin SYSTEM nên nhánh dưới
        // sẽ trả về đúng khoá đó và hộp thư mất thông tin "đây là việc tiền".
        if (withdrawalOrderId != null) {
            return PREVIEW_KEY_WITHDRAWAL;
        }
        if (body == null || body.isBlank()) {
            return attachmentUrl != null ? PREVIEW_KEY_IMAGE : "";
        }
        String flattened = body.replaceAll("\\s+", " ").trim();
        return flattened.length() <= PREVIEW_LENGTH
                ? flattened
                : flattened.substring(0, PREVIEW_LENGTH - 1) + "\u2026";
    }
}
