package com.rwg.identity.service;

import com.rwg.config.SecurityProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * Refresh token lưu trong Redis (rotation + phát hiện reuse).
 * Kích hoạt khi rwg.redis.enabled=true (mặc định).
 *
 * Dùng {@code GETDEL} (getAndDelete) để tiêu thụ token NGUYÊN TỬ: 2 request song song
 * cùng 1 token chỉ có đúng 1 request lấy được giá trị. Token đã tiêu thụ được đánh dấu
 * trong key "used" để phát hiện reuse -> thu hồi cả family.
 */
@Component
@ConditionalOnProperty(name = "rwg.redis.enabled", havingValue = "true", matchIfMissing = true)
public class RedisRefreshTokenStore implements RefreshTokenStore {

    private static final String ACTIVE = "rwg:auth:refresh:active:";
    private static final String USED = "rwg:auth:refresh:used:";
    private static final String FAMILY = "rwg:auth:refresh:family:";
    /** Index phụ userId -> tập tokenId (cho revokeAllForUser khi đổi mật khẩu). */
    private static final String USER = "rwg:auth:refresh:user:";
    private static final String SEP = "|";

    private final StringRedisTemplate redis;
    private final SecurityProperties securityProperties;

    public RedisRefreshTokenStore(StringRedisTemplate redis, SecurityProperties securityProperties) {
        this.redis = redis;
        this.securityProperties = securityProperties;
    }

    @Override
    public void save(String tokenId, UUID userId, String familyId, Duration ttl) {
        String value = userId + SEP + familyId;
        redis.opsForValue().set(ACTIVE + tokenId, value, ttl);
        redis.opsForValue().set(FAMILY + familyId, tokenId, ttl);
        // Index theo user để thu hồi hàng loạt khi đổi mật khẩu.
        redis.opsForSet().add(USER + userId, tokenId);
        redis.expire(USER + userId, ttl);
    }

    @Override
    public ConsumeResult consume(String tokenId) {
        // GETDEL: đọc VÀ xóa nguyên tử — chỉ 1 caller thắng.
        String value = redis.opsForValue().getAndDelete(ACTIVE + tokenId);
        if (value != null) {
            int sep = value.indexOf(SEP);
            if (sep <= 0 || sep == value.length() - 1) {
                return ConsumeResult.invalid();
            }
            String userIdStr = value.substring(0, sep);
            String familyId = value.substring(sep + 1);
            // Đánh dấu đã dùng để phát hiện reuse về sau.
            redis.opsForValue().set(USED + tokenId, familyId, securityProperties.refreshTokenTtl());
            try {
                return ConsumeResult.ok(UUID.fromString(userIdStr), familyId);
            } catch (IllegalArgumentException e) {
                return ConsumeResult.invalid();
            }
        }
        // Không còn hoạt động: kiểm tra có phải token ĐÃ tiêu thụ (reuse) không.
        String familyId = redis.opsForValue().get(USED + tokenId);
        if (familyId != null) {
            revokeFamily(familyId);
            return ConsumeResult.reuse();
        }
        return ConsumeResult.invalid();
    }

    @Override
    public void revokeFamily(String familyId) {
        String currentToken = redis.opsForValue().getAndDelete(FAMILY + familyId);
        if (currentToken != null) {
            redis.delete(ACTIVE + currentToken);
        }
    }

    @Override
    public void revokeAllForUser(UUID userId) {
        // Thu hồi MỌI token hoạt động của user (mọi family) — buộc đăng nhập lại.
        java.util.Set<String> tokenIds = redis.opsForSet().members(USER + userId);
        if (tokenIds != null) {
            for (String tokenId : tokenIds) {
                String value = redis.opsForValue().getAndDelete(ACTIVE + tokenId);
                if (value != null) {
                    int sep = value.indexOf(SEP);
                    if (sep > 0 && sep < value.length() - 1) {
                        redis.delete(FAMILY + value.substring(sep + 1));
                    }
                }
            }
        }
        redis.delete(USER + userId);
    }
}
