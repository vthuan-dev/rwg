package com.rwg.chat.repository;

import com.rwg.chat.domain.ChatConversation;
import com.rwg.chat.domain.ChatConversationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ChatConversationRepository extends JpaRepository<ChatConversation, UUID> {

    Optional<ChatConversation> findByUserId(UUID userId);

    /**
     * Hộp thư của khu quản trị. Mỗi bộ lọc là OPTIONAL (truyền null để bỏ qua),
     * theo đúng cách {@code UserRepository.searchForAdmin} đang làm.
     *
     * Chỉ trả về các luồng có tin nhắn còn hiệu lực trong vòng 30 phút gần nhất.
     * Các luồng hết hạn tin nhắn hoặc không còn tin nhắn sẽ tự động ẩn khỏi hàng đợi.
     */
    @Query("""
            SELECT c FROM ChatConversation c
            JOIN User u ON u.id = c.userId
            WHERE (:status IS NULL OR c.status = :status)
              AND (:assignedAdminId IS NULL OR c.assignedAdminId = :assignedAdminId)
              AND (:unassignedOnly IS NULL OR c.assignedAdminId IS NULL)
              AND (:keyword IS NULL OR lower(u.username) LIKE :keyword)
              AND c.lastMessageAt IS NOT NULL
              AND c.lastMessageAt >= :minActiveAt
            ORDER BY c.lastMessageAt DESC
            """)
    Page<ChatConversation> searchForAdmin(@Param("status") ChatConversationStatus status,
                                         @Param("assignedAdminId") UUID assignedAdminId,
                                         @Param("unassignedOnly") Boolean unassignedOnly,
                                         @Param("keyword") String keyword,
                                         @Param("minActiveAt") Instant minActiveAt,
                                         Pageable pageable);

    /**
     * Tổng số tin người chơi gửi mà chưa nhân sự nào đọc, trên toàn hệ thống.
     * Chỉ đếm các luồng đang hoạt động trong vòng 30 phút.
     */
    @Query("""
            SELECT COALESCE(SUM(c.unreadForAdmin), 0) FROM ChatConversation c
            WHERE c.status = com.rwg.chat.domain.ChatConversationStatus.OPEN
              AND c.lastMessageAt IS NOT NULL
              AND c.lastMessageAt >= :minActiveAt
            """)
    long totalUnreadForAdmin(@Param("minActiveAt") Instant minActiveAt);

    /** Số luồng đang mở còn tin chưa đọc trong vòng 30 phút. */
    @Query("""
            SELECT COUNT(c) FROM ChatConversation c
            WHERE c.status = com.rwg.chat.domain.ChatConversationStatus.OPEN
              AND c.unreadForAdmin > 0
              AND c.lastMessageAt IS NOT NULL
              AND c.lastMessageAt >= :minActiveAt
            """)
    long countConversationsAwaitingReply(@Param("minActiveAt") Instant minActiveAt);
}
