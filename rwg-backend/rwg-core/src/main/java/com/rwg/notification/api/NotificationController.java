package com.rwg.notification.api;

import com.rwg.common.PageResponse;
import com.rwg.notification.dto.NotificationResponse;
import com.rwg.notification.service.MyNotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * API thông báo của người chơi (yêu cầu JWT hợp lệ).
 *
 * KHÔNG cần thêm matcher vào {@code SecurityConfig}: cấu hình kết thúc bằng
 * {@code .anyRequest().authenticated()}, nên endpoint mới tự động yêu cầu đăng nhập.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications", description = "Thông báo tiền vào/ra ví và tin chung")
@SecurityRequirement(name = "bearerAuth")
public class NotificationController {

    private final MyNotificationService service;

    public NotificationController(MyNotificationService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Danh sách thông báo của tôi + tin chung (mới nhất trước)")
    public PageResponse<NotificationResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return service.list(UUID.fromString(jwt.getSubject()), page, size);
    }

    /**
     * Số thông báo cá nhân chưa đọc, cho viên tròn đỏ.
     *
     * Tách khỏi endpoint danh sách vì hai lý do: giao diện cần con số này ở trang hồ sơ mà
     * không cần tải cả danh sách, và nó được gọi lại thường xuyên hơn.
     */
    @GetMapping("/unread-count")
    @Operation(summary = "Số thông báo cá nhân chưa đọc")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal Jwt jwt) {
        return Map.of("count", service.unreadCount(UUID.fromString(jwt.getSubject())));
    }

    /**
     * Đánh dấu một thông báo đã đọc.
     *
     * Trả 204 không kèm nội dung: client đã biết id mình vừa đánh dấu, trả lại cả đối tượng
     * chỉ để nó tự bỏ đi là tốn băng thông vô ích.
     */
    @PostMapping("/{id}/read")
    @Operation(summary = "Đánh dấu một thông báo đã đọc")
    public ResponseEntity<Void> markRead(@AuthenticationPrincipal Jwt jwt,
                                         @PathVariable UUID id) {
        service.markRead(UUID.fromString(jwt.getSubject()), id);
        return ResponseEntity.noContent().build();
    }

    /** Đánh dấu tất cả đã đọc; trả về số dòng vừa đổi để giao diện biết có gì thay đổi hay không. */
    @PostMapping("/read-all")
    @Operation(summary = "Đánh dấu tất cả thông báo cá nhân đã đọc")
    public Map<String, Integer> markAllRead(@AuthenticationPrincipal Jwt jwt) {
        return Map.of("updated", service.markAllRead(UUID.fromString(jwt.getSubject())));
    }
}
