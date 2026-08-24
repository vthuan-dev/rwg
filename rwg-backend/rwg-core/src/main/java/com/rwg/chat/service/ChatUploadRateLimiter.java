package com.rwg.chat.service;

import com.rwg.config.MediaProperties;
import com.rwg.identity.service.RateLimitStore;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Hạn mức TẢI TỆP của một người chơi.
 *
 * TÁCH KHỎI {@link ChatRateLimiter} chứ không dùng chung bucket: hai hành vi có chi phí
 * chênh nhau hàng nghìn lần. Một tin nhắn là 2KB vào DB; một ảnh là tối đa 10MB xuống
 * đĩa. Dùng chung hạn mức 20/phút nghĩa là một người dùng có thể ghi 200MB mỗi phút, và
 * chỉ cần vài người làm vậy trong một giờ là đĩa server đầy — lúc đó không chỉ chat mà
 * cả việc ghi log và ghi DB đều dừng.
 *
 * Bucket riêng cũng để hai hạn mức KHÔNG ăn vào nhau: người chơi vừa gửi một ảnh vẫn
 * phải gõ tiếp được 19 tin nhắn bình thường.
 *
 * TÁI DÙNG {@link RateLimitStore} vì nó đã có hai hiện thực (Redis cho nhiều instance,
 * in-memory cho dev) và đã được kiểm thử.
 */
@Component
public class ChatUploadRateLimiter {

    private static final String KEY_PREFIX = "rwg:chat:upload-limit:user:";

    private final RateLimitStore store;
    private final BucketConfiguration configuration;

    public ChatUploadRateLimiter(RateLimitStore store, MediaProperties mediaProperties) {
        this.store = store;
        int capacity = mediaProperties.chatUploadLimitPerWindow();
        Bandwidth limit = Bandwidth.builder()
                .capacity(capacity)
                .refillIntervally(capacity, mediaProperties.chatUploadWindow())
                .build();
        this.configuration = BucketConfiguration.builder().addLimit(limit).build();
    }

    /**
     * Trừ một lượt tải tệp.
     *
     * @return true nếu được phép tải lên; false nếu đã vượt hạn mức.
     */
    public boolean tryConsume(UUID userId) {
        return store.bucketFor(KEY_PREFIX + userId, configuration).tryConsume(1);
    }

    /** Số giây phải chờ trước khi tải được tệp tiếp theo. */
    public long secondsUntilNextUpload(UUID userId) {
        long nanos = store.bucketFor(KEY_PREFIX + userId, configuration)
                .estimateAbilityToConsume(1)
                .getNanosToWaitForRefill();
        return Math.max(nanos / 1_000_000_000L, 1);
    }
}
