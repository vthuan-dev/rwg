package com.rwg.wallet.service;

import com.rwg.common.ApiException;
import com.rwg.common.ErrorCode;
import com.rwg.common.PageResponse;
import com.rwg.common.money.Money;
import com.rwg.config.AdminLimitProperties;
import com.rwg.identity.dto.AdminApprovalResponse;
import com.rwg.identity.repository.UserRepository;
import com.rwg.identity.service.AdminApprovalService;
import com.rwg.identity.service.AuditTrailService;
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
import java.time.LocalDate;
import java.time.ZoneOffset;
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
 * ===== SIẾT AN TOÀN (chặng 5) =====
 * Điều chỉnh ví là quyền TẠO TIỀN MỚI, nên có 3 lớp chặn xếp từng bước:
 *
 * 1. CHẮN TỰ GIAO DỊCH: admin không điều chỉnh được ví của chính mình. Trước đây
 *    thiếu chốt này nên một admin có thể tự cộng tiền rồi tự duyệt rút.
 * 2. TRẦN TỔNG MỖI NGÀY cho mỗi admin — giới hạn thiệt hại tối đa trong 24 giờ,
 *    kể cả khi từng lần đều dưới ngưỡng và đã được phê duyệt.
 * 3. QUY TRÌNH 4 MẮT: vượt trần mỗi lần thì KHÔNG thực thi ngay mà tạo đề nghị chờ
 *    admin THỨ HAI phê duyệt (xem {@link AdminApprovalService}).
 *
 * Thứ tự kiểm: tự giao dịch -> trần ngày -> trần mỗi lần. Trần ngày kiểm TRƯỚC khi
 * quyết định đi đường 4 mắt, để không tạo đề nghị mà chắc chắn sẽ bị chặn lúc thực thi.
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
    private final AdminApprovalService approvalService;
    private final AdminLimitProperties limits;

    public AdminWalletService(WalletService walletService,
                              WalletRepository walletRepository,
                              WalletTransactionRepository transactionRepository,
                              UserRepository userRepository,
                              AuditTrailService audit,
                              AdminApprovalService approvalService,
                              AdminLimitProperties limits) {
        this.walletService = walletService;
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
        this.audit = audit;
        this.approvalService = approvalService;
        this.limits = limits;
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
     * Kết quả điều chỉnh ví: HOẬC đã thực thi ngay, HOẬC tạo đề nghị chờ duyệt.
     *
     * Dùng kiểu chung thay vì hai method riêng để controller không phải tự quyết định
     * đường nào — việc đó phụ thuộc hạn mức, là nghiệp vụ của service.
     * Đúng MỘT trong hai trường khác null.
     */
    public record AdjustOutcome(WalletAdjustmentResponse executed, AdminApprovalResponse pending) {
        public boolean isPending() {
            return pending != null;
        }
    }

    /**
     * Cộng/trừ tiền thủ công. Sinh idempotencyKey MỚI mỗi lần gọi: hai lần điều chỉnh
     * giống nhau là hai nghiệp vụ khác nhau có chủ ý (khác với retry webhook), nên
     * KHÔNG gộp. Nếu cần chống double-submit từ UI, client phải tự truyền khóa riêng
     * — hiện chưa mở tham số đó để giữ API tối giản.
     *
     * Vượt trần mỗi lần -> KHÔNG chuyển tiền, trả đề nghị chờ admin thứ hai duyệt.
     */
    @Transactional
    public AdjustOutcome adjust(UUID userId, AdjustWalletRequest request, UUID adminId, String ip) {
        requireUserExists(userId);
        if (request.reason() == null || request.reason().isBlank()) {
            throw new ApiException(ErrorCode.ADMIN_REASON_REQUIRED);
        }
        String direction = normalizeDirection(request.direction());
        Money amount = parseAmount(request.amount());

        requireNotSelfDealing(userId, adminId, amount, ip);
        requireWithinDailyLimit(adminId, amount);

        // Vượt trần mỗi lần: chuyển sang quy trình 4 mắt, CHƯA chạm tiền.
        if (amount.amount().compareTo(limits.adjustMaxPerTransaction()) > 0) {
            return new AdjustOutcome(null, approvalService.createWalletAdjustment(
                    userId, direction, amount.amount(), request.reason(), adminId, ip));
        }

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

        return new AdjustOutcome(new WalletAdjustmentResponse(
                userId.toString(), direction,
                amount.amount().toPlainString(),
                balanceBefore.amount().toPlainString(),
                balanceAfter.amount().toPlainString(),
                request.reason(), idempotencyKey, Instant.now()), null);
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

    /**
     * Trần TỔNG mỗi admin mỗi ngày UTC. Cộng dồn từ chính bảng ledger nên không thể
     * lệch với số tiền thực sự đã chuyển.
     *
     * Dùng ngày UTC để nhất quán với hạn mức rút theo ngày và kỳ chốt hoa hồng.
     */
    private void requireWithinDailyLimit(UUID adminId, Money amount) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Instant from = today.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant to = today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        BigDecimal used = transactionRepository.sumAdjustmentsByAdmin(adminId.toString(), from, to);
        if (used == null) {
            used = BigDecimal.ZERO;
        }
        BigDecimal afterThis = used.add(amount.amount());
        if (afterThis.compareTo(limits.adjustDailyMaxPerAdmin()) > 0) {
            throw new ApiException(ErrorCode.ADMIN_LIMIT_EXCEEDED,
                    ErrorCode.ADMIN_LIMIT_EXCEEDED.defaultMessage(),
                    Map.of("used", used.toPlainString(),
                            "limit", limits.adjustDailyMaxPerAdmin().toPlainString()),
                    "error.admin.daily_limit_exceeded");
        }
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
