package com.rwg.chat.service;

import com.rwg.chat.domain.ChatConversation;
import com.rwg.chat.domain.ChatConversationStatus;
import com.rwg.chat.domain.ChatMessage;
import com.rwg.chat.domain.ChatSenderType;
import com.rwg.chat.dto.AdminChatConversationRowResponse;
import com.rwg.chat.dto.ChatAttachmentResponse;
import com.rwg.chat.dto.ChatEventPayload;
import com.rwg.chat.dto.ChatMessageResponse;
import com.rwg.chat.dto.ChatUnreadResponse;
import com.rwg.chat.dto.ChatWithdrawalCardResponse;
import com.rwg.chat.repository.ChatConversationRepository;
import com.rwg.chat.repository.ChatMessageRepository;
import com.rwg.common.ApiException;
import com.rwg.common.ErrorCode;
import com.rwg.common.PageResponse;
import com.rwg.config.ChatProperties;
import com.rwg.identity.domain.User;
import com.rwg.identity.repository.UserRepository;
import com.rwg.identity.service.AuditTrailService;
import com.rwg.media.service.MediaStorageService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Chat hỗ trợ — phía KHU QUẢN TRỊ.
 *
 * Tách khỏi {@code ChatService} — xem lý do trong javadoc của lớp đó.
 *
 * Phân quyền KHÔNG nằm ở lớp này mà ở {@code SecurityConfig} theo route (RISK chỉ
 * đọc, không POST được). Đây là điểm thực thi duy nhất của dự án, không rải
 * {@code @PreAuthorize} ở service.
 */
@Service
public class AdminChatService {

    /**
     * Chặn size ở tầng service, không chỉ ở controller.
     *
     * Ai gọi thẳng API với size=100000 sẽ bị kẹp về đây thay vì buộc DB dựng một
     * trang khổng lồ — cùng lý do với {@code MyNotificationService.MAX_PAGE_SIZE}.
     */
    private static final int MAX_PAGE_SIZE = 100;

    private final ChatConversationRepository conversationRepository;
    private final ChatMessageRepository messageRepository;
    private final UserRepository userRepository;
    private final ChatService chatService;
    private final ChatEventPublisher eventPublisher;
    private final AuditTrailService auditTrailService;
    private final ChatProperties chatProperties;
    private final MediaStorageService mediaStorageService;

    /** Dựng dữ liệu thẻ duyệt lệnh rút cho những tin là thẻ. */
    private final ChatWithdrawalCardResolver withdrawalCardResolver;

