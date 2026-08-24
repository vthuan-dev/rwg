package com.rwg.chat.service;

import com.rwg.chat.repository.ChatMessageRepository;
import com.rwg.config.SecurityConfig;
import com.rwg.media.service.MediaStorageService;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Ai được xem một ảnh đính kèm của chat.
 *
 * TÁCH RA MỘT LỚP RIÊNG thay vì để trong controller: cùng một câu hỏi được hỏi từ hai
 * app (người chơi ở 8080, nhân sự ở 8081) qua cùng một controller, và quy tắc này là
 * thứ duy nhất đứng giữa ảnh giấy tờ của một người chơi và mọi người dùng đã đăng nhập
 * khác. Nó xứng đáng có một chỗ riêng và có test riêng.
 */
@Service
public class ChatAttachmentAccessService {

    /**
     * Vai trò được xem MỌI ảnh trong mọi luồng, khớp {@code UserRole.isStaff()}.
     *
     * RISK CÓ trong danh sách này, dù họ không được TRẢ LỜI tin nhắn (xem matcher POST
     * trong {@code SecurityConfig}). Không phải bất nhất: điều tra gian lận cần xem
     * đúng những ảnh biên lai mà người chơi gửi lên, và xem không thay đổi gì. Cấm họ
     * xem thì việc điều tra phải đi mượn tài khoản của người khác — kết quả tệ hơn hẳn.
     */
    private static final Set<String> STAFF_ROLES =
            Set.of("ROLE_ADMIN", "ROLE_FINANCE", "ROLE_SUPPORT", "ROLE_RISK");

    private final ChatMessageRepository messageRepository;

    public ChatAttachmentAccessService(ChatMessageRepository messageRepository) {
        this.messageRepository = messageRepository;
    }

    /**
     * Người gọi có được xem ảnh này không?
     *
     * @param filename tên tệp trần, đã được kiểm tra không chứa đường dẫn.
     */
    @Transactional(readOnly = true)
    public boolean canView(Jwt jwt, String filename) {
        if (jwt == null) {
            return false;
        }

        // Nhân sự xem được mọi luồng: họ được phân công xử lý bất kỳ luồng nào, và một
        // luồng có thể chuyển tay giữa nhiều người trong ca.
        if (isStaff(jwt)) {
            return true;
        }

        UUID userId;
        try {
            userId = UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException notAUuid) {
            return false;
        }

        // So khớp bằng ĐÚNG chuỗi đã lưu trong DB, dựng lại từ cùng một hằng số tiền tố
        // mà lúc ghi đã dùng. Ghép chuỗi thủ công ở đây thì một lần đổi tiền tố sẽ làm
        // mọi kiểm tra trả về false, và triệu chứng là ảnh của chính mình cũng không xem
        // được — dễ bị chẩn đoán sai thành lỗi lưu tệp.
        String url = MediaStorageService.CHAT_URL_PREFIX + filename;
        return messageRepository.attachmentBelongsToPlayer(url, userId);
    }

    private static boolean isStaff(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList(SecurityConfig.ROLE_CLAIM);
        return roles != null && roles.stream().anyMatch(STAFF_ROLES::contains);
    }
}
