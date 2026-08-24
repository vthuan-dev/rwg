package com.rwg.notification.service;

import com.rwg.notification.domain.Notification;
import com.rwg.notification.domain.NotificationType;
import com.rwg.notification.dto.NotificationResponse;
import com.rwg.notification.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tạo và đẩy thông báo cho người chơi.
 *
 * HAI VIỆC TÁCH RÕ:
 * - Ghi DB: gọi TRONG transaction của nghiệp vụ tiền, nên thông báo và việc chuyển tiền cùng
 *   thành công hoặc cùng thất bại.
 * - Đẩy WebSocket: chỉ chạy SAU KHI transaction commit.
 *
 * Thứ tự đó là điều quan trọng nhất ở lớp này. Đẩy WebSocket trước khi commit sẽ có trường hợp
 * người chơi nhận tin "đã cộng $500" rồi transaction rollback — tiền không hề có. Người chơi
 * thấy thông báo, mở ví, không thấy tiền, và mở khiếu nại.
 *
 * Cách bảo đảm: đăng ký callback {@code afterCommit} qua
 * {@code TransactionSynchronizationManager} thay vì gọi thẳng. Cùng cách mà
 * {@code GameEventBroadcaster} đang dùng cho gói BET_PLACED.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    /** Kênh unicast thông báo mới cho một người: /user/queue/notifications. */
    public static final String USER_QUEUE_NOTIFICATIONS = "/queue/notifications";

    /** Kênh phát tin chung cho mọi người đang kết nối. */
    public static final String TOPIC_ANNOUNCEMENTS = "/topic/announcements";

    private final NotificationRepository repository;
    private final SimpMessagingTemplate messaging;

    public NotificationService(NotificationRepository repository,
                               @org.springframework.beans.factory.annotation.Autowired(required = false) SimpMessagingTemplate messaging) {
        this.repository = repository;
        this.messaging = messaging;
    }

    /**
     * Ghi một thông báo cá nhân và đẩy đi sau khi transaction hiện tại commit.
     *
     * Dùng {@code Propagation.MANDATORY}: hàm này BẮT BUỘC được gọi từ trong một transaction
     * đang mở. Nếu để {@code REQUIRED}, một lời gọi lỡ nằm ngoài transaction sẽ tự mở
     * transaction riêng và commit độc lập — nghĩa là thông báo "đã cộng tiền" vẫn tồn tại dù
     * việc chuyển tiền sau đó rollback. {@code MANDATORY} biến sai sót đó thành lỗi ngay lúc
     * chạy thử thay vì một dữ liệu sai lặng lẽ trên môi trường thật.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void notifyMoney(UUID userId, NotificationType type, BigDecimal amount) {
        Notification saved = repository.save(Notification.personal(
                userId, type, titleKeyFor(type), amountParams(amount)));

        publishAfterCommit(userId, saved);
    }

    /**
     * Đăng tin chung cho mọi người.
     *
     * Mở transaction riêng ({@code REQUIRED}) vì đây là hành động độc lập của admin, không đi
     * kèm nghiệp vụ tiền nào.
     */
    @Transactional
    public NotificationResponse broadcast(String titleKey, String body) {
        Notification saved = repository.save(Notification.broadcast(titleKey, body));

        registerAfterCommit(() -> {
            if (messaging != null) {
                try {
                    messaging.convertAndSend(TOPIC_ANNOUNCEMENTS, NotificationResponse.from(saved));
                } catch (RuntimeException sendFailed) {
                    log.warn("Không đẩy được tin chung id={}", saved.getId(), sendFailed);
                }
            }
        });

        return NotificationResponse.from(saved);
    }

    /**
     * Đẩy thông báo sau commit.
     *
     * Bọc trong try/catch và chỉ ghi log khi lỗi: transaction đã commit xong, tiền đã chuyển và
     * thông báo đã nằm trong DB. Ném tiếp một lỗi mạng WebSocket ở đây không cứu được gì mà chỉ
     * làm bẩn log của luồng nghiệp vụ — người chơi vẫn thấy thông báo khi mở danh sách.
     */
    private void publishAfterCommit(UUID userId, Notification notification) {
        NotificationResponse payload = NotificationResponse.from(notification);

        registerAfterCommit(() -> {
            if (messaging != null) {
                try {
                    messaging.convertAndSendToUser(userId.toString(), USER_QUEUE_NOTIFICATIONS, payload);
                } catch (RuntimeException sendFailed) {
                    log.warn("Không đẩy được thông báo userId={} id={}",
                            userId, notification.getId(), sendFailed);
                }
            }
        });
    }

    /**
     * Chạy một việc sau khi transaction hiện tại commit.
     *
     * Có nhánh dự phòng chạy ngay khi không có transaction nào đang mở: các test đơn vị gọi
     * trực tiếp mà không bọc transaction sẽ không im lặng bỏ qua việc đẩy tin.
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

    /**
     * Khoá dịch cho từng loại.
     *
     * Ánh xạ đặt ở đây, một chỗ duy nhất, thay vì để chỗ gọi tự truyền khoá: mỗi chỗ gọi tự
     * truyền sẽ sinh ra những khoá gõ sai chính tả mà không có gì phát hiện, và thông báo hiện
     * ra chuỗi thô cho người dùng.
     */
    private static String titleKeyFor(NotificationType type) {
        return switch (type) {
            case DEPOSIT_COMPLETED -> "notification.deposit_completed";
            case DEPOSIT_REQUESTED -> "notification.deposit_requested";
            case DEPOSIT_REJECTED -> "notification.deposit_rejected";
            case WITHDRAWAL_REQUESTED -> "notification.withdrawal_requested";
            case WITHDRAWAL_APPROVED -> "notification.withdrawal_approved";
            case WITHDRAWAL_REJECTED -> "notification.withdrawal_rejected";
            case ADMIN_CREDIT -> "notification.admin_credit";
            case ADMIN_DEBIT -> "notification.admin_debit";
            case ANNOUNCEMENT -> "notification.announcement";
        };
    }

    /**
     * Tham số dạng JSON cho khoá dịch.
     *
     * Tự dựng chuỗi JSON thay vì dùng Jackson: nội dung chỉ có một trường số tiền do chính
     * backend sinh ra, nên không có ký tự nào cần thoát. {@code toPlainString()} là bắt buộc —
     * {@code toString()} của BigDecimal cho ký hiệu khoa học với số rất nhỏ hoặc rất lớn, và
     * "5E+2" trong thông báo tiền là vô nghĩa với người đọc.
     */
    private static String amountParams(BigDecimal amount) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("amount", amount.toPlainString());
        return "{\"amount\":\"" + params.get("amount") + "\"}";
    }
}
