package com.rwg.wallet.repository;

import com.rwg.wallet.domain.WalletLedgerGuard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository guard idempotency ledger. Insert guard là thao tác ĐẦU TIÊN trong
 * transaction ghi sổ: trùng key -> vi phạm PK -> DataIntegrityViolationException
 * -> rollback và trả kết quả hiện có (idempotent success) — fix review C1.
 */
@Repository
public interface WalletLedgerGuardRepository extends JpaRepository<WalletLedgerGuard, String> {

    boolean existsByIdempotencyKey(String idempotencyKey);
}
