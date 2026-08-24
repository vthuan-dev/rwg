package com.rwg.chat.repository;

import com.rwg.chat.domain.ChatConversation;
import com.rwg.chat.domain.ChatConversationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ChatConversationRepository extends JpaRepository<ChatConversation, UUID> {

    Optional<ChatConversation> findByUserId(UUID userId);

    /**
     * Hộp thư của khu quản trị. Mỗi bộ lọc là OPTIONAL (truyền null để bỏ qua),
     * theo đúng cách {@code UserRepository.searchForAdmin} đang làm.
     *
     * `keyword` đã được service chuẩn hoá thành chữ thường kèm ký tự '%' —
     * repository KHÔNG tự thêm wildcard.
     *
     * JOIN sang User để tìm theo TÊN ĐĂNG NHẬP: người vận hành nhận cuộc gọi từ
     * người chơi và trong tay chỉ có tên đăng nhập, không ai đọc UUID hội thoại
     * qua điện thoại.
     *
     * `unassignedOnly` là Boolean chứ không phải boolean: null = không lọc,
     * TRUE = chỉ luồng chưa ai nhận. Dùng boolean nguyên thuỷ thì false sẽ mang
     * nghĩa "chỉ luồng ĐÃ có người nhận" và không còn cách nào diễn tả "tất cả".
     */
    @Query("""
            SELECT c FROM ChatConversation c
            JOIN User u ON u.id = c.userId
            WHERE (:status IS NULL OR c.status = :status)
              AND (:assignedAdminId IS NULL OR c.assignedAdminId = :assignedAdminId)
              AND (:unassignedOnly IS NULL OR c.assignedAdminId IS NULL)
              AND (:keyword IS NULL OR lower(u.username) LIKE :keyword)
            ORDER BY c.lastMessageAt DESC NULLS LAST
            """)
    Page<ChatConversation> searchForAdmin(@Param("status") ChatConversationStatus status,
                                         @Param("assignedAdminId") UUID assignedAdminId,
                                         @Param("unassignedOnly") Boolean unassignedOnly,
                                         @Param("keyword") String keyword,
                                         Pageable pageable);

    /**
     * Tổng số tin người chơi gửi mà chưa nhân sự nào đọc, trên toàn hệ thống.
     *
     * Cộng cột đã phi chuẩn hoá thay vì đếm trên chat_messages: con số này hiện ở
     * viên đỏ trên sidebar và được gọi lại mỗi 20 giây bởi MỌI nhân sự đang mở khu
     * quản trị. `COALESCE` cần cho trường hợp chưa có hội thoại nào — `SUM` trả
     * NULL trên tập rỗng, và một `long` nhận NULL sẽ ném lỗi.
     */
    @Query("""
            SELECT COALESCE(SUM(c.unreadForAdmin), 0) FROM ChatConversation c
            WHERE c.status = com.rwg.chat.domain.ChatConversationStatus.OPEN
            """)
    long totalUnreadForAdmin();

    /** Số luồng đang mở còn tin chưa đọc — dùng cho thẻ thống kê hộp thư. */
    @Query("""
            SELECT COUNT(c) FROM ChatConversation c
            WHERE c.status = com.rwg.chat.domain.ChatConversationStatus.OPEN
              AND c.unreadForAdmin > 0
            """)
    long countConversationsAwaitingReply();
}
