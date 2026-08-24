package com.rwg.chat.service;

import com.rwg.chat.domain.ChatConversation;
import com.rwg.chat.domain.ChatMessage;
import com.rwg.chat.domain.ChatSenderType;
import com.rwg.chat.dto.ChatAttachmentResponse;
import com.rwg.chat.dto.ChatConversationResponse;
import com.rwg.chat.dto.ChatEventPayload;
import com.rwg.chat.dto.ChatMessageResponse;
import com.rwg.chat.dto.ChatUnreadResponse;
import com.rwg.chat.repository.ChatConversationRepository;
import com.rwg.chat.repository.ChatMessageRepository;
import com.rwg.common.ApiException;
import com.rwg.common.ErrorCode;
import com.rwg.config.ChatProperties;
import com.rwg.identity.domain.User;
import com.rwg.identity.repository.UserRepository;
import com.rwg.media.service.MediaStorageService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Chat hỗ trợ — phía NGƯỜI CHƠI.
 *
 * Tách khỏi {@code AdminChatService} vì hai phía có ràng buộc khác nhau: người chơi
 * bị hạn mức gửi tin và chỉ chạm được vào luồng của chính mình, nhân sự thì không
 * bị hạn mức và chạm được mọi luồng. Gộp lại sẽ tạo ra những hàm nhận thêm tham số
 * "đang gọi với vai gì" và phân nhánh bên trong — nơi mà bỏ sót một nhánh nghĩa là
 * người chơi đọc được hội thoại của người khác.
 */
@Service
public class ChatService {

    private final ChatConversationRepository conversationRepository;
    private final ChatMessageRepository messageRepository;
    private final UserRepository userRepository;
    private final ChatRateLimiter rateLimiter;
    private final ChatUploadRateLimiter uploadRateLimiter;
    private final ChatEventPublisher eventPublisher;
    private final ChatProperties chatProperties;
    private final MediaStorageService mediaStorageService;

