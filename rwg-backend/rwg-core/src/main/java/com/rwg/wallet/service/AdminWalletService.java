package com.rwg.wallet.service;

import com.rwg.common.ApiException;
import com.rwg.common.ErrorCode;
import com.rwg.common.PageResponse;
import com.rwg.common.money.Money;
import com.rwg.identity.repository.UserRepository;
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

    public AdminWalletService(WalletService walletService,
                              WalletRepository walletRepository,
                              WalletTransactionRepository transactionRepository,
                              UserRepository userRepository,
                              AuditTrailService audit) {
        this.walletService = walletService;
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
        this.audit = audit;
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
     */
    @Transactional
    public WalletAdjustmentResponse adjust(UUID userId, AdjustWalletRequest request, UUID adminId, String ip) {
        requireUserExists(userId);
        if (request.reason() == null || request.reason().isBlank()) {
            throw new ApiException(ErrorCode.ADMIN_REASON_REQUIRED);
        }
        String direction = normalizeDirection(request.direction());
        Money amount = parseAmount(request.amount());

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

        return new WalletAdjustmentResponse(
                userId.toString(), direction,
                amount.amount().toPlainString(),
                balanceBefore.amount().toPlainString(),
                balanceAfter.amount().toPlainString(),
                request.reason(), idempotencyKey, Instant.now());
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
