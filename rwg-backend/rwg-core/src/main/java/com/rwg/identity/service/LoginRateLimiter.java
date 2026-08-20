package com.rwg.identity.service;

import com.rwg.config.RateLimitProperties;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Rate-limit đăng nhập với HAI bucket chạy song song:
 * 1. Bucket IP+identifier (capacity = lockThreshold, vd 10 sai/15 phút):
 *    chống brute-force từ một máy. Sai >= captchaThreshold (5) -> bắt buộc captcha.
 * 2. Bucket CHỈ identifier (capacity = accountLockThreshold, vd 20 sai/15 phút):
 *    khóa tài khoản TOÀN CỤC bất kể attacker đổi IP.
 *
 * Khi chạm ngưỡng khóa, ghi LOCK MARKER riêng có TTL đúng loginWindow (15 phút)
 * — khóa đủ 15 phút thật, không phụ thuộc thời điểm refill của bucket.
 * Đăng nhập thành công -> reset cả bucket lẫn lock marker của IP+identifier đó.
 */
@Component
public class LoginRateLimiter {

    /** Kết quả kiểm tra/ghi nhận một lần thử đăng nhập. */
    public record AttemptResult(boolean captchaRequired, boolean locked, long retryAfterSeconds) {

        public static AttemptResult ok() {
            return new AttemptResult(false, false, 0);
        }

        public static AttemptResult captcha() {
            return new AttemptResult(true, false, 0);
        }

        public static AttemptResult locked(long retryAfterSeconds) {
            return new AttemptResult(true, true, retryAfterSeconds);
        }
    }

    private final RateLimitStore store;
    private final RateLimitProperties props;
    private final BucketConfiguration ipConfiguration;
    private final BucketConfiguration accountConfiguration;

    public LoginRateLimiter(RateLimitStore store, RateLimitProperties props) {
        this.store = store;
        this.props = props;
        this.ipConfiguration = bucketConfiguration(props.lockThreshold(), props);
        this.accountConfiguration = bucketConfiguration(props.accountLockThreshold(), props);
    }

    /** Cấu hình bucket capacity tùy chọn, refill toàn bộ mỗi loginWindow. */
    static BucketConfiguration bucketConfiguration(int capacity, RateLimitProperties props) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(capacity)
                .refillIntervally(capacity, props.loginWindow())
                .build();
        return BucketConfiguration.builder().addLimit(limit).build();
    }

    // ===== keys =====

    private String ipBucketKey(String ip, String identifier) {
        return "rwg:auth:login-limit:ip:" + ip + ":" + identifier.toLowerCase(Locale.ROOT);
    }

    private String accountBucketKey(String identifier) {
        return "rwg:auth:login-limit:account:" + identifier.toLowerCase(Locale.ROOT);
    }

    private String ipLockKey(String ip, String identifier) {
        return "rwg:auth:login-lock:ip:" + ip + ":" + identifier.toLowerCase(Locale.ROOT);
    }

    private String accountLockKey(String identifier) {
        return "rwg:auth:login-lock:account:" + identifier.toLowerCase(Locale.ROOT);
    }

    // ===== API =====

    /** Kiểm tra TRƯỚC khi xác thực mật khẩu: nếu đã khóa thì từ chối ngay. */
    public AttemptResult checkBeforeAttempt(String ip, String identifier) {
        // Lock marker (TTL đúng 15 phút) ưu tiên hơn trạng thái bucket.
        long lockedSeconds = Math.max(
                store.lockRemainingSeconds(ipLockKey(ip, identifier)),
                store.lockRemainingSeconds(accountLockKey(identifier)));
        if (lockedSeconds > 0) {
            return AttemptResult.locked(lockedSeconds);
        }

        Bucket ipBucket = store.bucketFor(ipBucketKey(ip, identifier), ipConfiguration);
        Bucket accountBucket = store.bucketFor(accountBucketKey(identifier), accountConfiguration);
        long ipRemaining = ipBucket.getAvailableTokens();
        long accountRemaining = accountBucket.getAvailableTokens();
        if (ipRemaining <= 0) {
            return AttemptResult.locked(secondsToRefill(ipBucket));
        }
        if (accountRemaining <= 0) {
            return AttemptResult.locked(secondsToRefill(accountBucket));
        }
        long failed = capacity(ipConfiguration) - ipRemaining;
        if (failed >= props.captchaThreshold()) {
            return AttemptResult.captcha();
        }
        return AttemptResult.ok();
    }

    /** Ghi nhận một lần đăng nhập SAI; trả trạng thái sau khi trừ token ở CẢ HAI bucket. */
    public AttemptResult recordFailure(String ip, String identifier) {
        Bucket ipBucket = store.bucketFor(ipBucketKey(ip, identifier), ipConfiguration);
        Bucket accountBucket = store.bucketFor(accountBucketKey(identifier), accountConfiguration);
        ConsumptionProbe ipProbe = ipBucket.tryConsumeAndReturnRemaining(1);
        ConsumptionProbe accountProbe = accountBucket.tryConsumeAndReturnRemaining(1);

        if (!ipProbe.isConsumed() || !accountProbe.isConsumed()
                || ipProbe.getRemainingTokens() <= 0 || accountProbe.getRemainingTokens() <= 0) {
            // Chạm ngưỡng khóa ở ít nhất 1 bucket -> ghi lock marker TTL đúng 15 phút.
            return AttemptResult.locked(enterLock(ip, identifier));
        }
        long failed = capacity(ipConfiguration) - ipProbe.getRemainingTokens();
        if (failed >= props.captchaThreshold()) {
            return AttemptResult.captcha();
        }
        return AttemptResult.ok();
    }

    /** Ghi lock marker cho cả IP+identifier lẫn identifier toàn cục; trả số giây khóa. */
    private long enterLock(String ip, String identifier) {
        store.lock(ipLockKey(ip, identifier), props.loginWindow());
        store.lock(accountLockKey(identifier), props.loginWindow());
        long remaining = Math.max(
                store.lockRemainingSeconds(ipLockKey(ip, identifier)),
                store.lockRemainingSeconds(accountLockKey(identifier)));
        return remaining > 0 ? remaining : props.loginWindow().toSeconds();
    }

    /** Đăng nhập thành công: xóa bucket + lock marker để reset bộ đếm sai. */
    public void reset(String ip, String identifier) {
        store.reset(ipBucketKey(ip, identifier));
        store.reset(accountBucketKey(identifier));
        store.unlock(ipLockKey(ip, identifier));
        store.unlock(accountLockKey(identifier));
    }

    private long capacity(BucketConfiguration configuration) {
        return configuration.getBandwidths()[0].getCapacity();
    }

    private long secondsToRefill(Bucket bucket) {
        long nanos = bucket.estimateAbilityToConsume(1).getNanosToWaitForRefill();
        return Math.max(nanos / 1_000_000_000L, 1);
    }
}
