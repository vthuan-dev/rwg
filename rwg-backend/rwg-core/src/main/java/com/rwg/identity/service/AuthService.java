package com.rwg.identity.service;

import com.rwg.common.ApiException;
import com.rwg.common.ErrorCode;
import com.rwg.config.CaptchaProperties;
import com.rwg.config.SecurityProperties;
import com.rwg.identity.domain.User;
import com.rwg.identity.domain.UserStatus;
import com.rwg.identity.dto.ChangePasswordRequest;
import com.rwg.identity.dto.LoginRequest;
import com.rwg.identity.dto.RegisterRequest;
import com.rwg.identity.dto.SetWithdrawalPasswordRequest;
import com.rwg.identity.dto.TokenResponse;
import com.rwg.identity.dto.UpdateLocaleRequest;
import com.rwg.identity.dto.UserResponse;
import com.rwg.identity.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Nghiệp vụ xác thực & tài khoản (Bước 1):
 * register / login (JWT 15 phút + refresh rotation theo family) / refresh / logout /
 * mật khẩu rút tiền / hồ sơ cá nhân / danh sách user cho admin.
 *
 * Hardening (code review):
 * - Captcha được ENFORCE phía server TRƯỚC khi chạm BCrypt/DB khi rate-limiter báo captchaRequired.
 * - Login khi user không tồn tại vẫn chạy BCrypt với hash dummy (chống dò user qua timing).
 * - Register gộp lỗi trùng username/email thành 1 lỗi chung (chống dò tài khoản).
 * - Password validate theo BYTE UTF-8 <= 72 (BCrypt truncation).
 */
@Service
public class AuthService {

