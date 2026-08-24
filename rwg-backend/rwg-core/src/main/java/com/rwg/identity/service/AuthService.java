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
import com.rwg.identity.dto.UpdateProfileRequest;
import com.rwg.identity.dto.UpdateLocaleRequest;
import com.rwg.identity.dto.UserResponse;
import com.rwg.identity.dto.VerifyWithdrawalPasswordRequest;
import com.rwg.identity.dto.WithdrawalPasswordCheckResponse;
import com.rwg.identity.repository.UserRepository;
import com.rwg.affiliate.service.ReferralService;
import com.rwg.risk.service.AccountLinkDetector;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Nghiệp vụ xác thực & tài khoản (Bước 1):
 * register / login (JWT 15 phút + refresh rotation theo family) / refresh / logout /
 * mật khẩu rút tiền / hồ sơ cá nhân / danh sách user cho admin.
 *
 * Hardening (code review):
 * - Captcha được ENFORCE phía server TRƯỚC khi chạm BCrypt/DB khi rate-limiter báo captchaRequired.
 * - Login khi user không tồn tại vẫn chạy BCrypt với hash dummy (chống dò user qua timing).
 * - Register gộp lỗi trùng username thành 1 lỗi chung (chống dò tài khoản). API này
 *   KHÔNG còn nhận email nên không có gì để đụng ở cột email.
 * - Password validate theo BYTE UTF-8 <= 72 (BCrypt truncation).
 */
@Service
public class AuthService {

    /** BCrypt chỉ dùng tối đa 72 BYTE đầu tiên — dài hơn phải bị từ chối. */
    private static final int BCRYPT_MAX_PASSWORD_BYTES = 72;

    /**
     * Các mã ngôn ngữ có bundle thông báo. Phải khớp với RwgLocaleResolver và với
     * regex của UpdateLocaleRequest — thêm ngôn ngữ thì phải sửa cả ba chỗ.
     */
    private static final Set<String> SUPPORTED_LOCALES = Set.of("en", "vi", "zh", "ja");

