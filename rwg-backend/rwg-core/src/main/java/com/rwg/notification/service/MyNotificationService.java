package com.rwg.notification.service;

import com.rwg.common.ApiException;
import com.rwg.common.ErrorCode;
import com.rwg.common.PageResponse;
import com.rwg.notification.domain.Notification;
import com.rwg.notification.dto.NotificationResponse;
import com.rwg.notification.repository.NotificationRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Đọc thông báo của chính mình.
 *
 * TÁCH KHỎI {@link NotificationService}: lớp kia GHI thông báo và được gọi từ trong transaction
 * của nghiệp vụ tiền; lớp này ĐỌC và được gọi từ controller. Gộp lại sẽ khiến controller vô
 * tình gọi được các hàm ghi, và mọi thay đổi ở phần đọc lại phải xét đến ràng buộc transaction
 * của phần ghi.
 */
@Service
public class MyNotificationService {

    /**
     * Chặn size ở tầng service, không chỉ ở controller.
     *
     * Trang thông báo mặc định 20 dòng; ai gọi thẳng API với size=100000 sẽ bị kẹp về đây thay
     * vì buộc DB dựng một trang khổng lồ.
     */
    private static final int MAX_PAGE_SIZE = 100;

    private final NotificationRepository repository;

    public MyNotificationService(NotificationRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(UUID userId, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.clamp(size, 1, MAX_PAGE_SIZE));
        return PageResponse.from(repository.findVisibleTo(userId, pageable),
                NotificationResponse::from);
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return repository.countUnreadFor(userId);
    }

    /**
     * Đánh dấu một thông báo đã đọc.
     *
     * Trả NOT_FOUND khi thông báo không thuộc về người gọi, KHÔNG phải FORBIDDEN: trả 403 sẽ
     * xác nhận cho người gọi rằng id đó có tồn tại và thuộc về ai khác. Với 404 thì họ không
     * phân biệt được "không tồn tại" với "không phải của mình", nên không dò được id hợp lệ.
     */
    @Transactional
    public void markRead(UUID userId, UUID notificationId) {
        Notification notification = repository.findOwnedBy(notificationId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        ErrorCode.NOT_FOUND.defaultMessage(), null, "error.not_found.notification"));

        notification.markRead(Instant.now());
        repository.save(notification);
    }

    /** Đánh dấu tất cả tin cá nhân đã đọc. Trả về số dòng vừa đổi. */
    @Transactional
    public int markAllRead(UUID userId) {
        return repository.markAllRead(userId, Instant.now().truncatedTo(ChronoUnit.MICROS));
    }
}
