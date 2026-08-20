package com.rwg.payment.service;

import org.springframework.context.ApplicationEvent;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Sự kiện Spring publish khi user NẠP TIỀN THÀNH CÔNG LẦN ĐẦU (chặng 2 Phase b).
 * Listener (bonus/referral chặng sau) subscribe bằng @EventListener.
 */
public class FirstDepositEvent extends ApplicationEvent {

    private final UUID userId;
    private final UUID orderId;
    private final BigDecimal amount;

    public FirstDepositEvent(Object source, UUID userId, UUID orderId, BigDecimal amount) {
        super(source);
        this.userId = userId;
        this.orderId = orderId;
        this.amount = amount;
    }

    public UUID userId() { return userId; }
    public UUID orderId() { return orderId; }
    public BigDecimal amount() { return amount; }
}
