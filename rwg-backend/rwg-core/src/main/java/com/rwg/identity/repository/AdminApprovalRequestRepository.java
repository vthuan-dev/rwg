package com.rwg.identity.repository;

import com.rwg.identity.domain.AdminApprovalRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

@Repository
public interface AdminApprovalRequestRepository extends JpaRepository<AdminApprovalRequest, UUID> {

    /** Hàng đợi duyệt; status null = tất cả. */
    @Query("select r from AdminApprovalRequest r where "
            + "(:status is null or r.status = :status) and "
            + "(:makerId is null or r.makerId = :makerId)")
    Page<AdminApprovalRequest> searchForAdmin(@Param("status") String status,
                                             @Param("makerId") UUID makerId,
                                             Pageable pageable);

    /**
     * Chuyển PENDING -> quyết định bằng MỘT UPDATE điều kiện nguyên tử.
     *
     * Trả affected rows: 0 nghĩa là đề nghị đã bị admin khác xử lý trước — chặn hai
     * admin bấm duyệt đồng thời rồi chi tiền hai lần. KHÔNG kiểm tra-rồi-ghi (check
     * then act) vì mẫu đó có khe race giữa hai bước.
     *
     * clearAutomatically = false: entity đề nghị KHÔNG được dùng lại sau lệnh này
     * trong cùng transaction (service đọc lại từ DB), và quan trọng hơn — clear
     * persistence context ở đây sẽ làm mất dòng ledger đang chờ của WalletService.
     */
    @Modifying(clearAutomatically = false)
    @Query("update AdminApprovalRequest r set r.status = :newStatus, r.checkerId = :checkerId, "
            + "r.decisionNote = :note, r.decidedAt = :decidedAt "
            + "where r.id = :id and r.status = 'PENDING'")
    int decideIfPending(@Param("id") UUID id,
                        @Param("newStatus") String newStatus,
                        @Param("checkerId") UUID checkerId,
                        @Param("note") String note,
                        @Param("decidedAt") Instant decidedAt);
}
