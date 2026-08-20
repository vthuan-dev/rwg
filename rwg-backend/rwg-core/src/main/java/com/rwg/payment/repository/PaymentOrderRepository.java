package com.rwg.payment.repository;

import com.rwg.payment.domain.PaymentOrder;
import com.rwg.payment.domain.PaymentOrderId;
import com.rwg.payment.domain.PaymentStatus;
import com.rwg.payment.domain.PaymentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository lệnh thanh toán. id là UUID thực tế unique nên tra theo id (một phần của
 * PK composite) bằng findFirstById; webhook tra theo provider_txn_id.
 */
@Repository
public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, PaymentOrderId> {

    Optional<PaymentOrder> findFirstById(UUID id);

    Optional<PaymentOrder> findFirstByProviderTxnId(String providerTxnId);

    Optional<PaymentOrder> findFirstByIdempotencyKey(String idempotencyKey);

    /** Tổng tiền đã rút trong ngày (loại trừ VOIDED) — phục vụ hạn mức rút theo ngày. */
    @Query("select coalesce(sum(o.amount), 0) from PaymentOrder o "
            + "where o.userId = :userId and o.type = :type and o.status <> :excluded "
            + "and o.createdAt >= :start")
    BigDecimal sumAmountSince(@Param("userId") UUID userId,
                              @Param("type") PaymentType type,
                              @Param("excluded") PaymentStatus excluded,
                              @Param("start") Instant start);

    /**
     * Chuyển trạng thái NGUYÊN TỬ bằng 1 câu UPDATE điều kiện (fix review C2):
     * {@code set status = :to where id = :id and status = :from}. Trả số dòng bị
     * ảnh hưởng (1 = thắng, 0 = lệnh không tồn tại HOẶC đã bị luồng khác chuyển).
     * 2 thao tác approve/reject song song chỉ đúng 1 thao tác thắng.
     */
    @Modifying(clearAutomatically = true)
    @Query("update PaymentOrder o set o.status = :to, o.updatedAt = :now "
            + "where o.id = :id and o.status = :from")
    int transitionStatus(@Param("id") UUID id,
                         @Param("from") PaymentStatus from,
                         @Param("to") PaymentStatus to,
                         @Param("now") Instant now);
}
