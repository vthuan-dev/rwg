package com.rwg.game.repository;

import com.rwg.game.domain.GameRound;
import com.rwg.game.domain.GameRoundId;
import com.rwg.game.domain.RoundPhase;
import com.rwg.game.domain.RoundStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository vòng chơi. Mọi chuyển trạng thái dùng UPDATE điều kiện trả
 * affected rows — nguyên tử, chỉ 1 người thắng (claim) khi có race.
 */
@Repository
public interface GameRoundRepository extends JpaRepository<GameRound, GameRoundId> {

    /** Phân trang lịch sử quay số cho bàn. */
    Page<GameRound> findByTableIdAndStatusIn(UUID tableId, Collection<RoundStatus> statuses, Pageable pageable);

    /** Vòng mới nhất của bàn (theo seq) — dùng tính seq kế tiếp và API current. */
    Optional<GameRound> findFirstByTableIdOrderByRoundSeqDesc(UUID tableId);

    /** Vòng OPEN hiện tại của bàn (nếu có). */
    Optional<GameRound> findFirstByTableIdAndStatusOrderByRoundSeqDesc(UUID tableId, RoundStatus status);

    /** Tìm theo id (UUID duy nhất toàn cục dù PK composite). */
    Optional<GameRound> findFirstById(UUID id);

    /** Vòng OPEN còn sót (crash recovery khi khởi động). */
    List<GameRound> findByStatus(RoundStatus status);

    @Query("select coalesce(max(r.roundSeq), 0) from GameRound r where r.tableId = :tableId")
    long maxSeqByTableId(@Param("tableId") UUID tableId);

    /** Cập nhật pha (chỉ khi vòng còn OPEN). Trả số dòng bị ảnh hưởng. */
    @Modifying
    @Query("update GameRound r set r.phase = :phase, r.updatedAt = :now " +
            "where r.id = :id and r.createdAt = :createdAt and r.status = :open")
    int updatePhase(@Param("id") UUID id, @Param("createdAt") Instant createdAt,
                    @Param("phase") RoundPhase phase, @Param("open") RoundStatus open,
                    @Param("now") Instant now);

    /** Công bố kết quả khi vào RESULT: lưu số trúng + result_at (đo settlement_lag). */
    @Modifying
    @Query("update GameRound r set r.winningNumber = :winningNumber, r.resultAt = :resultAt, " +
            "r.updatedAt = :now where r.id = :id and r.createdAt = :createdAt and r.status = :open")
    int markResult(@Param("id") UUID id, @Param("createdAt") Instant createdAt,
                   @Param("winningNumber") Integer winningNumber, @Param("resultAt") Instant resultAt,
                   @Param("open") RoundStatus open, @Param("now") Instant now);

    /** Lưu kết quả ván Baccarat. */
    @Modifying
    @Query("update GameRound r set " +
            "r.baccaratPlayerCards = :playerCards, " +
            "r.baccaratBankerCards = :bankerCards, " +
            "r.baccaratPlayerScore = :playerScore, " +
            "r.baccaratBankerScore = :bankerScore, " +
            "r.baccaratPlayerPair = :playerPair, " +
            "r.baccaratBankerPair = :bankerPair, " +
            "r.baccaratResult = :result, " +
            "r.resultAt = :resultAt, " +
            "r.updatedAt = :now " +
            "where r.id = :id and r.createdAt = :createdAt and r.status = :open")
    int markBaccaratResult(@Param("id") UUID id, @Param("createdAt") Instant createdAt,
                           @Param("playerCards") String playerCards,
                           @Param("bankerCards") String bankerCards,
                           @Param("playerScore") Integer playerScore,
                           @Param("bankerScore") Integer bankerScore,
                           @Param("playerPair") Boolean playerPair,
                           @Param("bankerPair") Boolean bankerPair,
                           @Param("result") String result,
                           @Param("resultAt") Instant resultAt,
                           @Param("open") RoundStatus open,
                           @Param("now") Instant now);

    /** Lưu kết quả ván Korean Lucky 28. */
    @Modifying
    @Query("update GameRound r set " +
            "r.kl28Numbers = :kl28Numbers, " +
            "r.kl28Sum = :kl28Sum, " +
            "r.resultAt = :resultAt, " +
            "r.updatedAt = :now " +
            "where r.id = :id and r.createdAt = :createdAt and r.status = :open")
    int markKl28Result(@Param("id") UUID id, @Param("createdAt") Instant createdAt,
                       @Param("kl28Numbers") String kl28Numbers,
                       @Param("kl28Sum") Integer kl28Sum,
                       @Param("resultAt") Instant resultAt,
                       @Param("open") RoundStatus open,
                       @Param("now") Instant now);

    /**
     * Claim chuyển OPEN -> status mới (SETTLED hoặc VOIDED), kèm số trúng khi công bố.
     * Trả 1 nếu claim thành công, 0 nếu vòng đã bị xử lý bởi tiến trình khác.
     */
    @Modifying
    @Query("update GameRound r set r.status = :status, r.winningNumber = :winningNumber, " +
            "r.resultAt = :resultAt, r.updatedAt = :now " +
            "where r.id = :id and r.createdAt = :createdAt and r.status = :open")
    int claimTransition(@Param("id") UUID id, @Param("createdAt") Instant createdAt,
                      @Param("status") RoundStatus status,
                      @Param("winningNumber") Integer winningNumber,
                      @Param("resultAt") Instant resultAt,
                      @Param("open") RoundStatus open, @Param("now") Instant now);

    /** Chuyển trạng thái round chung cho cả Roulette và Baccarat. */
    @Modifying
    @Query("update GameRound r set r.status = :status, r.updatedAt = :now " +
            "where r.id = :id and r.createdAt = :createdAt and r.status = :open")
    int claimStatusTransition(@Param("id") UUID id, @Param("createdAt") Instant createdAt,
                              @Param("status") RoundStatus status,
                              @Param("open") RoundStatus open, @Param("now") Instant now);
}
