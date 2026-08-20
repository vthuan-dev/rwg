package com.rwg.payment.service;

import com.rwg.bank.domain.BankAccount;
import com.rwg.bank.domain.BankAccountStatus;
import com.rwg.bank.repository.BankAccountRepository;
import com.rwg.common.ApiException;
import com.rwg.common.ErrorCode;
import com.rwg.common.money.Money;
import com.rwg.config.PaymentProperties;
import com.rwg.config.WithdrawalProperties;
import com.rwg.identity.domain.User;
import com.rwg.identity.repository.UserRepository;
import com.rwg.identity.service.AuditTrailService;
import com.rwg.identity.service.LoginRateLimiter;
import com.rwg.payment.domain.PaymentOrder;
import com.rwg.payment.domain.PaymentStatus;
import com.rwg.payment.domain.PaymentType;
import com.rwg.payment.dto.PaymentOrderResponse;
import com.rwg.payment.dto.WithdrawalRequest;
import com.rwg.payment.repository.PaymentOrderRepository;
import com.rwg.wallet.domain.WalletRefType;
import com.rwg.wallet.service.WalletService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

/**
 * Nghiệp vụ RÚT TIỀN (chặng 2 Phase b, mô hình M1; đã sửa theo review 3 chiều):
 * - Tạo lệnh: verify withdrawal password (hash, có rate-limit chống brute-force — m9)
 *   -> bắt buộc bank default -> KHÓA row ví per-user (SELECT ... FOR UPDATE — M3)
 *   rồi mới kiểm hạn mức (min $20, max $5,000/ngày) -> DEBIT ví NGAY khi tạo lệnh.
 * - Admin duyệt/từ chối: chuyển trạng thái bằng 1 UPDATE điều kiện NGUYÊN TỬ
 *   (C2) — 2 thao tác song song chỉ 1 thao tác thắng; reject đổi VOIDED TRƯỚC
 *   rồi mới credit REFUND (idempotent key REFUND:{orderId}), cùng transaction.
 * Mọi bước audit qua AuditTrailService.
 */
@Service
public class WithdrawalService {

    /** Tiền tố bucket rate-limit riêng cho mật khẩu rút tiền (tách khỏi bucket đăng nhập). */
    private static final String RATE_LIMIT_PREFIX = "withdrawal:";

    private final PaymentOrderRepository orderRepository;
    private final WalletService walletService;
    private final UserRepository userRepository;
    private final BankAccountRepository bankAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final WithdrawalProperties withdrawalProperties;
    private final PaymentProperties paymentProperties;
    private final LoginRateLimiter loginRateLimiter;
    private final AuditTrailService audit;

    public WithdrawalService(PaymentOrderRepository orderRepository,
                             WalletService walletService,
                             UserRepository userRepository,
                             BankAccountRepository bankAccountRepository,
                             PasswordEncoder passwordEncoder,
                             WithdrawalProperties withdrawalProperties,
                             PaymentProperties paymentProperties,
                             LoginRateLimiter loginRateLimiter,
                             AuditTrailService audit) {
        this.orderRepository = orderRepository;
        this.walletService = walletService;
        this.userRepository = userRepository;
        this.bankAccountRepository = bankAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.withdrawalProperties = withdrawalProperties;
        this.paymentProperties = paymentProperties;
        this.loginRateLimiter = loginRateLimiter;
        this.audit = audit;
    }