    public ChatService(ChatConversationRepository conversationRepository,
                       ChatMessageRepository messageRepository,
                       UserRepository userRepository,
                       ChatRateLimiter rateLimiter,
                       ChatUploadRateLimiter uploadRateLimiter,
                       ChatEventPublisher eventPublisher,
                       ChatProperties chatProperties,
                       MediaStorageService mediaStorageService) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.rateLimiter = rateLimiter;
        this.uploadRateLimiter = uploadRateLimiter;
        this.eventPublisher = eventPublisher;
        this.chatProperties = chatProperties;
        this.mediaStorageService = mediaStorageService;
    }

    /**
     * Luồng hội thoại của người chơi, tạo mới nếu chưa có.
     *
     * KHÔNG readOnly: lần đầu người chơi mở khung chat sẽ tạo luồng. Đặt luồng ở
     * bước này thay vì chờ tin đầu tiên để giao diện có sẵn id mà gắn subscribe,
     * và để nhân sự thấy được ai đã mở khung chat mà chưa gõ gì.
     */
    @Transactional
    public ChatConversationResponse myConversation(UUID userId) {
        return ChatConversationResponse.from(loadOrCreate(userId));
    }

    /**
     * Một trang lịch sử tin nhắn của chính mình, mới nhất trước.
     *
     * DÙNG {@code findPageBeforeVisibleToPlayer}, KHÔNG dùng {@code findPageBefore}: luồng
     * chứa cả những tin chỉ dành cho nhân sự (thẻ duyệt lệnh rút, có nút chuyển tiền).
     *
     * @param before mốc keyset — chỉ lấy tin cũ hơn mốc này. null = trang đầu.
     */
    @Transactional(readOnly = true)
    public List<ChatMessageResponse> myMessages(UUID userId, Instant before) {
        ChatConversation conversation = conversationRepository.findByUserId(userId).orElse(null);
        if (conversation == null) {
            // Chưa từng mở khung chat: trả danh sách rỗng, KHÔNG tạo luồng ở đây.
            // Đây là đường đọc, và một hàm đọc lại ghi dữ liệu là thứ không ai đoán được.
            return List.of();
        }
        return messageRepository
                .findPageBeforeVisibleToPlayer(conversation.getId(), before,
                        PageRequest.of(0, chatProperties.pageSize()))
                .stream()
                .map(ChatMessageResponse::from)
                .toList();
    }

    /**
     * Người chơi gửi một tin.
     *
     * Người chơi bị khoá tài khoản VẪN gửi được — cố tình. Bị khoá là đúng lúc họ
     * cần hỏi nhất, và chặn họ khiếu nại sẽ đẩy toàn bộ việc đó sang các kênh ngoài
     * (mạng xã hội, tổ chức trung gian) nơi sàn không kiểm soát được gì.
     *
     * `body` có thể rỗng khi có `attachmentUrl` — gửi ảnh không kèm chữ là hành vi
     * bình thường. Điều kiện "có chữ hoặc có ảnh" do {@link ChatAttachments} kiểm.
     */
    @Transactional
    public ChatMessageResponse sendAsPlayer(UUID userId, String body, UUID clientMsgId,
                                            String attachmentUrl, String attachmentName,
                                            Long attachmentSize) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));

        ChatConversation conversation = loadOrCreate(userId);

        // Chống gửi trùng TRƯỚC khi trừ hạn mức: một lần thử lại do mạng lỗi không
        // được tính là một tin mới, nếu không thì mạng kém sẽ tự làm cạn hạn mức
        // của chính người dùng.
        if (clientMsgId != null) {
            var existing = messageRepository
                    .findByConversationIdAndClientMsgId(conversation.getId(), clientMsgId);
            if (existing.isPresent()) {
                return ChatMessageResponse.from(existing.get());
            }
        }

        if (!rateLimiter.tryConsume(userId)) {
            // Tính số giây MỘT LẦN rồi dùng cho cả details và tham số i18n: gọi hai lần
            // sẽ cho hai giá trị khác nhau (thời gian trôi giữa hai lệnh), và người dùng
            // thấy câu "chờ 7 giây" trong khi trường dữ liệu nói 6.
            long retryAfter = rateLimiter.secondsUntilNextSend(userId);
            throw new ApiException(ErrorCode.RATE_LIMITED,
                    ErrorCode.RATE_LIMITED.defaultMessage(),
                    Map.of("retryAfterSeconds", retryAfter),
                    "error.chat.rate_limited", retryAfter);
        }

        // trim() có điều kiện: body giờ nullable, gọi thẳng .trim() sẽ ném NPE khi người
        // dùng gửi ảnh không kèm chữ — đúng trường hợp phổ biến nhất của tính năng mới.
        String text = body == null ? null : body.trim();

        ChatMessage message = ChatAttachments.applyTo(
                ChatMessage.fromPlayer(conversation.getId(), userId, user.getUsername(),
                        text, clientMsgId),
                text, attachmentUrl, attachmentName, attachmentSize);

        ChatMessage saved = persist(conversation, message, ChatSenderType.PLAYER);

        eventPublisher.publishAfterCommit(ChatEventPayload.message(
                conversation.getId().toString(), userId.toString(),
                ChatMessageResponse.from(saved)));

        return ChatMessageResponse.from(saved);
    }

    /**
     * Người chơi tải một ảnh lên, chưa gắn vào tin nhắn nào.
     *
     * KHÔNG @Transactional: hàm này chỉ ghi ra đĩa, không chạm DB. Mở transaction ở đây
     * sẽ giữ một connection của pool trong suốt thời gian tải tệp 10MB qua mạng — với
     * pool 20 connection thì 20 người dùng gửi ảnh cùng lúc là đủ làm nghẽn toàn bộ
     * phần còn lại của hệ thống.
     *
     * HẠN MỨC RIÊNG, thấp hơn hẳn hạn mức gửi tin: gõ 20 tin một phút là hành vi bình
     * thường, tải 20 tệp 10MB một phút thì không — đó là 200MB/phút cho mỗi người dùng.
     */
    public ChatAttachmentResponse uploadAttachment(UUID userId, MultipartFile file) {
        if (!uploadRateLimiter.tryConsume(userId)) {
            long retryAfter = uploadRateLimiter.secondsUntilNextUpload(userId);
            throw new ApiException(ErrorCode.RATE_LIMITED,
                    ErrorCode.RATE_LIMITED.defaultMessage(),
                    Map.of("retryAfterSeconds", retryAfter),
                    "error.chat.attachment.rate_limited", retryAfter);
        }
        return ChatAttachmentResponse.image(mediaStorageService.storeChatImage(file));
    }


    /**
     * Người chơi đã xem: xoá bộ đếm chưa đọc của họ và đóng dấu mọi tin của nhân sự.
     *
     * Báo ngược lại cho khu quản trị qua sự kiện READ để nhân sự thấy dấu "đã xem" —
     * đây là thông tin họ cần khi người chơi không trả lời: đã đọc mà im lặng khác
     * hẳn với chưa từng mở ra.
     */
    @Transactional
    public int markMyMessagesRead(UUID userId) {
        ChatConversation conversation = conversationRepository.findByUserId(userId).orElse(null);
        if (conversation == null) {
            return 0;
        }

        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        int updated = messageRepository.markReadFrom(conversation.getId(),
                ChatSenderType.STAFF, now);
        conversation.markReadBy(ChatSenderType.STAFF);
        conversationRepository.save(conversation);

        if (updated > 0) {
            eventPublisher.publishAfterCommit(ChatEventPayload.read(
                    conversation.getId().toString(), userId.toString()));
        }
        return updated;
    }

    /** Số tin nhân sự gửi mà người chơi chưa xem, cho viên tròn đỏ. */
    @Transactional(readOnly = true)
    public ChatUnreadResponse myUnread(UUID userId) {
        return conversationRepository.findByUserId(userId)
                .map(c -> ChatUnreadResponse.of(c.getUnreadForPlayer(), 0))
                .orElseGet(() -> ChatUnreadResponse.of(0, 0));
    }

    /**
     * Chèn thẻ duyệt một lệnh rút vào luồng hỗ trợ của người gửi lệnh.
     *
     * Gọi từ {@code ChatWithdrawalCardListener} sau khi transaction rút tiền đã commit.
     *
     * DÙNG {@code loadOrCreate}: người chơi có thể chưa từng mở khung chat nên chưa có
     * luồng nào. Không tạo thì lệnh rút của đúng những người chưa bao giờ liên hệ hỗ trợ
     * sẽ không có thẻ — và đó lại là phần lớn người chơi.
     *
     * Phát gói realtime dạng CHỈ-NHÂN-SỰ để thẻ hiện ngay trên màn hình đang mở mà không
     * tới trình duyệt người chơi — xem {@code ChatEventPublisher.deliverLocally}.
     *
     * Gói realtime KHÔNG mang dữ liệu lệnh rút: tại đây chưa có sẵn thông tin ngân hàng
     * (nằm ở package bank) và đi lấy chúng sẽ biến một hàm ghi chat thành phụ thuộc vào
     * hai package khác. Giao diện quản trị tự tải lại luồng khi nhận gói có thẻ.
     *
     * REQUIRES_NEW, KHÔNG phải mặc định REQUIRED. Hàm này được gọi từ một listener ở phase
     * AFTER_COMMIT, nơi transaction của lệnh rút vẫn còn bound vào luồng nhưng đã commit
     * xong. Với REQUIRED, Spring thấy có transaction sẵn và JOIN vào nó thay vì mở cái mới,
     * rồi mọi lệnh ghi đều chết với "No active transaction" — và vì listener bắt hết ngoại
     * lệ, lỗi đó chỉ để lại một dòng log warn còn thẻ thì không bao giờ xuất hiện.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void appendWithdrawalCard(UUID userId, UUID withdrawalOrderId) {
        ChatConversation conversation = loadOrCreate(userId);

        ChatMessage saved = persist(conversation,
                ChatMessage.withdrawalCard(conversation.getId(), withdrawalOrderId),
                ChatSenderType.SYSTEM);

        eventPublisher.publishAfterCommit(ChatEventPayload.staffOnlyMessage(
                conversation.getId().toString(), userId.toString(),
                ChatMessageResponse.from(saved)));
    }

    // ===== dùng chung =====

    /**
     * Lấy luồng của người chơi, tạo mới nếu chưa có.
     *
     * Bắt {@link DataIntegrityViolationException} rồi đọc lại: hai request đầu tiên
     * của cùng một người gửi song song đều thấy "chưa có luồng" và đều tạo mới, và
     * UNIQUE trên user_id sẽ chặn cái thứ hai. Không bắt thì người chơi nhận lỗi 500
     * ngay lần đầu mở khung chat nếu giao diện tình cờ gọi hai lần.
     */
    private ChatConversation loadOrCreate(UUID userId) {
        return conversationRepository.findByUserId(userId).orElseGet(() -> {
            try {
                return conversationRepository.saveAndFlush(ChatConversation.openFor(userId));
            } catch (DataIntegrityViolationException raced) {
                return conversationRepository.findByUserId(userId).orElseThrow(() -> raced);
            }
        });
    }

    /**
     * Ghi tin nhắn VÀ cập nhật luồng trong CÙNG transaction.
     *
     * Hai việc này không được tách: bốn cột phi chuẩn hoá của {@code chat_conversations}
     * (tin cuối, đoạn xem trước, hai bộ đếm chưa đọc) là bản sao của dữ liệu trong
     * {@code chat_messages}, nên nếu chỉ một trong hai lượt ghi thành công thì hộp
     * thư sẽ hiển thị số liệu không khớp với nội dung thật — và không có gì phát hiện
     * ra điều đó.
     *
     * Bắt lỗi UNIQUE của client_msg_id ở đây làm lớp chặn CUỐI: bước kiểm tra trước
     * đó vẫn để hở một khoảng cho hai request song song cùng lọt qua.
     */
    ChatMessage persist(ChatConversation conversation, ChatMessage message,
                        ChatSenderType senderType) {
        ChatMessage saved;
        try {
            saved = messageRepository.saveAndFlush(message);
        } catch (DataIntegrityViolationException duplicate) {
            if (message.getClientMsgId() == null) {
                throw duplicate;
            }
            return messageRepository
                    .findByConversationIdAndClientMsgId(conversation.getId(), message.getClientMsgId())
                    .orElseThrow(() -> duplicate);
        }

        switch (senderType) {
            case PLAYER -> conversation.appendPlayerMessage(saved);
            case STAFF -> conversation.appendStaffMessage(saved);
            case SYSTEM -> conversation.appendSystemMessage(saved);
        }
        conversationRepository.save(conversation);
        return saved;
    }
}
