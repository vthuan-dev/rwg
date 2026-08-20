package com.rwg.identity.service;

import com.rwg.common.ApiException;
import com.rwg.common.ErrorCode;
import com.rwg.common.PageResponse;
import com.rwg.common.money.Money;
import com.rwg.identity.domain.KycLevel;
import com.rwg.identity.domain.User;
import com.rwg.identity.domain.UserRole;
import com.rwg.identity.domain.UserStatus;
import com.rwg.identity.dto.AdminUserDetailResponse;
import com.rwg.identity.dto.ChangeUserRoleRequest;
import com.rwg.identity.dto.ChangeUserStatusRequest;
import com.rwg.identity.dto.UpdateKycLevelRequest;
import com.rwg.identity.dto.UserResponse;
import com.rwg.identity.repository.UserRepository;
import com.rwg.payment.domain.PaymentStatus;
import com.rwg.payment.domain.PaymentType;
import com.rwg.payment.repository.PaymentOrderRepository;
import com.rwg.wallet.domain.Wallet;
import com.rwg.wallet.repository.WalletRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Nghiệp vụ quản trị người dùng (chặng 3). Toàn bộ endpoint gọi service này nằm dưới
 * /api/v1/admin/** nên đã được SecurityConfig chặn bằng hasRole("ADMIN") — service
 * KHÔNG tự kiểm tra quyền lần nữa, nhưng LUÔN kiểm tra các quy tắc nghiệp vụ:
 *
 * 1. THU HỒI SESSION: mọi thay đổi làm user rời trạng thái ACTIVE, hoặc đổi role,
 *    đều gọi refreshTokenStore.revokeAllForUser() — access token cũ vẫn còn hiệu lực
 *    tối đa 15 phút (JWT stateless) nhưng KHÔNG thể refresh để kéo dài phiên.
 * 2. BANNED LÀ VĨNH VIỄN: không có đường quay lại ACTIVE qua API.
 * 3. KHÔNG TỰ SỬA MÌNH: admin không thể tự khóa / tự hạ quyền (tránh tự khóa mình
 *    ra khỏi hệ thống và tránh lách quy trình 4 mắt).
 * 4. LÝ DO BẮT BUỘC khi khóa/cấm/đóng tài khoản — để audit trail truy vết được.
 *
 * KHÔNG có API xóa cứng user: wallets có FK ON DELETE RESTRICT và ledger là nguồn
 * sự thật tài chính; "xóa" = chuyển status CLOSED (soft delete).
 */
@Service
public class AdminUserService {

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final PaymentOrderRepository paymentOrderRepository;
    private final RefreshTokenStore refreshTokenStore;
    private final AuditTrailService audit;

    public AdminUserService(UserRepository userRepository,
                            WalletRepository walletRepository,
                            PaymentOrderRepository paymentOrderRepository,
                            RefreshTokenStore refreshTokenStore,
                            AuditTrailService audit) {
        this.userRepository = userRepository;
        this.walletRepository = walletRepository;
        this.paymentOrderRepository = paymentOrderRepository;
        this.refreshTokenStore = refreshTokenStore;
        this.audit = audit;
    }

    // ===== ĐỌC =====

