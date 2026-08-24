package com.rwg.identity.service;

import com.rwg.config.SecurityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test nhánh Redis của refresh token store — KHÔNG cần Docker.
 *
 * Mock {@link StringRedisTemplate} để kiểm chứng: thu hồi hết token active của user,
 * xóa key family tương ứng và xóa index user.
 *
 * VIỆC ĐỌC-VÀ-XÓA đi qua một script Lua chứ không phải {@code GETDEL}, nên test mock ở
 * mức {@code redis.execute(script, keys)}. Lý do đổi nằm trong Javadoc của
 * {@link RedisRefreshTokenStore}: {@code GETDEL} cần Redis 6.2+, còn môi trường phát
 * triển chạy Redis 3.0 và trả về "ERR unknown command".
 */
@ExtendWith(MockitoExtension.class)
class RedisRefreshTokenStoreTest {

    @Mock
    StringRedisTemplate redis;

    @Mock
    ValueOperations<String, String> valueOps;

    @Mock
    SetOperations<String, String> setOps;

    RedisRefreshTokenStore store;

    private static final String ACTIVE = "rwg:auth:refresh:active:";
    private static final String USED = "rwg:auth:refresh:used:";
    private static final String FAMILY = "rwg:auth:refresh:family:";
    private static final String USER = "rwg:auth:refresh:user:";

    /** Nội dung Redis giả lập cho script đọc-và-xóa. */
    private final Map<String, String> stored = new HashMap<>();

    @BeforeEach
    void setUp() {
        SecurityProperties props = new SecurityProperties(
                "test-only-rwg-jwt-secret-0123456789abcdef-9876543210",
                "rwg-backend", Duration.ofMinutes(15), Duration.ofDays(30));
        store = new RedisRefreshTokenStore(redis, props);
    }

    /**
     * Giả lập script đọc-và-xóa: trả giá trị đang có rồi bỏ key khỏi map.
     *
     * Có XÓA thật trong map, không chỉ trả giá trị: nếu mã nguồn gọi script hai lần trên
     * cùng một key thì lần thứ hai phải nhận null, đúng như Redis thật. Một mock chỉ trả
     * giá trị sẽ che mất lỗi gọi lặp.
     */
    private void stubGetAndDeleteScript() {
        when(redis.execute(ArgumentMatchers.<RedisScript<String>>any(), anyList()))
                .thenAnswer(invocation -> {
                    List<String> keys = invocation.getArgument(1);
                    return stored.remove(keys.get(0));
                });
    }

    @Test
    void revokeAllForUserRevokesActiveDeletesFamiliesAndUserIndex() {
        UUID userId = UUID.randomUUID();
        String token1 = "token-1";
        String token2 = "token-2";
        String family1 = "family-1";
        String family2 = "family-2";

        // Giá trị active lưu dạng "userId|familyId".
        stored.put(ACTIVE + token1, userId + "|" + family1);
        stored.put(ACTIVE + token2, userId + "|" + family2);

        when(redis.opsForSet()).thenReturn(setOps);
        when(setOps.members(USER + userId)).thenReturn(Set.of(token1, token2));
        stubGetAndDeleteScript();

        store.revokeAllForUser(userId);

        // Mỗi token active bị thu hồi -> xóa luôn family của nó.
        verify(redis).execute(ArgumentMatchers.<RedisScript<String>>any(),
                eq(List.of(ACTIVE + token1)));
        verify(redis).execute(ArgumentMatchers.<RedisScript<String>>any(),
                eq(List.of(ACTIVE + token2)));
        verify(redis).delete(FAMILY + family1);
        verify(redis).delete(FAMILY + family2);
        // Xóa index user để lần revoke sau không lặp lại.
        verify(redis).delete(USER + userId);
    }

    @Test
    void revokeAllForUserWithoutTokensStillDeletesUserIndex() {
        UUID userId = UUID.randomUUID();
        when(redis.opsForSet()).thenReturn(setOps);
        when(setOps.members(USER + userId)).thenReturn(Set.of());

        store.revokeAllForUser(userId);

        // Không có token -> KHÔNG chạy script, KHÔNG xóa family; chỉ xóa index user.
        verify(redis, never()).execute(ArgumentMatchers.<RedisScript<String>>any(), anyList());
        verify(redis, times(1)).delete(anyString());
        verify(redis).delete(USER + userId);
    }

    @Test
    void revokeAllForUserSkipsExpiredTokensFamily() {
        UUID userId = UUID.randomUUID();
        String tokenExpired = "token-expired";
        when(redis.opsForSet()).thenReturn(setOps);
        when(setOps.members(USER + userId)).thenReturn(Set.of(tokenExpired));
        // Không nạp vào `stored` -> token đã hết hạn phía Redis, script trả null.
        stubGetAndDeleteScript();

        store.revokeAllForUser(userId);

        // Vẫn chạy script trên token đó, nhưng value null -> KHÔNG xóa family;
        // delete(String) chỉ gọi ĐÚNG 1 lần cho index user.
        verify(redis).execute(ArgumentMatchers.<RedisScript<String>>any(),
                eq(List.of(ACTIVE + tokenExpired)));
        verify(redis, times(1)).delete(anyString());
        verify(redis).delete(USER + userId);
    }

    @Test
    void consumeReturnsOwnerAndMarksTokenUsed() {
        UUID userId = UUID.randomUUID();
        String tokenId = "token-active";
        String familyId = "family-active";
        stored.put(ACTIVE + tokenId, userId + "|" + familyId);

        when(redis.opsForValue()).thenReturn(valueOps);
        stubGetAndDeleteScript();

        RefreshTokenStore.ConsumeResult result = store.consume(tokenId);

        assertThat(result.status()).isEqualTo(RefreshTokenStore.ConsumeStatus.OK);
        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.familyId()).isEqualTo(familyId);
        // Đánh dấu đã dùng là điều kiện để phát hiện tái dùng ở lần sau; thiếu bước này
        // thì một token bị đánh cắp chỉ trông như token không hợp lệ.
        verify(valueOps).set(eq(USED + tokenId), eq(familyId), eq(Duration.ofDays(30)));
    }

    @Test
    void consumeDetectsReuseAndRevokesWholeFamily() {
        String tokenId = "token-replayed";
        String familyId = "family-compromised";
        // Không còn trong ACTIVE (đã tiêu thụ) nhưng CÓ trong USED -> đây là tái dùng.
        String currentToken = "token-current";
        stored.put(FAMILY + familyId, currentToken);

        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(USED + tokenId)).thenReturn(familyId);
        stubGetAndDeleteScript();

        RefreshTokenStore.ConsumeResult result = store.consume(tokenId);

        assertThat(result.status()).isEqualTo(RefreshTokenStore.ConsumeStatus.REUSE);
        // Cả family bị thu hồi: token đang hoạt động của kẻ kia cũng phải chết, nếu không
        // thì phát hiện được tái dùng mà vẫn để phiên bị chiếm tiếp tục sống.
        verify(redis).delete(ACTIVE + currentToken);
    }
}
