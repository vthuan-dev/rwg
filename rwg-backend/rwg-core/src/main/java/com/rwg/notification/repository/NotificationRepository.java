package com.rwg.notification.repository;

import com.rwg.notification.domain.Notification;
import com.rwg.notification.domain.NotificationId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, NotificationId> {

    /**
     * Thông báo của một người: tin cá nhân của họ CỘNG tin chung, mới nhất trước.
     *
     * Gộp hai loại trong một truy vấn thay vì gọi hai lần rồi trộn ở tầng Java: trộn thủ công
     * làm việc phân trang sai. Trang 1 lấy 20 tin cá nhân mới nhất và 20 tin chung mới nhất
     * rồi cắt 20 — những tin bị cắt sẽ không bao giờ xuất hiện ở trang 2, vì trang 2 lại lấy
     * 20 tiếp theo của mỗi loại.
     */
    @Query("""
            SELECT n FROM Notification n
            WHERE n.userId = :userId OR n.userId IS NULL
            ORDER BY n.createdAt DESC
            """)
    Page<Notification> findVisibleTo(@Param("userId") UUID userId, Pageable pageable);

    /**
     * Số tin CÁ NHÂN chưa đọc.
     *
     * CỐ TÌNH KHÔNG đếm tin chung: một tin chung được nhiều người xem nên trạng thái đọc của
     * nó không thể lưu trên chính dòng đó — đánh dấu đã đọc sẽ làm nó biến mất khỏi số đếm của
     * mọi người khác. Muốn đếm tin chung theo từng người thì cần một bảng "ai đã đọc tin nào"
     * riêng; chưa làm vì viên đếm chưa đọc chủ yếu để báo tiền vào/ra ví.
     */
    @Query("""
            SELECT COUNT(n) FROM Notification n
            WHERE n.userId = :userId AND n.readAt IS NULL
            """)
    long countUnreadFor(@Param("userId") UUID userId);

    /**
     * Một thông báo cá nhân theo id, CHỈ khi nó thuộc về người gọi.
     *
     * Lọc `userId` ngay trong truy vấn chứ không tải rồi so ở tầng service: quên bước so sánh
     * đó một lần là người chơi đánh dấu đã đọc được thông báo của người khác. Đưa điều kiện
     * vào truy vấn thì không có đường nào bỏ sót.
     */
    @Query("""
            SELECT n FROM Notification n
            WHERE n.id = :id AND n.userId = :userId
            """)
    Optional<Notification> findOwnedBy(@Param("id") UUID id, @Param("userId") UUID userId);

    /**
     * Đánh dấu đã đọc TẤT CẢ tin cá nhân chưa đọc, bằng một câu UPDATE.
     *
     * Dùng UPDATE trực tiếp thay vì tải danh sách rồi lưu từng dòng: người chơi lâu ngày không
     * xem có thể có hàng trăm tin, tải hết vào bộ nhớ chỉ để đặt một cột là vô ích.
     *
     * `readAt IS NULL` trong điều kiện giữ nguyên mốc đọc lần đầu của những tin đã đọc.
     */
    @Modifying
    @Query("""
            UPDATE Notification n SET n.readAt = :now
            WHERE n.userId = :userId AND n.readAt IS NULL
            """)
    int markAllRead(@Param("userId") UUID userId, @Param("now") Instant now);
}
