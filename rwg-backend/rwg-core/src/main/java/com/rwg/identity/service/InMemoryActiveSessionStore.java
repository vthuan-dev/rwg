package com.rwg.identity.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fallback in-memory khi dev KHÔNG có Redis (rwg.redis.enabled=false).
 *
 * Đây là bản hiện thực THẬT, không phải no-op — khác {@code NoOpPresenceStore}. Lý do: cả
 * việc ghi và việc đọc ở đây đều xảy ra trên đường xác thực của CÙNG một tiến trình khi
 * chạy một app. Trạng thái có mặt thì ghi ở app người chơi và đọc ở app quản trị, nên bộ
 * nhớ tiến trình luôn rỗng ở phía đọc và một bản in-memory sẽ chỉ gây nhầm lẫn.
 *
 * HẠN CHẾ: chạy nhiều tiến trình (người chơi 8080 + quản trị 8081) thì mỗi bên giữ bản đồ
 * riêng. Ở môi trường phát triển một tiến trình thì đủ dùng, và bộ test chạy với cấu hình
 * này nên vẫn kiểm được đúng hành vi nghiệp vụ.
 *
 * KHÔNG tự dọn theo TTL. Bản đồ nhiều nhất một ô cho mỗi người từng đăng nhập trong lần
 * chạy hiện tại, nên không phình theo thời gian như một tập token; và tiến trình phát triển
 * khởi động lại thường xuyên. Tham số {@code ttl} bị bỏ qua có chủ ý — giữ trong chữ ký để
 * hai bản hiện thực dùng thay thế nhau được.
 */
@Component
@ConditionalOnProperty(name = "rwg.redis.enabled", havingValue = "false")
public class InMemoryActiveSessionStore implements ActiveSessionStore {

    private final Map<UUID, String> currentSession = new ConcurrentHashMap<>();

    @Override
    public void claim(UUID userId, String sessionId, Duration ttl) {
        currentSession.put(userId, sessionId);
    }

    @Override
    public String current(UUID userId) {
        return currentSession.get(userId);
    }
}
