package com.rwg.payment.service;

import com.rwg.payment.domain.PaymentOrder;
import com.rwg.payment.domain.PaymentStatus;

/**
 * SPI cổng thanh toán (chặng 2 Phase b). MVP dùng {@link StubPaymentGateway}
 * (rwg.payment.provider=stub); provider thật implement interface này ở chặng sau.
 */
public interface PaymentGateway {

    /**
     * Khởi tạo lệnh nạp tiền phía provider.
     * Trả mã giao dịch provider + kết quả duyệt (stub: luôn duyệt sau delay ngắn).
     */
    GatewayResult createDeposit(PaymentOrder order);

    /** Tra cứu trạng thái lệnh theo mã giao dịch provider (đối soát/webhook hụt). */
    PaymentStatus queryStatus(String providerTxnId);
}
