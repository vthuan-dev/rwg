package com.rwg.identity.service;

import com.rwg.config.SecurityProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Mốc thu hồi phiên lưu trong Redis. Kích hoạt khi rwg.redis.enabled=true (mặc định).
 *
 * PHẢI dùng Redis chứ không phải bộ nhớ tiến trình: lệnh khóa tài khoản phát ra ở app quản
 * trị (cổng 8081) còn request của người chơi đi vào app người chơi (cổng 8080) — hai JVM
 * riêng biệt. Mốc ghi trong bộ nhớ của app quản trị sẽ không chặn được gì ở app người chơi.
 *
 * Giá trị lưu là epoch milli dạng chuỗi. Không dùng kiểu tuần tự hoá nào phức tạp hơn:
 * {@link StringRedisTemplate} là bean đã có sẵn của dự án, và một con số thì đọc được trực
 * tiếp bằng redis-cli khi cần chẩn đoán.
 */
@Component
@ConditionalOnProperty(name = "rwg.redis.enabled", havingValue = "true", matchIfMissing = true)
public class RedisSessionRevocationStore implements SessionRevocationStore {

    private static final String KEY_PREFIX = "rwg:auth:revoked-before:";

    private final StringRedisTemplate redis;
    private final SecurityProperties securityProperties;

    public RedisSessionRevocationStore(StringRedisTemplate redis,
                                       SecurityProperties securityProperties) {
        this.redis = redis;
        this.securityProperties = securityProperties;
    }

    @Override
    public void revokeBefore(UUID userId) {
        Instant now = Instant.now();

        // TTL nới thêm một phút so với access-token-ttl.
        //
        // Vì sao cần biên: `iat` của token do máy phát hành đóng dấu, còn mốc này do máy
        // đang xử lý ghi. Hai máy lệch giờ vài giây là bình thường, và nếu TTL vừa khít thì
        // một token phát ở sát ranh giới có thể sống thêm đúng khoảng lệch đó sau khi mốc đã
        // biến mất. Một phút là dư cho sai lệch NTP thông thường mà không giữ khoá lâu vô ích.
        Duration ttl = securityProperties.accessTokenTtl().plusMinutes(1);

        redis.opsForValue().set(KEY_PREFIX + userId, String.valueOf(now.toEpochMilli()), ttl);
    }

    @Override
    public Instant revokedBefore(UUID userId) {
        String raw = redis.opsForValue().get(KEY_PREFIX + userId);
        if (raw == null) {
            return null;
        }
        try {
            return Instant.ofEpochMilli(Long.parseLong(raw));
        } catch (NumberFormatException corrupted) {
            // Giá trị hỏng thì coi như KHÔNG có mốc, thay vì ném lỗi.
            //
            // Ném lỗi ở đây sẽ làm MỌI request của người đó trả 500 và không có cách nào tự
            // hồi phục cho tới khi ai đó xoá khoá bằng tay. Bỏ qua thì mất một lớp chặn cho
            // đúng người đó trong tối đa 15 phút — hậu quả nhỏ hơn hẳn, và trường hợp này chỉ
            // xảy ra nếu có gì khác ghi đè lên khoá.
            return null;
        }
    }
}
