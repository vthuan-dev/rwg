package com.rwg.identity.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fallback in-memory khi dev KHÔNG có Redis (rwg.redis.enabled=false).
 *
 * HẠN CHẾ QUAN TRỌNG: mốc chỉ nằm trong bộ nhớ của MỘT tiến trình. Lệnh khóa phát ra ở app
 * quản trị sẽ không chặn được request đang đi vào app người chơi, vì đó là hai JVM khác
 * nhau. Chấp nhận được ở môi trường phát triển một tiến trình, giống hệt cách chat mất tính
 * tức thời khi tắt Redis.
 *
 * KHÔNG tự dọn theo TTL như bản Redis. Bản đồ này chỉ có nhiều nhất một ô cho mỗi người
 * dùng từng bị khóa trong lần chạy hiện tại, nên nó không phình theo thời gian như một tập
 * token; và tiến trình phát triển thì khởi động lại thường xuyên.
 */
@Component
@ConditionalOnProperty(name = "rwg.redis.enabled", havingValue = "false")
public class InMemorySessionRevocationStore implements SessionRevocationStore {

    private final Map<UUID, Instant> revokedBefore = new ConcurrentHashMap<>();

    @Override
    public void revokeBefore(UUID userId) {
        revokedBefore.put(userId, Instant.now());
    }

    @Override
    public Instant revokedBefore(UUID userId) {
        return revokedBefore.get(userId);
    }
}
