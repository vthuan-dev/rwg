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

    /**
     * Một dòng tổng hợp thắng/thua của một người chơi tại một loại game.
     *
     * Dùng projection interface chứ không {@code Object[]}: với {@code Object[]}, tầng
     * trên phải nhọ thứ tự cột, và thêm một cột vào giữa {@code SELECT} sẽ làm lệch
     * toàn bộ dữ liệu MÀ KHÔNG gây lỗi biên dịch. Đây là báo cáo kế toán nên một lỗi
     * âm thầm như vậy là không chấp nhận được.
     */
    interface GameAggregate {
        String getGameType();
        long getBetCount();
        BigDecimal getTotalStake();
        BigDecimal getTotalPayout();
    }

    /**
     * Tổng hợp cược ĐÃ KẾT TOÁN của một người chơi, nhóm theo loại game.
     *
     * CHỬ {@code SETTLED}: cược {@code VOIDED} đã được hoàn tiền nên không phải cược
     * thật, cộng vào sẽ làm phồng doanh số. Cược {@code PENDING} chưa có kết quả nên
     * không thể tính lãi/lỗ — xem {@link #sumPendingStakeByGame}.
     *
     * {@code payout} ĐÃ BAO GỒM TIỀN GỐC (quy ước stake-inclusive của dự án), nên lãi
     * thật của người chơi là {@code totalPayout - totalStake}. Coi {@code payout} là
     * tiền lãi sẽ làm mọi con số phồng lên đúng bằng tổng tiền cược.
     *
     * Khoảng thời gian là NỬA MỞ {@code [from, to)} để hai tháng liền nhau không
     * đếm trùng bản ghi ở đúng ranh giới.
     */
    @Query("select t.gameType as gameType, count(b) as betCount, "
            + "coalesce(sum(b.stake), 0) as totalStake, "
            + "coalesce(sum(b.payout), 0) as totalPayout "
            + "from Bet b join GameTable t on t.id = b.tableId "
            + "where b.userId = :userId "
            + "and b.status = com.rwg.game.domain.BetStatus.SETTLED "
            + "and b.createdAt >= :from and b.createdAt < :to "
            + "group by t.gameType order by t.gameType")
    List<GameAggregate> sumSettledByGame(@Param("userId") UUID userId,
                                        @Param("from") Instant from,
                                        @Param("to") Instant to);

    /** Một dòng tiền cược đang treo (chưa kết toán) theo loại game. */
    interface PendingAggregate {
        String getGameType();
        BigDecimal getPendingStake();
    }

    /**
     * Tổng tiền cược ĐANG TREO theo loại game.
     *
     * Tách khỏi {@link #sumSettledByGame} thay vì gộp vào một truy vấn: cược treo đã bị
     * trừ tiền khỏi ví nhưng chưa có kết quả. Coi là thua thì sai, bỏ qua hẳn thì tổng
     * tiền ra vào ví không cân với sổ sách. Phải hiện thành một cột riêng.
     */
    @Query("select t.gameType as gameType, coalesce(sum(b.stake), 0) as pendingStake "
            + "from Bet b join GameTable t on t.id = b.tableId "
            + "where b.userId = :userId "
            + "and b.status = com.rwg.game.domain.BetStatus.PENDING "
            + "and b.createdAt >= :from and b.createdAt < :to "
            + "group by t.gameType")
    List<PendingAggregate> sumPendingStakeByGame(@Param("userId") UUID userId,
                                                 @Param("from") Instant from,
                                                 @Param("to") Instant to);

    /** Một dòng tổng hợp cược của MỘT người chơi (mọi game) trong kỳ. */
    interface PlayerAggregate {
        UUID getUserId();
        long getBetCount();
        BigDecimal getTotalStake();
        BigDecimal getTotalPayout();
    }

    /**
     * Tổng hợp cược đã kết toán của MỌI người chơi có hoạt động trong kỳ.
     *
     * KHÔNG PHÂN TRANG Ở TẦNG SQL: tầng trên còn phải trộn kết quả này với ba
     * nguồn khác (nạp, rút, điều chỉnh) — một người có thể nạp tiền mà không
     * cược ván nào, nên phân trang từng nguồn riêng sẽ cho ra các trang lệch nhau.
     * Sắp xếp và cắt trang diễn ra sau khi đã trộn.
     *
     * Số dòng bằng số người CÓ CƯỢC trong kỳ, không phải toàn bộ tài khoản.
     */
    @Query("select b.userId as userId, count(b) as betCount, "
            + "coalesce(sum(b.stake), 0) as totalStake, "
            + "coalesce(sum(b.payout), 0) as totalPayout "
            + "from Bet b "
            + "where b.status = com.rwg.game.domain.BetStatus.SETTLED "
            + "and b.createdAt >= :from and b.createdAt < :to "
            + "group by b.userId")
    List<PlayerAggregate> sumSettledByPlayer(@Param("from") Instant from,
                                             @Param("to") Instant to);

    /** Chi tiết từng ván của một người chơi tại một loại game trong kỳ (mức 2). */
    @Query("select b from Bet b join GameTable t on t.id = b.tableId "
            + "where b.userId = :userId and t.gameType = :gameType "
            + "and b.createdAt >= :from and b.createdAt < :to "
            + "order by b.createdAt desc")
    Page<Bet> findByUserAndGameTypeInRange(@Param("userId") UUID userId,
                                           @Param("gameType") String gameType,
                                           @Param("from") Instant from,
                                           @Param("to") Instant to,
                                           Pageable pageable);
}
