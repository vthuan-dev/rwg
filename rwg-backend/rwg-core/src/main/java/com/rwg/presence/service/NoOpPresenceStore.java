package com.rwg.presence.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Bản không làm gì, dùng khi rwg.redis.enabled=false.
 *
 * VÌ SAO KHÔNG PHẢI BẢN LƯU TRONG BỘ NHỚ như {@code InMemorySessionRevocationStore}: mốc
 * hoạt động được GHI ở app người chơi và ĐỌC ở app quản trị — hai JVM riêng. Một bản lưu
 * trong bộ nhớ sẽ luôn trả về rỗng ở phía đọc, tức là cùng kết quả với bản này nhưng kèm
 * ảo giác rằng nó có tác dụng.
 *
 * Hệ quả khi tắt Redis: mọi người chơi hiện offline, và cột đó lùi về dùng
 * {@code last_login_at} vốn đã có. Không có gì bị hỏng — đúng như thiết kế, trạng thái có
 * mặt là dữ liệu trang trí.
 *
 * Bean này BẮT BUỘC phải tồn tại: bộ test đặt {@code rwg.redis.enabled: false}, và không
 * có bản thay thế thì context không khởi động được vì thiếu bean {@link PresenceStore}.
 */
@Component
@ConditionalOnProperty(name = "rwg.redis.enabled", havingValue = "false")
public class NoOpPresenceStore implements PresenceStore {

    @Override
    public void touch(UUID userId) {
        // Không làm gì.
    }

    @Override
    public Map<UUID, Instant> lastSeen(Collection<UUID> userIds) {
        return Map.of();
    }
}
