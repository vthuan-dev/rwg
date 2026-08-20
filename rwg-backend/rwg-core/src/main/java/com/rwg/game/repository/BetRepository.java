package com.rwg.game.repository;

import com.rwg.game.domain.Bet;
import com.rwg.game.domain.BetId;
import com.rwg.game.domain.BetStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
}
