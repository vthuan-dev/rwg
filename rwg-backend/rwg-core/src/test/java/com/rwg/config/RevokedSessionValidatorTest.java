package com.rwg.config;

import com.rwg.identity.service.SessionRevocationStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Từ chối access token phát hành trước mốc thu hồi phiên.
 *
 * Đây là lớp chặn THẬT cho việc khóa tài khoản. Trước khi có nó, khóa một tài khoản chỉ
 * chặn được việc gia hạn phiên, còn access token đang cầm vẫn gọi API bình thường tới 15
 * phút — đủ để một tài khoản bị khóa vì nghi gian lận đặt thêm rất nhiều vòng cược.
 */
class RevokedSessionValidatorTest {

    /** Store trong bộ nhớ, đặt mốc trực tiếp để kiểm được cả mốc ở tương lai. */
    private static class FakeStore implements SessionRevocationStore {
        private final Map<UUID, Instant> marks = new HashMap<>();

        @Override
        public void revokeBefore(UUID userId) {
            marks.put(userId, Instant.now());
        }

        void revokeAt(UUID userId, Instant at) {
            marks.put(userId, at);
        }

        @Override
        public Instant revokedBefore(UUID userId) {
            return marks.get(userId);
        }
    }

    private static Jwt tokenFor(UUID userId, Instant issuedAt) {
        return Jwt.withTokenValue("dummy")
                .header("alg", "HS256")
                .subject(userId.toString())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(900))
                .build();
    }

    @Test
    @DisplayName("Không có mốc thu hồi -> token được nhận")
    void noMarkPasses() {
        FakeStore store = new FakeStore();
        RevokedSessionValidator validator = new RevokedSessionValidator(store);

        OAuth2TokenValidatorResult result =
                validator.validate(tokenFor(UUID.randomUUID(), Instant.now()));

        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    @DisplayName("Token phát TRƯỚC mốc thu hồi -> bị từ chối")
    void tokenIssuedBeforeMarkIsRejected() {
        UUID userId = UUID.randomUUID();
        FakeStore store = new FakeStore();
        RevokedSessionValidator validator = new RevokedSessionValidator(store);

        Instant issuedAt = Instant.now().minusSeconds(600);
        // Thu hồi SAU khi token được phát: đúng tình huống admin khóa tài khoản của người
        // đang đăng nhập.
        store.revokeAt(userId, issuedAt.plusSeconds(300));

        OAuth2TokenValidatorResult result = validator.validate(tokenFor(userId, issuedAt));

        assertThat(result.hasErrors()).isTrue();
    }

    @Test
    @DisplayName("Token phát SAU mốc thu hồi -> được nhận")
    void tokenIssuedAfterMarkPasses() {
        UUID userId = UUID.randomUUID();
        FakeStore store = new FakeStore();
        RevokedSessionValidator validator = new RevokedSessionValidator(store);

        Instant revokedAt = Instant.now().minusSeconds(600);
        store.revokeAt(userId, revokedAt);

        // Người dùng đăng nhập LẠI sau khi được mở khóa — token mới phải dùng được, nếu
        // không thì họ bị khóa vĩnh viễn suốt thời gian mốc còn hiệu lực.
        OAuth2TokenValidatorResult result =
                validator.validate(tokenFor(userId, revokedAt.plusSeconds(60)));

        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    @DisplayName("Mốc của người KHÁC không ảnh hưởng token của mình")
    void markIsPerUser() {
        UUID locked = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        FakeStore store = new FakeStore();
        RevokedSessionValidator validator = new RevokedSessionValidator(store);

        Instant issuedAt = Instant.now().minusSeconds(600);
        store.revokeAt(locked, issuedAt.plusSeconds(300));

        assertThat(validator.validate(tokenFor(other, issuedAt)).hasErrors()).isFalse();
        assertThat(validator.validate(tokenFor(locked, issuedAt)).hasErrors()).isTrue();
    }

    @Test
    @DisplayName("Token phát sát mốc vẫn bị từ chối nhờ biên lệch giờ")
    void clockSkewLeansTowardRejection() {
        UUID userId = UUID.randomUUID();
        FakeStore store = new FakeStore();
        RevokedSessionValidator validator = new RevokedSessionValidator(store);

        // Token phát ra 2 giây TRƯỚC mốc. Không có biên chịu lệch giờ thì `iat` của nó có thể
        // lớn hơn mốc do hai máy lệch đồng hồ, và token lọt qua đúng lúc cần chặn nhất.
        Instant revokedAt = Instant.now();
        store.revokeAt(userId, revokedAt);

        OAuth2TokenValidatorResult result =
                validator.validate(tokenFor(userId, revokedAt.minusSeconds(2)));

        assertThat(result.hasErrors()).isTrue();
    }

    @Test
    @DisplayName("Subject không phải UUID -> để validator khác quyết định")
    void nonUuidSubjectPasses() {
        FakeStore store = new FakeStore();
        RevokedSessionValidator validator = new RevokedSessionValidator(store);

        Jwt jwt = Jwt.withTokenValue("dummy")
                .header("alg", "HS256")
                .subject("not-a-uuid")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(900))
                .build();

        assertThat(validator.validate(jwt).hasErrors()).isFalse();
    }
}
