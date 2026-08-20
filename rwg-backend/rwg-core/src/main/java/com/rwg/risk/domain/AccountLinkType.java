package com.rwg.risk.domain;

/**
 * Loại tín hiệu liên kết hai tài khoản.
 *
 * ĐỘ MẠNH KHÁC NHAU — đây là lý do phải tách loại chứ không gộp thành một cờ
 * "đáng nghi": {@link #SHARED_IP} rất yếu vì NAT nhà mạng khiến hàng nghìn người
 * dùng chung một IP công cộng, còn {@link #SHARED_DEVICE} mạnh hơn nhiều.
 * Chính sách giữ hoa hồng dựa vào sự khác biệt này (xem AccountLink.blocksCommission).
 */
public enum AccountLinkType {

    /**
     * Cùng dấu vết thiết bị. Tín hiệu MẠNH nhưng KHÔNG chắc chắn: dấu vết do client
     * gửi nên giả mạo được (xoá localStorage là có id mới), và máy dùng chung trong
     * gia đình cũng cho cùng kết quả.
     */
    SHARED_DEVICE,

    /**
     * Nhiều tài khoản đăng ký từ cùng một IP trong thời gian ngắn. Tín hiệu YẾU:
     * kịch bản giới thiệu hợp pháp phổ biến nhất chính là trùng IP (cùng wifi nhà,
     * quán net, văn phòng). Chỉ dùng để đưa vào hàng đợi cho người thật xem.
     */
    SHARED_IP,

    /**
     * Người vận hành tự nối. Cần có vì gian lận thật thường lộ qua dấu hiệu máy
     * không thấy: cùng số tài khoản ngân hàng, cùng kiểu cược, giờ hoạt động trùng.
     */
    MANUAL
}
