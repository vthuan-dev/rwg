package com.rwg.chat.service;

import com.rwg.payment.service.WithdrawalRequestedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Gắn thẻ duyệt vào luồng chat khi người chơi tạo một lệnh rút tiền.
 *
 * ĐĂNG KÝ AFTER_COMMIT, không phải listener thường: nếu chạy trong cùng transaction với
 * lệnh rút thì một lỗi khi ghi chat sẽ rollback cả lệnh rút — và tại thời điểm đó ví đã
 * bị trừ, người chơi đã thấy số dư giảm. Việc chèn một thẻ cho tiện thao tác không được
 * phép làm hỏng một thao tác tiền.
 *
 * BẮT MỌI NGOẠI LỆ. Listener AFTER_COMMIT ném lỗi thì lỗi đó nổi lên ở một chỗ không còn
 * gì để hoàn tác: transaction đã commit xong. Thẻ thiếu là bất tiện — lệnh rút vẫn xử lý
 * được ở trang "Duyệt Nạp & Rút Tiền" như trước khi có tính năng này. Ghi log ở mức
 * warn để vẫn truy được nguyên nhân nếu nhân sự báo "có lệnh rút mà không thấy thẻ".
 */
@Component
public class ChatWithdrawalCardListener {

    private static final Logger log = LoggerFactory.getLogger(ChatWithdrawalCardListener.class);

    private final ChatService chatService;

    public ChatWithdrawalCardListener(ChatService chatService) {
        this.chatService = chatService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onWithdrawalRequested(WithdrawalRequestedEvent event) {
        try {
            chatService.appendWithdrawalCard(event.userId(), event.orderId());
        } catch (RuntimeException failed) {
            log.warn("Không gắn được thẻ duyệt lệnh rút vào chat: userId={} orderId={}",
                    event.userId(), event.orderId(), failed);
        }
    }
}
