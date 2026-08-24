package com.rwg.chat.dto;

import java.time.Instant;

/**
 * Dữ liệu một lệnh rút, đính vào tin nhắn để khu quản trị vẽ thành thẻ duyệt.
 *
 * ĐỌC TỪ {@code payment_orders} MỖI LẦN TẢI LUỒNG, không phải chép vào tin nhắn lúc
 * tạo thẻ. Chép vào thì có hai nguồn sự thật cho cùng một lệnh: một lệnh được duyệt ở
 * trang "Duyệt Nạp & Rút Tiền" sẽ để lại thẻ trong chat hiện "chờ duyệt" vĩnh viễn, và
 * người vận hành bấm duyệt lần thứ hai.
 *
 * SỐ TÀI KHOẢN CHỈ CÓ 4 SỐ CUỐI, giống {@code AdminWithdrawalRowResponse}. Số đầy đủ chỉ
 * lộ qua endpoint reveal riêng, nơi mỗi lần gọi ghi một dòng nhật ký
 * {@code ADMIN_PAYOUT_METHOD_REVEALED}. Trả số đầy đủ ngay trong luồng chat sẽ vô hiệu hoá
 * toàn bộ dấu vết đó — ai mở một cuộc trò chuyện cũng thấy số tài khoản mà không để lại vết.
 *
 * Các trường ngân hàng có thể null: bản ghi tài khoản đã bị gỡ, hoặc lệnh được tạo trước khi
 * hệ thống bắt buộc chọn tài khoản nhận. Giao diện phải chịu được null.
 *
 * {@code decidedByUsername} và {@code decisionNote} null với lệnh còn PENDING — chưa ai quyết
 * định. Chúng đọc từ {@code audit_log} vì bảng lệnh chỉ lưu trạng thái cuối, không lưu ai đã
 * bấm và vì sao.
 */
public record ChatWithdrawalCardResponse(
        String orderId,
        String amount,
        String currency,
        /** PENDING | SETTLED | VOIDED. */
        String status,
        String bankCode,
        String maskedLast4,
        String holderName,
        Instant requestedAt,
        String decidedByUsername,
        String decisionNote
) {
}
