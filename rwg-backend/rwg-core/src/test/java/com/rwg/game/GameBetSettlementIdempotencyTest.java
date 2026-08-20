package com.rwg.game;

import com.rwg.common.money.Money;
import com.rwg.game.domain.Bet;
import com.rwg.game.domain.BetStatus;
import com.rwg.game.domain.GameRound;
import com.rwg.game.domain.GameTable;
import com.rwg.game.domain.RoundStatus;
import com.rwg.game.dto.BetRequest;
import com.rwg.game.dto.BetResponse;
import com.rwg.game.repository.BetRepository;
import com.rwg.game.repository.GameRoundRepository;
import com.rwg.game.repository.GameTableRepository;
import com.rwg.game.service.BetService;
import com.rwg.game.service.SettlementService;
import com.rwg.identity.repository.UserRepository;
import com.rwg.wallet.domain.WalletRefType;
import com.rwg.wallet.repository.WalletTransactionRepository;
import com.rwg.wallet.service.WalletService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Idempotency BET/WIN (Phase c) — reuse guard pattern của WalletService:
 * - BET key "BET:{roundId}:{userId}:{seq}": gọi 2 lần (kể cả SONG SONG) chỉ trừ ví
 *   và ghi sổ 1 lần.
 * - WIN key "WIN:{roundId}:{userId}": settle 2 lần (kể cả SONG SONG) chỉ credit 1 lần;
 *   claim nguyên tử OPEN -> SETTLED chặn settle trùng.
 * Test dùng bàn RIÊNG tạo trong test để không dính vòng lặp của RoundScheduler.
 */
@SpringBootTest
@ActiveProfiles("test")
class GameBetSettlementIdempotencyTest {

    @Autowired
    BetService betService;

    @Autowired
    SettlementService settlementService;

    @Autowired
    WalletService walletService;

    @Autowired
    GameTableRepository tableRepository;

    @Autowired
    GameRoundRepository roundRepository;

    @Autowired
    BetRepository betRepository;

    @Autowired
    WalletTransactionRepository transactionRepository;

    @Autowired
    UserRepository userRepository;

    /** Bàn test riêng (scheduler KHÔNG chạy vòng lặp cho bàn tạo sau khi khởi động). */
    private GameTable newTable() {
        return tableRepository.save(new GameTable(UUID.randomUUID(), "ROULETTE",
                "{\"en\":\"Test Roulette\",\"vi\":\"Test Roulette\",\"zh\":\"测试轮盘\",\"ja\":\"テストルーレット\"}",
                BigDecimal.ONE, new BigDecimal("10000")));
    }

    private GameRound newRound(UUID tableId) {
        return roundRepository.save(new GameRound(tableId, 1));
    }

    private UUID fundedUser(String seed, String amount) {
        String username = seed + UUID.randomUUID().toString().substring(0, 8);
        var user = userRepository.save(new com.rwg.identity.domain.User(
                username, username + "@example.com", "not-used-hash"));
        walletService.credit(user.getId(), Money.of(amount), WalletRefType.DEPOSIT,
                "SEED:" + user.getId(), "SEED:" + user.getId());
        return user.getId();
    }

    @Test
    void sameBetSeqBooksOnlyOnce() {
        GameTable table = newTable();
        GameRound round = newRound(table.getId());
        UUID userId = fundedUser("betidem", "500");
        BetRequest request = new BetRequest("RED", "", "10", 7);

        BetResponse first = betService.placeBet(table.getId(), userId, request);
        BetResponse second = betService.placeBet(table.getId(), userId, request);

        // Cùng bet id, ví trừ ĐÚNG 1 lần ($500 - $10).
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(walletService.getBalance(userId).amount()).isEqualByComparingTo("490");
        String betKey = "BET:" + round.getId() + ":" + userId + ":7";
        assertThat(transactionRepository.countByIdempotencyKey(betKey)).isEqualTo(1);
        assertThat(betRepository.countByRoundId(round.getId())).isEqualTo(1);
    }

