package com.rwg.wallet.repository;

import com.rwg.wallet.domain.WalletRefType;
import com.rwg.wallet.domain.WalletTransaction;
import com.rwg.wallet.domain.WalletTransactionId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

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
}
