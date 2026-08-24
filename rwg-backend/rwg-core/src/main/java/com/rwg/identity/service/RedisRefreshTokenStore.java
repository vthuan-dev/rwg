package com.rwg.identity.service;

import com.rwg.config.SecurityProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Refresh token lưu trong Redis (rotation + phát hiện reuse).
 * Kích hoạt khi rwg.redis.enabled=true (mặc định).
 *
 * Tiêu thụ token phải NGUYÊN TỬ: 2 request song song cùng 1 token chỉ được đúng 1
 * request lấy được giá trị. Token đã tiêu thụ được đánh dấu trong key "used" để phát
 * hiện reuse -> thu hồi cả family.
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

    /**
     * GET rồi DEL trong MỘT lệnh, thay cho {@code GETDEL}.
     *
     * VÌ SAO KHÔNG DÙNG {@code GETDEL} (opsForValue().getAndDelete()): lệnh đó chỉ có từ
     * Redis 6.2. Máy phát triển đang chạy Redis 3.0.504 (bản port Windows) và trả về
     * {@code ERR unknown command 'GETDEL'}, làm MỌI lần gia hạn token đổ lỗi 500 —
     * người dùng bị đăng xuất sau 15 phút.
     *
     * VÌ SAO KHÔNG TÁCH THÀNH GET rồi DEL: hai lệnh riêng không nguyên tử. Hai request
     * song song cùng một token sẽ ĐỀU đọc được giá trị trước khi lệnh DEL nào chạy, và
     * cả hai đều được cấp token mới. Điều đó phá đúng thứ mà cơ chế này tồn tại để phát
     * hiện: một token bị đánh cắp dùng song song với token thật sẽ không còn bị nhận ra.
     *
     * Script Lua chạy nguyên tử trên server (Redis 2.6+), nên giữ nguyên bảo đảm cũ mà
     * không cần nâng cấp Redis.
     */
    private static final RedisScript<String> GET_AND_DELETE = new DefaultRedisScript<>(
            """
            local value = redis.call('GET', KEYS[1])
            if value then
                redis.call('DEL', KEYS[1])
            end
            return value
            """,
            String.class);

    private final StringRedisTemplate redis;
    private final SecurityProperties securityProperties;

    public RedisRefreshTokenStore(StringRedisTemplate redis, SecurityProperties securityProperties) {
        this.redis = redis;
        this.securityProperties = securityProperties;
    }

    /** Đọc và xóa nguyên tử. Trả null khi key không tồn tại. */
    private String getAndDelete(String key) {
        return redis.execute(GET_AND_DELETE, List.of(key));
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
        // Đọc VÀ xóa nguyên tử — chỉ 1 caller thắng.
        String value = getAndDelete(ACTIVE + tokenId);
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
        String currentToken = getAndDelete(FAMILY + familyId);
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
                String value = getAndDelete(ACTIVE + tokenId);
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
