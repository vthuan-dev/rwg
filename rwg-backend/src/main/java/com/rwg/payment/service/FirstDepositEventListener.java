package com.rwg.payment.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listener mốc nạp tiền đầu tiên (fix review M5).
 *
 * ĐĂNG KÝ {@code @TransactionalEventListener(phase = AFTER_COMMIT)}: chỉ nhận event
 * SAU KHI transaction nạp tiền COMMIT thành công — không bao giờ xử lý nghiệp vụ
 * dựa trên dữ liệu chưa commit (bản cũ phát event giữa transaction).
 *
 * Hiện là hook log; chặng sau (Phase c: referral/affiliate/rewards) sẽ gắn nghiệp
 * vụ thưởng vào đây mà KHÔNG cần đổi DepositService.
 */
@Component
public class FirstDepositEventListener {

    private static final Logger log = LoggerFactory.getLogger(FirstDepositEventListener.class);

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFirstDeposit(FirstDepositEvent event) {
        log.info("First deposit committed: user={} order={} amount={}",
                event.userId(), event.orderId(), event.amount());
    }
}
