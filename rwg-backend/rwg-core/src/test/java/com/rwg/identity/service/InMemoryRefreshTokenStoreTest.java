package com.rwg.identity.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test fallback refresh-token store in-memory (dev không có Redis):
 * consume nguyên tử (remove-and-return), phát hiện reuse -> thu hồi family.
 */
class InMemoryRefreshTokenStoreTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    private final InMemoryRefreshTokenStore store = new InMemoryRefreshTokenStore(clock);

    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Test
    void consumeActiveTokenReturnsOkWithUserIdAndFamily() {
        UUID userId = UUID.randomUUID();
        store.save("token-1", userId, "family-1", Duration.ofDays(30));

        RefreshTokenStore.ConsumeResult result = store.consume("token-1");

        assertThat(result.status()).isEqualTo(RefreshTokenStore.ConsumeStatus.OK);
        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.familyId()).isEqualTo("family-1");
    }

    @Test
    void consumeUnknownTokenReturnsInvalid() {
        assertThat(store.consume("khong-ton-tai").status())
                .isEqualTo(RefreshTokenStore.ConsumeStatus.INVALID);
    }

    @Test
    void consumeExpiredTokenReturnsInvalid() {
        UUID userId = UUID.randomUUID();
        store.save("token-1", userId, "family-1", Duration.ofDays(30));
        clock.advance(Duration.ofDays(31));
        assertThat(store.consume("token-1").status())
                .isEqualTo(RefreshTokenStore.ConsumeStatus.INVALID);
    }

    @Test
    void tokenConsumableWithinTtl() {
        UUID userId = UUID.randomUUID();
        store.save("token-1", userId, "family-1", Duration.ofDays(30));
        clock.advance(Duration.ofDays(29).plusHours(23));
        assertThat(store.consume("token-1").status())
                .isEqualTo(RefreshTokenStore.ConsumeStatus.OK);
    }

    @Test
    void reuseRotatedTokenReturnsReuseAndRevokesFamily() {
        UUID userId = UUID.randomUUID();
        store.save("token-1", userId, "family-1", Duration.ofDays(30));

        // Rotation: consume token-1 (OK) -> phát hành token-2 cùng family.
        assertThat(store.consume("token-1").status()).isEqualTo(RefreshTokenStore.ConsumeStatus.OK);
        store.save("token-2", userId, "family-1", Duration.ofDays(30));

        // Gửi lại token-1 (ĐÃ tiêu thụ) -> REUSE, cả family bị thu hồi.
        assertThat(store.consume("token-1").status()).isEqualTo(RefreshTokenStore.ConsumeStatus.REUSE);

        // Token mới nhất của family cũng không còn dùng được.
        assertThat(store.consume("token-2").status())
                .isEqualTo(RefreshTokenStore.ConsumeStatus.INVALID);
    }

    @Test
    void reuseDoesNotAffectOtherFamily() {
        UUID userId = UUID.randomUUID();
        store.save("token-a1", userId, "family-a", Duration.ofDays(30));
        store.save("token-b1", userId, "family-b", Duration.ofDays(30));

        assertThat(store.consume("token-a1").status()).isEqualTo(RefreshTokenStore.ConsumeStatus.OK);
        store.save("token-a2", userId, "family-a", Duration.ofDays(30));
        assertThat(store.consume("token-a1").status()).isEqualTo(RefreshTokenStore.ConsumeStatus.REUSE);

        // Family B không liên quan vẫn hoạt động.
        assertThat(store.consume("token-b1").status()).isEqualTo(RefreshTokenStore.ConsumeStatus.OK);
    }

    @Test
    void revokeFamilyDeletesCurrentToken() {
        UUID userId = UUID.randomUUID();
        store.save("token-1", userId, "family-1", Duration.ofDays(30));
        store.revokeFamily("family-1");
        assertThat(store.consume("token-1").status())
                .isEqualTo(RefreshTokenStore.ConsumeStatus.INVALID);
    }

    @Test
    void concurrentConsumeOnlyOneRequestWins() throws Exception {
        // Mô phỏng race rotation: N request song song cùng 1 refresh token
        // phải có ĐÚNG 1 request nhận OK (remove-and-return nguyên tử).
        int threads = 16;
        UUID userId = UUID.randomUUID();
        store.save("token-race", userId, "family-race", Duration.ofDays(30));

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<RefreshTokenStore.ConsumeResult>> futures = new java.util.ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return store.consume("token-race");
                }));
            }
            start.countDown();
            long okCount = futures.stream()
                    .map(f -> {
                        try {
                            return f.get();
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .filter(r -> r.status() == RefreshTokenStore.ConsumeStatus.OK)
                    .count();
            assertThat(okCount).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }
}
