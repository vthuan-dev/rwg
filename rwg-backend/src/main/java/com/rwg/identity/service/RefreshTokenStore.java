package com.rwg.identity.service;

import java.time.Duration;
import java.util.UUID;

/**
 * Abstraction lưu refresh token (rotation + phát hiện reuse).
 * - Prod/docker: {@link RedisRefreshTokenStore} (Redis + TTL, GETDEL nguyên tử).
 * - Dev không có Redis: {@link InMemoryRefreshTokenStore} (chỉ 1 instance).
 *
 * Mỗi lần đăng nhập mở một "family" (chuỗi rotation). Khi rotate, token mới giữ
 * nguyên familyId. Nếu một token ĐÃ bị tiêu thụ bị gửi lại (reuse) -> coi là bị đánh
 * cắp: thu hồi TOÀN BỘ family và buộc đăng nhập lại.
 */
public interface RefreshTokenStore {

    /** Trạng thái khi tiêu thụ (consume) một refresh token. */
    enum ConsumeStatus { OK, REUSE, INVALID }

    /** Kết quả consume: trạng thái + user + family (chỉ có khi OK). */
    record ConsumeResult(ConsumeStatus status, UUID userId, String familyId) {

        public static ConsumeResult ok(UUID userId, String familyId) {
            return new ConsumeResult(ConsumeStatus.OK, userId, familyId);
        }

        public static ConsumeResult reuse() {
            return new ConsumeResult(ConsumeStatus.REUSE, null, null);
        }

        public static ConsumeResult invalid() {
            return new ConsumeResult(ConsumeStatus.INVALID, null, null);
        }
    }

    /** Lưu token mới thuộc một family, hết hạn sau ttl. */
    void save(String tokenId, UUID userId, String familyId, Duration ttl);

    /**
     * Tiêu thụ token NGUYÊN TỬ (remove-and-return) — chống race giữa các request song song:
     * - Token đang hoạt động -> {@code OK} + đánh dấu "đã dùng" (cho phát hiện reuse).
     * - Token ĐÃ bị tiêu thụ trước đó (reuse) -> {@code REUSE}, đồng thời thu hồi cả family.
     * - Không tồn tại / hết hạn -> {@code INVALID}.
     */
    ConsumeResult consume(String tokenId);

    /** Thu hồi toàn bộ family (token hoạt động hiện tại). Dùng khi logout hoặc phát hiện reuse. */
    void revokeFamily(String familyId);

    /**
     * Thu hồi TOÀN BỘ refresh token đang hoạt động của một user (mọi family).
     * Dùng khi đổi mật khẩu đăng nhập — buộc đăng nhập lại trên mọi thiết bị.
     */
    void revokeAllForUser(UUID userId);
}
