package com.rwg.game.service;

import com.rwg.config.GameProperties;
import com.rwg.game.domain.GameRound;
import com.rwg.game.domain.GameTable;
import com.rwg.game.domain.GameTableStatus;
import com.rwg.game.domain.RoundPhase;
import com.rwg.game.domain.RoundStatus;
import com.rwg.game.repository.GameRoundRepository;
import com.rwg.game.repository.GameTableRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Vòng lặp phase của bàn chơi (Phase c, docs/round-lifecycle.md mục 1-2).
 *
 * SINGLE-WRITER PER TABLE: mỗi bàn có ĐÚNG 1 executor single-thread; mọi ghi lên
 * row rounds của bàn chỉ từ thread này. MVP CHẠY 1 INSTANCE — KHÔNG có khóa phân
 * tán; nhiều instance sẽ nhân đôi round (chặng sau: leader-election / DB lock).
 *
 * Thời lượng mỗi phase config-driven rwg.game.round.* (test rút ngắn 100-300ms).
 * Khởi động: VOID + REFUND mọi round OPEN "mồ côi" (app chết giữa round) rồi mới
 * chạy vòng lặp mới. Hủy vòng chủ động: {@link #voidCurrentRound(UUID)} interrupt
 * thread của bàn -> round hiện tại được VOID + REFUND ngay tại biên phase gần nhất.
 */
@Component
public class RoundScheduler implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(RoundScheduler.class);

    /** Chặn chờ settle tối đa: duration phase SETTLE + buffer xử lý batch. */
    private static final Duration SETTLE_WAIT_BUFFER = Duration.ofSeconds(15);

    private final GameTableRepository tableRepository;
    private final GameRoundRepository roundRepository;
    private final SettlementService settlementService;
    private final GameEventBroadcaster broadcaster;
    private final GameProperties gameProperties;
    private final TransactionTemplate txWrite;
    private final SecureRandom secureRandom = new SecureRandom();

    private final Map<UUID, ExecutorService> tableExecutors = new ConcurrentHashMap<>();
    private final Map<UUID, Thread> loopThreads = new ConcurrentHashMap<>();
    private volatile boolean running;

    @Value("${rwg.game.scheduler-enabled:true}")
    private boolean schedulerEnabled;

    public RoundScheduler(GameTableRepository tableRepository,
                          GameRoundRepository roundRepository,
                          SettlementService settlementService,
                          GameEventBroadcaster broadcaster,
                          GameProperties gameProperties,
                          PlatformTransactionManager transactionManager) {
        this.tableRepository = tableRepository;
        this.roundRepository = roundRepository;
        this.settlementService = settlementService;
        this.broadcaster = broadcaster;
        this.gameProperties = gameProperties;
        this.txWrite = new TransactionTemplate(transactionManager);
        this.txWrite.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (!schedulerEnabled) {
            log.info("round scheduler disabled (rwg.game.scheduler-enabled=false)");
            return;
        }
        running = true;
        recoverOrphanRounds();
        List<GameTable> tables = tableRepository.findByStatus(GameTableStatus.ACTIVE);
        for (GameTable table : tables) {
            startTable(table);
        }
        log.info("round scheduler started for {} active table(s)", tables.size());
    }

    /** Bàn ACTIVE có vòng lặp single-writer riêng. */
    private void startTable(GameTable table) {
        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "round-scheduler-" + table.getId().toString().substring(0, 8));
            t.setDaemon(true);
            return t;
        });
        tableExecutors.put(table.getId(), executor);
        executor.submit(() -> loop(table));
    }

    private void loop(GameTable table) {
        loopThreads.put(table.getId(), Thread.currentThread());
        while (running) {
            try {
                runRound(table);
            } catch (Exception unexpected) {
                log.error("round loop error tableId={}", table.getId(), unexpected);
                try {
                    Thread.sleep(250);
                } catch (InterruptedException e) {
                    // Cờ interrupt được dọn ở sleep kế tiếp của vòng mới; dừng nếu shutdown.
                    if (!running) {
                        break;
                    }
                }
            }
        }
        loopThreads.remove(table.getId());
    }

    /** Một vòng đời đầy đủ: BETTING_OPEN -> BETTING_CLOSED -> SPINNING -> RESULT -> SETTLE. */
    private void runRound(GameTable table) {
        GameRound round = createRound(table.getId());
        broadcaster.broadcastPhase(round);
        try {
            sleep(gameProperties.round().bettingOpen());

            requireTransition(round, RoundPhase.BETTING_CLOSED);
            sleep(gameProperties.round().bettingClosed());

            requireTransition(round, RoundPhase.SPINNING);
            sleep(gameProperties.round().spinning());

            if ("ROULETTE".equals(table.getGameType())) {
                int winningNumber = RouletteEngine.spin(secureRandom);
                publishResult(round, winningNumber);
                broadcaster.broadcastResult(round, winningNumber);
                sleep(gameProperties.round().result());

                requireTransition(round, RoundPhase.SETTLE);
                awaitSettlement(round, winningNumber);
            } else if ("BACCARAT".equals(table.getGameType())) {
                BaccaratEngine.RoundResult result = BaccaratEngine.playRound(secureRandom);
                publishBaccaratResult(round, result);
                broadcaster.broadcastBaccaratResult(round, result);
                sleep(gameProperties.round().result());

                requireTransition(round, RoundPhase.SETTLE);
                awaitBaccaratSettlement(round, result);
            } else if ("KL28".equals(table.getGameType())
                    || "LUCKY28".equals(table.getGameType())
                    || "BRITISH_LUCKY28".equals(table.getGameType())
                    || "TAIWAN_TIMES".equals(table.getGameType())) {
                Kl28Engine.RoundResult result = Kl28Engine.playRound(secureRandom);
                publishKl28Result(round, result);
                broadcaster.broadcastKl28Result(round, result);
                sleep(gameProperties.round().result());

                requireTransition(round, RoundPhase.SETTLE);
                awaitKl28Settlement(round, result);
            } else {
                throw new IllegalStateException("Unknown game type: " + table.getGameType());
            }
        } catch (InterruptedException interrupted) {
            // voidCurrentRound() (hoặc shutdown) interrupt: VOID + REFUND round hiện tại.
            // Vòng lặp tiếp tục vòng MỚI nếu scheduler còn chạy.
            if (running) {
                voidOrphanedRound(round);
            }
        } catch (RoundAborted aborted) {
            log.info("round {} aborted ({})", round.getId(), aborted.getMessage());
        }
    }

    /** Chờ settle async xong trong phase SETTLE (không chờ vô hạn). */
    private void awaitSettlement(GameRound round, int winningNumber) throws InterruptedException {
        try {
            settlementService.settleRoundAsync(round.getId(), winningNumber)
                    .get(gameProperties.round().settle().plus(SETTLE_WAIT_BUFFER).toMillis(),
                            TimeUnit.MILLISECONDS);
        } catch (TimeoutException settleTooSlow) {
            log.error("settlement timeout roundId={} — reconciliation job sẽ cảnh báo", round.getId());
        } catch (java.util.concurrent.ExecutionException settleFailed) {
            log.error("settlement failed roundId={}", round.getId(), settleFailed);
        }
    }

    private void awaitBaccaratSettlement(GameRound round, BaccaratEngine.RoundResult result) throws InterruptedException {
        try {
            settlementService.settleBaccaratRoundAsync(round.getId(), result)
                    .get(gameProperties.round().settle().plus(SETTLE_WAIT_BUFFER).toMillis(),
                            TimeUnit.MILLISECONDS);
        } catch (TimeoutException settleTooSlow) {
            log.error("baccarat settlement timeout roundId={} — reconciliation job sẽ cảnh báo", round.getId());
        } catch (java.util.concurrent.ExecutionException settleFailed) {
            log.error("baccarat settlement failed roundId={}", round.getId(), settleFailed);
        }
    }

    private void publishBaccaratResult(GameRound round, BaccaratEngine.RoundResult result) {
        Instant now = Instant.now();
        String playerCards = String.join(",", result.getPlayerCards());
        String bankerCards = String.join(",", result.getBankerCards());
        Integer updated = txWrite.execute(status -> roundRepository.markBaccaratResult(
                round.getId(), round.getCreatedAt(),
                playerCards, bankerCards,
                result.getPlayerScore(), result.getBankerScore(),
                result.isPlayerPair(), result.isBankerPair(),
                result.getOutcome(), now, RoundStatus.OPEN, now));
        if (updated == null || updated == 0) {
            throw new RoundAborted("baccarat result lost OPEN claim");
        }
        round.setBaccaratPlayerCards(playerCards);
        round.setBaccaratBankerCards(bankerCards);
        round.setBaccaratPlayerScore(result.getPlayerScore());
        round.setBaccaratBankerScore(result.getBankerScore());
        round.setBaccaratPlayerPair(result.isPlayerPair());
        round.setBaccaratBankerPair(result.isBankerPair());
        round.setBaccaratResult(result.getOutcome());
        round.setResultAt(now);
        round.setPhase(RoundPhase.RESULT);
    }

    private void awaitKl28Settlement(GameRound round, Kl28Engine.RoundResult result) throws InterruptedException {
        try {
            settlementService.settleKl28RoundAsync(round.getId(), result)
                    .get(gameProperties.round().settle().plus(SETTLE_WAIT_BUFFER).toMillis(),
                            TimeUnit.MILLISECONDS);
        } catch (TimeoutException settleTooSlow) {
            log.error("kl28 settlement timeout roundId={} — reconciliation job sẽ cảnh báo", round.getId());
        } catch (java.util.concurrent.ExecutionException settleFailed) {
            log.error("kl28 settlement failed roundId={}", round.getId(), settleFailed);
        }
    }

    private void publishKl28Result(GameRound round, Kl28Engine.RoundResult result) {
        Instant now = Instant.now();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < result.getNumbers().size(); i++) {
            sb.append(result.getNumbers().get(i));
            if (i < result.getNumbers().size() - 1) sb.append(",");
        }
        String numbersStr = sb.toString();
        Integer updated = txWrite.execute(status -> roundRepository.markKl28Result(
                round.getId(), round.getCreatedAt(),
                numbersStr, result.getSum(), now, RoundStatus.OPEN, now));
        if (updated == null || updated == 0) {
            throw new RoundAborted("kl28 result lost OPEN claim");
        }
        round.setKl28Numbers(numbersStr);
        round.setKl28Sum(result.getSum());
        round.setResultAt(now);
        round.setPhase(RoundPhase.RESULT);
    }

    // ===== thao tác round (mỗi thao tác 1 transaction riêng, single-writer) =====

    private GameRound createRound(UUID tableId) {
        return txWrite.execute(status -> {
            long nextSeq = roundRepository.maxSeqByTableId(tableId) + 1;
            return roundRepository.save(new GameRound(tableId, nextSeq));
        });
    }

    /** Chuyển phase; thua (round đã VOIDED bởi yêu cầu hủy) -> ném RoundAborted. */
    private void requireTransition(GameRound round, RoundPhase phase) {
        Integer updated = txWrite.execute(status -> roundRepository.updatePhase(
                round.getId(), round.getCreatedAt(), phase, RoundStatus.OPEN, Instant.now()));
        if (updated == null || updated == 0) {
            throw new RoundAborted("phase " + phase + " lost OPEN claim");
        }
        round.setPhase(phase);
        broadcaster.broadcastPhase(round);
    }

    /** Lưu số trúng + result_at khi vào RESULT (mốc đo settlement_lag). */
    private void publishResult(GameRound round, int winningNumber) {
        Instant now = Instant.now();
        Integer updated = txWrite.execute(status -> roundRepository.markResult(
                round.getId(), round.getCreatedAt(), winningNumber, now, RoundStatus.OPEN, now));
        if (updated == null || updated == 0) {
            throw new RoundAborted("result lost OPEN claim");
        }
        round.setWinningNumber(winningNumber);
        round.setResultAt(now);
        round.setPhase(RoundPhase.RESULT);
    }

    /** Void round mồ côi/bị yêu cầu hủy + hoàn tiền, phát sự kiện ROUND_VOIDED. */
    private void voidOrphanedRound(GameRound round) {
        try {
            if (settlementService.voidRound(round.getId())) {
                broadcaster.broadcastVoided(round);
            }
        } catch (RuntimeException refundFailed) {
            log.error("void/refund failed roundId={} — reconciliation sẽ cảnh báo", round.getId(), refundFailed);
        }
    }

    /**
     * Crash recovery (docs/round-lifecycle.md mục 6): round OPEN còn sót sau khi app
     * tắt giữa chừng -> VOID + REFUND toàn bộ bets; tiền debit rồi KHÔNG bị treo.
     */
    private void recoverOrphanRounds() {
        List<GameRound> orphans = roundRepository.findByStatus(RoundStatus.OPEN);
        for (GameRound orphan : orphans) {
            log.warn("recovering orphan OPEN round {} table {}", orphan.getId(), orphan.getTableId());
            voidOrphanedRound(orphan);
        }
    }

    /**
     * Hủy vòng hiện tại của bàn (test/admin): interrupt thread single-writer của bàn;
     * round đang chạy được VOID + REFUND tại điểm interrupt. Round đã SETTLED
     * KHÔNG bị hoàn tiền (claim VOIDED thua).
     */
    public void voidCurrentRound(UUID tableId) {
        Thread loopThread = loopThreads.get(tableId);
        if (loopThread != null) {
            loopThread.interrupt();
        }
    }

    private void sleep(Duration duration) throws InterruptedException {
        long millis = duration.toMillis();
        if (millis > 0) {
            Thread.sleep(millis);
        }
    }

    @PreDestroy
    void shutdown() {
        running = false;
        loopThreads.values().forEach(Thread::interrupt);
        tableExecutors.values().forEach(ExecutorService::shutdownNow);
    }

    /** Vòng hiện tại đã bị xử lý bởi tác vụ khác (vd void) — bỏ qua phần còn lại. */
    private static final class RoundAborted extends RuntimeException {
        RoundAborted(String reason) {
            super(reason);
        }
    }
}