    /** Ngôn ngữ mặc định, ứng với bundle messages.properties. */
    private static final String DEFAULT_LOCALE = "en";

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
    /**
     * Nghiệp vụ giới thiệu — TÙY CHỌN qua ObjectProvider: module identity KHÔNG phụ
     * thuộc cứng vào affiliate, nên app nào không quét com.rwg.affiliate vẫn khởi
     * động bình thường (đăng ký khi đó chỉ bỏ qua mã giới thiệu).
     */
    private final ObjectProvider<ReferralService> referralServiceProvider;
    /**
     * Dò đa tài khoản — TÙY CHỌN qua ObjectProvider, cùng lý do như referralService:
     * module identity không phụ thuộc cứng vào risk. App nào không quét com.rwg.risk
     * vẫn đăng ký bình thường (chỉ không ghi dấu vết).
     */
    private final ObjectProvider<AccountLinkDetector> accountLinkDetectorProvider;
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
                       UserLocaleService userLocaleService,
                       ObjectProvider<ReferralService> referralServiceProvider,
                       ObjectProvider<AccountLinkDetector> accountLinkDetectorProvider) {
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
        this.referralServiceProvider = referralServiceProvider;
        this.accountLinkDetectorProvider = accountLinkDetectorProvider;
        // Tính 1 lần lúc khởi động (BCrypt strength 12 chậm — không tính mỗi request).
        this.dummyPasswordHash = passwordEncoder.encode("dummy-password-for-timing-balance");
    }

    // ===== REGISTER =====

    @Transactional
    public UserResponse register(RegisterRequest request, String ip) {
        return register(request, ip, null, null);
    }

    /**
     * Đăng ký kèm dấu vết kỹ thuật để dò đa tài khoản (chặng 7).
     *
     * @param deviceId header X-Device-Id, CÓ THỂ NULL — cứ yêu cầu bắt buộc thì client
     *        cũ và người tắt JavaScript sẽ không đăng ký được, trong khi kẻ farm biết
     *        kỹ thuật vẫn tự sinh được giá trị hợp lệ — mất người thật mà không chặn
     *        được ai.
     */
    @Transactional
    public UserResponse register(RegisterRequest request, String ip,
                                String deviceId, String userAgent) {
        validatePasswordBytes(request.password());

        // Chỉ còn kiểm tra trùng TÊN ĐĂNG NHẬP: API này không nhận email nữa nên
        // không có gì để đụng ở cột email. Vẫn giữ thông báo lỗi CHUNG (không nói rõ
        // trường nào) để không biến API đăng ký thành công cụ dò tên tài khoản đã có.
        if (userRepository.existsByUsernameIgnoreCase(request.username())) {
            // i18n: message resolve từ bundle key error.conflict.registration theo locale request.
            throw new ApiException(ErrorCode.CONFLICT, ErrorCode.CONFLICT.defaultMessage(),
                    null, "error.conflict.registration");
        }
        String hash = passwordEncoder.encode(request.password()); // BCrypt strength 12

        // email = null: tài khoản tạo từ form người chơi không có email. Cột vẫn
        // UNIQUE nhưng MySQL cho phép nhiều dòng NULL nên không bị đụng nhau.
        User user = new User(request.username(), null, hash);

        // Ngôn ngữ của tài khoản lấy theo ngôn ngữ của chính request đăng ký.
        //
        // Trước đây để nguyên mặc định "en" của entity, nên người đăng ký ở giao diện
        // tiếng Việt vẫn được lưu locale="en"; đăng nhập vào là giao diện tự nhảy sang
        // tiếng Anh vì frontend đọc locale này từ /users/me rồi áp dụng.
        user.setLocale(registrationLocale());

        // Mật khẩu rút tiền đặt LUÔN khi đăng ký nếu form có gửi. Ở đây KHÔNG yêu cầu
        // xác nhận lại mật khẩu đăng nhập như setWithdrawalPassword: người dùng vừa tự
        // tạo cả hai mật khẩu trong cùng một form, chưa có phiên nào để chiếm quyền.
        String withdrawalPassword = request.withdrawalPassword();
        boolean withWithdrawalPassword = withdrawalPassword != null && !withdrawalPassword.isBlank();
        if (withWithdrawalPassword) {
            user.setWithdrawalPasswordHash(passwordEncoder.encode(withdrawalPassword));
        }

        user = userRepository.save(user);

        // Chi tiết audit dùng LinkedHashMap thay vì Map.of: Map.of NÉM
        // NullPointerException với giá trị null, mà email luôn null ở luồng này.
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("email", user.getEmail());
        details.put("withdrawalPasswordSet", withWithdrawalPassword);
        details.put("locale", user.getLocale());
        audit.record(user.getId(), user.getUsername(), AuditTrailService.USER_REGISTERED,
                "USER", user.getId().toString(), details, ip);

        // Ghi dấu vết + dò đa tài khoản. Phải đặt TRƯỚC attachReferral để liên kết
        // được tạo trước khi quan hệ đại lý hình thành — kỳ hoa hồng đầu tiên đã chặn
        // được, không để lọt một kỳ. Detector tự bắt mọi lỗi nên không cần try ở đây.
        AccountLinkDetector detector = accountLinkDetectorProvider.getIfAvailable();
        if (detector != null) {
            detector.recordAndDetect(user.getId(), ip, deviceId, userAgent);
        }

        // Gắn quan hệ giới thiệu (nếu có mã). CỐ TÌNH không để lỗi ở đây làm hỏng
        // việc đăng ký: mã sai/trùng/vòng lặp đều chỉ bị bỏ qua kèm audit, vì tạo
        // được tài khoản quan trọng hơn việc ghi nhận người giới thiệu.
        ReferralService referralService = referralServiceProvider.getIfAvailable();
        if (referralService != null) {
            referralService.attachReferral(user.getId(), request.referralCode(), ip);
        }
        return toResponse(user);
    }


    // ===== LOGIN =====

    @Transactional
    public TokenResponse login(LoginRequest request, String ip) {
        User user = authenticate(request, ip);
        // Mỗi lần đăng nhập mở một family rotation mới.
        TokenResponse tokens = issueTokens(user, UUID.randomUUID().toString());
        audit.record(user.getId(), user.getUsername(), AuditTrailService.LOGIN_SUCCESS,
                "USER", user.getId().toString(), null, ip);
        return tokens;
    }

    /**
     * Đăng nhập KHU QUẢN TRỊ (backoffice). Dùng CHUNG toàn bộ đường xác thực với
     * {@link #login} — rate limiter, enforce captcha, cân bằng thời gian BCrypt —
     * rồi thêm một tầng chặn: tài khoản PLAYER bị từ chối dù mật khẩu đúng.
     *
     * VÌ SAO KIỂM TRA ROLE SAU KHI XÁC THỰC MẬT KHẨU, KHÔNG PHẢI TRƯỚC: nếu chặn
     * ngay khi thấy role PLAYER thì thông báo lỗi (403) khác với khi sai mật khẩu
     * (401), và kẻ tấn công chỉ cần so hai mã lỗi là biết tài khoản nào là nhân sự
     * quản trị — đúng danh sách cần nhắm vào. Xác thực trước rồi mới chặn khiến hai
     * trường hợp không phân biệt được từ bên ngoài khi mật khẩu sai.
     *
     * Việc phân quyền theo route trong SecurityConfig vẫn là chốt cuối: token của
     * PLAYER dù có lọt ra cũng không chạm được /api/v1/admin/**. Tầng chặn ở đây là
     * lớp thứ hai, để người chơi không vào được cửa backoffice ngay từ đầu.
     */
    @Transactional
    public TokenResponse loginStaff(LoginRequest request, String ip) {
        User user = authenticate(request, ip);

        if (!user.getRole().isStaff()) {
            audit.record(user.getId(), user.getUsername(), AuditTrailService.ADMIN_LOGIN_FORBIDDEN,
                    "USER", user.getId().toString(), Map.of("role", user.getRole().name()), ip);
            throw new ApiException(ErrorCode.FORBIDDEN, ErrorCode.FORBIDDEN.defaultMessage(),
                    null, "error.forbidden.backoffice");
        }

        TokenResponse tokens = issueTokens(user, UUID.randomUUID().toString());
        audit.record(user.getId(), user.getUsername(), AuditTrailService.ADMIN_LOGIN_SUCCESS,
                "USER", user.getId().toString(), Map.of("role", user.getRole().name()), ip);
        return tokens;
    }

    /**
     * Xác thực thông tin đăng nhập và trả về user đã kích hoạt. KHÔNG phát hành
     * token và KHÔNG ghi audit thành công — việc đó thuộc về hàm gọi, vì luồng người
     * chơi và luồng quản trị ghi hai loại sự kiện khác nhau.
     */
    private User authenticate(LoginRequest request, String ip) {
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
        return user;
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

    /**
     * Kiểm mật khẩu rút tiền MÀ KHÔNG tạo lệnh rút.
     *
     * Trang rút tiền gọi hàm này NGẦM trong lúc người chơi gõ, để chỉ bật nút gửi lệnh khi mật
     * khẩu đã đúng. Không có bước này thì người dùng chỉ biết mình gõ sai sau khi đã bấm gửi,
     * và mỗi lần như vậy đều ăn một lượt trong bộ đếm chống dò.
     *
     * ĐÂY LÀ MỘT ORACLE DÒ MẬT KHẨU: nó trả lời đúng/sai cho một mã PIN 6 số. Bảo vệ duy
     * nhất là rate-limit, nên nó dùng ĐÚNG bucket mà {@code WithdrawalService.request} dùng
     * ({@link LoginRateLimiter#withdrawalKey}) — tổng ngân sách gõ sai của cả hai đường vẫn
     * là một, không nới thêm cho kẻ tấn công một lượt nào.
     *
     * KHÔNG {@code @Transactional}: hàm chỉ đọc, và việc trừ token rate-limit phải có hiệu lực
     * ngay cả khi có exception — nếu nằm trong transaction bị rollback thì bộ đếm sai bị xóa.
     */
    public WithdrawalPasswordCheckResponse verifyWithdrawalPassword(
            UUID userId, VerifyWithdrawalPasswordRequest request, String ip) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.defaultMessage(),
                        null, "error.not_found.user"));

        String limitKey = LoginRateLimiter.withdrawalKey(userId);

        // Đã bị khóa -> 429 NGAY, không chạm BCrypt. BCrypt strength 12 tốn hàng trăm ms nên
        // bỏ qua bước này biến endpoint thành đòn bẩy làm cạn CPU.
        LoginRateLimiter.AttemptResult pre = rateLimiter.checkBeforeAttempt(ip, limitKey);
        if (pre.locked()) {
            throw new ApiException(ErrorCode.RATE_LIMITED,
                    ErrorCode.RATE_LIMITED.defaultMessage(),
                    Map.of("retryAfterSeconds", pre.retryAfterSeconds()));
        }

        // Chưa đặt mật khẩu rút: báo đúng mã lỗi như luồng tạo lệnh để giao diện dẫn người dùng
        // sang trang đặt mật khẩu, thay vì báo "sai mật khẩu" cho một mật khẩu chưa tồn tại.
        if (user.getWithdrawalPasswordHash() == null) {
            throw new ApiException(ErrorCode.WITHDRAWAL_PASSWORD_NOT_SET);
        }

        if (!passwordEncoder.matches(request.withdrawalPassword(), user.getWithdrawalPasswordHash())) {
            LoginRateLimiter.AttemptResult after = rateLimiter.recordFailure(ip, limitKey);
            audit.record(user.getId(), user.getUsername(),
                    AuditTrailService.WITHDRAWAL_PASSWORD_VERIFY_FAILED,
                    "USER", user.getId().toString(), null, ip);
            if (after.locked()) {
                throw new ApiException(ErrorCode.RATE_LIMITED,
                        ErrorCode.RATE_LIMITED.defaultMessage(),
                        Map.of("retryAfterSeconds", after.retryAfterSeconds()));
            }
            return new WithdrawalPasswordCheckResponse(false,
                    rateLimiter.remainingAttempts(ip, limitKey));
        }

        // Đúng -> reset bộ đếm sai. Không reset thì người dùng gõ sai vài lần rồi gõ đúng vẫn
        // mang theo số lần sai cũ — lần rút tiền sau đó có thể bị khóa vì lỗi đã sửa xong.
        rateLimiter.reset(ip, limitKey);
        return new WithdrawalPasswordCheckResponse(true, rateLimiter.remainingAttempts(ip, limitKey));
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

    // ===== HỒ SƠ CÁ NHÂN =====

    /**
     * Cập nhật họ tên, quốc gia, số điện thoại.
     *
     * KHÔNG yêu cầu mật khẩu: đây là thông tin liên lạc, sửa sai không gây thiệt hại về
     * tiền. Ngược lại, thông tin nhận tiền (số tài khoản ngân hàng) thì BẮT BUỘC xác nhận
     * mật khẩu rút — xem {@code BankAccountService}.
     *
     * Trường nào gửi null thì GIỮ NGUYÊN giá trị cũ; gửi chuỗi rỗng thì XOÁ. Phân biệt hai
     * trường hợp này là cần thiết vì client có thể chỉ gửi một ô đang sửa, và người dùng
     * cũng phải xoá được thông tin đã khai.
     */
    @Transactional
    public UserResponse updateProfile(UUID userId, UpdateProfileRequest request, String ip) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, ErrorCode.NOT_FOUND.defaultMessage(),
                        null, "error.not_found.user"));

        if (request.fullName() != null) {
            user.setFullName(blankToNull(request.fullName()));
        }
        if (request.countryCode() != null) {
            user.setCountryCode(blankToNull(request.countryCode()));
        }
        if (request.phone() != null) {
            user.setPhone(blankToNull(request.phone()));
        }

        userRepository.save(user);

        // Dùng LinkedHashMap thay vì Map.of: Map.of NÉM NullPointerException với giá trị
        // null, mà cả ba trường ở đây đều có thể null sau khi người dùng xoá.
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("fullName", user.getFullName());
        details.put("countryCode", user.getCountryCode());
        details.put("phone", user.getPhone());
        audit.record(user.getId(), user.getUsername(), AuditTrailService.USER_PROFILE_UPDATED,
                "USER", user.getId().toString(), details, ip);

        return toResponse(user);
    }

    // ===== helpers =====

    /**
     * Chuỗi rỗng hoặc chỉ toàn khoảng trắng thành null.
     *
     * Lưu "" và lưu null vào cùng một cột sẽ tạo ra hai cách biểu diễn cho cùng một ý
     * nghĩa "chưa khai", nên mọi truy vấn sau này phải kiểm cả hai. Chuẩn hoá ngay tại
     * chỗ ghi để phía đọc chỉ cần kiểm null.
     */
    private static String blankToNull(String raw) {
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Ngôn ngữ để gán cho tài khoản vừa đăng ký.
     *
     * `LocaleContextHolder` được RwgLocaleResolver đặt sẵn theo header Accept-Language.
     * Vẫn phải lọc lại qua SUPPORTED_LOCALES: resolver trả về một Locale đầy đủ (ví dụ
     * "zh-CN" cho header "zh-CN") mà cột `users.locale` chỉ dài 8 ký tự và
     * UpdateLocaleRequest chỉ nhận đúng bốn mã ngắn — lưu "zh-CN" vào sẽ tạo ra một
     * tài khoản mà chính API đổi ngôn ngữ không đọc lại được.
     *
     * Dùng `getLanguage()` nên "zh-CN" và "zh-TW" đều thành "zh", khớp cách
     * RwgLocaleResolver so khớp.
     */
    private String registrationLocale() {
        Locale locale = LocaleContextHolder.getLocale();
        String language = locale == null ? null : locale.getLanguage();
        return language != null && SUPPORTED_LOCALES.contains(language)
                ? language
                : DEFAULT_LOCALE;
    }

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
                user.getFullName(),
                user.getCountryCode(),
                user.getPhone(),
                user.getCreatedAt());
    }
}
