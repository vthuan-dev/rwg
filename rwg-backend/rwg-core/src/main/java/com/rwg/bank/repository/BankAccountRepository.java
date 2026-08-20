package com.rwg.bank.repository;

import com.rwg.bank.domain.BankAccount;
import com.rwg.bank.domain.BankAccountId;
import com.rwg.bank.domain.BankAccountStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository tài khoản ngân hàng liên kết. id là UUID unique nên tra theo id
 * (một phần PK composite) bằng findFirstById.
 */
@Repository
public interface BankAccountRepository extends JpaRepository<BankAccount, BankAccountId> {

    List<BankAccount> findByUserIdAndStatusOrderByCreatedAtAsc(UUID userId, BankAccountStatus status);

    Optional<BankAccount> findFirstById(UUID id);

    /** Tài khoản mặc định đang hoạt động (bắt buộc khi rút tiền). */
    Optional<BankAccount> findFirstByUserIdAndIsDefaultTrueAndStatus(UUID userId, BankAccountStatus status);

    boolean existsByUserIdAndStatus(UUID userId, BankAccountStatus status);

    /** Bỏ cờ default của TẤT CẢ tài khoản user (trước khi đặt default mới). */
    @Modifying
    @Query("update BankAccount b set b.isDefault = false where b.userId = :userId")
    int clearDefaultForUser(@Param("userId") UUID userId);
}