    public AdminChatService(ChatConversationRepository conversationRepository,
                            ChatMessageRepository messageRepository,
                            UserRepository userRepository,
                            ChatService chatService,
                            ChatEventPublisher eventPublisher,
                            AuditTrailService auditTrailService,
                            ChatProperties chatProperties,
                            MediaStorageService mediaStorageService,
                            ChatWithdrawalCardResolver withdrawalCardResolver) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.chatService = chatService;
        this.eventPublisher = eventPublisher;
        this.auditTrailService = auditTrailService;
        this.chatProperties = chatProperties;
        this.mediaStorageService = mediaStorageService;
        this.withdrawalCardResolver = withdrawalCardResolver;
    }

    /**
     * Nhân sự tải một ảnh lên.
     *
     * KHÔNG có hạn mức, khớp với cách {@link ChatRateLimiter} chỉ áp cho người chơi:
     * nhân sự gửi ảnh hướng dẫn cho nhiều luồng cùng lúc là công việc bình thường, và
     * họ không phải nguồn spam cần đề phòng — tài khoản của họ do chính sàn cấp.
     * Giới hạn dung lượng và loại tệp vẫn áp dụng như với người chơi.
     */
    public ChatAttachmentResponse uploadAttachment(MultipartFile file) {
        return ChatAttachmentResponse.image(mediaStorageService.storeChatImage(file));
    }

    /**
     * Hộp thư.
     *
     * @param status         null = mọi trạng thái.
     * @param assignedTo     chỉ luồng của nhân sự này; null = không lọc.
     * @param unassignedOnly true = chỉ luồng chưa ai nhận; null/false = không lọc.
     * @param keyword        tìm theo tên đăng nhập người chơi; null = không lọc.
     */
    @Transactional(readOnly = true)
    public PageResponse<AdminChatConversationRowResponse> inbox(ChatConversationStatus status,
                                                               UUID assignedTo,
                                                               Boolean unassignedOnly,
                                                               String keyword,
                                                               int page,
                                                               int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.clamp(size, 1, MAX_PAGE_SIZE));

        // Chuẩn hoá keyword TẠI ĐÂY, không trong repository: câu JPQL dùng `like`
        // nên wildcard phải nằm trong giá trị tham số. Để repository tự thêm '%' sẽ
        // khiến mỗi truy vấn mới lại phải nhớ quy ước đó.
        String normalized = (keyword == null || keyword.isBlank())
                ? null
                : "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";

        Page<ChatConversation> conversations = conversationRepository.searchForAdmin(
                status, assignedTo, Boolean.TRUE.equals(unassignedOnly) ? Boolean.TRUE : null,
                normalized, pageable);

        Map<UUID, String> usernames = usernamesFor(conversations.getContent());

        return PageResponse.from(conversations, c -> AdminChatConversationRowResponse.from(
                c,
                usernames.get(c.getUserId()),
                c.getAssignedAdminId() == null ? null : usernames.get(c.getAssignedAdminId())));
    }

    /**
     * Tên đăng nhập của mọi user liên quan tới một trang hộp thư, lấy trong MỘT truy vấn.
     *
     * Gom cả người chơi và nhân sự phụ trách vào cùng một tập id rồi gọi
     * {@code findAllById} một lần. Cách tự nhiên hơn là tra tên trong vòng lặp, nhưng
     * đó là N+1: một trang 20 dòng thành tối đa 40 truy vấn phụ, trên màn hình được
     * tải lại mỗi vài giây.
     */
    private Map<UUID, String> usernamesFor(List<ChatConversation> conversations) {
        Set<UUID> ids = new HashSet<>();
        for (ChatConversation c : conversations) {
            ids.add(c.getUserId());
            if (c.getAssignedAdminId() != null) {
                ids.add(c.getAssignedAdminId());
            }
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, User::getUsername, (a, b) -> a));
    }

    /**
     * Một trang lịch sử của một luồng bất kỳ (keyset, mới nhất trước).
     *
     * DÙNG {@code findPageBefore} — KHÔNG lọc theo {@code visibleTo}: nhân sự phải thấy
     * cả những tin nội bộ, đó chính là lý do chúng tồn tại.
     *
     * Dữ liệu lệnh rút được nạp MỘT LƯỢT cho cả trang rồi gắn vào từng thẻ — xem
     * {@link ChatWithdrawalCardResolver}.
     */
    @Transactional(readOnly = true)
    public List<ChatMessageResponse> messages(UUID conversationId, Instant before) {
        requireConversation(conversationId);

        List<ChatMessage> page = messageRepository
                .findPageBefore(conversationId, before,
                        PageRequest.of(0, chatProperties.pageSize()));

        Set<UUID> orderIds = page.stream()
                .map(ChatMessage::getWithdrawalOrderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));

        Map<UUID, ChatWithdrawalCardResponse> cards = withdrawalCardResolver.cardsFor(orderIds);

        return page.stream()
                .map(m -> {
                    ChatMessageResponse dto = ChatMessageResponse.from(m);
                    if (m.getWithdrawalOrderId() == null) {
                        return dto;
                    }
                    // Lệnh không tìm thấy (bản ghi đã bị dọn) để null: giao diện vẽ tin đó
                    // như một dòng hệ thống thường, thay vì làm sụp cả lịch sử hội thoại.
                    ChatWithdrawalCardResponse card = cards.get(m.getWithdrawalOrderId());
                    return card == null ? dto : dto.withWithdrawal(card);
                })
                .toList();
    }

    /**
     * Nhân sự trả lời một luồng.
     *
     * TỰ NHẬN VIỆC nếu luồng chưa có ai phụ trách: người đã bỏ công trả lời chính là
     * người đang xử lý. Bắt họ bấm "nhận việc" trước rồi mới gõ được là một bước thừa
     * mà ai cũng sẽ quên, và khi đó hàng đợi đầy những luồng đã được trả lời nhưng
     * vẫn hiện là "chưa ai nhận".
     */
    @Transactional
    public ChatMessageResponse replyAsStaff(UUID conversationId, UUID adminId, String adminUsername,
                                           String body, UUID clientMsgId,
                                           String attachmentUrl, String attachmentName,
                                           Long attachmentSize) {
        ChatConversation conversation = requireConversation(conversationId);

        if (clientMsgId != null) {
            var existing = messageRepository
                    .findByConversationIdAndClientMsgId(conversationId, clientMsgId);
            if (existing.isPresent()) {
                return ChatMessageResponse.from(existing.get());
            }
        }

        if (conversation.getAssignedAdminId() == null) {
            conversation.assignTo(adminId);
        }

        String text = body == null ? null : body.trim();

        ChatMessage saved = chatService.persist(conversation,
                ChatAttachments.applyTo(
                        ChatMessage.fromStaff(conversationId, adminId, adminUsername, text, clientMsgId),
                        text, attachmentUrl, attachmentName, attachmentSize),
                ChatSenderType.STAFF);

        eventPublisher.publishAfterCommit(ChatEventPayload.message(
                conversationId.toString(), conversation.getUserId().toString(),
                ChatMessageResponse.from(saved)));

        return ChatMessageResponse.from(saved);
    }

    /**
     * Giao luồng cho một nhân sự.
     *
     * Chèn một dòng SYSTEM để lịch sử ghi lại việc bàn giao ĐÚNG vị trí theo thời
     * gian, thay vì chỉ đổi một cột mà không ai biết nó đã đổi lúc nào.
     *
     * GHI audit vì đây là thao tác đổi trách nhiệm giữa người với người — cần trả lời
     * được "ai đã lấy luồng này khỏi tay ai" khi có tranh chấp về việc xử lý chậm.
     * Riêng từng TIN NHẮN thì không ghi audit: bản thân bảng chat_messages đã là lịch
     * sử đầy đủ, và dìm audit_log dưới hàng nghìn dòng "đã gửi tin" sẽ làm nó vô dụng
     * cho đúng việc nó tồn tại — tra soát hành vi nhạy cảm.
     */
    @Transactional
    public AdminChatConversationRowResponse assign(UUID conversationId, UUID adminId,
                                                   String adminUsername, String ipAddress) {
        ChatConversation conversation = requireConversation(conversationId);

        boolean changed = conversation.assignTo(adminId);
        if (changed) {
            chatService.persist(conversation,
                    ChatMessage.fromSystem(conversationId, "chat.system.assigned"),
                    ChatSenderType.SYSTEM);

            auditTrailService.record(adminId, adminUsername,
                    AuditTrailService.SUPPORT_CHAT_ASSIGNED,
                    "chat_conversation", conversationId.toString(),
                    Map.of("playerId", conversation.getUserId().toString()), ipAddress);

            eventPublisher.publishAfterCommit(ChatEventPayload.conversation(
                    conversationId.toString(), conversation.getUserId().toString(),
                    conversation.getStatus().name()));
        }
        conversationRepository.save(conversation);

        return toRow(conversation);
    }

    /**
     * Đóng luồng.
     *
     * Người chơi gửi tin mới sẽ tự mở lại (xem {@code ChatConversation.appendPlayerMessage}),
     * nên đóng ở đây nghĩa là "đã xử lý xong", không phải "chặn không cho nói nữa".
     */
    @Transactional
    public AdminChatConversationRowResponse close(UUID conversationId, UUID adminId,
                                                  String adminUsername, String ipAddress) {
        ChatConversation conversation = requireConversation(conversationId);

        boolean changed = conversation.close();
        if (changed) {
            chatService.persist(conversation,
                    ChatMessage.fromSystem(conversationId, "chat.system.closed"),
                    ChatSenderType.SYSTEM);

            auditTrailService.record(adminId, adminUsername,
                    AuditTrailService.SUPPORT_CHAT_CLOSED,
                    "chat_conversation", conversationId.toString(),
                    Map.of("playerId", conversation.getUserId().toString()), ipAddress);

            eventPublisher.publishAfterCommit(ChatEventPayload.conversation(
                    conversationId.toString(), conversation.getUserId().toString(),
                    conversation.getStatus().name()));
        }
        conversationRepository.save(conversation);

        return toRow(conversation);
    }

    /** Nhân sự đã xem: đóng dấu mọi tin của người chơi trong luồng và xoá bộ đếm. */
    @Transactional
    public int markRead(UUID conversationId) {
        ChatConversation conversation = requireConversation(conversationId);

        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        int updated = messageRepository.markReadFrom(conversationId, ChatSenderType.PLAYER, now);
        conversation.markReadBy(ChatSenderType.PLAYER);
        conversationRepository.save(conversation);

        if (updated > 0) {
            // Báo cho người chơi thấy dấu "đã xem": biết tin của mình đã được đọc là
            // thứ giảm hẳn số lần họ gửi lại cùng một câu hỏi.
            eventPublisher.publishAfterCommit(ChatEventPayload.read(
                    conversationId.toString(), conversation.getUserId().toString()));
        }
        return updated;
    }

    /** Tổng chưa đọc toàn hệ thống, cho viên tròn đỏ trên sidebar quản trị. */
    @Transactional(readOnly = true)
    public ChatUnreadResponse unread() {
        return ChatUnreadResponse.of(
                conversationRepository.totalUnreadForAdmin(),
                conversationRepository.countConversationsAwaitingReply());
    }

    // ===== helper =====

    private ChatConversation requireConversation(UUID conversationId) {
        return conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        ErrorCode.NOT_FOUND.defaultMessage(), null,
                        "error.not_found.chat_conversation"));
    }

    /** Dựng một dòng hộp thư cho MỘT hội thoại (sau khi ghi). */
    private AdminChatConversationRowResponse toRow(ChatConversation conversation) {
        Map<UUID, String> usernames = usernamesFor(List.of(conversation));
        return AdminChatConversationRowResponse.from(
                conversation,
                usernames.get(conversation.getUserId()),
                conversation.getAssignedAdminId() == null
                        ? null
                        : usernames.get(conversation.getAssignedAdminId()));
    }
}
