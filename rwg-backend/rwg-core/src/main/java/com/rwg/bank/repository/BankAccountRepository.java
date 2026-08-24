package com.rwg.bank.repository;

import com.rwg.bank.domain.BankAccount;
import com.rwg.bank.domain.BankAccountId;
import com.rwg.bank.domain.BankAccountStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository phương thức nhận tiền (bảng bank_accounts). id là UUID unique nên tra
 * theo id (một phần PK composite) bằng findFirstById.
 */
@Repository
public interface BankAccountRepository extends JpaRepository<BankAccount, BankAccountId> {

    List<BankAccount> findByUserIdAndStatusOrderByCreatedAtAsc(UUID userId, BankAccountStatus status);

    /**
     * TẤT CẢ phương thức của user, kể cả đã gỡ (REMOVED) — dùng cho khu quản trị.
     *
     * Người vận hành cần thấy cả bản ghi đã gỡ: lịch sử đổi ví nhận tiền là thông tin
     * điều tra có giá trị, và ẩn nó đi thì một tài khoản đổi ví ngay trước khi rút
     * trông giống như chưa từng có ví nào khác.
     */
    List<BankAccount> findByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<BankAccount> findFirstById(UUID id);

    /**
     * Nạp NHIỀU tài khoản trong một truy vấn — cho bảng danh sách của khu quản trị.
     *
     * Không dùng được {@code findAllById} có sẵn: PK của entity này là composite
     * {@code (id, created_at)}, nên hàm đó đòi {@code BankAccountId} chứ không nhận UUID trần.
     *
     * Cần thiết để tránh N+1: một trang 20 lệnh rút mà tra tài khoản từng dòng là 20 lượt
     * gọi DB thêm cho mỗi lần mở trang.
     */
    List<BankAccount> findByIdIn(Collection<UUID> ids);

    /** Tài khoản mặc định đang hoạt động (bắt buộc khi rút tiền). */
    Optional<BankAccount> findFirstByUserIdAndIsDefaultTrueAndStatus(UUID userId, BankAccountStatus status);

    boolean existsByUserIdAndStatus(UUID userId, BankAccountStatus status);

    /** Bỏ cờ default của TẤT CẢ tài khoản user (trước khi đặt default mới). */
    @Modifying
    @Query("update BankAccount b set b.isDefault = false where b.userId = :userId")
    int clearDefaultForUser(@Param("userId") UUID userId);
}
