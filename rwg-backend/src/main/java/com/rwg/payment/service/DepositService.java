package com.rwg.payment.service;

import com.rwg.common.ApiException;
import com.rwg.common.ErrorCode;
import com.rwg.common.money.Money;
import com.rwg.config.PaymentProperties;
import com.rwg.identity.service.AuditTrailService;
import com.rwg.payment.domain.PaymentOrder;
import com.rwg.payment.domain.PaymentStatus;
import com.rwg.payment.domain.PaymentType;
import com.rwg.payment.dto.DepositRequest;
import com.rwg.payment.dto.PaymentCallbackRequest;
import com.rwg.payment.dto.PaymentOrderResponse;
import com.rwg.payment.repository.PaymentOrderRepository;
import com.rwg.wallet.domain.WalletRefType;
import com.rwg.wallet.repository.WalletRepository;
import com.rwg.wallet.service.WalletService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Nghiệp vụ NẠP TIỀN (chặng 2 Phase b; đã sửa theo review 3 chiều):
 * tạo order PENDING -> gateway duyệt (stub auto-success) -> credit ví IDEMPOTENT
 * (idempotencyKey "DEPOSIT:{orderId}" + guard DB duy nhất toàn cục) +
 * {@link FirstDepositEvent} CHỈ lần đầu nhờ claim NGUYÊN TỬ ở DB (fix M5).
 * Webhook callback: chỉ xử lý SUCCESS/FAILED, status khác (PROCESSING...) -> 200
 * no-op (fix M4); chuyển trạng thái bằng UPDATE điều kiện nguyên tử nên 2 callback
 * song song chỉ 1 luồng thắng.
 *
 * Hạn mức theo KE-HOACH: min $10 / max $50,000 mỗi lệnh nạp.
 */
@Service
public class DepositService {

    static final BigDecimal MIN_DEPOSIT = new BigDecimal("10");
    static final BigDecimal MAX_DEPOSIT = new BigDecimal("50000");

    private final PaymentOrderRepository orderRepository;
    private final PaymentGateway gateway;
    private final WalletService walletService;
    private final WalletRepository walletRepository;
    private final AuditTrailService audit;
    private final PaymentProperties paymentProperties;
    private final ApplicationEventPublisher events;

    public DepositService(PaymentOrderRepository orderRepository,
                          PaymentGateway gateway,
                          WalletService walletService,
                          WalletRepository walletRepository,
                          AuditTrailService audit,
                          PaymentProperties paymentProperties,
                          ApplicationEventPublisher events) {
        this.orderRepository = orderRepository;
        this.gateway = gateway;
        this.walletService = walletService;
        this.walletRepository = walletRepository;
        this.audit = audit;
        this.paymentProperties = paymentProperties;
        this.events = events;
    }

    /** Tạo lệnh nạp và (stub) hoàn tất ngay: PENDING -> SUCCESS + credit ví. */
    @Transactional
    public PaymentOrderResponse deposit(UUID userId, DepositRequest request) {
        BigDecimal amount = parseAmount(request.amount());
        PaymentOrder order = orderRepository.save(PaymentOrder.deposit(
                userId, providerName(), amount, "DEPOSIT:" + UUID.randomUUID()));

        GatewayResult result = gateway.createDeposit(order);
        order.setProviderTxnId(result.providerTxnId());
        if (result.approved()) {
            order.setStatus(PaymentStatus.SUCCESS);
            orderRepository.save(order);
            completeDeposit(order);
        } else {
            order.setStatus(PaymentStatus.FAILED);
            orderRepository.save(order);
        }
        return PaymentOrderResponse.from(order);
    }

    /**
     * Webhook provider (fix M4):
     * - CHỈ xử lý SUCCESS / FAILED; mọi status khác (vd PROCESSING) -> 200 no-op.
     * - Lệnh đã ở trạng thái cuối -> trả hiện trạng, KHÔNG xử lý lại (idempotent).
     * - Chuyển trạng thái bằng 1 UPDATE điều kiện nguyên tử: 2 callback song song
     *   cùng providerTxnId chỉ đúng 1 luồng thắng, luồng thua trả hiện trạng.
     */
    @Transactional
    public PaymentOrderResponse handleCallback(PaymentCallbackRequest request) {
        PaymentOrder order = orderRepository.findFirstByProviderTxnId(request.providerTxnId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        ErrorCode.NOT_FOUND.defaultMessage(), null, "error.not_found.payment_order"));

