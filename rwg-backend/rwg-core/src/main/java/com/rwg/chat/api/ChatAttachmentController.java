package com.rwg.chat.api;

import com.rwg.chat.service.ChatAttachmentAccessService;
import com.rwg.media.service.MediaStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Phục vụ ảnh đính kèm của chat, CÓ KIỂM TRA QUYỀN.
 *
 * TÁCH KHỎI {@code MediaController} (phục vụ {@code /uploads/media/**} công khai) vì
 * hai loại tệp có yêu cầu đối lập: banner là ảnh tiếp thị, càng dễ tải càng tốt; ảnh
 * chat là biên lai chuyển tiền và ảnh giấy tờ. Dùng chung một đường phục vụ nghĩa là
 * ảnh chat cũng công khai, và bất kỳ ai có đường dẫn đều xem được vĩnh viễn.
 *
 * CHẠY Ở CẢ HAI APP (không nằm trong excludeFilters của {@code AdminApplication}), vì
 * cả người chơi và nhân sự đều phải xem được cùng một ảnh qua cùng một đường dẫn. Tách
 * hai endpoint riêng thì đường dẫn lưu trong DB chỉ đúng với một phía, và phía kia phải
 * viết lại chuỗi URL — một chỗ nữa để sai mà không có gì bắt lỗi.
 */
@RestController
@Tag(name = "Chat", description = "Ảnh đính kèm trong chat hỗ trợ")
@SecurityRequirement(name = "bearerAuth")
public class ChatAttachmentController {

    private final MediaStorageService mediaStorageService;
    private final ChatAttachmentAccessService accessService;

    public ChatAttachmentController(MediaStorageService mediaStorageService,
                                   ChatAttachmentAccessService accessService) {
        this.mediaStorageService = mediaStorageService;
        this.accessService = accessService;
    }

    /**
     * Tải một ảnh đính kèm.
     *
     * Trả 404 khi không có quyền, KHÔNG trả 403: 403 xác nhận rằng tệp đó có tồn tại,
     * và đó là thông tin có giá trị với người đang thử dò đường dẫn. Với người dùng hợp
     * lệ thì hai mã này không khác gì nhau — họ không bao giờ gặp trường hợp đó.
     */
    @GetMapping("/api/v1/chat/attachments/{filename:.+}")
    @Operation(summary = "Xem ảnh đính kèm trong luồng chat của mình (nhân sự xem được mọi luồng)")
    public ResponseEntity<Resource> serve(@PathVariable("filename") String filename,
                                          @AuthenticationPrincipal Jwt jwt) {

        Path file = mediaStorageService.findChatAttachment(filename)
                .filter(f -> accessService.canView(jwt, filename))
                .orElse(null);

        if (file == null) {
            return ResponseEntity.notFound().build();
        }

        MediaType mediaType = MediaTypeFactory.getMediaType(filename)
                .orElse(MediaType.APPLICATION_OCTET_STREAM);

        return ResponseEntity.ok()
                .contentType(mediaType)
                // Cache RIÊNG TỪNG NGƯỜI (`cachePrivate`), không phải cache chung.
                //
                // Nội dung một ảnh không bao giờ đổi (tên tệp là UUID), nên cache lâu là
                // đúng — cuộn lại lịch sử chat không nên tải lại ảnh. Nhưng nếu để cache
                // chung thì một proxy hoặc CDN đứng giữa sẽ giữ ảnh đó và phục vụ cho
                // người tiếp theo hỏi cùng đường dẫn, bỏ qua hoàn toàn phần kiểm tra
                // quyền phía trên.
                .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePrivate())
                .body(new FileSystemResource(file));
    }
}
