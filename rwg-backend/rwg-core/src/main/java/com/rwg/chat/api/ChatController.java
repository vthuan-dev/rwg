package com.rwg.chat.api;

import com.rwg.chat.dto.ChatAttachmentResponse;
import com.rwg.chat.dto.ChatConversationResponse;
import com.rwg.chat.dto.ChatMessageResponse;
import com.rwg.chat.dto.ChatUnreadResponse;
import com.rwg.chat.dto.SendChatMessageRequest;
import com.rwg.chat.service.ChatGeoService;
import com.rwg.chat.service.ChatService;
import com.rwg.common.web.ClientAddresses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
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
 * API chat hỗ trợ của NGƯỜI CHƠI (yêu cầu JWT hợp lệ).
 *
 * KHÔNG cần thêm matcher vào {@code SecurityConfig}: cấu hình kết thúc bằng
 * {@code .anyRequest().authenticated()} nên endpoint mới tự động yêu cầu đăng nhập.
 *
 * Mọi endpoint làm việc trên luồng của CHÍNH người gọi, lấy từ {@code jwt.getSubject()}.
 * KHÔNG có tham số conversationId ở bất kỳ đâu — không có id để truyền thì cũng
 * không có đường nào để đọc luồng của người khác.
 */
@RestController
@RequestMapping("/api/v1/chat")
@Tag(name = "Chat", description = "Trò chuyện trực tiếp với bộ phận hỗ trợ")
@SecurityRequirement(name = "bearerAuth")
public class ChatController {

    private final ChatService service;
    private final ChatGeoService geoService;

    public ChatController(ChatService service, ChatGeoService geoService) {
        this.service = service;
        this.geoService = geoService;
    }

    /**
     * Luồng hội thoại của tôi.
     *
     * GHI NHẬN IP Ở ĐÂY, không chỉ ở lúc gửi tin: nhân sự cần thấy vị trí của cả những
     * người đã mở khung chat mà chưa gõ gì — đó đúng là nhóm cần chủ động hỏi trước.
     *
     * Gọi SAU khi phương thức nghiệp vụ trả về, không gọi bên trong nó: bước này có thể
     * đi ra Internet, và giữ một connection của pool trong lúc chờ mạng là thứ
     * {@code ChatGeoService} được tách ra để tránh.
     */
    @GetMapping("/conversation")
    @Operation(summary = "Luồng hội thoại hỗ trợ của tôi (tạo mới nếu chưa có)")
    public ChatConversationResponse myConversation(@AuthenticationPrincipal Jwt jwt,
                                                   HttpServletRequest httpRequest) {
        UUID userId = UUID.fromString(jwt.getSubject());
        ChatConversationResponse conversation = service.myConversation(userId);
        geoService.track(userId, ClientAddresses.clientIp(httpRequest));
        return conversation;
    }

    /**
     * Lịch sử tin nhắn, mới nhất trước.
     *
     * Phân trang bằng MỐC THỜI GIAN (`before`) chứ không bằng số trang: với chat, tin
     * mới chèn vào đầu liên tục nên số trang bị trôi — xem chú thích ở
     * {@code ChatMessageRepository.findPageBefore}. Client lấy `createdAt` của tin cũ
     * nhất đang hiển thị làm `before` cho lần gọi kế tiếp.
     */
    @GetMapping("/messages")
    @Operation(summary = "Lịch sử tin nhắn của tôi, mới nhất trước (phân trang theo mốc thời gian)")
    public List<ChatMessageResponse> myMessages(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant before) {

        return service.myMessages(UUID.fromString(jwt.getSubject()), before);
    }

    /**
     * Gửi một tin.
     *
     * IP được ghi nhận SAU khi tin đã lưu thành công. Thứ tự đó là chủ ý: bước ghi nhận
     * vị trí không bao giờ được làm một tin nhắn đã gửi được trở thành lỗi.
     */
    @PostMapping("/messages")
    @Operation(summary = "Gửi một tin nhắn tới bộ phận hỗ trợ (có thể kèm ảnh)")
    public ChatMessageResponse send(@AuthenticationPrincipal Jwt jwt,
                                    @Valid @RequestBody SendChatMessageRequest request,
                                    HttpServletRequest httpRequest) {
        UUID userId = UUID.fromString(jwt.getSubject());
        ChatMessageResponse sent = service.sendAsPlayer(userId,
                request.body(), request.clientMsgId(),
                request.attachmentUrl(), request.attachmentName(), request.attachmentSize());
        geoService.track(userId, ClientAddresses.clientIp(httpRequest));
        return sent;
    }

    /**
     * Tải một ảnh lên, chưa gắn vào tin nhắn nào.
     *
     * HAI BƯỚC (tải lên rồi mới gửi tin) thay vì một request multipart mang cả ảnh và
     * nội dung: người dùng chọn ảnh xong thấy ngay ảnh đã lên kèm thanh tiến trình, rồi
     * mới gõ chữ và bấm gửi. Gộp một bước thì toàn bộ thời gian tải lên nằm SAU lần bấm
     * gửi — trên mạng 3G với ảnh 8MB đó là hàng chục giây giao diện không phản hồi gì.
     */
    @PostMapping(value = "/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Tải ảnh đính kèm lên, trả về đường dẫn để gửi kèm tin nhắn")
    public ChatAttachmentResponse uploadAttachment(@AuthenticationPrincipal Jwt jwt,
                                                   @RequestPart("file") MultipartFile file) {
        return service.uploadAttachment(UUID.fromString(jwt.getSubject()), file);
    }

    /**
     * Đánh dấu đã xem mọi tin của nhân sự.
     *
     * Trả về số dòng vừa đổi để giao diện biết có gì thay đổi hay không — nếu 0 thì
     * không cần vẽ lại viên đếm.
     */
    @PostMapping("/read")
    @Operation(summary = "Đánh dấu đã đọc mọi tin nhắn từ bộ phận hỗ trợ")
    public Map<String, Integer> markRead(@AuthenticationPrincipal Jwt jwt) {
        return Map.of("updated", service.markMyMessagesRead(UUID.fromString(jwt.getSubject())));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Số tin nhắn hỗ trợ tôi chưa đọc")
    public ChatUnreadResponse unreadCount(@AuthenticationPrincipal Jwt jwt) {
        return service.myUnread(UUID.fromString(jwt.getSubject()));
    }
}
