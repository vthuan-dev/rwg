package com.rwg.risk.repository;

import com.rwg.risk.domain.AccountLink;
import com.rwg.risk.domain.AccountLinkStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Repository liên kết tài khoản. */
@Repository
public interface AccountLinkRepository extends JpaRepository<AccountLink, UUID> {

    /**
     * Mọi liên kết của một user, ở CẢ HAI cột.
     *
     * Phải kiểm cả hai cột vì cặp được lưu đã sắp xếp: một user có thể nằm ở cột a
     * với người này và cột b với người khác. Chỉ tra một cột sẽ bỏ sót một nửa.
     */
    @Query("select l from AccountLink l where l.userAId = :userId or l.userBId = :userId")
    List<AccountLink> findAllForUser(@Param("userId") UUID userId);

    /** Tra một cặp cụ thể — gọi với cặp ĐÃ SẮP XẾP. */
    Optional<AccountLink> findByUserAIdAndUserBId(UUID userAId, UUID userBId);

    /** Hàng đợi cho người thật xem. */
    Page<AccountLink> findByStatus(AccountLinkStatus status, Pageable pageable);

    Page<AccountLink> findAll(Pageable pageable);

    long countByStatus(AccountLinkStatus status);
}
