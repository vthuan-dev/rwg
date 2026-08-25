package com.rwg.game.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;

@Service
public class GameEventRelay {

    private static final Logger log = LoggerFactory.getLogger(GameEventRelay.class);

    public static final String REDIS_TOPIC_GAME_RELAY = "rwg:game:relay";

    private final String originId = UUID.randomUUID().toString();
    private final SimpMessagingTemplate messaging;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public GameEventRelay(@Autowired(required = false) SimpMessagingTemplate messaging,
                          @Autowired(required = false) StringRedisTemplate redis,
                          ObjectMapper objectMapper) {
        this.messaging = messaging;
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public String originId() {
        return originId;
    }

    public void publishWalletUpdate(UUID userId, String balance) {
        sendEnvelope(new RelayEnvelope(originId, "WALLET_UPDATE", userId.toString(), balance));
    }

    public void publishOddsUpdate(UUID userId, UUID tableId) {
        sendEnvelope(new RelayEnvelope(originId, "ODDS_UPDATE", userId.toString(), tableId.toString()));
    }

    /**
     * Báo cho mọi tab của một người rằng phiên của họ đã bị thu hồi.
     *
     * Gọi khi admin khóa/cấm/đóng tài khoản hoặc đổi quyền. Frontend nhận gói này thì xoá
     * token và về trang đăng nhập ngay, thay vì để người dùng tiếp tục bấm trên một giao diện
     * mà mọi lời gọi API đằng sau đều đã bị từ chối.
     *
     * ĐÂY LÀ LỚP TRẢI NGHIỆM, KHÔNG PHẢI LỚP BẢO VỆ. Việc chặn thật do
     * {@code RevokedSessionValidator} làm ở phía server. Ai chặn WebSocket vẫn không gọi được
     * API — họ chỉ không được đá khỏi màn hình ngay.
     *
     * `data` là chuỗi rỗng: gói này không mang thông tin gì ngoài chính sự kiện. Không gửi lý
     * do khóa xuống client — người bị khóa không cần biết, và tiết lộ ra chỉ giúp người đang
     * dò tìm ngưỡng phát hiện của hệ thống.
     */
    public void publishSessionRevoked(UUID userId) {
        sendEnvelope(new RelayEnvelope(originId, "SESSION_REVOKED", userId.toString(), ""));
    }

    private void sendEnvelope(RelayEnvelope envelope) {
        // Gửi cục bộ trước
        deliverLocally(envelope);

        // Gửi qua Redis
        if (redis != null) {
            try {
                String json = objectMapper.writeValueAsString(envelope);
                redis.convertAndSend(REDIS_TOPIC_GAME_RELAY, json);
            } catch (Exception e) {
                log.warn("Không thể publish game relay event lên Redis: {}", e.getMessage());
            }
        }
    }

    public void deliverLocally(RelayEnvelope envelope) {
        if (messaging == null) return;
        try {
            if ("WALLET_UPDATE".equals(envelope.type())) {
                messaging.convertAndSendToUser(envelope.userId(), "/queue/wallet",
                        Map.of("balance", envelope.data()));
            } else if ("ODDS_UPDATE".equals(envelope.type())) {
                messaging.convertAndSendToUser(envelope.userId(), "/queue/game/odds-updated",
                        Map.of("tableId", envelope.data()));
            } else if ("SESSION_REVOKED".equals(envelope.type())) {
                // Đích riêng `/queue/session` chứ không gộp vào `/queue/notifications`: kênh
                // thông báo hiện toast cho người dùng đọc, còn gói này là một lệnh cho client
                // thực hiện. Gộp lại thì mỗi bên nhận đều phải kiểm loại để biết nên vẽ hay
                // nên hành động.
                messaging.convertAndSendToUser(envelope.userId(), "/queue/session",
                        Map.of("type", "REVOKED"));
            }
        } catch (Exception e) {
            log.warn("Không thể gửi websocket cục bộ cho user={}: {}", envelope.userId(), e.getMessage());
        }
    }

    public static record RelayEnvelope(String originId, String type, String userId, String data) {}
}
