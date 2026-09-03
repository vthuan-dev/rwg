package com.rwg.presence.service;

import com.rwg.config.PresenceProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mốc hoạt động cuối lưu trong Redis. Kích hoạt khi rwg.redis.enabled=true (mặc định).
 *
 * PHẢI dùng Redis chứ không phải bộ nhớ tiến trình: người chơi gửi request vào app người
 * chơi (cổng 8080) còn bảng danh sách được vẽ bởi app quản trị (cổng 8081) — hai JVM
 * riêng biệt. Mốc ghi trong bộ nhớ của app người chơi thì app quản trị không đọc được.
 *
 * Đây cũng là lý do KHÔNG thể dùng {@code SimpUserRegistry} của app quản trị để trả lời
 * câu hỏi này: {@code WebSocketProperties.audience} chặn token PLAYER mở phiên STOMP trên
 * broker quản trị, nên sổ đăng ký ở đó không bao giờ chứa một người chơi nào.
 *
 * Giá trị lưu là epoch milli dạng chuỗi, theo đúng cách
 * {@code RedisSessionRevocationStore} đã làm: {@link StringRedisTemplate} là bean có sẵn
 * của dự án, và một con số thì đọc được trực tiếp bằng redis-cli khi cần chẩn đoán.
 */
@Component
@ConditionalOnProperty(name = "rwg.redis.enabled", havingValue = "true", matchIfMissing = true)
public class RedisPresenceStore implements PresenceStore {

    private static final Logger log = LoggerFactory.getLogger(RedisPresenceStore.class);

    private static final String KEY_PREFIX = "rwg:presence:";

    private final StringRedisTemplate redis;
    private final PresenceProperties properties;

    public RedisPresenceStore(StringRedisTemplate redis, PresenceProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    @Override
    public void touch(UUID userId) {
        try {
            // Ghi kèm TTL trong MỘT lệnh, không phải SET rồi EXPIRE riêng: hai lệnh thì
            // một lần mất kết nối giữa chúng để lại khoá không bao giờ hết hạn.
            redis.opsForValue().set(
                    KEY_PREFIX + userId,
                    String.valueOf(Instant.now().toEpochMilli()),
                    properties.retention());
        } catch (RuntimeException redisDown) {
            // Nuốt lỗi CÓ CHỦ Ý. Hàm này nằm trên đường đi của MỌI request người chơi;
            // để lỗi thoát ra sẽ biến một sự cố Redis thành lỗi 500 trên toàn bộ ứng dụng
            // chỉ vì một chấm màu trong khu quản trị.
            log.debug("Không ghi được mốc hoạt động cho {}: {}", userId, redisDown.getMessage());
        }
    }

    @Override
    public Map<UUID, Instant> lastSeen(Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }

        // Giữ thứ tự để ghép lại được với kết quả của multiGet — Redis trả về danh sách
        // theo đúng thứ tự khoá đã gửi, kèm null cho khoá không tồn tại.
        List<UUID> ids = new ArrayList<>(userIds);
        List<String> keys = ids.stream().map(id -> KEY_PREFIX + id).toList();

        List<String> values;
        try {
            values = redis.opsForValue().multiGet(keys);
        } catch (RuntimeException redisDown) {
            log.debug("Không đọc được mốc hoạt động: {}", redisDown.getMessage());
            return Map.of();
        }

        if (values == null) {
            return Map.of();
        }

        Map<UUID, Instant> result = new HashMap<>();
        for (int i = 0; i < ids.size() && i < values.size(); i++) {
            String raw = values.get(i);
            if (raw == null) {
                // Khoá đã hết hạn hoặc người này chưa từng hoạt động. BỎ QUA thay vì đưa
                // vào map với giá trị null: người gọi phân biệt được "chưa rõ" bằng việc
                // khoá không có mặt.
                continue;
            }
            try {
                result.put(ids.get(i), Instant.ofEpochMilli(Long.parseLong(raw)));
            } catch (NumberFormatException corrupted) {
                // Giá trị hỏng thì coi như KHÔNG có mốc. Người đó hiện offline — sai lệch
                // nhỏ hơn hẳn việc làm cả bảng danh sách trả lỗi.
                log.debug("Mốc hoạt động hỏng ở khoá {}", keys.get(i));
            }
        }
        return result;
    }
}
