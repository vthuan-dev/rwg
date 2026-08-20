package com.rwg.wallet.repository;

import com.rwg.wallet.domain.Wallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository ví. debit/credit dùng UPDATE điều kiện NGUYÊN TỬ (1 câu) trả affected rows:
 * debit chỉ thành công khi balance >= amt, chống double-spend kể cả 2 thread song song.
 */
@Repository
public interface WalletRepository extends JpaRepository<Wallet, UUID> {

    Optional<Wallet> findByUserId(UUID userId);

    /**
     * Khóa row ví của user (SELECT ... FOR UPDATE) — serialize các thao tác cần
     * đọc-kiểm-tra-ghi trên cùng user trong transaction (vd hạn mức rút ngày,
     * fix review M3). Phải gọi trong transaction đang mở.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.userId = :userId")
    Optional<Wallet> findByUserIdForUpdate(@Param("userId") UUID userId);

    /**
     * Claim NGUYÊN TỬ mốc nạp tiền đầu tiên (fix review M5): conditional UPDATE
     * chỉ đúng 1 row thắng khi có nhiều giao dịch nạp song song. Trả số dòng
     * bị ảnh hưởng (1 = user này chưa từng claim, 0 = đã có lệnh khác thắng).
     */
    @Modifying(clearAutomatically = true)
    @Query("update Wallet w set w.firstDepositAt = :now "
            + "where w.userId = :userId and w.firstDepositAt is null")
    int claimFirstDeposit(@Param("userId") UUID userId, @Param("now") Instant now);

    /**
     * Trừ tiền NGUYÊN TỬ: chỉ update khi balance >= amt. Trả số dòng bị ảnh hưởng
     * (1 = thành công, 0 = thiếu tiền). balance_after đọc lại sau đó trong cùng transaction.
     */
    @Modifying
    @Query("update Wallet w set w.balance = w.balance - :amt, w.version = w.version + 1, "
            + "w.updatedAt = :now where w.id = :id and w.balance >= :amt")
    int debitIfSufficient(@Param("id") UUID id, @Param("amt") BigDecimal amt, @Param("now") Instant now);

    /** Cộng tiền (luôn thành công nếu ví tồn tại). Trả số dòng bị ảnh hưởng. */
    @Modifying
    @Query("update Wallet w set w.balance = w.balance + :amt, w.version = w.version + 1, "
            + "w.updatedAt = :now where w.id = :id")
    int credit(@Param("id") UUID id, @Param("amt") BigDecimal amt, @Param("now") Instant now);

    /** Đọc số dư mới nhất (sau UPDATE) — bypass persistence context để lấy giá trị DB. */
    @Query("select w.balance from Wallet w where w.id = :id")
    BigDecimal findBalanceById(@Param("id") UUID id);
}
