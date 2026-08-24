package com.rwg.chat.repository;

import com.rwg.chat.domain.ChatMessage;
import com.rwg.chat.domain.ChatMessageId;
import com.rwg.chat.domain.ChatSenderType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, ChatMessageId> {

    /**
     * Một trang lịch sử tin nhắn, mới nhất trước — KEYSET pagination.
     *
     * VÌ SAO KHÔNG DÙNG OFFSET như {@code MyNotificationService}: chat có tin mới
     * chèn vào đầu liên tục. Với offset, người chơi cuộn lên xem lại trong lúc nhân
     * sự đang trả lời sẽ thấy tin LẶP (mỗi tin mới đẩy mọi thứ xuống một bậc, nên
     * trang sau lấy lại phần trang trước đã hiện). Đó đúng là loại lỗi người dùng
     * báo là "chat bị nhảy" và gần như không tái hiện được trên máy dev vì cần một
     * tin đến đúng giữa hai lần tải trang.
     *
     * Keyset không có vấn đề đó: mốc `before` là một thời điểm cụ thể, tin mới đến
     * sau không làm dịch chuyển những gì nằm trước nó.
     *
     * `before` null = trang đầu (tin mới nhất).
     */
    @Query("""
            SELECT m FROM ChatMessage m
            WHERE m.conversationId = :conversationId
              AND (:before IS NULL OR m.createdAt < :before)
            ORDER BY m.createdAt DESC
            """)
    List<ChatMessage> findPageBefore(@Param("conversationId") UUID conversationId,
                                     @Param("before") Instant before,
                                     Pageable pageable);

    /**
     * Như {@link #findPageBefore} nhưng CHỈ những tin người chơi được đọc.
     *
     * VÌ SAO LÀ HÀM RIÊNG, không phải một tham số thêm vào hàm trên: nếu là tham số
     * thì một chỗ gọi mới quên truyền sẽ mặc định thành "thấy hết", và sai sót đó
     * không lộ ra ở bất kỳ đâu — không lỗi biên dịch, không lỗi lúc chạy. Nó chỉ hiện
     * ra khi có người chơi kể lại rằng họ nhìn thấy thẻ duyệt tiền của nội bộ.
     *
     * Hai hàm tách rời thì mỗi đường đọc phải nói rõ mình là đường nào.
     */
    @Query("""
            SELECT m FROM ChatMessage m
            WHERE m.conversationId = :conversationId
              AND m.visibleTo = com.rwg.chat.domain.ChatVisibility.ALL
              AND (:before IS NULL OR m.createdAt < :before)
            ORDER BY m.createdAt DESC
            """)
    List<ChatMessage> findPageBeforeVisibleToPlayer(@Param("conversationId") UUID conversationId,
                                                    @Param("before") Instant before,
                                                    Pageable pageable);

    /**
     * Tin đã tồn tại với cùng {@code clientMsgId} — chống gửi trùng.
     *
     * Kiểm tra TRƯỚC khi chèn để trả về đúng bản ghi cũ thay vì để UNIQUE của DB
     * ném lỗi rồi biến một lần thử lại bình thường thành lỗi 500 trên màn hình
     * người dùng. UNIQUE ở DB vẫn giữ nguyên làm lớp chặn cuối cho trường hợp hai
     * request song song cùng lọt qua bước kiểm tra này.
     */
    Optional<ChatMessage> findByConversationIdAndClientMsgId(UUID conversationId, UUID clientMsgId);

    /**
     * Đánh dấu đã đọc mọi tin CHƯA ĐỌC của một phía trong luồng.
     *
     * UPDATE trực tiếp thay vì tải danh sách rồi lưu từng dòng: một luồng hỗ trợ
     * lâu ngày có thể có hàng trăm tin chưa đọc, tải hết vào bộ nhớ chỉ để đặt một
     * cột là vô ích.
     *
     * `readAt IS NULL` trong điều kiện giữ nguyên mốc đọc lần đầu của những tin đã đọc.
     */
    @Modifying
    @Query("""
            UPDATE ChatMessage m SET m.readAt = :now
            WHERE m.conversationId = :conversationId
              AND m.senderType = :senderType
              AND m.readAt IS NULL
            """)
    int markReadFrom(@Param("conversationId") UUID conversationId,
                     @Param("senderType") ChatSenderType senderType,
                     @Param("now") Instant now);

    /**
     * Ảnh đính kèm này có nằm trong luồng của ĐÚNG người chơi đó không?
     *
     * VÌ SAO CẦN: tên tệp là UUID, nhưng "khó đoán" không phải là kiểm soát truy cập.
     * Đường dẫn ảnh bị chia sẻ lại, nằm trong lịch sử trình duyệt, trong log của proxy,
     * và không bao giờ hết hiệu lực. Ảnh trong chat hỗ trợ là biên lai chuyển tiền và
     * ảnh giấy tờ — một đường dẫn lộ ra là lộ vĩnh viễn.
     *
     * JOIN sang hội thoại để so người sở hữu: không có bước này thì người chơi A dán
     * đường dẫn ảnh của người chơi B vào trình duyệt là xem được, vì cả hai đều là
     * người dùng đã đăng nhập hợp lệ.
     *
     * Nhân sự KHÔNG đi qua hàm này: họ xử lý mọi luồng nên không có "luồng của mình"
     * để so sánh, và endpoint của họ đã bị chặn theo vai trò ở tầng route.
     */
    @Query("""
            SELECT COUNT(m) > 0 FROM ChatMessage m
            JOIN ChatConversation c ON c.id = m.conversationId
            WHERE m.attachmentUrl = :attachmentUrl
              AND c.userId = :userId
            """)
    boolean attachmentBelongsToPlayer(@Param("attachmentUrl") String attachmentUrl,
                                      @Param("userId") UUID userId);
}
