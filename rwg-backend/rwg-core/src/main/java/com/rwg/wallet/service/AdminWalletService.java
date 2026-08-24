package com.rwg.wallet.service;

import com.rwg.common.ApiException;
import com.rwg.common.ErrorCode;
import com.rwg.common.PageResponse;
import com.rwg.common.money.Money;
import com.rwg.identity.repository.UserRepository;
import com.rwg.identity.service.AuditTrailService;
import com.rwg.notification.domain.NotificationType;
import com.rwg.notification.service.NotificationService;
import com.rwg.wallet.domain.Wallet;
import com.rwg.wallet.domain.WalletRefType;
import com.rwg.wallet.dto.AdjustWalletRequest;
import com.rwg.wallet.dto.WalletAdjustmentResponse;
import com.rwg.wallet.dto.WalletResponse;
import com.rwg.wallet.dto.WalletTransactionResponse;
import com.rwg.wallet.repository.WalletRepository;
import com.rwg.wallet.repository.WalletTransactionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Nghiệp vụ ví phía quản trị (chặng 3): xem ví / ledger của user và ĐIỀU CHỈNH
 * SỐ DƯ THỦ CÔNG.
 *
 * NGUYÊN TẮC BẤT BIẾN — điều chỉnh thủ công KHÔNG được viết SQL số dư trực tiếp:
 * mọi thay đổi đi qua {@link WalletService#credit}/{@link WalletService#debit} nên
 * luôn sinh đúng 1 dòng ledger + guard idempotency ở tầng DB. Nhờ vậy job đối soát
 * (SUM(credit) - SUM(debit) vs wallets.balance) không bao giờ lệch vì thao tác admin.
 *
 * Chỉ hỗ trợ DELTA (CREDIT/DEBIT), KHÔNG có "set số dư = X": ledger phải luôn tái
 * dựng lại được số dư từ đầu.
 *
 * DEBIT vượt số dư tự trả INSUFFICIENT_BALANCE do WalletService dùng
 * {@code UPDATE ... WHERE balance >= amt} — không cần kiểm tra trước, không có race.
 *
 * ===== KHÔNG CÒN HẠN MỨC SỐ TIỀN =====
 * Trước đây có hai trần chặn: trần mỗi lần (vượt thì phải qua quy trình 4 mắt, chờ
 * admin thứ hai phê duyệt) và trần TỔNG mỗi admin mỗi ngày. Cả hai ĐÃ BỎ theo yêu cầu
 * vận hành: nạp bao nhiêu cũng thực thi ngay, không có bước chờ duyệt.
 *
 * CHỐT CHẶN CÒN LẠI là tự giao dịch: admin KHÔNG điều chỉnh được ví của chính mình.
 * Chốt này KHÔNG thuộc quy trình 4 mắt và cố tình giữ lại — nó ngăn kịch bản một admin
 * tự cộng tiền cho mình rồi tự rút ra, là loại thất thoát khó truy trách nhiệm nhất.
 */
@Service
public class AdminWalletService {

    private static final String DIRECTION_CREDIT = "CREDIT";
    private static final String DIRECTION_DEBIT = "DEBIT";

    private final WalletService walletService;
    private final WalletRepository walletRepository;
    private final WalletTransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final AuditTrailService audit;
    private final NotificationService notifications;

    public AdminWalletService(WalletService walletService,
                              WalletRepository walletRepository,
                              WalletTransactionRepository transactionRepository,
                              UserRepository userRepository,
                              AuditTrailService audit,
                              NotificationService notifications) {
        this.walletService = walletService;
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
        this.audit = audit;
        this.notifications = notifications;
    }

    // ===== ĐỌC =====

    /** Ví của user (không tạo ví nếu chưa có — trả ví ảo balance 0). */
    @Transactional(readOnly = true)
    public WalletResponse walletOf(UUID userId) {
        requireUserExists(userId);
        return walletService.getWallet(userId);
    }

    /**
     * Lịch sử biến động số dư. refType null = tất cả; truyền ADJUSTMENT để soi riêng
     * các lần admin can thiệp thủ công.
     */
    @Transactional(readOnly = true)
    public PageResponse<WalletTransactionResponse> ledgerOf(UUID userId, String refType, int page, int size) {
        requireUserExists(userId);
        Wallet wallet = walletRepository.findByUserId(userId).orElse(null);
        if (wallet == null) {
            return new PageResponse<>(List.of(), page, size, 0, 0, true);
        }
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        if (refType == null || refType.isBlank()) {
            return PageResponse.from(transactionRepository.findByWalletId(wallet.getId(), pageable),
                    WalletService::toResponse);
        }
        return PageResponse.from(
                transactionRepository.findByWalletIdAndRefType(wallet.getId(), parseRefType(refType), pageable),
                WalletService::toResponse);
    }

    // ===== GHI =====

    /**
     * Cộng/trừ tiền thủ công. Sinh idempotencyKey MỚI mỗi lần gọi: hai lần điều chỉnh
     * giống nhau là hai nghiệp vụ khác nhau có chủ ý (khác với retry webhook), nên
     * KHÔNG gộp. Nếu cần chống double-submit từ UI, client phải tự truyền khóa riêng
     * — hiện chưa mở tham số đó để giữ API tối giản.
     *
     * KHÔNG còn ngưỡng số tiền: mọi khoản thực thi ngay trong cùng transaction này.
     */
    @Transactional
    public WalletAdjustmentResponse adjust(UUID userId, AdjustWalletRequest request, UUID adminId, String ip) {
        requireUserExists(userId);
        if (request.reason() == null || request.reason().isBlank()) {
            throw new ApiException(ErrorCode.ADMIN_REASON_REQUIRED);
        }
        String direction = normalizeDirection(request.direction());
        Money amount = parseAmount(request.amount());

        requireNotSelfDealing(userId, adminId, amount, ip);

        Money balanceBefore = walletService.getBalance(userId);
        String idempotencyKey = "ADJUST:" + UUID.randomUUID();

        Money balanceAfter = DIRECTION_CREDIT.equals(direction)
                ? walletService.credit(userId, amount, WalletRefType.ADJUSTMENT, adminId.toString(), idempotencyKey)
                : walletService.debit(userId, amount, WalletRefType.ADJUSTMENT, adminId.toString(), idempotencyKey);

        audit.record(adminId, null, AuditTrailService.ADMIN_WALLET_ADJUSTED,
                "WALLET", userId.toString(),
                Map.of("direction", direction,
                        "amount", amount.amount().toPlainString(),
                        "reason", request.reason(),
                        "balanceBefore", balanceBefore.amount().toPlainString(),
                        "balanceAfter", balanceAfter.amount().toPlainString(),
                        "idempotencyKey", idempotencyKey), ip);

        // Báo cho người chơi. TRƯỚC ĐÂY KHÔNG CÓ BƯỚC NÀY: admin cộng tiền xong, người chơi
        // không được báo gì và chỉ tự phát hiện khi tình cờ mở ví.
        //
        // Ghi trong CÙNG transaction với việc chuyển tiền; việc đẩy WebSocket được
        // NotificationService hoãn tới sau khi commit.
        notifications.notifyMoney(userId,
                DIRECTION_CREDIT.equals(direction)
                        ? NotificationType.ADMIN_CREDIT
                        : NotificationType.ADMIN_DEBIT,
                amount.amount());

        return new WalletAdjustmentResponse(
                userId.toString(), direction,
                amount.amount().toPlainString(),
                balanceBefore.amount().toPlainString(),
                balanceAfter.amount().toPlainString(),
                request.reason(), idempotencyKey, Instant.now());
    }

    // ===== các lớp chặn an toàn =====

    /**
     * Admin KHÔNG được điều chỉnh ví của chính mình.
     *
     * Ghi audit trước khi ném lỗi: đây là dấu hiệu cần điều tra chứ không phải lỗi
     * thao tác thông thường. audit.record chạy REQUIRES_NEW nên vết vẫn còn dù giao
     * dịch chính rollback vì ApiException.
     */
    private void requireNotSelfDealing(UUID userId, UUID adminId, Money amount, String ip) {
        if (!userId.equals(adminId)) {
            return;
        }
        audit.record(adminId, null, AuditTrailService.ADMIN_SELF_DEALING_BLOCKED,
                "WALLET", userId.toString(),
                Map.of("attempt", "WALLET_ADJUSTMENT",
                        "amount", amount.amount().toPlainString()), ip);
        throw new ApiException(ErrorCode.CANNOT_MODIFY_SELF,
                ErrorCode.CANNOT_MODIFY_SELF.defaultMessage(), null,
                "error.admin.cannot_adjust_own_wallet");
    }

    // ===== helpers =====

    private void requireUserExists(UUID userId) {
        if (!userRepository.existsById(userId)) {
            throw new ApiException(ErrorCode.NOT_FOUND,
                    ErrorCode.NOT_FOUND.defaultMessage(), null, "error.not_found.user");
        }
    }

    private String normalizeDirection(String raw) {
        String direction = raw == null ? "" : raw.trim().toUpperCase();
        if (!DIRECTION_CREDIT.equals(direction) && !DIRECTION_DEBIT.equals(direction)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    ErrorCode.VALIDATION_ERROR.defaultMessage(), Map.of("field", "direction"),
                    "validation.admin.adjust.direction.invalid");
        }
        return direction;
    }

    /** Parse String -> BigDecimal (CẤM float/double); bắt buộc > 0. */
    private Money parseAmount(String raw) {
        BigDecimal amount;
        try {
            amount = new BigDecimal(raw.trim());
        } catch (NumberFormatException invalid) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    ErrorCode.VALIDATION_ERROR.defaultMessage(), Map.of("field", "amount"),
                    "validation.admin.adjust.amount.invalid");
        }
        if (amount.signum() <= 0) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    ErrorCode.VALIDATION_ERROR.defaultMessage(), Map.of("field", "amount"),
                    "validation.admin.adjust.amount.invalid");
        }
        return Money.of(amount);
    }

    private WalletRefType parseRefType(String raw) {
        try {
            return WalletRefType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException invalid) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    ErrorCode.VALIDATION_ERROR.defaultMessage(), Map.of("field", "refType"));
        }
    }
}
