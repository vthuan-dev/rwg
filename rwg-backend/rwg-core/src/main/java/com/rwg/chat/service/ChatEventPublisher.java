package com.rwg.chat.service;

import com.rwg.chat.dto.ChatEventPayload;
import com.rwg.config.ChatProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

/**
 * Đẩy sự kiện chat tới các client đang kết nối, và bắc cầu sang app còn lại.
 *
 * HAI VIỆC TÁCH RÕ, theo đúng cách {@code NotificationService} đang làm:
 * - Ghi DB nằm trong transaction của service gọi.
 * - Đẩy realtime CHỈ chạy sau khi transaction commit.
 *
 * Thứ tự đó quan trọng: đẩy trước khi commit sẽ có trường hợp người chơi thấy tin
 * trả lời của nhân sự rồi transaction rollback — tin đó không hề tồn tại, và họ tải
 * lại trang thì nó biến mất.
 *
 * VÌ SAO CẦN CẦU REDIS: broker STOMP đang là {@code enableSimpleBroker} — in-memory
 * trong từng JVM. Nhân sự nối vào app admin (8081), người chơi nối vào app player
 * (8080), nên một lần {@code convertAndSendToUser} ở app admin KHÔNG bao giờ tới
 * được trình duyệt người chơi. Redis chở gói sự kiện qua lại giữa hai app.
 *
 * Redis chỉ là lớp TĂNG TỐC, không phải nguồn sự thật: MySQL đã giữ tin nhắn. Tắt
 * Redis (dev, hoặc Redis chết) thì tin vẫn tới qua polling, chỉ chậm hơn.
 */
@Service
public class ChatEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ChatEventPublisher.class);

    /** Kênh unicast tin chat cho một người chơi: /user/queue/chat. */
    public static final String USER_QUEUE_CHAT = "/queue/chat";

    /**
     * Kênh phát cho khu quản trị.
     *
     * MỘT topic chung cho mọi nhân sự, không phải một kênh riêng theo từng người:
     * hộp thư là hàng đợi DÙNG CHUNG, ai cũng cần thấy tin mới đến để nhận việc.
     * Kênh riêng từng người sẽ phải quyết định trước "gói này gửi cho ai" — mà đó
     * chính là câu hỏi hộp thư tồn tại để trả lời.
     *
     * Quyền subscribe kênh này bị chặn trong {@code WsAuthChannelInterceptor}:
     * SimpleBroker tự nó cho mọi client đã xác thực subscribe mọi topic.
     */
    public static final String TOPIC_ADMIN_CHAT = "/topic/admin/chat";

    /**
     * Id của tiến trình đang chạy, sinh mới mỗi lần khởi động.
     *
     * CẦN để chống vòng lặp vọng âm: app này publish gói lên Redis, rồi chính
     * listener của nó cũng nhận lại gói đó. Không phân biệt được nguồn thì mỗi tin
     * sẽ được đẩy xuống client hai lần.
     */
    private final String originId = UUID.randomUUID().toString();

    private final SimpMessagingTemplate messaging;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final ChatProperties chatProperties;

    /**
     * {@code messaging} và {@code redis} đều OPTIONAL:
     * - messaging null khi app không bật WebSocket (context test tối giản).
     * - redis null khi rwg.redis.enabled=false (dev không cài Redis).
     * Cả hai trường hợp đều phải chạy được, chỉ mất phần realtime.
     */
    public ChatEventPublisher(@Autowired(required = false) SimpMessagingTemplate messaging,
                              @Autowired(required = false) StringRedisTemplate redis,
                              ObjectMapper objectMapper,
                              ChatProperties chatProperties) {
        this.messaging = messaging;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.chatProperties = chatProperties;
    }

    /** Id tiến trình — cầu relay dùng để bỏ qua gói do chính mình phát. */
    public String originId() {
        return originId;
    }

    /**
     * Đẩy một sự kiện sau khi transaction hiện tại commit: xuống client cục bộ VÀ
     * sang app còn lại qua Redis.
     */
    public void publishAfterCommit(ChatEventPayload payload) {
        registerAfterCommit(() -> {
            deliverLocally(payload);
            relay(payload);
        });
    }

    /**
     * Đẩy xuống các client đang nối vào CHÍNH tiến trình này.
     *
     * Gửi cả hai đích trong mọi trường hợp. Ở app player không có nhân sự nào
     * subscribe {@code /topic/admin/chat} nên lần gửi đó rơi vào hư không — vô hại,
     * và đổi lại là một hàm duy nhất dùng được ở cả hai app. Tự phân nhánh theo
     * "đang chạy ở app nào" sẽ tạo ra hai đường code mà chỉ một đường được chạy
     * trong mỗi lần triển khai, nên lỗi ở đường kia không ai phát hiện.
     *
     * NGOẠI LỆ DUY NHẤT là {@code staffOnly}: gói đó không được tới kênh của người chơi.
     * Chặn TẠI ĐÂY vì đây là chỗ duy nhất trong dự án gọi {@code convertAndSendToUser} —
     * lọc ở tầng service thì mỗi đường phát tin mới lại phải tự nhớ, và một lần quên
     * nghĩa là thẻ duyệt tiền tới thẳng trình duyệt người chơi. Khi đó API đã lọc cũng
     * không cứu được gì: ai mở tab Network cũng đọc được nội dung gói.
     */
    public void deliverLocally(ChatEventPayload payload) {
        if (messaging == null) {
            return;
        }
        try {
            if (payload.targetUserId() != null && !payload.staffOnly()) {
                messaging.convertAndSendToUser(payload.targetUserId(), USER_QUEUE_CHAT, payload);
            }
            messaging.convertAndSend(TOPIC_ADMIN_CHAT, payload);
        } catch (RuntimeException sendFailed) {
            // Transaction đã commit, tin đã nằm trong DB. Ném tiếp một lỗi mạng
            // WebSocket ở đây không cứu được gì mà chỉ làm bẩn log của luồng nghiệp
            // vụ — client vẫn thấy tin khi tải lại luồng.
            log.warn("Không đẩy được sự kiện chat conversationId={} type={}",
                    payload.conversationId(), payload.type(), sendFailed);
        }
    }

    /** Phát gói sang app còn lại qua Redis, kèm dấu nguồn để không tự nhận lại. */
    private void relay(ChatEventPayload payload) {
        if (redis == null) {
            return;
        }
        try {
            String envelope = objectMapper.writeValueAsString(
                    new RelayEnvelope(originId, payload));
            redis.convertAndSend(chatProperties.relayChannel(), envelope);
        } catch (RuntimeException relayFailed) {
            // Redis chết KHÔNG được làm hỏng việc gửi tin: tin đã nằm trong MySQL và
            // phía kia sẽ thấy nó ở lần poll tiếp theo.
            log.warn("Không bắc cầu được sự kiện chat qua Redis conversationId={}",
                    payload.conversationId(), relayFailed);
        }
    }

    /**
     * Chạy một việc sau khi transaction hiện tại commit.
     *
     * Có nhánh dự phòng chạy ngay khi không có transaction nào đang mở: test đơn vị
     * gọi trực tiếp mà không bọc transaction sẽ không im lặng bỏ qua việc đẩy tin.
     */
    private void registerAfterCommit(Runnable task) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            task.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                task.run();
            }
        });
    }

    /** Gói vận chuyển qua Redis: dấu nguồn + nội dung sự kiện. */
    public record RelayEnvelope(String originId, ChatEventPayload payload) {
    }
}
