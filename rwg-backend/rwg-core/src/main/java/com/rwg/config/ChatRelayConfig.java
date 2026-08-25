package com.rwg.config;

import com.rwg.chat.service.ChatEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

/**
 * Cầu Redis pub/sub chở sự kiện chat qua lại giữa app người chơi (8080) và app quản
 * trị (8081).
 *
 * VÌ SAO PHẢI CÓ: broker STOMP của dự án là {@code enableSimpleBroker} — nó nằm
 * TRONG BỘ NHỚ của từng JVM. Người chơi nối vào app player, nhân sự nối vào app
 * admin, nên một lần {@code convertAndSendToUser} ở app admin không bao giờ tới được
 * trình duyệt người chơi. Đây là lý do duy nhất lớp này tồn tại.
 *
 * Cách khác là chuyển sang một broker ngoài (RabbitMQ/ActiveMQ) qua
 * {@code enableStompBrokerRelay}. Bỏ vì nó thêm một thành phần hạ tầng phải vận hành
 * và giám sát, chỉ để phục vụ một tính năng; Redis thì dự án đã chạy sẵn.
 *
 * KHÔNG bật khi {@code rwg.redis.enabled=false} (dev không cài Redis). Lúc đó chat
 * vẫn hoạt động đầy đủ qua HTTP, chỉ mất phần đẩy tức thời sang app kia — MySQL mới
 * là nguồn sự thật, Redis chỉ là lớp tăng tốc.
 */
@Configuration
@ConditionalOnProperty(name = "rwg.redis.enabled", havingValue = "true", matchIfMissing = true)
public class ChatRelayConfig {

    private static final Logger log = LoggerFactory.getLogger(ChatRelayConfig.class);

    /**
     * Nghe kênh relay và phát lại gói xuống broker STOMP cục bộ.
     *
     * BỎ QUA gói do CHÍNH tiến trình này phát: Redis pub/sub gửi tới mọi subscriber
     * kể cả người publish, nên không kiểm tra {@code originId} thì mỗi tin nhắn được
     * đẩy xuống client hai lần — một lần trực tiếp, một lần vọng về qua Redis.
     */
    @Bean
    public RedisMessageListenerContainer chatRelayListenerContainer(
            RedisConnectionFactory connectionFactory,
            ChatEventPublisher eventPublisher,
            ObjectMapper objectMapper,
            ChatProperties chatProperties) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);

        MessageListener listener = (message, pattern) -> {
            try {
                String json = new String(message.getBody(), StandardCharsets.UTF_8);
                ChatEventPublisher.RelayEnvelope envelope =
                        objectMapper.readValue(json, ChatEventPublisher.RelayEnvelope.class);

                if (eventPublisher.originId().equals(envelope.originId())) {
                    return;
                }
                eventPublisher.deliverLocally(envelope.payload());
            } catch (RuntimeException malformed) {
                // Một gói lỗi KHÔNG được làm chết listener: container sẽ không tự
                // khởi động lại, và khi đó mọi sự kiện sau đó cũng mất mà không có
                // dấu hiệu gì ngoài việc "chat đột nhiên không realtime nữa".
                log.warn("Bỏ qua gói relay chat không đọc được", malformed);
            }
        };

        container.addMessageListener(listener, new ChannelTopic(chatProperties.relayChannel()));

        // Log ở mức INFO, không DEBUG: đây là thông tin cần thấy được trong log khởi
        // động bình thường. Không có dòng này thì không có cách nào phân biệt "cầu relay
        // đã dựng" với "cầu relay không tồn tại" ngoài việc thử gửi tin và xem có tới
        // hay không.
        log.info("Cầu relay chat qua Redis đã bật — kênh '{}', originId={}. "
                        + "Sự kiện chat sẽ được đẩy giữa app người chơi và app quản trị.",
                chatProperties.relayChannel(), eventPublisher.originId());

        return container;
    }

    @Bean
    public RedisMessageListenerContainer gameRelayListenerContainer(
            RedisConnectionFactory connectionFactory,
            com.rwg.game.service.GameEventRelay gameEventRelay,
            ObjectMapper objectMapper) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);

        MessageListener listener = (message, pattern) -> {
            try {
                String json = new String(message.getBody(), StandardCharsets.UTF_8);
                com.rwg.game.service.GameEventRelay.RelayEnvelope envelope =
                        objectMapper.readValue(json, com.rwg.game.service.GameEventRelay.RelayEnvelope.class);

                if (gameEventRelay.originId().equals(envelope.originId())) {
                    return;
                }
                gameEventRelay.deliverLocally(envelope);
            } catch (Exception e) {
                log.warn("Bỏ qua gói game relay không đọc được", e);
            }
        };

        container.addMessageListener(listener, new ChannelTopic(com.rwg.game.service.GameEventRelay.REDIS_TOPIC_GAME_RELAY));
        log.info("Cầu relay game qua Redis đã bật — kênh '{}', originId={}.",
                com.rwg.game.service.GameEventRelay.REDIS_TOPIC_GAME_RELAY, gameEventRelay.originId());

        return container;
    }

    /**
     * Cảnh báo khi cầu relay KHÔNG được bật.
     *
     * Lý do lớp này tồn tại: khi {@code rwg.redis.enabled=false}, chat mất hoàn toàn
     * realtime nhưng KHÔNG có bất kỳ dấu hiệu nào — tin nhắn vẫn vào MySQL, API vẫn
     * trả 200, không lỗi nào được ném ra. Triệu chứng duy nhất là "phải tải lại trang
     * mới thấy tin", và người gặp phải nó không có đường nào lần về nguyên nhân ngoài
     * việc đọc mã nguồn.
     *
     * Một dòng WARN lúc khởi động biến việc đó thành hiển nhiên.
     *
     * Tách thành class lồng riêng vì điều kiện bean phải ĐẢO so với lớp ngoài, và
     * {@code @ConditionalOnProperty} chỉ áp dụng ở cấp class hoặc method-bean.
     */
    @Configuration
    @ConditionalOnProperty(name = "rwg.redis.enabled", havingValue = "false")
    public static class ChatRelayDisabledWarning {

        public ChatRelayDisabledWarning() {
            log.warn("Redis đang TẮT (rwg.redis.enabled=false) — CHAT HỖ TRỢ SẼ KHÔNG REALTIME. "
                    + "Tin nhắn vẫn được lưu đầy đủ vào MySQL, nhưng phía bên kia chỉ thấy tin mới "
                    + "sau khi TẢI LẠI TRANG. Bật lại bằng RWG_REDIS_ENABLED=true o CẢ HAI app.");
        }
    }
}
