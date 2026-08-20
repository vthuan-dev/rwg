package com.rwg.identity.service;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Rate-limit lưu trong Redis qua Bucket4j ProxyManager — share state giữa nhiều instance.
 * Kích hoạt khi rwg.redis.enabled=true (mặc định, dùng cho prod/docker).
 *
 * Lock marker lưu bằng key Redis riêng với SETNX + TTL đúng cửa sổ khóa
 * (không dựa thời điểm refill bucket).
 */
@Component
@ConditionalOnProperty(name = "rwg.redis.enabled", havingValue = "true", matchIfMissing = true)
public class RedisRateLimitStore implements RateLimitStore {

    private final ProxyManager<String> proxyManager;
    private final StringRedisTemplate redis;

    public RedisRateLimitStore(ProxyManager<String> proxyManager, StringRedisTemplate redis) {
        this.proxyManager = proxyManager;
        this.redis = redis;
    }

    @Override
    public Bucket bucketFor(String key, BucketConfiguration configuration) {
        return proxyManager.builder().build(key, () -> configuration);
    }

    @Override
    public void reset(String key) {
        proxyManager.removeProxy(key);
    }

    @Override
    public boolean lock(String key, Duration ttl) {
        // SETNX + TTL: chỉ tạo khi CHƯA khóa (không gia hạn marker đang tồn tại).
        Boolean created = redis.opsForValue().setIfAbsent(key, "1", ttl);
        return Boolean.TRUE.equals(created);
    }

    @Override
    public long lockRemainingSeconds(String key) {
        Long ttl = redis.getExpire(key, TimeUnit.SECONDS);
        return ttl == null || ttl < 0 ? 0 : ttl;
    }

    @Override
    public void unlock(String key) {
        redis.delete(key);
    }
}
