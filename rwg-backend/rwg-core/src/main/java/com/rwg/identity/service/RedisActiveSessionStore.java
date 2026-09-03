package com.rwg.identity.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * Phiên hiện hành lưu trong Redis. Kích hoạt khi rwg.redis.enabled=true (mặc định).
 *
 * PHẢI dùng Redis chứ không phải bộ nhớ tiến trình, cùng lý do như
 * {@link RedisSessionRevocationStore}: quyết định chốt phiên xảy ra ở tiến trình xử lý lời
 * gọi đăng nhập, còn việc kiểm tra xảy ra ở mọi tiến trình đang phục vụ request — kể cả
 * app quản trị khi nhân sự mở khung chat. Bản ghi trong bộ nhớ của một JVM không chặn được
 * gì ở JVM còn lại.
 *
 * KHÔNG bắt {@code RuntimeException} như {@code RedisPresenceStore} làm.
 *
 * Chỗ khác nhau nằm ở hậu quả của việc thất bại. Với trạng thái có mặt, đọc lỗi chỉ làm một
 * chấm màu hiện sai. Ở đây, đọc lỗi mà trả {@code null} nghĩa là "không có phiên nào được
 * chốt" — tức CHO QUA mọi token, và quy tắc một phiên bị vô hiệu hoá âm thầm đúng lúc hệ
 * thống đang có sự cố. Để ngoại lệ nổi lên thì bộ giải mã token thất bại và request nhận
 * 401: hướng an toàn, và ồn ào đủ để có người biết mà xử lý.
 */
@Component
@ConditionalOnProperty(name = "rwg.redis.enabled", havingValue = "true", matchIfMissing = true)
public class RedisActiveSessionStore implements ActiveSessionStore {

    private static final String KEY_PREFIX = "rwg:auth:current-session:";

    private final StringRedisTemplate redis;

    public RedisActiveSessionStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void claim(UUID userId, String sessionId, Duration ttl) {
        // Ghi giá trị và đặt TTL trong MỘT lệnh. Tách thành SET rồi EXPIRE sẽ để lại một
        // khoá không bao giờ hết hạn nếu tiến trình chết giữa hai lệnh — và một khoá vĩnh
        // viễn ở đây nghĩa là người đó bị ghim vào đúng một phiên mãi mãi.
        redis.opsForValue().set(KEY_PREFIX + userId, sessionId, ttl);
    }

    @Override
    public String current(UUID userId) {
        return redis.opsForValue().get(KEY_PREFIX + userId);
    }
}
