package com.rwg.notification.domain;

/**
 * Loại thông báo.
 *
 * Sáu loại này khớp một-một với các hằng đã có trong {@code AuditTrailService}, nên mọi chỗ
 * cần ghi thông báo đều đã có sẵn một điểm ghi audit — chỉ thêm một dòng cạnh đó.
 *
 * KHÔNG có loại cho THẮNG/THUA CƯỢC, và đây là chủ ý: vòng chơi dài 58 giây nên một người
 * chơi cược liên tục một bàn sinh khoảng 62 thông báo mỗi giờ. Lưu chúng vào đây sẽ chôn vùi
 * những tin thật sự quan trọng (admin cộng tiền, rút bị từ chối) giữa hàng trăm dòng "thua
 * $30", và thông tin đó đã có đầy đủ ở trang lịch sử cược. Kết quả cược được đẩy trực tiếp
 * qua WebSocket để hiện thẻ nổi tạm thời, không đi qua bảng này.
 */
public enum NotificationType {

    /** Nạp tiền thành công, tiền đã vào ví. */
    DEPOSIT_COMPLETED,

    /**
     * Yêu cầu nạp tiền đã gửi, đang chờ nhân sự duyệt.
     *
     * Khác với {@link #WITHDRAWAL_REQUESTED} ở chỗ không có tiền nào rời ví, nhưng vẫn cần
     * vì khách đã CHUYỂN TIỀN THẬT ra ngoài hệ thống rồi mới gửi yêu cầu. Không có tin
     * xác nhận thì họ không biết yêu cầu có tới nơi hay không, và sẽ gửi lại — tạo ra
     * những yêu cầu trùng mà nhân sự khó phân biệt với hai lần chuyển khoản thật.
     */
    DEPOSIT_REQUESTED,

    /** Yêu cầu nạp tiền bị nhân sự từ chối; KHÔNG có tiền nào vào ví. */
    DEPOSIT_REJECTED,

    /**
     * Lệnh rút vừa được gửi đi, đang chờ admin duyệt.
     *
     * PHẢI có loại này dù nó chỉ là tin xác nhận: tiền bị TRỪ KHỎI VÍ NGAY lúc tạo lệnh,
     * nhưng admin có thể duyệt sau nhiều giờ. Không có tin này thì người chơi thấy số dư
     * tụt mà không có gì giải thích — đúng loại tình huống sinh khiếu nại.
     */
    WITHDRAWAL_REQUESTED,

    /**
     * Lệnh rút được admin duyệt.
     *
     * Lưu ý về nghiệp vụ: tiền đã bị TRỪ khỏi ví ngay lúc tạo lệnh, nên thông báo này không
     * kèm thay đổi số dư — nó chỉ báo lệnh đã được xử lý.
     */
    WITHDRAWAL_APPROVED,

    /** Lệnh rút bị từ chối; tiền đã được hoàn lại ví. */
    WITHDRAWAL_REJECTED,

    /** Admin cộng tiền thủ công. */
    ADMIN_CREDIT,

    /** Admin trừ tiền thủ công. */
    ADMIN_DEBIT,

    /** Tin chung do admin đăng cho mọi người ({@code user_id} NULL). */
    ANNOUNCEMENT
}
