package com.rwg.chat.api;

import com.rwg.chat.domain.ChatConversationStatus;
import com.rwg.chat.dto.AdminChatConversationRowResponse;
import com.rwg.chat.dto.ChatAttachmentResponse;
import com.rwg.chat.dto.ChatMessageResponse;
import com.rwg.chat.dto.ChatUnreadResponse;
import com.rwg.chat.dto.DeleteChatMessagesRequest;
import com.rwg.chat.dto.SendChatMessageRequest;
import com.rwg.chat.service.AdminChatService;
import com.rwg.common.PageResponse;
import com.rwg.common.web.ClientAddresses;
import com.rwg.config.SecurityConfig;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * API chat hỗ trợ của KHU QUẢN TRỊ.
 *
 * Phân quyền enforce tập trung trong {@code SecurityConfig}: ĐỌC mở cho mọi vai trò
 * quản trị (gồm RISK), GHI chỉ ADMIN/FINANCE/SUPPORT — RISK là vai trò chỉ-đọc nên
 * không được trả lời người chơi. KHÔNG rải {@code @PreAuthorize} ở đây.
 */
@RestController
@RequestMapping("/api/v1/admin/chat")
@Tag(name = "Admin", description = "Hộp thư hỗ trợ người chơi - khu quản trị")
public class AdminChatController {

    private final AdminChatService service;

    public AdminChatController(AdminChatService service) {
        this.service = service;
    }

    /**
     * Hộp thư.
     *
     * @param status         OPEN | CLOSED; bỏ trống = mọi trạng thái.
     * @param mine           true = chỉ luồng tôi đang phụ trách.
     * @param unassigned     true = chỉ luồng chưa ai nhận (hàng đợi cần người).
     * @param q              tìm theo tên đăng nhập người chơi.
     */
    @GetMapping("/conversations")
    @Operation(summary = "Hộp thư hỗ trợ: lọc theo trạng thái / người phụ trách / tên đăng nhập")
    public PageResponse<AdminChatConversationRowResponse> inbox(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) ChatConversationStatus status,
            @RequestParam(defaultValue = "false") boolean mine,
            @RequestParam(defaultValue = "false") boolean unassigned,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        // `mine` được dịch thành id của CHÍNH người gọi, không nhận adminId từ query:
        // nhận id từ ngoài thì một nhân sự xem được hàng đợi riêng của người khác, và
        // đó là thông tin về hiệu suất làm việc của đồng nghiệp họ.
        UUID assignedTo = mine ? UUID.fromString(jwt.getSubject()) : null;

        return service.inbox(status, assignedTo, unassigned, q, page, size);
    }

    /**
     * Lịch sử một luồng, mới nhất trước.
     *
     * Phân trang theo MỐC THỜI GIAN — xem lý do ở {@code ChatController.myMessages}.
     */
    @GetMapping("/conversations/{id}/messages")
    @Operation(summary = "Lịch sử tin nhắn của một luồng (phân trang theo mốc thời gian)")
    public List<ChatMessageResponse> messages(
            @PathVariable UUID id,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant before) {

        return service.messages(id, before);
    }

    @PostMapping("/conversations/{id}/messages")
    @Operation(summary = "Trả lời người chơi (tự nhận luồng nếu chưa ai phụ trách)")
    public ChatMessageResponse reply(@PathVariable UUID id,
                                     @Valid @RequestBody SendChatMessageRequest request,
                                     @AuthenticationPrincipal Jwt jwt) {
        return service.replyAsStaff(id, UUID.fromString(jwt.getSubject()),
                username(jwt), request.body(), request.clientMsgId(),
                request.attachmentUrl(), request.attachmentName(), request.attachmentSize());
    }

    /**
     * Tải một ảnh lên để gửi kèm câu trả lời.
     *
     * Đường dẫn nằm dưới {@code /api/v1/admin/chat/**} nên matcher POST sẵn có trong
     * {@code SecurityConfig} đã loại RISK — không cần thêm luật mới, và cũng không được
     * đặt endpoint này ra ngoài tiền tố đó.
     */
    @PostMapping(value = "/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Tải ảnh đính kèm lên, trả về đường dẫn để gửi kèm câu trả lời")
    public ChatAttachmentResponse uploadAttachment(@RequestPart("file") MultipartFile file) {
        return service.uploadAttachment(file);
    }

    @PostMapping("/conversations/{id}/assign")
    @Operation(summary = "Nhận phụ trách một luồng hỗ trợ")
    public AdminChatConversationRowResponse assign(@PathVariable UUID id,
                                                   @AuthenticationPrincipal Jwt jwt,
                                                   HttpServletRequest httpRequest) {
        return service.assign(id, UUID.fromString(jwt.getSubject()), username(jwt),
                ClientAddresses.clientIp(httpRequest));
    }

    @PostMapping("/conversations/{id}/close")
    @Operation(summary = "Đóng một luồng hỗ trợ (người chơi gửi tin mới sẽ tự mở lại)")
    public AdminChatConversationRowResponse close(@PathVariable UUID id,
                                                  @AuthenticationPrincipal Jwt jwt,
                                                  HttpServletRequest httpRequest) {
        return service.close(id, UUID.fromString(jwt.getSubject()), username(jwt),
                ClientAddresses.clientIp(httpRequest));
    }

    @PostMapping("/conversations/{id}/read")
    @Operation(summary = "Đánh dấu đã đọc mọi tin của người chơi trong luồng")
    public Map<String, Integer> markRead(@PathVariable UUID id) {
        return Map.of("updated", service.markRead(id));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Tổng số tin chưa đọc và số luồng đang chờ trả lời")
    public ChatUnreadResponse unreadCount() {
        return service.unread();
    }

    /**
     * Xóa một hoặc nhiều tin nhắn (của admin hoặc của người chơi).
     *
     * Chỉ ADMIN được gọi endpoint này (xác thực bởi SecurityConfig).
     * Tin biến mất ngay trên màn hình của cả hai phía qua WebSocket,
     * nhưng vẫn còn trong DB để khiếu nại sau này.
     */
    @DeleteMapping("/conversations/{id}/messages")
    @Operation(summary = "Xóa tin nhắn (soft delete, biến mất realtime cả hai phía)")
    public Map<String, Integer> deleteMessages(@PathVariable UUID id,
                                               @Valid @RequestBody DeleteChatMessagesRequest request,
                                               @AuthenticationPrincipal Jwt jwt,
                                               HttpServletRequest httpRequest) {
        int deleted = service.deleteMessages(id, request.getMessageIds(), request.getConfirmPin(),
                UUID.fromString(jwt.getSubject()), username(jwt),
                ClientAddresses.clientIp(httpRequest));
        return Map.of("deleted", deleted);
    }

    /**
     * Tên đăng nhập lấy từ claim của token.
     *
     * Đọc claim thay vì truy vấn bảng users: tên này được CHỤP LẠI vào tin nhắn và
     * audit, nên nó phải là tên tại thời điểm hành động — đúng cái nằm trong token.
     * Thêm một truy vấn DB cho mỗi tin nhắn gửi ra cũng là chi phí không cần thiết.
     */
    private static String username(Jwt jwt) {
        return jwt.getClaimAsString(SecurityConfig.USERNAME_CLAIM);
    }
}
