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
import org.springframework.scheduling.annotation.Scheduled;
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
 *
 * ===== PHẢN ỨNG VỚI LỆNH BẬT/TẮT BÀN TỪ APP ADMIN =====
 * Scheduler này CHỈ chạy ở app player, còn API bật/tắt bàn nằm ở app admin — hai
 * TIẾN TRÌNH KHÁC NHAU, không gọi hàm nhau được. Nên trạng thái bàn được ĐỌC LẠI
 * ở đầu mỗi vòng, và một supervisor định kỳ khởi động vòng lặp cho bàn vừa được
 * bật lại. Trước đây danh sách bàn chỉ đọc MỘT LẦN lúc khởi động, nên nếu chỉ thêm
 * API mà không sửa chỗ này thì API tắt bàn sẽ là NÚT GIẢ: bàn vẫn tự quay.
 *
 * Dừng ở BIÊN VÒNG (không cắt giữa round đang chạy) để không phải hoàn tiền hàng
 * loạt mỗi lần admin tắt bàn. Muốn dừng ngay và hoàn tiền thì dùng voidCurrentRound.
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
    private final com.rwg.settings.repository.AppSettingRepository settingRepository;
    private final com.rwg.game.repository.BetRepository betRepository;
    private final com.rwg.identity.repository.UserRepository userRepository;

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
                          PlatformTransactionManager transactionManager,
                          com.rwg.settings.repository.AppSettingRepository settingRepository,
                          com.rwg.game.repository.BetRepository betRepository,
                          com.rwg.identity.repository.UserRepository userRepository) {
        this.tableRepository = tableRepository;
        this.roundRepository = roundRepository;
        this.settlementService = settlementService;
        this.broadcaster = broadcaster;
        this.gameProperties = gameProperties;
        this.settingRepository = settingRepository;
        this.betRepository = betRepository;
        this.userRepository = userRepository;
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

    /**
     * Supervisor: đồng bộ tập vòng lặp đang chạy với danh sách bàn ACTIVE trong DB.
     *
     * Cần thiết vì lệnh bật bàn đến từ TIẾN TRÌNH KHÁC (app admin): bàn được bật lại
     * sẽ không có ai khởi động vòng lặp cho nó nếu không có vòng quét này. Chiều
     * ngược lại (tắt bàn) do chính vòng lặp tự phát hiện và thoát.
     */
    @Scheduled(fixedDelayString = "${rwg.game.table-sync-interval:PT10S}")
    void syncActiveTables() {
        if (!running) {
            return;
        }
        for (GameTable table : tableRepository.findByStatus(GameTableStatus.ACTIVE)) {
            // Kiểm tableExecutors (không phải loopThreads): entry ở đây được ghi NGAY
            // khi submit, còn loopThreads chỉ có sau khi thread thực sự bắt đầu chạy —
            // dùng loopThreads sẽ tạo khe sinh vòng lặp thứ hai, vi phạm single-writer.
            if (!tableExecutors.containsKey(table.getId())) {
                log.info("table {} vua duoc bat lai -> khoi dong vong lap", table.getId());
                startTable(table);
            }
        }
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
        try {
            while (running) {
                // ĐỌC LẠI trạng thái ở ĐẦU MỖI VÒNG: admin tắt bàn ở tiến trình khác nên
                // đây là cách duy nhất biết được. Dừng ở biên vòng, không cắt giữa round
                // đang chạy -> không phát sinh hoàn tiền hàng loạt.
                GameTable current = tableRepository.findById(table.getId()).orElse(null);
                if (current == null || current.getStatus() != GameTableStatus.ACTIVE) {
                    log.info("table {} da bi tat -> dung vong lap", table.getId());
                    break;
                }
                try {
                    // Dùng bản VỪA ĐỌC: hạn mức min/max có thể đã được admin sửa.
                    runRound(current);
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
        } finally {
            // Dọn CẢ HAI map trong finally: nếu chỉ dọn loopThreads thì tableExecutors còn
            // sót entry, khiến supervisor tưởng bàn vẫn đang chạy và không bao giờ khởi
            // động lại được bàn đó sau khi admin bật lại.
            loopThreads.remove(table.getId());
            ExecutorService finished = tableExecutors.remove(table.getId());
            if (finished != null) {
                finished.shutdown();
            }
        }
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
            } else if ("XOC_DIA".equals(table.getGameType())) {
                XocDiaEngine.RoundResult result = null;
                String forceResult = readSetting(com.rwg.settings.domain.AppSetting.XOC_DIA_FORCE_RESULT, "AUTO");
                String forceMode = readSetting(com.rwg.settings.domain.AppSetting.XOC_DIA_FORCE_MODE, "ONCE");

                if (forceResult != null && !"AUTO".equalsIgnoreCase(forceResult.trim())) {
                    int redCount = switch (forceResult.trim().toUpperCase()) {
                        case "FOUR_RED" -> 4;
                        case "FOUR_WHITE" -> 0;
                        case "THREE_RED" -> 3;
                        case "THREE_WHITE" -> 1;
                        case "TWO_RED", "EVEN_2_2" -> 2;
                        case "EVEN" -> {
                            int r = secureRandom.nextInt(10);
                            yield r < 8 ? 2 : (r == 8 ? 4 : 0);
                        }
                        case "ODD" -> secureRandom.nextBoolean() ? 3 : 1;
                        default -> -1;
                    };

                    if (redCount >= 0) {
                        result = forcedXocDiaResult(redCount);
                        log.info("Admin can thiep ket qua XocDia vong {}: forceResult={}, redCount={}", round.getRoundSeq(), forceResult, redCount);
                        if ("ONCE".equalsIgnoreCase(forceMode)) {
                            resetForceResult();
                        }
                    }
                }

                XocDiaJackpotService.JackpotDecision jp = XocDiaJackpotService.NO_HIT;
                if (result == null) {
                    result = XocDiaEngine.playRound(secureRandom);
                    jp = decideJackpot();
                    if (jp.hit()) {
                        result = forcedXocDiaResult(jp.redCount());
                    }
                }
                publishXocDiaResult(round, result);
                broadcaster.broadcastXocDiaResult(round, result);
                sleep(gameProperties.round().result());

                requireTransition(round, RoundPhase.SETTLE);
                awaitXocDiaSettlement(round, result);
                if (jp.hit()) {
                    handleJackpotWin(round, jp);
                }
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

    private void awaitXocDiaSettlement(GameRound round, XocDiaEngine.RoundResult result) throws InterruptedException {
        try {
            settlementService.settleXocDiaRoundAsync(round.getId(), result)
                    .get(gameProperties.round().settle().plus(SETTLE_WAIT_BUFFER).toMillis(),
                            TimeUnit.MILLISECONDS);
        } catch (TimeoutException settleTooSlow) {
            log.error("xocdia settlement timeout roundId={} — reconciliation job sẽ cảnh báo", round.getId());
        } catch (java.util.concurrent.ExecutionException settleFailed) {
            log.error("xocdia settlement failed roundId={}", round.getId(), settleFailed);
        }
    }

    private void publishXocDiaResult(GameRound round, XocDiaEngine.RoundResult result) {
        Instant now = Instant.now();
        String coinsStr = result.formattedCoins();
        Integer updated = txWrite.execute(status -> roundRepository.markXocDiaResult(
                round.getId(), round.getCreatedAt(),
                coinsStr, result.getRedCount(),
                result.getSeed(), result.getSeedHash(),
                now, RoundStatus.OPEN, now));
        if (updated == null || updated == 0) {
            throw new RoundAborted("xocdia result lost OPEN claim");
        }
        round.setXocDiaCoins(coinsStr);
        round.setXocDiaRedCount(result.getRedCount());
        round.setXocDiaSeed(result.getSeed());
        round.setXocDiaSeedHash(result.getSeedHash());
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

    // ===== jackpot Tu Quy (admin chi dinh) =====

    private XocDiaJackpotService.JackpotDecision decideJackpot() {
        try {
            long pool = parseLongSetting(com.rwg.settings.domain.AppSetting.XOC_DIA_JACKPOT_POOL, 295320203L);
            long threshold = parseLongSetting(com.rwg.settings.domain.AppSetting.XOC_DIA_JACKPOT_THRESHOLD, 200000000L);
            String triggerMode = readSetting(com.rwg.settings.domain.AppSetting.XOC_DIA_JACKPOT_TRIGGER_MODE, "AUTO");
            double autoRate = parseDoubleSetting(com.rwg.settings.domain.AppSetting.XOC_DIA_JACKPOT_AUTO_RATE, 0.001);
            String targetDoor = readSetting(com.rwg.settings.domain.AppSetting.XOC_DIA_JACKPOT_TARGET_DOOR, "RANDOM");
            String winMode = readSetting(com.rwg.settings.domain.AppSetting.XOC_DIA_JACKPOT_WIN_MODE, "FULL_POOL");
            String winValue = readSetting(com.rwg.settings.domain.AppSetting.XOC_DIA_JACKPOT_WIN_VALUE, "100");
            java.math.BigDecimal winVal;
            try {
                winVal = new java.math.BigDecimal(winValue.trim());
            } catch (Exception e) {
                winVal = new java.math.BigDecimal("100");
            }
            var cfg = new XocDiaJackpotService.JackpotConfig(pool, threshold, triggerMode, autoRate,
                    targetDoor, winMode, winVal);
            return XocDiaJackpotService.decide(cfg, secureRandom);
        } catch (Exception e) {
            log.warn("decideJackpot failed, coi nhu khong no", e);
            return XocDiaJackpotService.NO_HIT;
        }
    }

    private XocDiaEngine.RoundResult forcedXocDiaResult(int redCount) {
        java.util.List<Integer> coins = new java.util.ArrayList<>(java.util.List.of(0, 0, 0, 0));
        for (int i = 0; i < redCount && i < 4; i++) {
            coins.set(i, 1);
        }
        java.util.Collections.shuffle(coins, secureRandom);
        byte[] seedBytes = new byte[16];
        secureRandom.nextBytes(seedBytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : seedBytes) {
            sb.append(String.format("%02x", b));
        }
        return XocDiaEngine.fromCoins(java.util.List.copyOf(coins), "jackpot-" + sb);
    }

    private void handleJackpotWin(GameRound round, XocDiaJackpotService.JackpotDecision jp) {
        try {
            String winnerMode = readSetting(com.rwg.settings.domain.AppSetting.XOC_DIA_JACKPOT_WINNER_MODE, "ALL_BETTOR_SHARE");
            String targetUser = readSetting(com.rwg.settings.domain.AppSetting.XOC_DIA_JACKPOT_TARGET_USER, "");
            boolean requireBet = Boolean.parseBoolean(readSetting(com.rwg.settings.domain.AppSetting.XOC_DIA_JACKPOT_REQUIRE_BET, "false"));
            java.math.BigDecimal total = jp.winAmount() == null ? java.math.BigDecimal.ZERO : jp.winAmount();
            if (total.compareTo(java.math.BigDecimal.ZERO) <= 0) {
                resetForceMode();
                return;
            }
            java.util.List<com.rwg.game.domain.Bet> bets = betRepository.findByRoundId(round.getId());
            java.util.Map<java.util.UUID, java.math.BigDecimal> stakeByUser = new java.util.LinkedHashMap<>();
            for (com.rwg.game.domain.Bet b : bets) {
                stakeByUser.merge(b.getUserId(), b.getStake(), java.math.BigDecimal::add);
            }
            java.util.Map<java.util.UUID, java.math.BigDecimal> payouts = new java.util.LinkedHashMap<>();
            if ("SPECIFIC_USER".equalsIgnoreCase(winnerMode) && !targetUser.isBlank()) {
                var user = userRepository.findByUsername(targetUser.trim()).orElse(null);
                if (user != null && (!requireBet || stakeByUser.containsKey(user.getId()))) {
                    payouts.put(user.getId(), total);
                } else if (!stakeByUser.isEmpty()) {
                    payouts.put(randomBettor(stakeByUser), total);
                }
            } else if ("ALL_BETTOR_SHARE".equalsIgnoreCase(winnerMode) && !stakeByUser.isEmpty()) {
                java.math.BigDecimal sumStake = stakeByUser.values().stream()
                        .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
                java.math.BigDecimal assigned = java.math.BigDecimal.ZERO;
                java.util.List<java.util.UUID> users = new java.util.ArrayList<>(stakeByUser.keySet());
                for (int i = 0; i < users.size(); i++) {
                    java.util.UUID u = users.get(i);
                    java.math.BigDecimal share;
                    if (i == users.size() - 1) {
                        share = total.subtract(assigned);
                    } else {
                        share = sumStake.compareTo(java.math.BigDecimal.ZERO) > 0
                                ? total.multiply(stakeByUser.get(u))
                                        .divide(sumStake, 0, java.math.RoundingMode.DOWN)
                                : java.math.BigDecimal.ZERO;
                        assigned = assigned.add(share);
                    }
                    if (share.compareTo(java.math.BigDecimal.ZERO) > 0) {
                        payouts.put(u, share);
                    }
                }
            } else if (!stakeByUser.isEmpty()) {
                payouts.put(randomBettor(stakeByUser), total);
            }
            if (payouts.isEmpty()) {
                resetForceMode();
                return;
            }
            String winnerName = targetUser;
            java.math.BigDecimal paidTotal = java.math.BigDecimal.ZERO;
            for (var e : payouts.entrySet()) {
                com.rwg.common.money.Money bal = settlementService.creditJackpot(round.getId(), e.getKey(), e.getValue());
                paidTotal = paidTotal.add(e.getValue());
                if (winnerName == null || winnerName.isBlank()) {
                    winnerName = userRepository.findById(e.getKey())
                            .map(u -> u.getUsername()).orElse(e.getKey().toString().substring(0, 8));
                }
                log.info("jackpot hit roundId={} door={} winner={} amount={} balanceAfter={}",
                        round.getId(), jp.door(), e.getKey(), e.getValue(), bal.amount());
            }
            if (payouts.size() == 1) {
                var onlyId = payouts.keySet().iterator().next();
                var onlyUser = userRepository.findById(onlyId).orElse(null);
                if (onlyUser != null) {
                    winnerName = onlyUser.getUsername();
                }
            }
            updatePoolAfterWin(paidTotal, winnerName, round.getRoundSeq());
            resetForceMode();
            broadcaster.broadcastJackpot(round, jp.door(), jp.diceQuad(),
                    winnerName == null ? "" : winnerName, paidTotal.toPlainString());
        } catch (Exception e) {
            log.error("handleJackpotWin failed roundId={}", round.getId(), e);
        }
    }

    private java.util.UUID randomBettor(java.util.Map<java.util.UUID, java.math.BigDecimal> stakeByUser) {
        java.util.List<java.util.UUID> users = new java.util.ArrayList<>(stakeByUser.keySet());
        return users.get(secureRandom.nextInt(users.size()));
    }

    private void updatePoolAfterWin(java.math.BigDecimal paid, String winnerName, long roundSeq) {
        try {
            long pool = parseLongSetting(com.rwg.settings.domain.AppSetting.XOC_DIA_JACKPOT_POOL, 295320203L);
            long minPool = parseLongSetting(com.rwg.settings.domain.AppSetting.XOC_DIA_JACKPOT_MIN_POOL, 100000000L);
            long next = Math.max(minPool, pool - paid.longValue());
            txWrite.execute(s -> {
                settingRepository.findById(com.rwg.settings.domain.AppSetting.XOC_DIA_JACKPOT_POOL)
                        .ifPresent(v -> v.update(String.valueOf(next), "system"));
                settingRepository.findById(com.rwg.settings.domain.AppSetting.XOC_DIA_JACKPOT_LAST_WON)
                        .ifPresent(v -> v.update((winnerName == null ? "" : winnerName)
                                + " +" + paid.toPlainString() + " (van " + roundSeq + ")", "system"));
                return null;
            });
        } catch (Exception e) {
            log.warn("updatePoolAfterWin failed", e);
        }
    }

    private void resetForceMode() {
        try {
            String mode = readSetting(com.rwg.settings.domain.AppSetting.XOC_DIA_JACKPOT_TRIGGER_MODE, "AUTO");
            if ("FORCE_NEXT_ROUND".equalsIgnoreCase(mode)) {
                txWrite.execute(s -> {
                    settingRepository.findById(com.rwg.settings.domain.AppSetting.XOC_DIA_JACKPOT_TRIGGER_MODE)
                            .ifPresent(v -> v.update("AUTO", "system"));
                    return null;
                });
            }
        } catch (Exception e) {
            log.warn("resetForceMode failed", e);
        }
    }

    private void resetForceResult() {
        try {
            txWrite.execute(s -> {
                settingRepository.findById(com.rwg.settings.domain.AppSetting.XOC_DIA_FORCE_RESULT)
                        .ifPresent(v -> v.update("AUTO", "system"));
                return null;
            });
        } catch (Exception e) {
            log.warn("resetForceResult failed", e);
        }
    }

    private String readSetting(String key, String def) {
        try {
            return settingRepository.findById(key).map(v -> v.getSettingValue()).orElse(def);
        } catch (Exception e) {
            return def;
        }
    }

    private long parseLongSetting(String key, long def) {
        String raw = readSetting(key, String.valueOf(def));
        try {
            String digits = raw.replaceAll("[^0-9]", "");
            return digits.isEmpty() ? def : Long.parseLong(digits);
        } catch (Exception e) {
            return def;
        }
    }

    private double parseDoubleSetting(String key, double def) {
        String raw = readSetting(key, String.valueOf(def));
        try {
            String clean = raw.replaceAll("[^0-9.]", "");
            return clean.isEmpty() ? def : Double.parseDouble(clean);
        } catch (Exception e) {
            return def;
        }
    }
}
