package com.rwg.identity.repository;

import com.rwg.identity.domain.AdminApprovalRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Đọc lịch sử đề nghị phê duyệt thao tác admin.
 *
 * CHỈ ĐỌC: quy trình 4 mắt đã bỏ nên không còn tạo đề nghị mới hay quyết định. Method
 * {@code decideIfPending} (UPDATE điều kiện nguyên tử) đã xoá cùng nghiệp vụ đó.
 */
@Repository
public interface AdminApprovalRequestRepository extends JpaRepository<AdminApprovalRequest, UUID> {

    /**
     * Số đề nghị phê duyệt nhắm tới người này.
     *
     * Dùng khi quyết định có xóa hẳn được hay không. Đề nghị là vết của quy trình bốn mắt —
     * ai đề xuất, ai duyệt — và đó là thứ kiểm toán sẽ hỏi đầu tiên khi có nghi vấn nội bộ.
     */
    long countByTargetUserId(UUID targetUserId);

    /** Xoá mọi đề nghị nhắm tới người này. Chỉ dùng cho tài khoản SẠCH. */
    void deleteByTargetUserId(UUID targetUserId);

    /** Lịch sử đề nghị; status null = tất cả. */
    @Query("select r from AdminApprovalRequest r where "
            + "(:status is null or r.status = :status) and "
            + "(:makerId is null or r.makerId = :makerId)")
    Page<AdminApprovalRequest> searchForAdmin(@Param("status") String status,
                                             @Param("makerId") UUID makerId,
                                             Pageable pageable);
}