    @Test
    void concurrentBetsWithSameSeqDebitOnlyOnce() throws Exception {
        GameTable table = newTable();
        newRound(table.getId());
        UUID userId = fundedUser("betrace", "500");
        BetRequest request = new BetRequest("BLACK", "", "20", 1);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger accepted = new AtomicInteger();
        for (int i = 0; i < 2; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    betService.placeBet(table.getId(), userId, request);
                    accepted.incrementAndGet();
                } catch (Exception ignored) {
                    // thua race hoặc lỗi — assert dưới kiểm tra tiền
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        done.await();
        pool.shutdown();

        assertThat(accepted.get()).isEqualTo(2); // cả 2 đều nhận kết quả idempotent
        assertThat(walletService.getBalance(userId).amount()).isEqualByComparingTo("480"); // trừ 1 lần
        assertThat(betRepository.countByRoundId(newRoundQuery(table.getId()))).isEqualTo(1);
    }

    private UUID newRoundQuery(UUID tableId) {
        return roundRepository.findFirstByTableIdAndStatusOrderByRoundSeqDesc(tableId, RoundStatus.OPEN)
                .orElseThrow().getId();
    }

    @Test
    void settleRoundTwiceCreditsWinOnlyOnce() {
        GameTable table = newTable();
        GameRound round = newRound(table.getId());
        UUID userId = fundedUser("winidem", "100");
        // Cược STRAIGHT 17, $10.
        betService.placeBet(table.getId(), userId, new BetRequest("STRAIGHT", "17", "10", 1));

        // Số trúng 17 -> payout stake-inclusive = 10 + 10×35 = 360.
        boolean firstSettle = settlementService.settleRound(round.getId(), 17);
        boolean secondSettle = settlementService.settleRound(round.getId(), 17);

        assertThat(firstSettle).isTrue();
        assertThat(secondSettle).as("settle lần hai phải thua claim OPEN->SETTLED").isFalse();
        assertThat(walletService.getBalance(userId).amount()).isEqualByComparingTo("450"); // 100 - 10 + 360
        String winKey = "WIN:" + round.getId() + ":" + userId;
        assertThat(transactionRepository.countByIdempotencyKey(winKey)).isEqualTo(1);

        Bet bet = betRepository.findByRoundId(round.getId()).get(0);
        assertThat(bet.getStatus()).isEqualTo(BetStatus.SETTLED);
        assertThat(bet.getPayout()).isEqualByComparingTo("360");
        GameRound settled = roundRepository.findFirstById(round.getId()).orElseThrow();
        assertThat(settled.getStatus()).isEqualTo(RoundStatus.SETTLED);
        assertThat(settled.getWinningNumber()).isEqualTo(17);
    }

    @Test
    void concurrentSettlesCreditWinOnlyOnce() throws Exception {
        GameTable table = newTable();
        GameRound round = newRound(table.getId());
        UUID userId = fundedUser("winrace", "100");
        betService.placeBet(table.getId(), userId, new BetRequest("STRAIGHT", "5", "10", 1));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger winners = new AtomicInteger();
        for (int i = 0; i < 2; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    if (settlementService.settleRound(round.getId(), 5)) {
                        winners.incrementAndGet();
                    }
                } catch (Exception ignored) {
                    // thua race claim
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        done.await();
        pool.shutdown();

        assertThat(winners.get()).isEqualTo(1); // claim nguyên tử: chỉ 1 settle thắng
        assertThat(walletService.getBalance(userId).amount()).isEqualByComparingTo("450"); // credit đúng 1 lần
        assertThat(transactionRepository.countByIdempotencyKey("WIN:" + round.getId() + ":" + userId))
                .isEqualTo(1);
    }

    @Test
    void losingBetSettlesWithoutCredit() {
        GameTable table = newTable();
        GameRound round = newRound(table.getId());
        UUID userId = fundedUser("lose", "100");
        betService.placeBet(table.getId(), userId, new BetRequest("STRAIGHT", "17", "10", 1));

        // Số ra 5 -> thua: SETTLED payout 0, KHÔNG có dòng credit WIN.
        assertThat(settlementService.settleRound(round.getId(), 5)).isTrue();
        assertThat(walletService.getBalance(userId).amount()).isEqualByComparingTo("90");
        assertThat(transactionRepository.countByIdempotencyKey("WIN:" + round.getId() + ":" + userId))
                .isZero();
        Bet bet = betRepository.findByRoundId(round.getId()).get(0);
        assertThat(bet.getStatus()).isEqualTo(BetStatus.SETTLED);
        assertThat(bet.getPayout()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
