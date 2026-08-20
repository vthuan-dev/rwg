package com.rwg.game.service;

import com.rwg.config.GameProperties;
import com.rwg.game.domain.GameRound;
import com.rwg.game.dto.BetPlacedPayload;
import com.rwg.game.dto.PlayerWinPayload;
import com.rwg.game.dto.RoundPhasePayload;
import com.rwg.game.dto.RoundResultPayload;
import com.rwg.game.dto.RoundVoidedPayload;
import com.rwg.game.dto.WalletBalancePayload;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Phát sự kiện realtime game (Phase c) — CHỐNG storm theo docs/round-lifecycle.md mục 7:
 * - phase/kết quả/hủy vòng: 1 gói/bàn trên /topic/game/table/{tableId}, kèm serverTime
 *   để client TỰ countdown (server không spam gói tick).
 * - BET_PLACED: AGGREGATE cửa sổ 250ms (config rwg.game.bet-placed-window) —
 *   gom số lệnh + tổng stake mỗi bàn, KHÔNG phát từng lệnh.
 * - Thắng + số dư: unicast /user/queue/game/results và /user/queue/wallet theo
 *   principal userId (WsAuthChannelInterceptor).
 */
@Service
public class GameEventBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(GameEventBroadcaster.class);

    public static final String TABLE_TOPIC_PREFIX = "/topic/game/table/";
    public static final String USER_QUEUE_WALLET = "/queue/wallet";
    public static final String USER_QUEUE_GAME_RESULTS = "/queue/game/results";

    private final SimpMessagingTemplate messaging;
    private final ConcurrentMap<UUID, BetAggregate> betAggregates = new ConcurrentHashMap<>();
    private final ScheduledExecutorService aggregateFlusher;

    public GameEventBroadcaster(SimpMessagingTemplate messaging, GameProperties gameProperties) {
        this.messaging = messaging;
        long windowMs = Math.max(50, gameProperties.betPlacedWindow().toMillis());
        this.aggregateFlusher = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "bet-placed-aggregate-flusher");
            t.setDaemon(true);
            return t;
        });
        // Flush định kỳ cửa sổ aggregate; bàn không có cược mới thì KHÔNG phát gói nào.
        this.aggregateFlusher.scheduleAtFixedRate(this::flushAll, windowMs, windowMs, TimeUnit.MILLISECONDS);
    }

    // ===== broadcast 1 gói/bàn =====

    public void broadcastPhase(GameRound round) {
        messaging.convertAndSend(TABLE_TOPIC_PREFIX + round.getTableId(),
                RoundPhasePayload.of(round.getTableId().toString(), round.getId().toString(),
                        round.getRoundSeq(), round.getPhase().name()));
    }

    public void broadcastResult(GameRound round, int winningNumber) {
        messaging.convertAndSend(TABLE_TOPIC_PREFIX + round.getTableId(),
                RoundResultPayload.of(round.getTableId().toString(), round.getId().toString(),
                        round.getRoundSeq(), winningNumber));
    }

    public void broadcastBaccaratResult(GameRound round, BaccaratEngine.RoundResult result) {
        messaging.convertAndSend(TABLE_TOPIC_PREFIX + round.getTableId(),
                RoundResultPayload.baccarat(
                        round.getTableId().toString(),
                        round.getId().toString(),
                        round.getRoundSeq(),
                        String.join(",", result.getPlayerCards()),
                        String.join(",", result.getBankerCards()),
                        result.getPlayerScore(),
                        result.getBankerScore(),
                        result.isPlayerPair(),
                        result.isBankerPair(),
                        result.getOutcome()
                ));
    }

    public void broadcastKl28Result(GameRound round, Kl28Engine.RoundResult result) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < result.getNumbers().size(); i++) {
            sb.append(result.getNumbers().get(i));
            if (i < result.getNumbers().size() - 1) sb.append(",");
        }
        messaging.convertAndSend(TABLE_TOPIC_PREFIX + round.getTableId(),
                RoundResultPayload.kl28(
                        round.getTableId().toString(),
                        round.getId().toString(),
                        round.getRoundSeq(),
                        sb.toString(),
                        result.getSum()
                ));
    }

    public void broadcastVoided(GameRound round) {
        messaging.convertAndSend(TABLE_TOPIC_PREFIX + round.getTableId(),
                RoundVoidedPayload.of(round.getTableId().toString(), round.getId().toString(),
                        round.getRoundSeq()));
    }

    // ===== BET_PLACED aggregate =====

    /** BetService gọi mỗi lệnh cược thành công — KHÔNG phát ngay, gom vào cửa sổ 250ms. */
    public void recordBet(UUID tableId, UUID roundId, BigDecimal stake) {
        betAggregates.computeIfAbsent(tableId, t -> new BetAggregate()).accumulate(roundId, stake);
    }

    private void flushAll() {
        betAggregates.forEach((tableId, aggregate) -> {
            BetPlacedPayload payload = aggregate.drain(tableId);
            if (payload != null) {
                try {
                    messaging.convertAndSend(TABLE_TOPIC_PREFIX + tableId, payload);
                } catch (RuntimeException sendFailed) {
                    log.warn("BET_PLACED aggregate flush failed tableId={}", tableId, sendFailed);
                }
            }
        });
    }

    // ===== unicast cho user (principal name = userId) =====

    public void unicastWin(UUID userId, String tableId, String roundId, int winningNumber,
                           BigDecimal payout, BigDecimal balanceAfter) {
        messaging.convertAndSendToUser(userId.toString(), USER_QUEUE_GAME_RESULTS,
                PlayerWinPayload.of(tableId, roundId, winningNumber,
                        payout.toPlainString(), balanceAfter.toPlainString()));
        messaging.convertAndSendToUser(userId.toString(), USER_QUEUE_WALLET,
                WalletBalancePayload.of(balanceAfter.toPlainString()));
    }

    public void unicastBaccaratWin(UUID userId, String tableId, String roundId, BaccaratEngine.RoundResult result,
                                   BigDecimal payout, BigDecimal balanceAfter) {
        messaging.convertAndSendToUser(userId.toString(), USER_QUEUE_GAME_RESULTS,
                PlayerWinPayload.baccarat(
                        tableId,
                        roundId,
                        String.join(",", result.getPlayerCards()),
                        String.join(",", result.getBankerCards()),
                        result.getPlayerScore(),
                        result.getBankerScore(),
                        result.isPlayerPair(),
                        result.isBankerPair(),
                        result.getOutcome(),
                        payout.toPlainString(),
                        balanceAfter.toPlainString()
                ));
        messaging.convertAndSendToUser(userId.toString(), USER_QUEUE_WALLET,
                WalletBalancePayload.of(balanceAfter.toPlainString()));
    }

    public void unicastKl28Win(UUID userId, String tableId, String roundId, Kl28Engine.RoundResult result,
                               BigDecimal payout, BigDecimal balanceAfter) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < result.getNumbers().size(); i++) {
            sb.append(result.getNumbers().get(i));
            if (i < result.getNumbers().size() - 1) sb.append(",");
        }
        messaging.convertAndSendToUser(userId.toString(), USER_QUEUE_GAME_RESULTS,
                PlayerWinPayload.kl28(
                        tableId,
                        roundId,
                        sb.toString(),
                        result.getSum(),
                        payout.toPlainString(),
                        balanceAfter.toPlainString()
                ));
        messaging.convertAndSendToUser(userId.toString(), USER_QUEUE_WALLET,
                WalletBalancePayload.of(balanceAfter.toPlainString()));
    }

    public void unicastBalance(UUID userId, BigDecimal balance) {
        messaging.convertAndSendToUser(userId.toString(), USER_QUEUE_WALLET,
                WalletBalancePayload.of(balance.toPlainString()));
    }

    @PreDestroy
    void shutdown() {
        aggregateFlusher.shutdownNow();
    }

    /** Bộ gom cược mỗi bàn: đếm số lệnh + tổng stake giữa 2 lần flush. */
    private static final class BetAggregate {
        private final AtomicLong betCount = new AtomicLong();
        private final Object sumLock = new Object();
        private BigDecimal totalStake = BigDecimal.ZERO;
        private volatile UUID roundId;

        void accumulate(UUID currentRoundId, BigDecimal stake) {
            this.roundId = currentRoundId;
            synchronized (sumLock) {
                totalStake = totalStake.add(stake);
            }
            betCount.incrementAndGet();
        }

        /** Trả payload nếu cửa sổ có cược; reset bộ đếm. Không có cược -> null (không phát). */
        BetPlacedPayload drain(UUID tableId) {
            long count = betCount.getAndSet(0);
            if (count == 0) {
                return null;
            }
            BigDecimal sum;
            synchronized (sumLock) {
                sum = totalStake;
                totalStake = BigDecimal.ZERO;
            }
            UUID round = roundId;
            return BetPlacedPayload.of(tableId.toString(), round == null ? null : round.toString(),
                    count, sum.toPlainString());
        }
    }
}
