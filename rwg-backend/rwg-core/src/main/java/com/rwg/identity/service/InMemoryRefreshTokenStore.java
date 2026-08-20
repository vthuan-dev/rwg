package com.rwg.identity.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fallback in-memory khi dev KHÔNG có Redis (rwg.redis.enabled=false).
 * HẠN CHẾ: state không chia sẻ giữa nhiều instance — chỉ dùng dev 1 instance.
 *
 * Consume dùng {@code Map.remove(key)} (remove-and-return nguyên tử) nên 2 request
 * song song cùng 1 token chỉ có ĐÚNG 1 request nhận được token mới.
 */
@Component
@ConditionalOnProperty(name = "rwg.redis.enabled", havingValue = "false")
public class InMemoryRefreshTokenStore implements RefreshTokenStore {

    private record Entry(UUID userId, String familyId, Instant expiresAt) {
    }

    private record UsedEntry(String familyId, Instant expiresAt) {
    }

    /** Token đang hoạt động: tokenId -> Entry. */
    private final Map<String, Entry> active = new ConcurrentHashMap<>();
    /** Token ĐÃ tiêu thụ (cho phát hiện reuse): tokenId -> UsedEntry(familyId). */
    private final Map<String, UsedEntry> used = new ConcurrentHashMap<>();
    /** Token hoạt động hiện tại của mỗi family: familyId -> tokenId. */
    private final Map<String, String> familyCurrent = new ConcurrentHashMap<>();

    private final Clock clock;

    public InMemoryRefreshTokenStore() {
        this(Clock.systemUTC());
    }

    public InMemoryRefreshTokenStore(Clock clock) {
        this.clock = clock;
    }

    @Override
    public synchronized void save(String tokenId, UUID userId, String familyId, Duration ttl) {
        Instant expiresAt = Instant.now(clock).plus(ttl);
        active.put(tokenId, new Entry(userId, familyId, expiresAt));
        familyCurrent.put(familyId, tokenId);
    }

    @Override
    public synchronized ConsumeResult consume(String tokenId) {
        // remove-and-return nguyên tử: chỉ 1 caller nhận được entry.
        Entry entry = active.remove(tokenId);
        if (entry != null) {
            if (entry.expiresAt().isBefore(Instant.now(clock))) {
                return ConsumeResult.invalid(); // đã hết hạn
            }
            used.put(tokenId, new UsedEntry(entry.familyId(), entry.expiresAt()));
            return ConsumeResult.ok(entry.userId(), entry.familyId());
        }
        UsedEntry usedEntry = used.get(tokenId);
        if (usedEntry != null) {
            if (usedEntry.expiresAt().isBefore(Instant.now(clock))) {
                used.remove(tokenId);
                return ConsumeResult.invalid();
            }
            // REUSE: token đã bị tiêu thụ trước đó -> thu hồi cả family.
            revokeFamilyInternal(usedEntry.familyId());
            return ConsumeResult.reuse();
        }
        return ConsumeResult.invalid();
    }

    @Override
    public synchronized void revokeFamily(String familyId) {
        revokeFamilyInternal(familyId);
    }

    @Override
    public synchronized void revokeAllForUser(UUID userId) {
        // Thu hồi MỌI token hoạt động của user (mọi family) — vd sau khi đổi mật khẩu.
        active.entrySet().removeIf(e -> {
            if (e.getValue().userId().equals(userId)) {
                familyCurrent.remove(e.getValue().familyId());
                return true;
            }
            return false;
        });
    }

    private void revokeFamilyInternal(String familyId) {
        String currentToken = familyCurrent.remove(familyId);
        if (currentToken != null) {
            active.remove(currentToken);
        }
    }
}