    /** Tạo lệnh rút: debit ví ngay (M1) + order PENDING chờ admin duyệt. */
    @Transactional
    public PaymentOrderResponse request(UUID userId, WithdrawalRequest request, String ip) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        ErrorCode.NOT_FOUND.defaultMessage(), null, "error.not_found.user"));

        // 0) Chống brute-force mật khẩu rút tiền (m9): reuse LoginRateLimiter theo userId.
        String limitKey = RATE_LIMIT_PREFIX + userId;
        LoginRateLimiter.AttemptResult pre = loginRateLimiter.checkBeforeAttempt(ip, limitKey);
        if (pre.locked()) {
            throw new ApiException(ErrorCode.RATE_LIMITED,
                    ErrorCode.RATE_LIMITED.defaultMessage(),
                    Map.of("retryAfterSeconds", pre.retryAfterSeconds()));
        }

        // 1) Mật khẩu rút tiền phải ĐÃ đặt và khớp hash.
        if (user.getWithdrawalPasswordHash() == null) {
            throw new ApiException(ErrorCode.WITHDRAWAL_PASSWORD_NOT_SET);
        }
        if (!passwordEncoder.matches(request.withdrawalPassword(), user.getWithdrawalPasswordHash())) {
            LoginRateLimiter.AttemptResult after = loginRateLimiter.recordFailure(ip, limitKey);
            if (after.locked()) {
                throw new ApiException(ErrorCode.RATE_LIMITED,
                        ErrorCode.RATE_LIMITED.defaultMessage(),
                        Map.of("retryAfterSeconds", after.retryAfterSeconds()));
            }
            // Ngưỡng captcha (captchaRequired) KHÔNG áp dụng cho endpoint này.
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS,
                    ErrorCode.INVALID_CREDENTIALS.defaultMessage(),
                    null, "error.invalid_credentials.withdrawal_password_mismatch");
        }
        loginRateLimiter.reset(ip, limitKey);

        // 2) Bắt buộc có tài khoản ngân hàng mặc định.
        BankAccount bank = bankAccountRepository
                .findFirstByUserIdAndIsDefaultTrueAndStatus(userId, BankAccountStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(ErrorCode.BANK_ACCOUNT_REQUIRED));

        // 3) Khóa row ví per-user (SELECT ... FOR UPDATE — fix M3): serialize các lệnh
        //    song song của cùng user để sumAmountSince + insert chạy tuần tự.
        walletService.lockWallet(userId);

        // 4) Hạn mức: min mỗi lệnh + tổng tối đa/ngày (UTC) — đọc SAU khi khóa.
        BigDecimal amount = parseAmount(request.amount());
        Instant startOfDayUtc = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
        BigDecimal usedToday = orderRepository.sumAmountSince(
                userId, PaymentType.WITHDRAWAL, PaymentStatus.VOIDED, startOfDayUtc);
        if (usedToday.add(amount).compareTo(withdrawalProperties.dailyMaxAmount()) > 0) {
            throw new ApiException(ErrorCode.WITHDRAWAL_LIMIT_EXCEEDED);
        }

        // 5) Lưu lệnh rồi DEBIT ví trong CÙNG transaction (đủ tiền mới có lệnh).
        PaymentOrder order = orderRepository.save(PaymentOrder.withdrawal(
                userId, providerName(), amount, bank.getId(), "WITHDRAWAL:" + UUID.randomUUID()));
        walletService.debit(userId, Money.of(amount), WalletRefType.WITHDRAWAL,
                order.getId().toString(), "WITHDRAWAL:" + order.getId());

        audit.record(userId, user.getUsername(), AuditTrailService.WITHDRAWAL_REQUESTED,
                "PAYMENT_ORDER", order.getId().toString(),
                Map.of("amount", amount.toPlainString(),
                        "bankAccountId", bank.getId().toString(),
                        "maskedLast4", bank.getMaskedLast4()), ip);
        return PaymentOrderResponse.from(order);
    }

    /**
     * Admin duyệt: chuyển PENDING -> SETTLED bằng 1 UPDATE điều kiện nguyên tử (fix C2).
     * KHÔNG động ví (tiền đã debit lúc tạo lệnh). Lệnh đã bị luồng khác chuyển -> 400.
     *
     * CHẮN TỰ DUYỆT (chặng 5): admin không được duyệt lệnh rút của chính mình. Thiếu
     * chốt này thì một admin có thể tự cộng tiền vào ví rồi tự duyệt rút — chuyển tiền
     * ra khỏi sàn mà không ai khác biết.
     */
    @Transactional
    public PaymentOrderResponse approve(UUID orderId, UUID adminId, String ip) {
        requireNotOwnOrder(orderId, adminId, "WITHDRAWAL_APPROVE", ip);
        int updated = orderRepository.transitionStatus(
                orderId, PaymentStatus.PENDING, PaymentStatus.SETTLED, nowMicros());
        if (updated == 0) {
            throw requireKnownPendingWithdrawal(orderId);
        }
        PaymentOrder order = orderRepository.findFirstById(orderId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        ErrorCode.NOT_FOUND.defaultMessage(), null, "error.not_found.payment_order"));
        audit.record(adminId, null, AuditTrailService.WITHDRAWAL_APPROVED,
                "PAYMENT_ORDER", order.getId().toString(),
                Map.of("amount", order.getAmount().toPlainString(),
                        "userId", order.getUserId().toString()), ip);
        return PaymentOrderResponse.from(order);
    }

    /**
     * Admin từ chối (fix C2): chuyển PENDING -> VOIDED NGUYÊN TỬ TRƯỚC, thắng rồi
     * mới hoàn tiền credit(REFUND) idempotent — CÙNG transaction; nếu credit lỗi
     * cả transaction rollback, lệnh trở về PENDING (không mất/không hoàn hụt tiền).
     *
     * Cũng chặn tự xử lý lệnh của chính mình: từ chối làm tiền quay lại ví nên vẫn
     * là thao tác tài chính trên tài khoản của chính admin đó.
     */
    @Transactional
    public PaymentOrderResponse reject(UUID orderId, UUID adminId, String ip) {
        requireNotOwnOrder(orderId, adminId, "WITHDRAWAL_REJECT", ip);
        int updated = orderRepository.transitionStatus(
                orderId, PaymentStatus.PENDING, PaymentStatus.VOIDED, nowMicros());
        if (updated == 0) {
            throw requireKnownPendingWithdrawal(orderId);
        }
        PaymentOrder order = orderRepository.findFirstById(orderId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        ErrorCode.NOT_FOUND.defaultMessage(), null, "error.not_found.payment_order"));

        // Chỉ luồng THẮNG transition mới hoàn tiền; key REFUND:{orderId} chặn hoàn 2 lần.
        Money balanceAfter = walletService.credit(order.getUserId(), Money.of(order.getAmount()),
                WalletRefType.REFUND, order.getId().toString(), "REFUND:" + order.getId());

        audit.record(adminId, null, AuditTrailService.WITHDRAWAL_REJECTED,
                "PAYMENT_ORDER", order.getId().toString(),
                Map.of("amount", order.getAmount().toPlainString(),
                        "userId", order.getUserId().toString(),
                        "balanceAfter", balanceAfter.amount().toPlainString()), ip);
        return PaymentOrderResponse.from(order);
    }

    // ===== helpers =====

    /**
     * Admin KHÔNG được tự xử lý lệnh rút của chính mình.
     *
     * Kiểm TRƯỚC khi transition trạng thái — nếu kiểm sau, lệnh đã chuyển sang
     * SETTLED/VOIDED rồi mới ném lỗi, và dù transaction rollback thì đây vẫn là thứ tự
     * sai về ý định: đã biết không được phép thì không nên bắt đầu.
     *
     * Lệnh không tồn tại thì bỏ qua — để luồng chính trả 404 đúng ngữ nghĩa.
     */
    private void requireNotOwnOrder(UUID orderId, UUID adminId, String attempt, String ip) {
        PaymentOrder order = orderRepository.findFirstById(orderId).orElse(null);
        if (order == null || !order.getUserId().equals(adminId)) {
            return;
        }
        audit.record(adminId, null, AuditTrailService.ADMIN_SELF_DEALING_BLOCKED,
                "PAYMENT_ORDER", orderId.toString(),
                Map.of("attempt", attempt,
                        "amount", order.getAmount().toPlainString()), ip);
        throw new ApiException(ErrorCode.CANNOT_APPROVE_OWN_REQUEST,
                ErrorCode.CANNOT_APPROVE_OWN_REQUEST.defaultMessage(), null,
                "error.admin.cannot_decide_own_withdrawal");
    }

    /**
     * Lệnh KHÔNG chuyển được trạng thái: phân biệt KHÔNG TỒN TẠI (404) với
     * đã bị luồng khác duyệt/từ chối trước (400 — idempotent-guard).
     */
    private ApiException requireKnownPendingWithdrawal(UUID orderId) {
        PaymentOrder order = orderRepository.findFirstById(orderId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        ErrorCode.NOT_FOUND.defaultMessage(), null, "error.not_found.payment_order"));
        if (order.getType() != PaymentType.WITHDRAWAL || order.getStatus() != PaymentStatus.PENDING) {
            return new ApiException(ErrorCode.INVALID_REQUEST);
        }
        // Transition trả 0 rows nhưng lệnh vẫn PENDING (race cực hẹp giữa 2 câu lệnh)
        // -> thử lại ngữ nghĩa: báo yêu cầu không hợp lệ để an toàn.
        return new ApiException(ErrorCode.INVALID_REQUEST);
    }

    private Instant nowMicros() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    private BigDecimal parseAmount(String raw) {
        BigDecimal amount;
        try {
            amount = new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    ErrorCode.VALIDATION_ERROR.defaultMessage(), Map.of("field", "amount"),
                    "validation.withdrawal.amount.invalid");
        }
        if (amount.signum() <= 0) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    ErrorCode.VALIDATION_ERROR.defaultMessage(), Map.of("field", "amount"),
                    "validation.withdrawal.amount.invalid");
        }
        if (amount.compareTo(withdrawalProperties.minAmount()) < 0) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    ErrorCode.VALIDATION_ERROR.defaultMessage(), Map.of("field", "amount"),
                    "validation.withdrawal.amount.min");
        }
        if (amount.compareTo(withdrawalProperties.dailyMaxAmount()) > 0) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    ErrorCode.VALIDATION_ERROR.defaultMessage(), Map.of("field", "amount"),
                    "validation.withdrawal.amount.max");
        }
        return amount;
    }

    private String providerName() {
        return paymentProperties.provider() == null ? "stub" : paymentProperties.provider();
    }
}
