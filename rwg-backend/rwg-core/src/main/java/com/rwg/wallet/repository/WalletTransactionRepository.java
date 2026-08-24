package com.rwg.wallet.repository;

import com.rwg.wallet.domain.WalletRefType;
import com.rwg.wallet.domain.WalletTransaction;
import com.rwg.wallet.domain.WalletTransactionId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Repository ledger. idempotency_key được tra trước khi ghi sổ để đảm bảo một
 * nghiệp vụ tiền không trừ/cộng 2 lần.
 */
@Repository
public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, WalletTransactionId> {

    Page<WalletTransaction> findByWalletId(UUID walletId, Pageable pageable);

    boolean existsByIdempotencyKey(String idempotencyKey);

    long countByIdempotencyKey(String idempotencyKey);

    long countByWalletIdAndRefType(UUID walletId, WalletRefType refType);

    /**
     * Tổng (credit - debit) theo từng ví — phục vụ reconciliation job 5 phút
     * so ledger vs số dư (chỉ cảnh báo, KHÔNG tự sửa).
     */
    @Query("select t.walletId, coalesce(sum(t.credit), 0) - coalesce(sum(t.debit), 0) " +
            "from WalletTransaction t group by t.walletId")
    List<Object[]> sumNetByWallet();

    /**
     * Ledger của một ví, lọc theo loại tham chiếu — admin xem riêng các dòng
     * ADJUSTMENT (điều chỉnh thủ công) hoặc COMMISSION (hoa hồng đại lý).
     */
    Page<WalletTransaction> findByWalletIdAndRefType(UUID walletId, WalletRefType refType, Pageable pageable);

    /**
     * Tổng tiền (credit + debit) mà MỘT admin đã điều chỉnh thủ công trong khoảng
     * nửa mở [from, to) — phục vụ trần hạn mức mỗi admin mỗi ngày.
     *
     * Nguồn sự thật là chính bảng ledger: {@code AdminWalletService.adjust} truyền
     * adminId vào refId của dòng ADJUSTMENT. Cách này chính xác hơn việc parse chuỗi
     * details trong audit_log, và không thể lệch với số tiền thực sự đã chuyển.
     *
     * Cộng CẢ credit và debit: cả hai chiều đều là quyền lực tài chính cần giới hạn.
     */
    @Query("select coalesce(sum(t.credit), 0) + coalesce(sum(t.debit), 0) "
            + "from WalletTransaction t "
            + "where t.refType = com.rwg.wallet.domain.WalletRefType.ADJUSTMENT "
            + "and t.refId = :adminId and t.createdAt >= :from and t.createdAt < :to")
    BigDecimal sumAdjustmentsByAdmin(@Param("adminId") String adminId,
                                     @Param("from") Instant from,
                                     @Param("to") Instant to);

    @Query("select coalesce(sum(t.credit), 0) from WalletTransaction t where t.walletId = :walletId and t.refType = :refType")
    BigDecimal sumCreditByWalletIdAndRefType(@Param("walletId") UUID walletId, @Param("refType") WalletRefType refType);

    @Query("select coalesce(sum(t.debit), 0) from WalletTransaction t where t.walletId = :walletId and t.refType = :refType")
    BigDecimal sumDebitByWalletIdAndRefType(@Param("walletId") UUID walletId, @Param("refType") WalletRefType refType);
}
