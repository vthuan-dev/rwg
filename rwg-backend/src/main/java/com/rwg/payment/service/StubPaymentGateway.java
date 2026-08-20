package com.rwg.payment.service;

import com.rwg.config.PaymentProperties;
import com.rwg.payment.domain.PaymentOrder;
import com.rwg.payment.domain.PaymentStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Gateway giả lập (rwg.payment.provider=stub — mặc định).
 * Auto-SUCCESS sau {@code rwg.payment.success-delay} (test đặt PT0S cho nhanh).
 * Provider thật sẽ implement {@link PaymentGateway} và bật bằng property.
 */
@Component
@ConditionalOnProperty(name = "rwg.payment.provider", havingValue = "stub", matchIfMissing = true)
public class StubPaymentGateway implements PaymentGateway {

    private final PaymentProperties properties;

    public StubPaymentGateway(PaymentProperties properties) {
        this.properties = properties;
    }

    @Override
    public GatewayResult createDeposit(PaymentOrder order) {
        // Giả lập độ trễ xử lý phía provider (dev/UX); test cấu hình PT0S.
        if (properties.successDelay() != null && !properties.successDelay().isZero()) {
            try {
                Thread.sleep(properties.successDelay().toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        // Stub luôn duyệt: sinh mã giao dịch provider dạng STUB-<uuid>.
        return new GatewayResult("STUB-" + UUID.randomUUID(), true);
    }

    @Override
    public PaymentStatus queryStatus(String providerTxnId) {
        // Stub: mọi lệnh đã tạo đều SUCCESS.
        return PaymentStatus.SUCCESS;
    }
}
