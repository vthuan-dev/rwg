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
     * Số dòng sổ của một ví, mọi loại.
     *
     * Dùng khi quyết định một tài khoản có xóa hẳn được hay không. Sổ ví là nguồn sự thật tài
     * chính, nên chỉ MỘT dòng thôi cũng đủ để tài khoản đó phải giữ lại.
     */
    long countByWalletId(UUID walletId);

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

    /**
     * Tổng tiền admin CỘNG tay vào một ví trong khoảng nửa mở {@code [from, to)}.
     *
     * TÁCH RIÊNG khỏi tiền nạp qua cổng, không gộp thành một cột "Nạp": về kế toán đây
     * là hai loại hoàn toàn khác nhau — một là tiền thật vào hệ thống, một là tiền do
     * admin tạo ra. Trên dữ liệu dev thực tế, tiền admin cộng tay đang gấp gần 6 lần
     * tiền nạp thật — gộp lại sẽ che mất đúng điều sổ sách cần thấy nhất.
     */
    @Query("select coalesce(sum(t.credit), 0) from WalletTransaction t "
            + "where t.walletId = :walletId and t.refType = :refType "
            + "and t.createdAt >= :from and t.createdAt < :to")
    BigDecimal sumCreditInRange(@Param("walletId") UUID walletId,
                                @Param("refType") WalletRefType refType,
                                @Param("from") Instant from,
                                @Param("to") Instant to);

    /** Tổng tiền bị TRỪ khỏi ví theo loại tham chiếu, trong khoảng nửa mở. */
    @Query("select coalesce(sum(t.debit), 0) from WalletTransaction t "
            + "where t.walletId = :walletId and t.refType = :refType "
            + "and t.createdAt >= :from and t.createdAt < :to")
    BigDecimal sumDebitInRange(@Param("walletId") UUID walletId,
                               @Param("refType") WalletRefType refType,
                               @Param("from") Instant from,
                               @Param("to") Instant to);

    /**
     * Số dư ngay TRƯỚC thời điểm {@code before} — số dư đầu kỳ của báo cáo.
     *
     * LẤY TỪ {@code balance_after} CỦA DÒNG LEDGER GẦN NHẤT chứ không cộng dồn toàn bộ
     * lịch sử: cộng dồn sẽ quét mọi dòng từ đầu đến mốc đó, và chi phí tăng dần theo
     * tuổi của tài khoản. {@code balance_after} đã là số dư tích luỹ tại thời điểm đó.
     *
     * Trả về danh sách vì JPQL không có LIMIT — tầng trên lấy phần tử đầu tiên.
     * Rỗng nghĩa là ví chưa có giao dịch nào trước mốc đó, tức số dư đầu kỳ bằng 0.
     */
    @Query("select t.balanceAfter from WalletTransaction t "
            + "where t.walletId = :walletId and t.createdAt < :before "
            + "order by t.createdAt desc")
    List<BigDecimal> findBalanceBefore(@Param("walletId") UUID walletId,
                                       @Param("before") Instant before,
                                       Pageable pageable);

    /** Một dòng điều chỉnh tổng hợp theo từng người chơi. */
    interface PlayerAdjustment {
        UUID getUserId();
        BigDecimal getTotalCredit();
        BigDecimal getTotalDebit();
    }

    /**
     * Tổng tiền admin điều chỉnh thủ công, nhóm theo từng người chơi.
     *
     * PHẢI JOIN {@code Wallet}: bảng ledger khoá theo {@code wallet_id}, không có cột
     * {@code user_id} nào để nhóm trực tiếp.
     */
    @Query("select w.userId as userId, "
            + "coalesce(sum(t.credit), 0) as totalCredit, "
            + "coalesce(sum(t.debit), 0) as totalDebit "
            + "from WalletTransaction t join Wallet w on w.id = t.walletId "
            + "where t.refType = com.rwg.wallet.domain.WalletRefType.ADJUSTMENT "
            + "and t.createdAt >= :from and t.createdAt < :to "
            + "group by w.userId")
    List<PlayerAdjustment> sumAdjustmentsByPlayer(@Param("from") Instant from,
                                                  @Param("to") Instant to);
}
