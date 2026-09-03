package com.rwg.identity.repository;

import com.rwg.identity.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Append-only: chỉ dùng save() (INSERT). Không cập nhật/xóa bản ghi audit.
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    long countByAction(String action);

    /** Tra cứu theo action (phục vụ kiểm thử/đối soát — KHÔNG sửa/xóa). */
    List<AuditLog> findByAction(String action);

    /**
     * Tra cứu nhật ký cho khu quản trị. Mọi filter OPTIONAL (null = bỏ qua).
     * Khoảng thời gian nửa mở [from, to). Có index sẵn trên actor_id / action /
     * created_at (V1__init_schema.sql) nên các filter này không quét toàn bảng.
     */
    @Query("select a from AuditLog a where "
            + "(:actorId is null or a.actorId = :actorId) and "
            + "(:action is null or a.action = :action) and "
            + "(:targetId is null or a.targetId = :targetId) and "
            + "a.createdAt >= :from and a.createdAt < :to")
    Page<AuditLog> searchForAdmin(@Param("actorId") UUID actorId,
                                  @Param("action") String action,
                                  @Param("targetId") String targetId,
                                  @Param("from") Instant from,
                                  @Param("to") Instant to,
                                  Pageable pageable);

    /**
     * Nạp nhật ký của NHIỀU đối tượng trong một truy vấn — cho bảng lịch sử rút tiền, nơi mỗi
     * dòng cần biết ai đã quyết định và vì sao.
     *
     * Nếu tra từng dòng thì một trang 10 lệnh là 10 lượt gọi DB thêm mỗi lần mở trang.
     *
     * Vẫn là thao tác ĐỌC: bảng audit_log append-only, repository này không có method sửa/xoá.
     */
    List<AuditLog> findByActionInAndTargetIdIn(Collection<String> actions, Collection<String> targetIds);

    /**
     * Lịch sử đăng nhập của MỘT tài khoản, mới nhất trước.
     *
     * <h2>VÌ SAO LỌC THEO actorId MÀ KHÔNG PHẢI targetId</h2>
     * Với sự kiện đăng nhập, {@code actor_id} và {@code target_id} mang CÙNG một giá trị —
     * người đăng nhập vừa là người thực hiện vừa là đối tượng. Nhưng chỉ {@code actor_id}
     * có index ({@code idx_audit_log_actor_id} trong V1__init_schema.sql); {@code target_id}
     * KHÔNG có. Lọc theo cột không index trên bảng tăng nhanh nhất hệ thống là quét toàn
     * bảng mỗi lần một người vận hành mở modal.
     *
     * <h2>HỆ QUẢ ĐÃ BIẾT</h2>
     * Lần đăng nhập sai mà KHÔNG tìm ra tài khoản nào (gõ sai cả tên đăng nhập) được ghi
     * với {@code actor_id = null} nên KHÔNG xuất hiện ở đây. Đúng như mong muốn: lần đó
     * không thuộc về tài khoản nào cả. Muốn tra loại đó thì dùng
     * {@code GET /admin/audit/logs?action=LOGIN_FAILED}.
     *
     * Vẫn là thao tác ĐỌC — bảng audit_log append-only.
     */
    List<AuditLog> findByActorIdAndActionInOrderByCreatedAtDesc(
            UUID actorId, Collection<String> actions, Pageable pageable);
}
