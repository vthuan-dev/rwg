package com.rwg.game.service;

import com.rwg.common.money.Money;
import com.rwg.game.domain.Bet;
import com.rwg.game.domain.BetStatus;
import com.rwg.game.domain.GameRound;
import com.rwg.game.domain.RoundStatus;
import com.rwg.game.domain.BetType;
import com.rwg.game.domain.GameTable;
import com.rwg.game.repository.BetRepository;
import com.rwg.game.repository.GameRoundRepository;
import com.rwg.game.repository.GameTableRepository;
import com.rwg.wallet.domain.WalletRefType;
import com.rwg.wallet.service.WalletService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Trả thưởng / hoàn tiền Roulette (Phase c, docs/round-lifecycle.md mục 4-5).
 *
 * SETTLE (M2 stake-inclusive):
 * - Chạy ASYNC trên executor riêng NGOÀI request thread; batch: bets UPDATE qua
 *   saveAll (JDBC batch + rewriteBatchedStatements MySQL), credit mỗi user 1 lần.
 * - Payout qua {@link Money#winningPayoutAtOdds} bên trong {@link RouletteEngine#payout};
 *   thắng -> credit key "WIN:{roundId}:{userId}"; thua -> bet SETTLED payout 0, KHÔNG credit.
 * - Claim nguyên tử OPEN -> SETTLED trong CÙNG transaction với credit/update bets;
 *   crash giữa chừng -> rollback toàn bộ, round còn OPEN để retry/recovery.
 * - Sau settle gọi SPI {@link WagerSettledListener} (chưa có impl vẫn chạy).
 *
 * VOID/REFUND: claim nguyên tử OPEN -> VOIDED, hoàn tổng stake mỗi user 1 lần
 * key "REFUND:{roundId}:{userId}", bets -> VOIDED.
 */
@Service
public class SettlementService {

    private static final Logger log = LoggerFactory.getLogger(SettlementService.class);

    private final GameRoundRepository roundRepository;
    private final BetRepository betRepository;
    private final WalletService walletService;
    private final GameEventBroadcaster broadcaster;
    private final List<WagerSettledListener> wagerSettledListeners;
    private final TransactionTemplate txWrite;
    private final ExecutorService settlementExecutor;
    private final GameTableRepository tableRepository;

    public SettlementService(GameRoundRepository roundRepository,
                             BetRepository betRepository,
                             WalletService walletService,
                             GameEventBroadcaster broadcaster,
                             List<WagerSettledListener> wagerSettledListeners,
                             PlatformTransactionManager transactionManager,
                             GameTableRepository tableRepository) {
        this.roundRepository = roundRepository;
        this.betRepository = betRepository;
        this.walletService = walletService;
        this.broadcaster = broadcaster;
        this.wagerSettledListeners = wagerSettledListeners;
        this.tableRepository = tableRepository;
        this.txWrite = new TransactionTemplate(transactionManager);
        this.txWrite.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.settlementExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "game-settlement");
            t.setDaemon(true);
            return t;
        });
    }

    /** RoundScheduler gọi khi vào phase SETTLE — async, KHÔNG chặn request thread. */
    public CompletableFuture<Boolean> settleRoundAsync(UUID roundId, int winningNumber) {
        return CompletableFuture.supplyAsync(() -> settleRound(roundId, winningNumber), settlementExecutor);
    }

    public CompletableFuture<Boolean> settleBaccaratRoundAsync(UUID roundId, BaccaratEngine.RoundResult result) {
        return CompletableFuture.supplyAsync(() -> settleBaccaratRound(roundId, result), settlementExecutor);
    }

    public CompletableFuture<Boolean> settleKl28RoundAsync(UUID roundId, Kl28Engine.RoundResult result) {
        return CompletableFuture.supplyAsync(() -> settleKl28Round(roundId, result), settlementExecutor);
    }

    /**
     * Settle một vòng. Trả true nếu vòng được claim và trả thưởng xong.
     * Idempotent: guard DB "WIN:{roundId}:{userId}" + claim OPEN->SETTLED nguyên tử
     * -> gọi 2 lần chỉ ghi sổ 1.
     */
    public boolean settleRound(UUID roundId, int winningNumber) {
        Instant settledAt = Instant.now();
        SettlementOutcome outcome = txWrite.execute(status -> {
            GameRound round = roundRepository.findFirstById(roundId).orElse(null);
            if (round == null || round.getStatus() != RoundStatus.OPEN) {
                return null; // đã settle/void bởi tiến trình khác
            }
            int claimed = roundRepository.claimTransition(round.getId(), round.getCreatedAt(),
                    RoundStatus.SETTLED, winningNumber, round.getResultAt(), RoundStatus.OPEN, settledAt);
            if (claimed == 0) {
                return null; // thua race (vd vòng vừa bị void)
            }

            List<Bet> pendingBets = betRepository.findByRoundIdAndStatus(roundId, BetStatus.PENDING);
            Map<UUID, Money> stakeByUser = new LinkedHashMap<>();
            Map<UUID, Money> winByUser = new LinkedHashMap<>();
            for (Bet bet : pendingBets) {
                Money stake = Money.of(bet.getStake());
                Money payout = RouletteEngine.payout(bet.getBetType(), bet.getSelection(),
                        winningNumber, stake);
                bet.settle(payout.amount());
                stakeByUser.merge(bet.getUserId(), stake, Money::add);
                if (payout.isPositive()) {
                    winByUser.merge(bet.getUserId(), payout, Money::add);
                }
            }
            // Batch UPDATE bets (multi-row, rewriteBatchedStatements ở MySQL profiles).
            betRepository.saveAll(pendingBets);

            Map<UUID, Money> balanceAfterWin = new LinkedHashMap<>();
            for (Map.Entry<UUID, Money> entry : winByUser.entrySet()) {
                String winKey = "WIN:" + roundId + ":" + entry.getKey();
                // ref_id VARCHAR(64): chỉ gắn roundId; key WIN:* giữ đủ ở idempotency_key (128).
                Money balance = walletService.credit(entry.getKey(), entry.getValue(),
                        WalletRefType.WIN, roundId.toString(), winKey);
                balanceAfterWin.put(entry.getKey(), balance);
            }
            return new SettlementOutcome(round, stakeByUser, winByUser, balanceAfterWin, winningNumber);
        });

        if (outcome == null) {
            return false;
        }

        // Ngoài transaction: unicast kết quả + gọi SPI loyalty (KHÔNG để lỗi listener phá settle).
        outcome.winByUser().forEach((userId, payout) -> broadcaster.unicastWin(userId,
                outcome.round().getTableId().toString(), outcome.round().getId().toString(),
                outcome.winningNumber(), payout.amount(),
                outcome.balanceAfterWin().get(userId).amount()));
        outcome.stakeByUser().forEach((userId, staked) -> notifyListeners(userId,
                outcome.round().getTableId(), staked.amount(),
                outcome.winByUser().getOrDefault(userId, Money.zero()).amount()));

        long lagMs = outcome.round().getResultAt() == null ? 0
                : Duration.between(outcome.round().getResultAt(), Instant.now()).toMillis();
        log.info("settlement_lag roundId={} bets={} winners={} lagMs={}",
                roundId, outcome.stakeByUser().size(), outcome.winByUser().size(), lagMs);
        return true;
    }

    /**
     * Hủy vòng + hoàn tiền (docs/round-lifecycle.md mục 5). Trả true nếu claim
     * VOIDED thành công; false nếu vòng đã SETTLED/VOIDED (không hoàn tiền lần hai).
     */
    public boolean voidRound(UUID roundId) {
        Instant voidedAt = Instant.now();
        RefundOutcome outcome = txWrite.execute(status -> {
            GameRound round = roundRepository.findFirstById(roundId).orElse(null);
            if (round == null || round.getStatus() != RoundStatus.OPEN) {
                return null;
            }
            int claimed = roundRepository.claimTransition(round.getId(), round.getCreatedAt(),
                    RoundStatus.VOIDED, round.getWinningNumber(), round.getResultAt(),
                    RoundStatus.OPEN, voidedAt);
            if (claimed == 0) {
                return null;
            }

            List<Bet> pendingBets = betRepository.findByRoundIdAndStatus(roundId, BetStatus.PENDING);
            Map<UUID, Money> refundByUser = new LinkedHashMap<>();
            for (Bet bet : pendingBets) {
                bet.markVoided();
                refundByUser.merge(bet.getUserId(), Money.of(bet.getStake()), Money::add);
            }
            betRepository.saveAll(pendingBets);

            Map<UUID, Money> balanceAfterRefund = new LinkedHashMap<>();
            for (Map.Entry<UUID, Money> entry : refundByUser.entrySet()) {
                String refundKey = "REFUND:" + roundId + ":" + entry.getKey();
                Money balance = walletService.credit(entry.getKey(), entry.getValue(),
                        WalletRefType.REFUND, roundId.toString(), refundKey);
                balanceAfterRefund.put(entry.getKey(), balance);
            }
            return new RefundOutcome(round, balanceAfterRefund);
        });

        if (outcome == null) {
            return false;
        }
        outcome.balanceAfterRefund().forEach((userId, balance) ->
                broadcaster.unicastBalance(userId, balance.amount()));
        log.info("round voided roundId={} refundedUsers={}", roundId, outcome.balanceAfterRefund().size());
        return true;
    }

    private void notifyListeners(UUID userId, UUID tableId, BigDecimal amountBet, BigDecimal amountWon) {
        for (WagerSettledListener listener : wagerSettledListeners) {
            try {
                listener.onWagerSettled(userId, "ROULETTE:" + tableId, amountBet, amountWon);
            } catch (RuntimeException listenerFailed) {
                log.warn("WagerSettledListener {} failed userId={}", listener.getClass(), userId, listenerFailed);
            }
        }
    }

    public boolean settleBaccaratRound(UUID roundId, BaccaratEngine.RoundResult result) {
        Instant settledAt = Instant.now();
        SettlementOutcome outcome = txWrite.execute(status -> {
            GameRound round = roundRepository.findFirstById(roundId).orElse(null);
            if (round == null || round.getStatus() != RoundStatus.OPEN) {
                return null; // đã settle/void bởi tiến trình khác
            }
            int claimed = roundRepository.claimStatusTransition(round.getId(), round.getCreatedAt(),
                    RoundStatus.SETTLED, RoundStatus.OPEN, settledAt);
            if (claimed == 0) {
                return null; // thua race
            }

            List<Bet> pendingBets = betRepository.findByRoundIdAndStatus(roundId, BetStatus.PENDING);
            Map<UUID, Money> stakeByUser = new java.util.LinkedHashMap<>();
            Map<UUID, Money> winByUser = new java.util.LinkedHashMap<>();
            Map<UUID, Money> commissionByUser = new java.util.LinkedHashMap<>();

            for (Bet bet : pendingBets) {
                Money stake = Money.of(bet.getStake());
                Money payout = BaccaratEngine.payout(bet.getBetType(), result.getOutcome(),
                        result.isPlayerPair(), result.isBankerPair(), stake);

                Money commission = Money.zero();
                if (bet.getBetType() == BetType.BANKER && "BANKER".equals(result.getOutcome())) {
                    commission = stake.multiply(new BigDecimal("0.05"));
                }

                BigDecimal netPayout = payout.amount().subtract(commission.amount());
                bet.settle(netPayout);

                stakeByUser.merge(bet.getUserId(), stake, Money::add);
                if (payout.isPositive()) {
                    winByUser.merge(bet.getUserId(), payout, Money::add);
                }
                if (commission.isPositive()) {
                    commissionByUser.merge(bet.getUserId(), commission, Money::add);
                }
            }

            betRepository.saveAll(pendingBets);

            Map<UUID, Money> balanceAfterWin = new java.util.LinkedHashMap<>();
            for (Map.Entry<UUID, Money> entry : winByUser.entrySet()) {
                String winKey = "WIN:" + roundId + ":" + entry.getKey();
                Money balance = walletService.credit(entry.getKey(), entry.getValue(),
                        WalletRefType.WIN, roundId.toString(), winKey);
                balanceAfterWin.put(entry.getKey(), balance);
            }

            for (Map.Entry<UUID, Money> entry : commissionByUser.entrySet()) {
                String commKey = "COMMISSION:" + roundId + ":" + entry.getKey();
                Money balance = walletService.debit(entry.getKey(), entry.getValue(),
                        WalletRefType.COMMISSION, roundId.toString(), commKey);
                balanceAfterWin.put(entry.getKey(), balance);
            }

            return new SettlementOutcome(round, stakeByUser, winByUser, balanceAfterWin, 0);
        });

        if (outcome == null) {
            return false;
        }

        outcome.winByUser().forEach((userId, payout) -> broadcaster.unicastBaccaratWin(userId,
                outcome.round().getTableId().toString(), outcome.round().getId().toString(),
                result, payout.amount(),
                outcome.balanceAfterWin().get(userId).amount()));

        outcome.stakeByUser().forEach((userId, staked) -> notifyBaccaratListeners(userId,
                outcome.round().getTableId(), staked.amount(),
                outcome.winByUser().getOrDefault(userId, Money.zero()).amount()));

        long lagMs = outcome.round().getResultAt() == null ? 0
                : Duration.between(outcome.round().getResultAt(), Instant.now()).toMillis();
        log.info("baccarat settlement_lag roundId={} bets={} winners={} lagMs={}",
                roundId, outcome.stakeByUser().size(), outcome.winByUser().size(), lagMs);
        return true;
    }

    private void notifyBaccaratListeners(UUID userId, UUID tableId, BigDecimal amountBet, BigDecimal amountWon) {
        for (WagerSettledListener listener : wagerSettledListeners) {
            try {
                listener.onWagerSettled(userId, "BACCARAT:" + tableId, amountBet, amountWon);
            } catch (RuntimeException listenerFailed) {
                log.warn("WagerSettledListener {} failed for Baccarat userId={}", listener.getClass(), userId, listenerFailed);
            }
        }
    }

    public boolean settleKl28Round(UUID roundId, Kl28Engine.RoundResult result) {
        Instant settledAt = Instant.now();
        SettlementOutcome outcome = txWrite.execute(status -> {
            GameRound round = roundRepository.findFirstById(roundId).orElse(null);
            if (round == null || round.getStatus() != RoundStatus.OPEN) {
                return null;
            }
            int claimed = roundRepository.claimStatusTransition(round.getId(), round.getCreatedAt(),
                    RoundStatus.SETTLED, RoundStatus.OPEN, settledAt);
            if (claimed == 0) {
                return null;
            }

            List<Bet> pendingBets = betRepository.findByRoundIdAndStatus(roundId, BetStatus.PENDING);
            Map<UUID, Money> stakeByUser = new java.util.LinkedHashMap<>();
            Map<UUID, Money> winByUser = new java.util.LinkedHashMap<>();

            for (Bet bet : pendingBets) {
                Money stake = Money.of(bet.getStake());
                Money payout = Kl28Engine.payout(bet.getBetType(), bet.getSelection(), result.getSum(), stake);
                bet.settle(payout.amount());

                stakeByUser.merge(bet.getUserId(), stake, Money::add);
                if (payout.isPositive()) {
                    winByUser.merge(bet.getUserId(), payout, Money::add);
                }
            }

            betRepository.saveAll(pendingBets);

            Map<UUID, Money> balanceAfterWin = new java.util.LinkedHashMap<>();
            for (Map.Entry<UUID, Money> entry : winByUser.entrySet()) {
                String winKey = "WIN:" + roundId + ":" + entry.getKey();
                Money balance = walletService.credit(entry.getKey(), entry.getValue(),
                        WalletRefType.WIN, roundId.toString(), winKey);
                balanceAfterWin.put(entry.getKey(), balance);
            }

            return new SettlementOutcome(round, stakeByUser, winByUser, balanceAfterWin, 0);
        });

        if (outcome == null) {
            return false;
        }

        outcome.winByUser().forEach((userId, payout) -> broadcaster.unicastKl28Win(userId,
                outcome.round().getTableId().toString(), outcome.round().getId().toString(),
                result, payout.amount(),
                outcome.balanceAfterWin().get(userId).amount()));

        String gameType = tableRepository.findById(outcome.round().getTableId())
                .map(GameTable::getGameType)
                .orElse("KL28");

        outcome.stakeByUser().forEach((userId, staked) -> notifyKl28Listeners(userId,
                outcome.round().getTableId(), staked.amount(),
                outcome.winByUser().getOrDefault(userId, Money.zero()).amount(), gameType));

        long lagMs = outcome.round().getResultAt() == null ? 0
                : Duration.between(outcome.round().getResultAt(), Instant.now()).toMillis();
        log.info("kl28 settlement_lag roundId={} bets={} winners={} lagMs={}",
                roundId, outcome.stakeByUser().size(), outcome.winByUser().size(), lagMs);
        return true;
    }

    private void notifyKl28Listeners(UUID userId, UUID tableId, BigDecimal amountBet, BigDecimal amountWon, String gameType) {
        for (WagerSettledListener listener : wagerSettledListeners) {
            try {
                listener.onWagerSettled(userId, gameType + ":" + tableId, amountBet, amountWon);
            } catch (RuntimeException listenerFailed) {
                log.warn("WagerSettledListener {} failed for {} userId={}", listener.getClass(), gameType, userId, listenerFailed);
            }
        }
    }

    @PreDestroy
    void shutdown() {
        settlementExecutor.shutdown();
    }

    /** Kết quả một lần settle thành công (trong tx) để xử lý tiếp NGOÀI tx. */
    private record SettlementOutcome(GameRound round, Map<UUID, Money> stakeByUser,
                                     Map<UUID, Money> winByUser, Map<UUID, Money> balanceAfterWin,
                                     int winningNumber) {
    }

    /** Kết quả một lần void thành công (trong tx). */
    private record RefundOutcome(GameRound round, Map<UUID, Money> balanceAfterRefund) {
    }
}
