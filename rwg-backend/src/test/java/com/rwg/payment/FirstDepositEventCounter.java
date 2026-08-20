package com.rwg.payment;

import com.rwg.payment.service.FirstDepositEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bộ đếm test (chỉ nằm trong src/test) cho {@link FirstDepositEvent}.
 * Đăng ký {@code @TransactionalEventListener(AFTER_COMMIT)} giống listener thật —
 * chỉ đếm SAU KHI transaction nạp tiền commit, chứng minh event không phát
 * trước commit (fix review M5).
 */
@Component
public class FirstDepositEventCounter {

    private final ConcurrentMap<UUID, AtomicInteger> counts = new ConcurrentHashMap<>();

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFirstDeposit(FirstDepositEvent event) {
        counts.computeIfAbsent(event.userId(), k -> new AtomicInteger()).incrementAndGet();
    }

    public int countFor(UUID userId) {
        AtomicInteger counter = counts.get(userId);
        return counter == null ? 0 : counter.get();
    }
}