    /** BCrypt chỉ dùng tối đa 72 BYTE đầu tiên — dài hơn phải bị từ chối. */
    private static final int BCRYPT_MAX_PASSWORD_BYTES = 72;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenStore refreshTokenStore;
    private final LoginRateLimiter rateLimiter;
    private final AuditTrailService audit;
    private final SecurityProperties securityProperties;
    private final CaptchaVerifier captchaVerifier;
    private final CaptchaProperties captchaProperties;
    private final UserLocaleService userLocaleService;
    /** Hash dummy để cân bằng thời gian BCrypt khi user không tồn tại. */
    private final String dummyPasswordHash;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       RefreshTokenStore refreshTokenStore,
                       LoginRateLimiter rateLimiter,
                       AuditTrailService audit,
                       SecurityProperties securityProperties,
                       CaptchaVerifier captchaVerifier,
                       CaptchaProperties captchaProperties,
                       UserLocaleService userLocaleService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenStore = refreshTokenStore;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
        this.securityProperties = securityProperties;
        this.captchaVerifier = captchaVerifier;
        this.captchaProperties = captchaProperties;
        this.userLocaleService = userLocaleService;
        // Tính 1 lần lúc khởi động (BCrypt strength 12 chậm — không tính mỗi request).
        this.dummyPasswordHash = passwordEncoder.encode("dummy-password-for-timing-balance");
    }

    // ===== REGISTER =====

    @Transactional
    public UserResponse register(RegisterRequest request, String ip) {
        validatePasswordBytes(request.password());
        // Gộp 2 kiểm tra trùng thành 1 lỗi CHUNG — không tiết lộ trường nào đã tồn tại
        // (chống dò tài khoản đã đăng ký qua thông báo lỗi).
        boolean duplicate = userRepository.existsByUsernameIgnoreCase(request.username())
                || userRepository.existsByEmailIgnoreCase(request.email());
        if (duplicate) {
            // i18n: message resolve từ bundle key error.conflict.registration theo locale request.
            throw new ApiException(ErrorCode.CONFLICT, ErrorCode.CONFLICT.defaultMessage(),
                    null, "error.conflict.registration");
        }
        String hash = passwordEncoder.encode(request.password()); // BCrypt strength 12
        User user = userRepository.save(new User(request.username(), request.email().toLowerCase(), hash));
        audit.record(user.getId(), user.getUsername(), AuditTrailService.USER_REGISTERED,
                "USER", user.getId().toString(), Map.of("email", user.getEmail()), ip);
        return toResponse(user);
    }

    // ===== LOGIN =====

    @Transactional
    public TokenResponse login(LoginRequest request, String ip) {
        String identifier = request.identifier().trim();

        LoginRateLimiter.AttemptResult pre = rateLimiter.checkBeforeAttempt(ip, identifier);
        if (pre.locked()) {
            audit.record(null, identifier, AuditTrailService.LOGIN_LOCKED, "USER", null,
                    Map.of("retryAfterSeconds", pre.retryAfterSeconds()), ip);
            throw new ApiException(ErrorCode.ACCOUNT_LOCKED,
                    ErrorCode.ACCOUNT_LOCKED.defaultMessage(),
                    Map.of("retryAfterSeconds", pre.retryAfterSeconds(),
                            "captchaRequired", true));
        }

        // ENFORCE captcha phía server TRƯỚC khi chạm DB/BCrypt: đã quá ngưỡng sai mà
        // thiếu captchaToken hợp lệ -> từ chối ngay.
        if (pre.captchaRequired() && captchaProperties.enforced()
                && !captchaVerifier.verify(request.captchaToken())) {
            throw new ApiException(ErrorCode.CAPTCHA_REQUIRED,
                    ErrorCode.CAPTCHA_REQUIRED.defaultMessage(),
                    Map.of("captchaRequired", true));
        }

        validatePasswordBytes(request.password());

        User user = userRepository.findByUsername(identifier)
                .or(() -> userRepository.findByEmailIgnoreCase(identifier))
                .orElse(null);

        // Luôn chạy BCrypt kể cả khi user==null (hash dummy) để cân bằng thời gian,
        // chống dò tên tài khoản hợp lệ qua timing.
        boolean passwordOk = passwordEncoder.matches(
                request.password(), user != null ? user.getPasswordHash() : dummyPasswordHash);

        if (user == null || user.getStatus() != UserStatus.ACTIVE || !passwordOk) {
            LoginRateLimiter.AttemptResult after = rateLimiter.recordFailure(ip, identifier);
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("captchaRequired", after.captchaRequired() || pre.captchaRequired());
            if (after.locked()) {
                details.put("retryAfterSeconds", after.retryAfterSeconds());
            }
            audit.record(user == null ? null : user.getId(), identifier,
                    AuditTrailService.LOGIN_FAILED, "USER",
                    user == null ? null : user.getId().toString(), details, ip);
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS,
                    ErrorCode.INVALID_CREDENTIALS.defaultMessage(), details);
        }

        rateLimiter.reset(ip, identifier);
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        // Mỗi lần đăng nhập mở một family rotation mới.
        TokenResponse tokens = issueTokens(user, UUID.randomUUID().toString());
        audit.record(user.getId(), user.getUsername(), AuditTrailService.LOGIN_SUCCESS,
                "USER", user.getId().toString(), null, ip);
        return tokens;
    }

    // ===== REFRESH (rotation + phát hiện reuse) =====

    @Transactional(readOnly = true)
    public TokenResponse refresh(String refreshToken, String ip) {
        // Consume NGUYÊN TỬ (GETDEL / remove-and-return): chỉ 1 request song song thắng.
        RefreshTokenStore.ConsumeResult consumed = refreshTokenStore.consume(refreshToken);
        if (consumed.status() == RefreshTokenStore.ConsumeStatus.REUSE) {
            // Token ĐÃ bị tiêu thụ bị gửi lại -> nghi ngờ bị đánh cắp: family đã bị thu hồi
            // bên trong store; buộc đăng nhập lại.
            audit.record(null, null, AuditTrailService.REFRESH_TOKEN_REUSE, "USER", null, null, ip);
            throw new ApiException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        if (consumed.status() != RefreshTokenStore.ConsumeStatus.OK) {
            throw new ApiException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        User user = userRepository.findById(consumed.userId())
                .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(ErrorCode.REFRESH_TOKEN_INVALID));

        // Rotation: token mới giữ NGUYÊN familyId.
        TokenResponse tokens = issueTokens(user, consumed.familyId());
        audit.record(user.getId(), user.getUsername(), AuditTrailService.REFRESH_TOKEN_ROTATED,
                "USER", user.getId().toString(), null, ip);
        return tokens;
    }

    // ===== LOGOUT =====

    public void logout(String refreshToken, String ip) {
        RefreshTokenStore.ConsumeResult consumed = refreshTokenStore.consume(refreshToken);
        if (consumed.status() != RefreshTokenStore.ConsumeStatus.OK) {
            return; // REUSE: family đã bị thu hồi trong store; INVALID: không có gì để làm.
        }
        // Thu hồi toàn bộ family (token mới nhất nếu client khác đang giữ).
        refreshTokenStore.revokeFamily(consumed.familyId());
        userRepository.findById(consumed.userId()).ifPresent(user ->
                audit.record(user.getId(), user.getUsername(), AuditTrailService.LOGOUT,
                        "USER", user.getId().toString(), null, ip));
    }

    // ===== MẬT KHẨU RÚT TIỀN =====

    @Transactional
    public UserResponse setWithdrawalPassword(UUID userId, SetWithdrawalPasswordRequest request, String ip) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.defaultMessage(),
                        null, "error.not_found.user"));
        if (!passwordEncoder.matches(request.loginPassword(), user.getPasswordHash())) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS,
                    ErrorCode.INVALID_CREDENTIALS.defaultMessage(),
                    null, "error.invalid_credentials.withdrawal_password");
        }
        validatePasswordBytes(request.newWithdrawalPassword());
        user.setWithdrawalPasswordHash(passwordEncoder.encode(request.newWithdrawalPassword()));
        userRepository.save(user);
        audit.record(user.getId(), user.getUsername(), AuditTrailService.WITHDRAWAL_PASSWORD_SET,
                "USER", user.getId().toString(), null, ip);
        return toResponse(user);
    }

    // ===== ĐỔI MẬT KHẨU ĐĂNG NHẬP (chặng 2 Phase b) =====

    /**
     * Đổi mật khẩu đăng nhập: verify mật khẩu cũ -> hash BCrypt mới ->
     * THU HỒI TOÀN BỘ refresh token của user (buộc đăng nhập lại mọi thiết bị) -> audit.
     */
    @Transactional
    public UserResponse changeLoginPassword(UUID userId, ChangePasswordRequest request, String ip) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.defaultMessage(),
                        null, "error.not_found.user"));
        if (!passwordEncoder.matches(request.oldPassword(), user.getPasswordHash())) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS,
                    ErrorCode.INVALID_CREDENTIALS.defaultMessage(),
                    null, "error.invalid_credentials.old_password");
        }
        validatePasswordBytes(request.newPassword());
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        // Thu hồi mọi refresh token đang hoạt động — token cũ KHÔNG dùng lại được nữa.
        refreshTokenStore.revokeAllForUser(userId);
        audit.record(user.getId(), user.getUsername(), AuditTrailService.PASSWORD_CHANGED,
                "USER", user.getId().toString(), null, ip);
        return toResponse(user);
    }

    // ===== HỒ SƠ =====

    @Transactional(readOnly = true)
    public UserResponse me(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.defaultMessage(),
                        null, "error.not_found.user"));
        return toResponse(user);
    }

    // ===== LOCALE (i18n chặng 2) =====

    /** Đổi ngôn ngữ hiển thị; cập nhật cache để request kế tiếp áp dụng ngay. */
    @Transactional
    public UserResponse updateLocale(UUID userId, UpdateLocaleRequest request, String ip) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.defaultMessage(),
                        null, "error.not_found.user"));
        user.setLocale(request.locale());
        userRepository.save(user);
        userLocaleService.put(userId, request.locale());
        audit.record(user.getId(), user.getUsername(), AuditTrailService.USER_LOCALE_UPDATED,
                "USER", user.getId().toString(), Map.of("locale", request.locale()), ip);
        return toResponse(user);
    }

    // ===== helpers =====

    /** Từ chối password vượt 72 BYTE UTF-8 (BCrypt băm tối đa 72 byte đầu). */
    private void validatePasswordBytes(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_PASSWORD_BYTES) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    ErrorCode.VALIDATION_ERROR.defaultMessage(), Map.of("field", "password"),
                    "error.validation.password.too_many_bytes");
        }
    }

    private TokenResponse issueTokens(User user, String familyId) {
        String tokenId = UUID.randomUUID().toString();
        refreshTokenStore.save(tokenId, user.getId(), familyId, securityProperties.refreshTokenTtl());
        return new TokenResponse(jwtService.issueAccessToken(user), tokenId,
                jwtService.accessTokenExpiresInSeconds());
    }

    static UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getRole().name(),
                user.getStatus().name(),
                user.getKycLevel().name(),
                user.getWithdrawalPasswordHash() != null,
                user.getLocale(),
                user.getCreatedAt());
    }
}
