package com.rwg.wallet.service;

import com.rwg.common.ApiException;
import com.rwg.common.ErrorCode;
import com.rwg.common.money.Money;
import com.rwg.identity.domain.User;
import com.rwg.identity.repository.UserRepository;
import com.rwg.wallet.domain.Wallet;
import com.rwg.wallet.domain.WalletRefType;
import com.rwg.wallet.domain.WalletTransaction;
import com.rwg.wallet.repository.WalletTransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Test lõi tiền (chặng 2 Phase b) trên H2 MODE=MySQL:
 * - idempotency: cùng idempotencyKey KHÔNG trừ/cộng 2 lần.
 * - double-spend: 2 thread song song debit KHÔNG thể làm âm số dư.
 * - tổng ledger (credit - debit) khớp balance.
 */
@SpringBootTest
@ActiveProfiles("test")
class WalletServiceTest {

    @Autowired
    WalletService walletService;

    @Autowired
    WalletTransactionRepository transactionRepository;

    @Autowired
    UserRepository userRepository;

    private User newUser(String prefix) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return userRepository.save(new User(prefix + suffix, null,
                "$2a$12$dummyhashdummyhashdummyhashdummyhashdummyhashdummyhashdu"));
    }

    @Test
    void getOrCreateWalletIsLazyAndIdempotent() {
        User user = newUser("walletlazy");
        Wallet first = walletService.getOrCreateWallet(user.getId());
        Wallet second = walletService.getOrCreateWallet(user.getId());
        assertThat(first.getId()).isEqualTo(second.getId());
        assertThat(first.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void sameKeyDoesNotBookTwice() {
        User user = newUser("walletidem");
        String creditKey = "TEST-CREDIT-" + UUID.randomUUID();
        walletService.credit(user.getId(), Money.of("50"), WalletRefType.DEPOSIT, "ref1", creditKey);
        // Gọi lại CÙNG key -> không cộng thêm.
        Money afterDupCredit = walletService.credit(user.getId(), Money.of("50"),
                WalletRefType.DEPOSIT, "ref1", creditKey);
        assertThat(afterDupCredit.amount()).isEqualByComparingTo("50");

        String debitKey = "TEST-DEBIT-" + UUID.randomUUID();
        walletService.debit(user.getId(), Money.of("30"), WalletRefType.WITHDRAWAL, "ref2", debitKey);
        // Gọi lại CÙNG key -> không trừ thêm.
        Money afterDupDebit = walletService.debit(user.getId(), Money.of("30"),
                WalletRefType.WITHDRAWAL, "ref2", debitKey);
        assertThat(afterDupDebit.amount()).isEqualByComparingTo("20");

        assertThat(walletService.getBalance(user.getId()).amount()).isEqualByComparingTo("20");
    }

    @Test
    void debitWithInsufficientFundsReturnsInsufficientBalance() {
        User user = newUser("walletpoor");
        walletService.credit(user.getId(), Money.of("10"), WalletRefType.DEPOSIT, "ref", "K-" + UUID.randomUUID());
        assertThatThrownBy(() -> walletService.debit(user.getId(), Money.of("10.00000001"),
                WalletRefType.BET, "ref", "K2-" + UUID.randomUUID()))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ErrorCode.INSUFFICIENT_BALANCE);
    }

    @Test
    void concurrentSameKeyBooksOnlyOnce() throws Exception {
        // Fix C1: 2 thread cùng idempotencyKey -> đúng 1 dòng ledger, balance đổi 1 lần.
        User user = newUser("walletidemkey");
        walletService.credit(user.getId(), Money.of("100"), WalletRefType.DEPOSIT, "seed", "SEED-" + UUID.randomUUID());

        String key = "RACE-KEY-" + UUID.randomUUID();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger success = new AtomicInteger();

        for (int i = 0; i < 2; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    walletService.credit(user.getId(), Money.of("50"),
                            WalletRefType.DEPOSIT, "race", key);
                    success.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        done.await();
        pool.shutdown();

        // Cả 2 đều "thành công" về mặt API (idempotent) nhưng ledger CHỈ 1 dòng,
        // balance cộng đúng 1 lần: 100 + 50 = 150 (KHÔNG phải 200).
        assertThat(success.get()).isEqualTo(2);
        assertThat(transactionRepository.countByIdempotencyKey(key)).isEqualTo(1);
        assertThat(walletService.getBalance(user.getId()).amount()).isEqualByComparingTo("150");
    }

    @Test
    void concurrentDebitsNeverOverdrawWallet() throws Exception {
        User user = newUser("walletspend");
        walletService.credit(user.getId(), Money.of("100"), WalletRefType.DEPOSIT, "seed", "K-" + UUID.randomUUID());

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(2);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger insufficient = new AtomicInteger();

        for (int i = 0; i < 2; i++) {
            final String key = "SPEND-" + i + "-" + UUID.randomUUID();
            pool.submit(() -> {
                try {
                    start.await();
                    walletService.debit(user.getId(), Money.of("80"), WalletRefType.BET, "bet", key);
                    success.incrementAndGet();
                } catch (ApiException e) {
                    if (e.errorCode() == ErrorCode.INSUFFICIENT_BALANCE) {
                        insufficient.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        done.await();
        pool.shutdown();

        // Chỉ ĐÚNG 1 thread trừ được tiền; số dư KHÔNG âm.
        assertThat(success.get()).isEqualTo(1);
        assertThat(insufficient.get()).isEqualTo(1);
        assertThat(walletService.getBalance(user.getId()).amount()).isEqualByComparingTo("20");
    }

    @Test
    void ledgerSumsMatchBalance() {
        User user = newUser("walletledger");
        walletService.credit(user.getId(), Money.of("100"), WalletRefType.DEPOSIT, "d1", "L1-" + UUID.randomUUID());
        walletService.debit(user.getId(), Money.of("25"), WalletRefType.BET, "b1", "L2-" + UUID.randomUUID());
        walletService.credit(user.getId(), Money.of("7.5"), WalletRefType.WIN, "w1", "L3-" + UUID.randomUUID());
        walletService.debit(user.getId(), Money.of("2.5"), WalletRefType.WITHDRAWAL, "wd1", "L4-" + UUID.randomUUID());

        Wallet wallet = walletService.getOrCreateWallet(user.getId());
        List<WalletTransaction> txs = transactionRepository
                .findByWalletId(wallet.getId(), PageRequest.of(0, 100)).getContent();

        BigDecimal sumCredit = txs.stream().map(WalletTransaction::getCredit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sumDebit = txs.stream().map(WalletTransaction::getDebit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(sumCredit.subtract(sumDebit))
                .isEqualByComparingTo(walletService.getBalance(user.getId()).amount());
        // balance = 100 - 25 + 7.5 - 2.5 = 80
        assertThat(walletService.getBalance(user.getId()).amount()).isEqualByComparingTo("80");
    }
}
