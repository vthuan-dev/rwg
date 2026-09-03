package com.rwg.identity.service;

import com.rwg.common.ApiException;
import com.rwg.common.ErrorCode;
import com.rwg.common.PageResponse;
import com.rwg.common.money.Money;
import com.rwg.affiliate.repository.CommissionRunRepository;
import com.rwg.affiliate.repository.ReferralCodeRepository;
import com.rwg.affiliate.repository.UserRelationRepository;
import com.rwg.bank.repository.BankAccountRepository;
import com.rwg.game.repository.BetRepository;
import com.rwg.game.repository.UserGameOddsRepository;
import com.rwg.game.service.GameEventRelay;
import com.rwg.identity.domain.KycLevel;
import com.rwg.identity.domain.User;
import com.rwg.identity.domain.UserRole;
import com.rwg.identity.domain.UserStatus;
import com.rwg.identity.dto.AdminUserDetailResponse;
import com.rwg.identity.dto.AdminUserListItemResponse;
import com.rwg.presence.service.PresenceQueryService;
import com.rwg.identity.dto.ChangeUserRoleRequest;
import com.rwg.identity.dto.ChangeUserStatusRequest;
import com.rwg.identity.dto.DeleteUserRequest;
import com.rwg.identity.dto.UpdateKycLevelRequest;
import com.rwg.identity.dto.UserResponse;
import com.rwg.identity.dto.LoginHistoryEntryResponse;
import com.rwg.identity.repository.AdminApprovalRequestRepository;
import com.rwg.identity.repository.AuditLogRepository;
import com.rwg.identity.repository.UserRepository;
import com.rwg.identity.service.AdminDestructivePinService;
import com.rwg.payment.domain.PaymentStatus;
import com.rwg.payment.domain.PaymentType;
import com.rwg.payment.repository.PaymentOrderRepository;
import com.rwg.risk.repository.AccountLinkRepository;
import com.rwg.risk.repository.AccountSignalRepository;
import com.rwg.wallet.domain.Wallet;
import com.rwg.wallet.domain.WalletRefType;
import com.rwg.wallet.repository.WalletRepository;
import com.rwg.wallet.repository.WalletTransactionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Nghiệp vụ quản trị người dùng (chặng 3). Toàn bộ endpoint gọi service này nằm dưới
 * /api/v1/admin/** nên đã được SecurityConfig chặn bằng hasRole("ADMIN") — service
 * KHÔNG tự kiểm tra quyền lần nữa, nhưng LUÔN kiểm tra các quy tắc nghiệp vụ:
 *
 * 1. THU HỒI SESSION: mọi thay đổi làm user rời trạng thái ACTIVE, hoặc đổi role, hoặc
 *    đặt lại mật khẩu đều gọi {@link #revokeSessions} — xem chú thích của hàm đó để
 *    biết vì sao riêng việc thu hồi refresh token là KHÔNG ĐỦ.
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
    private final WalletTransactionRepository transactionRepository;
    private final PaymentOrderRepository paymentOrderRepository;
    private final BetRepository betRepository;
    private final BankAccountRepository bankAccountRepository;
    private final ReferralCodeRepository referralCodeRepository;
    private final UserRelationRepository userRelationRepository;
    private final CommissionRunRepository commissionRunRepository;
    private final AccountLinkRepository accountLinkRepository;
    private final AccountSignalRepository accountSignalRepository;
    private final AdminApprovalRequestRepository approvalRequestRepository;
    private final UserGameOddsRepository userGameOddsRepository;
    private final AuditLogRepository auditLogRepository;
    private final RefreshTokenStore refreshTokenStore;
    private final SessionRevocationStore sessionRevocationStore;
    private final GameEventRelay gameEventRelay;
    private final AuditTrailService audit;
    private final AdminDestructivePinService pinService;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    private final PresenceQueryService presenceQueryService;

    public AdminUserService(UserRepository userRepository,
                            WalletRepository walletRepository,
                            WalletTransactionRepository transactionRepository,
                            PaymentOrderRepository paymentOrderRepository,
                            BetRepository betRepository,
                            BankAccountRepository bankAccountRepository,
                            ReferralCodeRepository referralCodeRepository,
                            UserRelationRepository userRelationRepository,
                            CommissionRunRepository commissionRunRepository,
                            AccountLinkRepository accountLinkRepository,
                            AccountSignalRepository accountSignalRepository,
                            AdminApprovalRequestRepository approvalRequestRepository,
                            UserGameOddsRepository userGameOddsRepository,
                            AuditLogRepository auditLogRepository,
                            RefreshTokenStore refreshTokenStore,
                            SessionRevocationStore sessionRevocationStore,
                            GameEventRelay gameEventRelay,
                            AuditTrailService audit,
                            AdminDestructivePinService pinService,
                            org.springframework.security.crypto.password.PasswordEncoder passwordEncoder,
                            PresenceQueryService presenceQueryService) {
        this.userRepository = userRepository;
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
        this.paymentOrderRepository = paymentOrderRepository;
        this.betRepository = betRepository;
        this.bankAccountRepository = bankAccountRepository;
        this.referralCodeRepository = referralCodeRepository;
        this.userRelationRepository = userRelationRepository;
        this.commissionRunRepository = commissionRunRepository;
        this.accountLinkRepository = accountLinkRepository;
        this.accountSignalRepository = accountSignalRepository;
        this.approvalRequestRepository = approvalRequestRepository;
        this.userGameOddsRepository = userGameOddsRepository;
        this.auditLogRepository = auditLogRepository;
        this.refreshTokenStore = refreshTokenStore;
        this.sessionRevocationStore = sessionRevocationStore;
        this.gameEventRelay = gameEventRelay;
        this.audit = audit;
        this.pinService = pinService;
        this.passwordEncoder = passwordEncoder;
        this.presenceQueryService = presenceQueryService;
    }

    /**
     * ĐẨY NGƯỜI DÙNG RA KHỎI HỆ THỐNG ngay lập tức, trên mọi thiết bị.
     *
     * Gồm ba việc, và cả ba đều cần:
     *
     * 1. {@code revokeAllForUser} — chặn GIA HẠN phiên.
     * 2. {@code revokeBefore} — chặn chính access token đang cầm. Đây là việc THIẾU trước
     *    đây: JWT không trạng thái nên server không tra cứu gì khi xác thực, và một tài
     *    khoản vừa bị khóa vẫn gọi API bình thường tới 15 phút — đủ để đặt thêm rất
     *    nhiều vòng cược. Xem {@link SessionRevocationStore}.
     * 3. {@code publishSessionRevoked} — đá họ khỏi MÀN HÌNH ngay, thay vì để họ bấm tiếp
     *    trên một giao diện mà mọi lời gọi API đằng sau đều đã bị từ chối.
     *
     * Việc (2) là lớp bảo vệ thật, việc (3) chỉ là trải nghiệm — ai chặn WebSocket vẫn bị
     * (2) chặn, họ chỉ không được đá ra ngay trên màn hình.
     *
     * Gọi BÊN TRONG giao dịch, tức hai việc đầu ghi vào Redis TRƯỚC khi giao dịch commit.
     * Nếu giao dịch rollback thì người dùng bị đăng xuất oan một lần — họ đăng nhập lại
     * được ngay vì trạng thái trong cơ sở dữ liệu không đổi. Chiều ngược lại (commit xong
     * mới thu hồi, rồi bước thu hồi thất bại) sẽ để một tài khoản ĐÃ bị khóa vẫn cược
     * tiếp — tệ hơn hẳn.
     */
    private void revokeSessions(UUID userId) {
        refreshTokenStore.revokeAllForUser(userId);
        sessionRevocationStore.revokeBefore(userId);
        gameEventRelay.publishSessionRevoked(userId);
    }

    // ===== ĐỌC =====

    /**
     * Tìm kiếm tài khoản KHÁCH; mọi filter optional (null/blank = bỏ qua).
     *
     * Kèm số dư ví của từng dòng: người vận hành cần thấy số dư ngay trên bảng để biết nên
     * mở tài khoản nào, thay vì phải bấm vào từng người mới biết.
     *
     * Kèm cả trạng thái đang online. Cột "đăng nhập gần nhất" KHÔNG trả lời được câu đó:
     * {@code last_login_at} chỉ ghi một lần lúc đăng nhập, nên người đang chơi và người đã
     * tắt máy từ lâu hiện y như nhau nếu họ đăng nhập cùng lúc.
     *
     * KHÔNG nhận filter vai trò: truy vấn ở repository đã gắt PLAYER (xem
     * {@code UserRepository.searchForAdmin}). Nhân sự không bao giờ có mặt trong danh
     * sách này, nên một tham số role chỉ tạo ảo giác là lọc được.
     */
    @Transactional(readOnly = true)
    public PageResponse<AdminUserListItemResponse> search(String status, String keyword,
                                                          int page, int size) {
        UserStatus statusFilter = status == null || status.isBlank() ? null : parseStatus(status);
        // Wildcard được thêm Ở ĐÂY (repository nhận pattern hoàn chỉnh) và escape các
        // ký tự đặc biệt của LIKE để keyword người dùng không đổi ngữ nghĩa truy vấn.
        String keywordFilter = keyword == null || keyword.isBlank()
                ? null
                : "%" + escapeLike(keyword.trim().toLowerCase()) + "%";

        Page<User> found = userRepository.searchForAdmin(
                statusFilter,
                // Khi admin chủ động lọc theo status cụ thể thì không ẩn CLOSED nữa —
                // họ biết họ đang tìm gì. Khi tìm theo keyword cũng hiện CLOSED để
                // không bỏ sót kết quả. Chỉ ẩn khi duyệt danh sách không filter.
                statusFilter == null && (keyword == null || keyword.isBlank()),
                keywordFilter,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));

        // MỘT truy vấn cho toàn bộ ví của trang, không phải mỗi dòng một lượt: với size mặc
        // định 20 thì cách kia thành 21 truy vấn cho một lần vẽ bảng.
        List<UUID> pageUserIds = found.getContent().stream().map(User::getId).toList();

        Map<UUID, Wallet> walletByUser = pageUserIds.isEmpty()
                ? Map.of()
                : walletRepository.findByUserIdIn(pageUserIds).stream()
                .collect(Collectors.toMap(Wallet::getUserId, w -> w));

        // MỘT lượt đọc Redis cho cả trang, cùng lý do như truy vấn ví ở trên.
        //
        // Không có trong map nghĩa là chưa rõ, KHÔNG phải offline từ lâu: Redis bị xoá hay
        // tạm không sẵn sàng đều cho kết quả rỗng, và lúc đó cột này lùi về lastLoginAt.
        Map<UUID, Instant> lastSeenByUser = pageUserIds.isEmpty()
                ? Map.of()
                : presenceQueryService.lastSeen(pageUserIds);

        return PageResponse.from(found, user -> {
            Wallet wallet = walletByUser.get(user.getId());

            // Ví null nghĩa là tài khoản chưa từng phát sinh giao dịch. Trả "0.00" thay vì
            // null: với người vận hành, "chưa có ví" và "số dư bằng không" là cùng một điều.
            String balance = wallet == null
                    ? Money.zero().amount().toPlainString()
                    : Money.of(wallet.getBalance()).amount().toPlainString();
            String currency = wallet == null ? Wallet.DEFAULT_CURRENCY : wallet.getCurrency();

            Instant lastSeenAt = lastSeenByUser.get(user.getId());

            return new AdminUserListItemResponse(
                    user.getId(), user.getUsername(), user.getEmail(),
                    user.getRole().name(), user.getStatus().name(), user.getKycLevel().name(),
                    user.getWithdrawalPasswordHash() != null, user.getLocale(),
                    // lastLoginAt là cột của chính entity User đã nạp ở searchForAdmin, không
                    // phải quan hệ lazy — thêm nó vào đây KHÔNG phát sinh truy vấn nào.
                    user.getLastLoginAt(), user.getCreatedAt(), balance, currency,
                    // Kết luận online tính Ở ĐÂY chứ không để phía hiển thị tự so với hiện
                    // tại: ngưỡng im lặng là quyết định nghiệp vụ nằm trong cấu hình, và
                    // đồng hồ của máy người vận hành có thể lệch.
                    presenceQueryService.isOnline(lastSeenAt), lastSeenAt);
        });
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

        // Cộng thêm số tiền điều chỉnh thủ công (ADJUSTMENT) của Admin
        if (wallet != null) {
            BigDecimal adjustCredit = transactionRepository.sumCreditByWalletIdAndRefType(wallet.getId(), WalletRefType.ADJUSTMENT);
            BigDecimal adjustDebit = transactionRepository.sumDebitByWalletIdAndRefType(wallet.getId(), WalletRefType.ADJUSTMENT);
            if (adjustCredit != null) {
                deposited = deposited.add(adjustCredit);
            }
            if (adjustDebit != null) {
                withdrawn = withdrawn.add(adjustDebit);
            }
        }

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

    /**
     * Ba loại sự kiện tạo nên lịch sử đăng nhập của một tài khoản.
     *
     * {@code ADMIN_LOGIN_FORBIDDEN} KHÔNG nằm ở đây: đó là người chơi bị chặn ở cửa
     * backoffice, tức mật khẩu ĐÚNG nhưng không có quyền vào. Xếp nó thành "đăng nhập thất
     * bại" sẽ khiến người vận hành đọc lịch sử tưởng có ai đang dò mật khẩu. Sự kiện đó tra
     * qua {@code GET /admin/audit/logs?action=ADMIN_LOGIN_FORBIDDEN}.
     */
    private static final Set<String> LOGIN_HISTORY_ACTIONS = Set.of(
            AuditTrailService.LOGIN_SUCCESS,
            AuditTrailService.LOGIN_FAILED,
            AuditTrailService.ADMIN_LOGIN_SUCCESS);

    /** Trần số dòng trả về, chặn client yêu cầu cả nghìn dòng trong một lượt. */
    private static final int MAX_LOGIN_HISTORY = 100;

    /**
     * Lịch sử đăng nhập gần nhất của một tài khoản: khi nào, từ IP nào, thành công hay không.
     *
     * ĐỌC LẠI {@code audit_log} THAY VÌ TẠO BẢNG MỚI: mỗi lần đăng nhập vốn đã ghi một dòng
     * ở đó kèm IP (xem {@code AuthService.login} / {@code authenticate}). Thêm một bảng
     * riêng sẽ là ghi TRÙNG cùng một sự kiện vào hai chỗ, và khi hai chỗ lệch nhau thì không
     * còn biết tin cái nào — đúng vấn đề mà một dòng thời gian duy nhất tránh được.
     *
     * KIỂM TRA TÀI KHOẢN TỒN TẠI TRƯỚC: id sai phải trả 404 chứ không phải danh sách rỗng.
     * Với người đang điều tra, "tài khoản này chưa từng đăng nhập" và "không có tài khoản
     * nào như vậy" là hai kết luận hoàn toàn khác nhau.
     */
    @Transactional(readOnly = true)
    public List<LoginHistoryEntryResponse> loginHistory(UUID userId, int limit) {
        requireUser(userId);

        int capped = Math.min(Math.max(limit, 1), MAX_LOGIN_HISTORY);
        return auditLogRepository.findByActorIdAndActionInOrderByCreatedAtDesc(
                        userId, LOGIN_HISTORY_ACTIONS, PageRequest.of(0, capped))
                .stream()
                .map(entry -> new LoginHistoryEntryResponse(
                        entry.getCreatedAt(),
                        !AuditTrailService.LOGIN_FAILED.equals(entry.getAction()),
                        entry.getIpAddress(),
                        AuditTrailService.ADMIN_LOGIN_SUCCESS.equals(entry.getAction())
                                ? LoginHistoryEntryResponse.CHANNEL_BACKOFFICE
                                : LoginHistoryEntryResponse.CHANNEL_PLAYER))
                .toList();
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

        // Rời ACTIVE -> đẩy user khỏi hệ thống NGAY, không chỉ chặn gia hạn phiên.
        if (to != UserStatus.ACTIVE) {
            revokeSessions(userId);
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
        revokeSessions(userId);

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
     * Admin tự đặt lại MẬT KHẨU ĐĂNG NHẬP (Cấp 1) cho người chơi.
     */
    @Transactional
    public UserResponse overrideLoginPassword(UUID userId, String newPassword, UUID adminId, String ip) {
        User user = requireUser(userId);
        if (newPassword == null || newPassword.trim().length() < 6) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Mật khẩu mới phải từ 6 ký tự trở lên",
                    Map.of("field", "newPassword"), "validation.password.length");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword.trim()));
        userRepository.save(user);

        // Thu hồi phiên để buộc user đăng nhập lại bằng mật khẩu mới.
        revokeSessions(user.getId());

        audit.record(adminId, null, "ADMIN_PASSWORD_OVERRIDE",
                "USER", userId.toString(), null, ip);
        return AuthService.toResponse(user);
    }

    /**
     * Admin tự đặt lại MẬT KHẨU RÚT TIỀN (PIN 6 số - Cấp 2) cho người chơi.
     */
    @Transactional
    public UserResponse overrideWithdrawalPassword(UUID userId, String newPin, UUID adminId, String ip) {
        User user = requireUser(userId);
        if (newPin == null || !newPin.matches("^\\d{6}$")) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "Mã PIN rút tiền phải gồm đúng 6 chữ số",
                    Map.of("field", "newPin"), "validation.pin.length");
        }

        user.setWithdrawalPasswordHash(passwordEncoder.encode(newPin));
        userRepository.save(user);

        audit.record(adminId, null, "ADMIN_WITHDRAWAL_PASSWORD_OVERRIDE",
                "USER", userId.toString(), null, ip);
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

    /**
     * XÓA tài khoản người chơi — thao tác KHÔNG HOÀN TÁC.
     *
     * <h2>HAI ĐƯỜNG, HỆ THỐNG TỰ CHỌN</h2>
     * <ol>
     *   <li><b>Tài khoản SẠCH</b> (chưa từng có ví, giao dịch, cược, hoa hồng, liên kết
     *       nghi vấn, hay đề nghị phê duyệt nào): xóa hẳn khỏi cơ sở dữ liệu. Áp dụng
     *       cho tài khoản test hoặc đăng ký xong bỏ không dùng.</li>
     *   <li><b>Tài khoản có dấu vết</b>: chuyển {@code CLOSED} (đá phiên ngay, không đăng
     *       nhập được nữa, ẩn khỏi danh sách mặc định). Sổ sách tài chính và lịch sử giao
     *       dịch còn nguyên cho mục đích kiểm toán và giải quyết khiếu nại.</li>
     * </ol>
     *
     * Kiểm tra sự sạch sẽ theo thứ tự TỪ NHANH ĐẾN CHẬM: nếu một phép kiểm rẻ tiền đã
     * khẳng định có dấu vết thì không cần chạy các phép kiểm đắt hơn.
     *
     * @param request mang mã xác nhận {@code confirmPin} — sai thì bị chặn và bộ đếm tăng.
     * @return chuỗi mô tả đường đã chọn, để frontend hiện đúng thông báo.
     */
    @Transactional
    public String deleteUser(UUID userId, UUID adminId, DeleteUserRequest request, String ip) {
        requireNotSelf(userId, adminId);
        pinService.verify(adminId, request.confirmPin());

        User user = requireUser(userId);
        String username = user.getUsername();

        // Đá phiên ngay — dù sẽ xóa hẳn hay chỉ đóng, người đó không được dùng hệ thống nữa.
        revokeSessions(userId);

        boolean isClean = isCleanAccount(userId);

        if (isClean) {
            // XÓA HẲN: thứ tự quan trọng — xóa bản con TRƯỚC bản cha (users).
            // Không có FK ON DELETE CASCADE nào giúp ở đây vì mọi FK đều RESTRICT.
            userGameOddsRepository.deleteByUserId(userId);
            bankAccountRepository.deleteByUserId(userId);
            referralCodeRepository.deleteByUserId(userId);
            userRelationRepository.deleteByAncestorIdOrDescendantId(userId, userId);
            commissionRunRepository.deleteByAgentId(userId);
            accountLinkRepository.deleteByUserAIdOrUserBId(userId, userId);
            accountSignalRepository.deleteByUserId(userId);
            approvalRequestRepository.deleteByTargetUserId(userId);
            // Notifications dùng ON DELETE CASCADE — tự xóa khi user bị xóa.
            // Chat conversations dùng ON DELETE CASCADE — tự xóa khi user bị xóa.
            // Wallet chưa có transaction nào → xóa ví trước khi xóa user.
            walletRepository.findByUserId(userId).ifPresent(w -> walletRepository.delete(w));
            userRepository.deleteById(userId);

            audit.record(adminId, null, AuditTrailService.ADMIN_USER_DELETED,
                    "USER", userId.toString(),
                    Map.of("username", username, "method", "HARD_DELETE",
                           "reason", "no_financial_trail"), ip);
            return "HARD_DELETE";
        } else {
            // ĐÓNG TÀI KHOẢN: ẩn khỏi danh sách, không đăng nhập được, sổ sách còn nguyên.
            user.setStatus(UserStatus.CLOSED);
            userRepository.save(user);

            audit.record(adminId, null, AuditTrailService.ADMIN_USER_DELETED,
                    "USER", userId.toString(),
                    Map.of("username", username, "method", "SOFT_DELETE",
                           "reason", "has_financial_trail"), ip);
            return "SOFT_DELETE";
        }
    }

    /**
     * Kiểm tra tài khoản có "sạch" không — tức chưa từng có dấu vết tài chính hay điều tra.
     *
     * Thứ tự kiểm TỪ NHANH ĐẾN CHẬM (phép kiểm ví/giao dịch rẻ hơn quét toàn bộ bảng):
     * một phép kiểm trả không sạch thì thoát sớm, không chạy tiếp.
     *
     * THÊM BẢNG MỚI? Thêm phép kiểm ở đây. Nếu quên thì tài khoản có dấu vết vẫn bị xóa
     * cứng và mất dữ liệu — không có cơ chế nào phát hiện lúc chạy thật.
     */
    private boolean isCleanAccount(UUID userId) {
        // Ví: hầu hết tài khoản có ví (tạo khi đăng ký). Kiểm trước.
        var wallet = walletRepository.findByUserId(userId).orElse(null);
        if (wallet != null && transactionRepository.countByWalletId(wallet.getId()) > 0) {
            return false;
        }
        // Lệnh nạp/rút.
        if (paymentOrderRepository.countByUserId(userId) > 0) return false;
        // Lệnh cược.
        if (betRepository.countByUserId(userId) > 0) return false;
        // Hoa hồng đại lý.
        if (commissionRunRepository.countByAgentId(userId) > 0) return false;
        // Tài khoản ngân hàng (kể cả đã gỡ — là đầu mối điều tra).
        if (bankAccountRepository.countByUserId(userId) > 0) return false;
        // Liên kết đa tài khoản.
        if (accountLinkRepository.countByUserAIdOrUserBId(userId, userId) > 0) return false;
        // Đề nghị phê duyệt.
        if (approvalRequestRepository.countByTargetUserId(userId) > 0) return false;
        // Quan hệ tuyến đại lý.
        if (userRelationRepository.countByAncestorIdOrDescendantId(userId, userId) > 0) return false;
        return true;
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
