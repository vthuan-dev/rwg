package com.rwg.payment.dto;

import java.time.Instant;

/**
 * Một dòng trong bảng lệnh rút của khu quản trị — dùng cho CẢ hàng chờ duyệt và lịch sử.
 *
 * VÌ SAO KHÔNG DÙNG {@link PaymentOrderResponse}: DTO đó cũng phục vụ endpoint của người
 * chơi ({@code GET /users/me/payments}). Thêm {@code username} và thông tin ngân hàng vào
 * đó là gửi dữ liệu không ai cần ra app công khai — và một khi trường đã có trong DTO thì
 * mọi endpoint dùng chung nó đều trả kèm, kể cả những endpoint chưa ai xem lại.
 *
 * SỐ TÀI KHOẢN CHỈ CÓ 4 SỐ CUỐI. Số đầy đủ chỉ lộ qua
 * {@code POST /admin/users/{userId}/payout-methods/{methodId}/reveal} — mỗi lần gọi endpoint
 * đó ghi một dòng audit {@code ADMIN_PAYOUT_METHOD_REVEALED}. Trả số đầy đủ ngay trong danh
 * sách sẽ vô hiệu hoá toàn bộ dấu vết ấy: ai mở trang cũng thấy mọi số tài khoản mà không
 * để lại vết nào.
 *
 * Các trường ngân hàng có thể {@code null}: lệnh rút cũ được tạo trước khi hệ thống bắt buộc
 * chọn tài khoản, hoặc bản ghi tài khoản đã bị gỡ khỏi bảng. Giao diện phải chịu được null
 * thay vì giả định luôn có.
 *
 * ===== BA TRƯỜNG QUYẾT ĐỊNH =====
 * {@code decidedByUsername}, {@code decisionNote}, {@code decidedAt} lấy từ bảng
 * {@code audit_log}, KHÔNG phải từ {@code payment_orders}.
 *
 * Lý do: bảng lệnh chỉ lưu trạng thái cuối (SETTLED/VOIDED) chứ không lưu ai đã quyết định.
 * Thông tin đó nằm ở nhật ký, nơi nó không thể bị sửa — cột trong bảng lệnh thì có thể bị
 * ghi đè bởi một lần cập nhật sau, còn audit_log là append-only.
 *
 * Cả ba đều null với lệnh còn PENDING (chưa ai quyết định), và có thể null với lệnh cũ được
 * quyết định trước khi hệ thống bắt buộc nhập lý do.
 */
public record AdminWithdrawalRowResponse(
        String id,
        String userId,
        String username,
        String amount,
        String currency,
        String status,
        String bankAccountId,
        String bankCode,
        String maskedLast4,
        String holderName,
        Instant createdAt,
        String decidedByUsername,
        String decisionNote,
        Instant decidedAt
) {
}
