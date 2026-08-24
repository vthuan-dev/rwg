package com.rwg.chat.service;

import com.rwg.config.ChatProperties;
import com.rwg.identity.service.LoginRateLimiter;
import com.rwg.identity.service.RateLimitStore;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Hạn mức gửi tin nhắn của một người chơi.
 *
 * TÁI DÙNG {@link RateLimitStore} thay vì tự đếm trong bộ nhớ: store đó đã có hai
 * cách hiện thực (Redis cho nhiều instance, in-memory cho dev) và đã được kiểm thử.
 * Tự đếm bằng một {@code Map} trong service sẽ mất hiệu lực ngay khi chạy hai
 * instance — mỗi instance cho người dùng đủ hạn mức riêng.
 *
 * KHÔNG dùng bucket của {@link LoginRateLimiter}: nó có khái niệm "khoá tài khoản
 * 15 phút" và mức captcha, đều vô nghĩa với việc gửi tin. Gửi nhanh quá thì tin bị
 * từ chối trong giây lát, KHÔNG khoá tài khoản — một người chơi bị khoá tài khoản
 * vì gõ nhanh trong lúc đang khiếu nại là hỏng hoàn toàn.
 *
 * Chỉ áp cho PLAYER. Nhân sự quản trị không bị hạn mức: họ trả lời nhiều luồng cùng
 * lúc là công việc bình thường, và họ không phải nguồn spam cần đề phòng.
 */
@Component
public class ChatRateLimiter {

    private static final String KEY_PREFIX = "rwg:chat:send-limit:user:";

    private final RateLimitStore store;
    private final BucketConfiguration configuration;

    public ChatRateLimiter(RateLimitStore store, ChatProperties chatProperties) {
        this.store = store;
        int capacity = chatProperties.rateLimitPerWindow();
        // Refill TOÀN BỘ mỗi cửa sổ, khớp cách LoginRateLimiter cấu hình bucket:
        // đơn giản để giải thích cho người dùng ("20 tin mỗi phút") hơn là refill
        // rải đều theo từng token.
        Bandwidth limit = Bandwidth.builder()
                .capacity(capacity)
                .refillIntervally(capacity, chatProperties.rateWindow())
                .build();
        this.configuration = BucketConfiguration.builder().addLimit(limit).build();
    }

    /**
     * Trừ một lượt gửi.
     *
     * @return true nếu được phép gửi; false nếu đã vượt hạn mức.
     */
    public boolean tryConsume(UUID userId) {
        return store.bucketFor(KEY_PREFIX + userId, configuration).tryConsume(1);
    }

    /** Số giây phải chờ trước khi gửi được tiếp — để thông báo lỗi nói rõ chờ bao lâu. */
    public long secondsUntilNextSend(UUID userId) {
        long nanos = store.bucketFor(KEY_PREFIX + userId, configuration)
                .estimateAbilityToConsume(1)
                .getNanosToWaitForRefill();
        return Math.max(nanos / 1_000_000_000L, 1);
    }
}
