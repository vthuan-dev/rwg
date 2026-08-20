package com.rwg.game.repository;

import com.rwg.game.domain.Bet;
import com.rwg.game.domain.BetId;
import com.rwg.game.domain.BetStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Repository lệnh cược. */
@Repository
public interface BetRepository extends JpaRepository<Bet, BetId> {

    /** Phân trang lịch sử cược của người chơi trên toàn hệ thống. */
    Page<Bet> findByUserId(UUID userId, Pageable pageable);

    /** Phân trang lịch sử cược của người chơi trên từng bàn cụ thể. */
    Page<Bet> findByUserIdAndTableId(UUID userId, UUID tableId, Pageable pageable);

    List<Bet> findByRoundId(UUID roundId);

    List<Bet> findByRoundIdAndStatus(UUID roundId, BetStatus status);

    List<Bet> findByUserIdAndRoundId(UUID userId, UUID roundId);

    /** Bet theo idempotency_key "BET:{roundId}:{userId}:{seq}" — fast-path idempotent. */
    Optional<Bet> findFirstByIdempotencyKey(String idempotencyKey);

    /** Tìm theo id (UUID duy nhất toàn cục dù PK composite). */
    Optional<Bet> findFirstById(UUID id);

    long countByRoundId(UUID roundId);

    boolean existsByIdempotencyKey(String idempotencyKey);

    /**
     * Tổng cược hợp lệ (turnover) theo từng user trong khoảng nửa mở [from, to).
     * Trả về các cặp (userId, tổng stake); user không cược trong khoảng sẽ KHÔNG
     * xuất hiện trong kết quả.
     *
     * CHỈ tính {@link BetStatus#SETTLED}: cược VOIDED đã được hoàn stake nên không
     * phải cược thật — nếu tính vào turnover thì đại lý có thể trục lợi bằng cách
     * cho tuyến dưới cược ở bàn hay bị huỷ. Cược PENDING chưa chốt nên cũng loại.
     */
    @Query("select b.userId, coalesce(sum(b.stake), 0) from Bet b "
            + "where b.userId in :userIds and b.status = com.rwg.game.domain.BetStatus.SETTLED "
            + "and b.createdAt >= :from and b.createdAt < :to "
            + "group by b.userId")
    List<Object[]> sumSettledTurnoverByUsers(@Param("userIds") Collection<UUID> userIds,
                                             @Param("from") Instant from,
                                             @Param("to") Instant to);

    /** Tổng turnover toàn hệ thống trong khoảng — phục vụ dashboard admin. */
    @Query("select coalesce(sum(b.stake), 0) from Bet b "
            + "where b.status = com.rwg.game.domain.BetStatus.SETTLED "
            + "and b.createdAt >= :from and b.createdAt < :to")
    BigDecimal sumSettledTurnover(@Param("from") Instant from, @Param("to") Instant to);
}
