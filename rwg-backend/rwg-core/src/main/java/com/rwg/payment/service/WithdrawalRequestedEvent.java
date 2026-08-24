package com.rwg.payment.service;

import org.springframework.context.ApplicationEvent;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Người chơi vừa tạo một lệnh rút tiền (ví đã bị trừ, lệnh đang chờ duyệt).
 *
 * VÌ SAO LÀ EVENT chứ không gọi thẳng tầng chat: việc chèn thẻ duyệt vào luồng chat là
 * tiện ích cho người vận hành, không phải một phần của nghiệp vụ rút tiền. Gọi trực tiếp
 * thì một lỗi khi ghi chat sẽ rollback cả transaction rút tiền — mà tại thời điểm đó ví
 * đã bị trừ và người chơi đã thấy số dư giảm. Đổi một tiện ích thành nguyên nhân làm
 * hỏng một thao tác tiền là cái giá không đáng.
 *
 * Cũng giữ đúng chiều phụ thuộc: package payment không cần biết package chat tồn tại.
 * Đặt cạnh {@link FirstDepositEvent} theo khuôn đã có của dự án.
 */
public class WithdrawalRequestedEvent extends ApplicationEvent {

    private final UUID userId;
    private final UUID orderId;
    private final BigDecimal amount;

    public WithdrawalRequestedEvent(Object source, UUID userId, UUID orderId, BigDecimal amount) {
        super(source);
        this.userId = userId;
        this.orderId = orderId;
        this.amount = amount;
    }

    public UUID userId() {
        return userId;
    }

    public UUID orderId() {
        return orderId;
    }

    public BigDecimal amount() {
        return amount;
    }
}
