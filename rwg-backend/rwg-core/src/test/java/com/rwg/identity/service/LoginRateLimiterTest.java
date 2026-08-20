package com.rwg.identity.service;

import com.rwg.config.RateLimitProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test quy tắc rate-limit đăng nhập (KHÔNG cần Redis/Docker):
 * - Bucket IP+identifier: 5 sai -> captcha, 10 sai -> khóa.
 * - Bucket CHỈ identifier: 20 sai -> khóa tài khoản TOÀN CỤC bất kể IP.
 * - Khóa chạm ngưỡng ghi lock marker TTL đúng 15 phút (không dựa refill bucket).
 */
class LoginRateLimiterTest {

    private LoginRateLimiter limiter;

    @BeforeEach
    void setUp() {
        RateLimitProperties props = new RateLimitProperties(Duration.ofMinutes(15), 5, 10, 20);
        limiter = new LoginRateLimiter(new InMemoryRateLimitStore(), props);
    }

    @Test
    void noFailuresAllowsLogin() {
        LoginRateLimiter.AttemptResult result = limiter.checkBeforeAttempt("1.2.3.4", "alice");
        assertThat(result.locked()).isFalse();
        assertThat(result.captchaRequired()).isFalse();
    }

    @Test
    void belowFiveFailuresNoCaptcha() {
        for (int i = 1; i <= 4; i++) {
            LoginRateLimiter.AttemptResult r = limiter.recordFailure("1.2.3.4", "alice");
            assertThat(r.captchaRequired()).as("lần sai %d", i).isFalse();
            assertThat(r.locked()).isFalse();
        }
    }

    @Test
    void fromFiveFailuresCaptchaFlagSet() {
        for (int i = 1; i <= 4; i++) {
            limiter.recordFailure("1.2.3.4", "alice");
        }
        LoginRateLimiter.AttemptResult fifth = limiter.recordFailure("1.2.3.4", "alice");
        assertThat(fifth.captchaRequired()).isTrue();
        assertThat(fifth.locked()).isFalse();

        // checkBeforeAttempt sau đó cũng phải báo captcha
        assertThat(limiter.checkBeforeAttempt("1.2.3.4", "alice").captchaRequired()).isTrue();
    }

    @Test
    void tenthFailureLocksAndReturnsRetryAfterWithin15Minutes() {
        LoginRateLimiter.AttemptResult last = null;
        for (int i = 1; i <= 10; i++) {
            last = limiter.recordFailure("1.2.3.4", "alice");
        }
        assertThat(last).isNotNull();
        assertThat(last.locked()).isTrue();
        // Lock marker MỚI ghi có TTL đúng cửa sổ 15 phút = 900 giây.
        assertThat(last.retryAfterSeconds()).isEqualTo(15L * 60);

        // Lần thử tiếp theo bị chặn ngay từ checkBeforeAttempt, vẫn đủ ~15 phút.
        LoginRateLimiter.AttemptResult blocked = limiter.checkBeforeAttempt("1.2.3.4", "alice");
        assertThat(blocked.locked()).isTrue();
        assertThat(blocked.captchaRequired()).isTrue();
        assertThat(blocked.retryAfterSeconds()).isBetween(14L * 60, 15L * 60);
    }

    @Test
    void ipLockStillLocksAccountGloballyFromOtherIp() {
        for (int i = 1; i <= 10; i++) {
            limiter.recordFailure("1.2.3.4", "alice");
        }
        // Cùng user từ IP KHÁC vẫn bị khóa (account lock marker toàn cục).
        assertThat(limiter.checkBeforeAttempt("5.6.7.8", "alice").locked()).isTrue();
        // User khác không bị ảnh hưởng.
        assertThat(limiter.checkBeforeAttempt("1.2.3.4", "bob").locked()).isFalse();
        // identifier không phân biệt hoa thường.
        assertThat(limiter.checkBeforeAttempt("1.2.3.4", "ALICE").locked()).isTrue();
    }

    @Test
    void accountLocksGloballyAfter20FailuresFromMultipleIps() {
        // Mỗi IP chỉ sai 2 lần (chưa chạm ngưỡng IP=10), nhưng tổng cộng 20 lần
        // trên CÙNG identifier -> bucket tài khoản cạn -> khóa toàn cục.
        for (int ipIndex = 1; ipIndex <= 10; ipIndex++) {
            String ip = "10.0.0." + ipIndex;
            for (int i = 1; i <= 2; i++) {
                limiter.recordFailure(ip, "alice");
            }
        }
        // Từ một IP hoàn toàn mới vẫn bị khóa.
        LoginRateLimiter.AttemptResult blocked = limiter.checkBeforeAttempt("10.0.0.99", "alice");
        assertThat(blocked.locked()).isTrue();
        assertThat(blocked.retryAfterSeconds()).isBetween(14L * 60, 15L * 60);
    }

    @Test
    void successfulLoginResetsCountersAndLockMarker() {
        for (int i = 1; i <= 9; i++) {
            limiter.recordFailure("1.2.3.4", "alice");
        }
        limiter.reset("1.2.3.4", "alice");
        LoginRateLimiter.AttemptResult result = limiter.checkBeforeAttempt("1.2.3.4", "alice");
        assertThat(result.locked()).isFalse();
        assertThat(result.captchaRequired()).isFalse();
    }
}
