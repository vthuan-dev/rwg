package com.rwg.identity.service;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;

import java.time.Duration;

/**
 * Abstraction lưu bucket rate-limit + lock marker.
 * - Prod/docker: {@link RedisRateLimitStore} (Bucket4j + Redis, share state đa instance).
 * - Dev không có Redis: {@link InMemoryRateLimitStore} (chỉ 1 instance).
 *
 * Lock marker TÁCH RIÊNG khỏi bucket: khi chạm ngưỡng khóa, marker được ghi với
 * TTL đúng bằng cửa sổ khóa (vd 15 phút) — không dựa thời điểm refill bucket
 * (bucket refill theo cửa sổ cố định từ lúc tạo nên lock có thể ngắn hơn 15 phút).
 */
public interface RateLimitStore {

    /** Lấy (hoặc tạo mới) bucket cho key với cấu hình capacity/refill cho trước. */
    Bucket bucketFor(String key, BucketConfiguration configuration);

    /** Xóa bucket — dùng khi đăng nhập thành công để reset bộ đếm sai. */
    void reset(String key);

    /**
     * Ghi lock marker với TTL nếu CHƯA có (không gia hạn marker đang tồn tại).
     *
     * @return true nếu marker được tạo mới, false nếu đã khóa trước đó.
     */
    boolean lock(String key, Duration ttl);

    /** Số giây còn lại của lock marker; 0 nếu không bị khóa. */
    long lockRemainingSeconds(String key);

    /** Xóa lock marker (đăng nhập thành công). */
    void unlock(String key);
}
