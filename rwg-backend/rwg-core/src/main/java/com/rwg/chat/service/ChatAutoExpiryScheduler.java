package com.rwg.chat.service;

import com.rwg.chat.domain.ChatConversation;
import com.rwg.chat.domain.ChatMessage;
import com.rwg.chat.dto.ChatEventPayload;
import com.rwg.chat.repository.ChatConversationRepository;
import com.rwg.chat.repository.ChatMessageRepository;
import com.rwg.config.ChatProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Quét dọn định kỳ và tự động xóa các tin nhắn CSKH đã quá 30 phút.
 *
 * Sau khi soft-delete các tin quá hạn:
 * 1. Cập nhật lại tin nhắn mới nhất / preview cho các hội thoại tương ứng.
 * 2. Phát sự kiện WebSocket MESSAGES_DELETED đến cả Admin và Player để giao diện
 *    tự động ẩn các tin quá hạn theo thời gian thực mà không cần tải lại trang.
 */
@Component
@EnableScheduling
public class ChatAutoExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(ChatAutoExpiryScheduler.class);

    private final ChatProperties chatProperties;
    private final ChatMessageRepository messageRepository;
    private final ChatConversationRepository conversationRepository;
    private final ChatEventPublisher eventPublisher;

    public ChatAutoExpiryScheduler(ChatProperties chatProperties,
                                   ChatMessageRepository messageRepository,
                                   ChatConversationRepository conversationRepository,
                                   ChatEventPublisher eventPublisher) {
        this.chatProperties = chatProperties;
        this.messageRepository = messageRepository;
        this.conversationRepository = conversationRepository;
        this.eventPublisher = eventPublisher;
    }

    @Scheduled(fixedDelayString = "${rwg.chat.auto-delete-interval:PT30S}")
    @Transactional
    public void sweepExpiredMessages() {
        try {
            Instant cutoff = Instant.now().minus(chatProperties.autoDeleteAfter());
            // Quét theo lô 200 tin mỗi lượt để tránh nghẽn transaction
            List<ChatMessage> expired = messageRepository.findExpiredMessages(cutoff, PageRequest.of(0, 200));
            if (expired.isEmpty()) {
                return;
            }

            Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
            for (ChatMessage msg : expired) {
                msg.markDeleted(null, "SYSTEM_AUTO_30M");
            }
            messageRepository.saveAll(expired);

            Map<UUID, List<ChatMessage>> byConversation = expired.stream()
                    .collect(Collectors.groupingBy(ChatMessage::getConversationId));

            for (Map.Entry<UUID, List<ChatMessage>> entry : byConversation.entrySet()) {
                UUID conversationId = entry.getKey();
                List<ChatMessage> deletedInConv = entry.getValue();

                ChatConversation conversation = conversationRepository.findById(conversationId).orElse(null);
                if (conversation == null) {
                    continue;
                }

                // Cập nhật lại tin mới nhất còn hiệu lực cho hội thoại
                List<ChatMessage> latestActive = messageRepository.findLatestActiveInConversation(
                        conversationId, cutoff, PageRequest.of(0, 1));
                if (latestActive.isEmpty()) {
                    conversation.updateLastMessage(null);
                } else {
                    conversation.updateLastMessage(latestActive.get(0));
                }
                conversationRepository.save(conversation);

                // Phát sự kiện xóa tin thời gian thực
                String[] deletedIds = deletedInConv.stream()
                        .map(m -> m.getId().toString())
                        .toArray(String[]::new);

                eventPublisher.publishAfterCommit(ChatEventPayload.messagesDeleted(
                        conversationId.toString(),
                        conversation.getUserId().toString(),
                        deletedIds));
            }

            log.info("ChatAutoExpiryScheduler: Đã tự động xóa {} tin nhắn quá hạn 30 phút.", expired.size());
        } catch (Exception ex) {
            log.error("Lỗi khi quét dọn tin nhắn chat tự động quá hạn: {}", ex.getMessage(), ex);
        }
    }
}
