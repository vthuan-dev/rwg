package com.rwg.identity.service;

import com.rwg.config.SecurityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test nhánh Redis của revokeAllForUser (m8) — KHÔNG cần Docker.
 * Mock StringRedisTemplate để kiểm chứng: thu hồi hết token active của user,
 * xóa key family tương ứng và xóa index user.
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
    private static final String FAMILY = "rwg:auth:refresh:family:";
    private static final String USER = "rwg:auth:refresh:user:";

    @BeforeEach
    void setUp() {
        SecurityProperties props = new SecurityProperties(
                "test-only-rwg-jwt-secret-0123456789abcdef-9876543210",
                "rwg-backend", Duration.ofMinutes(15), Duration.ofDays(30));
        store = new RedisRefreshTokenStore(redis, props);
    }

    @Test
    void revokeAllForUserRevokesActiveDeletesFamiliesAndUserIndex() {
        UUID userId = UUID.randomUUID();
        String token1 = "token-1";
        String token2 = "token-2";
        String family1 = "family-1";
        String family2 = "family-2";

        when(redis.opsForSet()).thenReturn(setOps);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(setOps.members(USER + userId)).thenReturn(Set.of(token1, token2));
        // Giá trị active lưu dạng "userId|familyId".
        when(valueOps.getAndDelete(ACTIVE + token1)).thenReturn(userId + "|" + family1);
        when(valueOps.getAndDelete(ACTIVE + token2)).thenReturn(userId + "|" + family2);

        store.revokeAllForUser(userId);

        // Mỗi token active bị thu hồi -> xóa luôn family của nó.
        verify(valueOps).getAndDelete(ACTIVE + token1);
        verify(valueOps).getAndDelete(ACTIVE + token2);
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

        // Không có token -> KHÔNG đọc active, KHÔNG xóa family; chỉ xóa index user.
        verify(redis, never()).opsForValue();
        verify(redis, times(1)).delete(anyString());
        verify(redis).delete(USER + userId);
    }

    @Test
    void revokeAllForUserSkipsExpiredTokensFamily() {
        UUID userId = UUID.randomUUID();
        String tokenExpired = "token-expired";
        when(redis.opsForSet()).thenReturn(setOps);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(setOps.members(USER + userId)).thenReturn(Set.of(tokenExpired));
        // Token đã hết hạn phía Redis -> getAndDelete trả null.
        when(valueOps.getAndDelete(ACTIVE + tokenExpired)).thenReturn(null);

        store.revokeAllForUser(userId);

        // Vẫn đọc active token đó, nhưng value null -> KHÔNG xóa family;
        // delete(String) chỉ gọi ĐÚNG 1 lần cho index user.
        verify(valueOps).getAndDelete(ACTIVE + tokenExpired);
        verify(redis, times(1)).delete(anyString());
        verify(redis).delete(USER + userId);
    }
}
