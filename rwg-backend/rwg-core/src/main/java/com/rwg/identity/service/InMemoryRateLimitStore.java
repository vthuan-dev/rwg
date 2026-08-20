package com.rwg.identity.service;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Fallback in-memory khi dev KHÔNG có Redis (rwg.redis.enabled=false).
 * HẠN CHẾ: bộ đếm không chia sẻ giữa nhiều instance — chỉ dùng dev 1 instance.
 */
@Component
@ConditionalOnProperty(name = "rwg.redis.enabled", havingValue = "false")
public class InMemoryRateLimitStore implements RateLimitStore {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    /** Lock marker: key -> thời điểm hết khóa. */
    private final Map<String, Instant> lockUntil = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryRateLimitStore() {
        this(Clock.systemUTC());
    }

    public InMemoryRateLimitStore(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Bucket bucketFor(String key, BucketConfiguration configuration) {
        return buckets.computeIfAbsent(key, k ->
                Bucket.builder().addLimit(configuration.getBandwidths()[0]).build());
    }

    @Override
    public void reset(String key) {
        buckets.remove(key);
    }

    @Override
    public boolean lock(String key, Duration ttl) {
        Instant now = Instant.now(clock);
        AtomicBoolean created = new AtomicBoolean(false);
        // Nguyên tử: giữ marker còn hiệu lực; hết hạn/chưa có -> tạo mới (không gia hạn).
        lockUntil.compute(key, (k, until) -> {
            if (until != null && until.isAfter(now)) {
                return until;
            }
            created.set(true);
            return now.plus(ttl);
        });
        return created.get();
    }

    @Override
    public long lockRemainingSeconds(String key) {
        Instant until = lockUntil.get(key);
        if (until == null) {
            return 0;
        }
        long seconds = Duration.between(Instant.now(clock), until).toSeconds();
        if (seconds <= 0) {
            lockUntil.remove(key);
            return 0;
        }
        return seconds;
    }

    @Override
    public void unlock(String key) {
        lockUntil.remove(key);
    }
}