    /** Tìm kiếm user; mọi filter optional (null/blank = bỏ qua). */
    @Transactional(readOnly = true)
    public PageResponse<UserResponse> search(String status, String role, String keyword, int page, int size) {
        UserStatus statusFilter = status == null || status.isBlank() ? null : parseStatus(status);
        UserRole roleFilter = role == null || role.isBlank() ? null : parseRole(role);
        // Wildcard được thêm Ở ĐÂY (repository nhận pattern hoàn chỉnh) và escape các
        // ký tự đặc biệt của LIKE để keyword người dùng không đổi ngữ nghĩa truy vấn.
        String keywordFilter = keyword == null || keyword.isBlank()
                ? null
                : "%" + escapeLike(keyword.trim().toLowerCase()) + "%";
        return PageResponse.from(
                userRepository.searchForAdmin(statusFilter, roleFilter, keywordFilter,
                        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))),
                AuthService::toResponse);
    }

    /** Chi tiết user kèm ảnh chụp tài chính (số dư, tổng nạp/rút, lệnh chờ duyệt). */
    @Transactional(readOnly = true)
    public AdminUserDetailResponse detail(UUID userId) {
        User user = requireUser(userId);

        Wallet wallet = walletRepository.findByUserId(userId).orElse(null);
        String balance = wallet == null
                ? Money.zero().amount().toPlainString()
                : Money.of(wallet.getBalance()).amount().toPlainString();
        String currency = wallet == null ? Wallet.DEFAULT_CURRENCY : wallet.getCurrency();

        // Lệnh nạp hoàn tất = SUCCESS; lệnh rút hoàn tất = SETTLED (xem PaymentStatus).
        // PENDING/VOIDED/FAILED không phải tiền thực nên không tính vào tổng.
        BigDecimal deposited = paymentOrderRepository.sumAmountByUserAndTypeAndStatus(
                userId, PaymentType.DEPOSIT, PaymentStatus.SUCCESS);
        BigDecimal withdrawn = paymentOrderRepository.sumAmountByUserAndTypeAndStatus(
                userId, PaymentType.WITHDRAWAL, PaymentStatus.SETTLED);

        return new AdminUserDetailResponse(
                user.getId(), user.getUsername(), user.getEmail(),
                user.getRole().name(), user.getStatus().name(), user.getKycLevel().name(),
                user.getWithdrawalPasswordHash() != null, user.getLocale(),
                user.getLastLoginAt(), user.getCreatedAt(),
                balance, currency,
                Money.of(deposited).amount().toPlainString(),
                Money.of(withdrawn).amount().toPlainString(),
                paymentOrderRepository.countByUserIdAndTypeAndStatus(
                        userId, PaymentType.WITHDRAWAL, PaymentStatus.PENDING));
    }

    // ===== GHI =====

    /**
     * Khóa / cấm / mở lại / đóng tài khoản. Rời ACTIVE -> thu hồi toàn bộ refresh token
     * để user không kéo dài được phiên hiện tại.
     */
    @Transactional
    public UserResponse changeStatus(UUID userId, ChangeUserStatusRequest request, UUID adminId, String ip) {
        requireNotSelf(userId, adminId);
        User user = requireUser(userId);
        UserStatus from = user.getStatus();
        UserStatus to = parseStatus(request.status());

        if (from == to) {
            throw new ApiException(ErrorCode.INVALID_STATUS_TRANSITION,
                    ErrorCode.INVALID_STATUS_TRANSITION.defaultMessage(), null,
                    "error.invalid_status_transition.same_status");
        }
        // BANNED là trạng thái cuối: không mở lại qua API (cần can thiệp DB có kiểm soát).
        if (from == UserStatus.BANNED) {
            throw new ApiException(ErrorCode.INVALID_STATUS_TRANSITION,
                    ErrorCode.INVALID_STATUS_TRANSITION.defaultMessage(), null,
                    "error.invalid_status_transition.banned_is_final");
        }
        if (to != UserStatus.ACTIVE) {
            requireReason(request.reason());
        }

        user.setStatus(to);
        userRepository.save(user);

        // Rời ACTIVE -> đẩy user khỏi hệ thống: refresh token không dùng được nữa.
        if (to != UserStatus.ACTIVE) {
            refreshTokenStore.revokeAllForUser(userId);
        }

        audit.record(adminId, null, AuditTrailService.ADMIN_USER_STATUS_CHANGED,
                "USER", userId.toString(),
                details("from", from.name(), "to", to.name(), "reason", request.reason()), ip);
        return AuthService.toResponse(user);
    }

    /**
     * Nâng/hạ quyền. Thu hồi refresh token vì claim "roles" trong token cũ đã lệch
     * với DB — buộc đăng nhập lại để nhận authority mới.
     */
    @Transactional
    public UserResponse changeRole(UUID userId, ChangeUserRoleRequest request, UUID adminId, String ip) {
        requireNotSelf(userId, adminId);
        User user = requireUser(userId);
        UserRole from = user.getRole();
        UserRole to = parseRole(request.role());

        if (from == to) {
            throw new ApiException(ErrorCode.INVALID_STATUS_TRANSITION,
                    ErrorCode.INVALID_STATUS_TRANSITION.defaultMessage(), null,
                    "error.invalid_status_transition.same_status");
        }

        user.setRole(to);
        userRepository.save(user);
        refreshTokenStore.revokeAllForUser(userId);

        audit.record(adminId, null, AuditTrailService.ADMIN_USER_ROLE_CHANGED,
                "USER", userId.toString(),
                details("from", from.name(), "to", to.name(), "reason", request.reason()), ip);
        return AuthService.toResponse(user);
    }

    /** Duyệt KYC (nâng/hạ mức xác minh). Không ảnh hưởng phiên đăng nhập. */
    @Transactional
    public UserResponse updateKycLevel(UUID userId, UpdateKycLevelRequest request, UUID adminId, String ip) {
        User user = requireUser(userId);
        KycLevel from = user.getKycLevel();
        KycLevel to = parseKycLevel(request.kycLevel());

        user.setKycLevel(to);
        userRepository.save(user);

        audit.record(adminId, null, AuditTrailService.ADMIN_KYC_UPDATED,
                "USER", userId.toString(),
                details("from", from.name(), "to", to.name(), "reason", request.reason()), ip);
        return AuthService.toResponse(user);
    }

    /**
     * XÓA mật khẩu rút tiền để user tự đặt lại (hỗ trợ user quên mật khẩu).
     * Admin KHÔNG được đặt mật khẩu thay user — nếu cho phép, admin sẽ có thể tự
     * đặt rồi rút tiền của user đó.
     */
    @Transactional
    public UserResponse resetWithdrawalPassword(UUID userId, UUID adminId, String ip) {
        User user = requireUser(userId);
        user.setWithdrawalPasswordHash(null);
        userRepository.save(user);

        audit.record(adminId, null, AuditTrailService.ADMIN_WITHDRAWAL_PASSWORD_RESET,
                "USER", userId.toString(), null, ip);
        return AuthService.toResponse(user);
    }

    // ===== helpers =====

    private User requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        ErrorCode.NOT_FOUND.defaultMessage(), null, "error.not_found.user"));
    }

    private void requireNotSelf(UUID targetUserId, UUID adminId) {
        if (targetUserId.equals(adminId)) {
            throw new ApiException(ErrorCode.CANNOT_MODIFY_SELF);
        }
    }

    private void requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new ApiException(ErrorCode.ADMIN_REASON_REQUIRED);
        }
    }

    private UserStatus parseStatus(String raw) {
        try {
            return UserStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException invalid) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    ErrorCode.VALIDATION_ERROR.defaultMessage(), Map.of("field", "status"),
                    "validation.admin.status.invalid");
        }
    }

    private UserRole parseRole(String raw) {
        try {
            return UserRole.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException invalid) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    ErrorCode.VALIDATION_ERROR.defaultMessage(), Map.of("field", "role"),
                    "validation.admin.role.invalid");
        }
    }

    private KycLevel parseKycLevel(String raw) {
        try {
            return KycLevel.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException invalid) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    ErrorCode.VALIDATION_ERROR.defaultMessage(), Map.of("field", "kycLevel"),
                    "validation.admin.kyc_level.invalid");
        }
    }

    /**
     * Escape ký tự đặc biệt của LIKE. Không escape thì keyword "%" sẽ khớp toàn bộ
     * bảng và "_" khớp mọi ký tự — người dùng vô tình (hoặc cố ý) đổi ngữ nghĩa truy vấn.
     */
    private String escapeLike(String raw) {
        return raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /** Map audit details bỏ qua giá trị null (details là JSON, không cần key rỗng). */
    private Map<String, Object> details(String k1, String v1, String k2, String v2, String k3, String v3) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        if (v3 != null && !v3.isBlank()) {
            map.put(k3, v3);
        }
        return map;
    }
}