        String status = request.status() == null ? "" : request.status().trim().toUpperCase(Locale.ROOT);
        boolean success = "SUCCESS".equals(status);
        boolean failed = "FAILED".equals(status);
        if (!success && !failed) {
            return PaymentOrderResponse.from(order); // PROCESSING/không rõ -> 200 no-op
        }
        if (order.getStatus().isTerminal()) {
            return PaymentOrderResponse.from(order); // idempotent no-op
        }
        if (order.getType() != PaymentType.DEPOSIT) {
            return PaymentOrderResponse.from(order); // callback này chỉ dành cho lệnh nạp
        }

        PaymentStatus target = success ? PaymentStatus.SUCCESS : PaymentStatus.FAILED;
        int updated = orderRepository.transitionStatus(order.getId(), PaymentStatus.PENDING, target, nowMicros());
        if (updated == 0) {
            // Luồng khác đã chuyển trạng thái trước -> trả hiện trạng (idempotent).
            PaymentOrder fresh = orderRepository.findFirstById(order.getId()).orElse(order);
            return PaymentOrderResponse.from(fresh);
        }

        PaymentOrder fresh = orderRepository.findFirstById(order.getId()).orElse(order);
        if (success) {
            completeDeposit(fresh);
        }
        return PaymentOrderResponse.from(fresh);
    }

    /**
     * Hoàn tất nạp: credit ví (idempotent theo orderId) + claim mốc nạp đầu tiên
     * NGUYÊN TỬ ở DB rồi mới phát {@link FirstDepositEvent}.
     * LƯU Ý: lệnh PHẢI đã ở trạng thái SUCCESS trước khi gọi (do caller chuyển
     * bằng UPDATE nguyên tử hoặc lệnh vừa tạo). Tiền chỉ vào ví ĐÚNG 1 lần dù
     * callback đến nhiều lần (guard idempotency_key tầng DB).
     */
    private void completeDeposit(PaymentOrder order) {
        Money balanceAfter = walletService.credit(order.getUserId(), Money.of(order.getAmount()),
                WalletRefType.DEPOSIT, order.getId().toString(), "DEPOSIT:" + order.getId());

        audit.record(order.getUserId(), null, AuditTrailService.DEPOSIT_COMPLETED,
                "PAYMENT_ORDER", order.getId().toString(),
                Map.of("amount", order.getAmount().toPlainString(),
                        "balanceAfter", balanceAfter.amount().toPlainString()), null);

        // Claim mốc nạp đầu tiên NGUYÊN TỬ (fix M5): conditional UPDATE trên wallets —
        // 2 giao dịch nạp song song chỉ đúng 1 row thắng, hết cảnh event phát 2 lần.
        boolean firstDeposit = walletRepository.claimFirstDeposit(order.getUserId(), nowMicros()) == 1;
        if (firstDeposit) {
            // Listener đăng ký @TransactionalEventListener(AFTER_COMMIT) nên chỉ nhận
            // event SAU khi transaction nạp tiền commit thành công (fix M5).
            events.publishEvent(new FirstDepositEvent(this, order.getUserId(),
                    order.getId(), order.getAmount()));
        }
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
                    "validation.deposit.amount.invalid");
        }
        if (amount.signum() <= 0) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    ErrorCode.VALIDATION_ERROR.defaultMessage(), Map.of("field", "amount"),
                    "validation.deposit.amount.invalid");
        }
        if (amount.compareTo(MIN_DEPOSIT) < 0) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    ErrorCode.VALIDATION_ERROR.defaultMessage(), Map.of("field", "amount"),
                    "validation.deposit.amount.min");
        }
        if (amount.compareTo(MAX_DEPOSIT) > 0) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR,
                    ErrorCode.VALIDATION_ERROR.defaultMessage(), Map.of("field", "amount"),
                    "validation.deposit.amount.max");
        }
        return amount;
    }

    private String providerName() {
        return paymentProperties.provider() == null ? "stub" : paymentProperties.provider();
    }
}
