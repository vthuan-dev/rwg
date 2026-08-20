package com.rwg.risk.domain;

/**
 * Trạng thái xét duyệt của một liên kết tài khoản.
 *
 * Vòng đời: {@code SUSPECTED} (máy dò ra) -> {@code CONFIRMED} hoặc {@code CLEARED}
 * (người thật kết luận). Không có đường quay lại {@code SUSPECTED}: một khi người
 * đã xem thì kết luận của người thắng máy.
 */
public enum AccountLinkStatus {

    /** Máy dò ra, chưa ai xem. */
    SUSPECTED,

    /** Người vận hành xác nhận đúng là cùng một người. */
    CONFIRMED,

    /**
     * Gỡ oan — người vận hành xác nhận là hai người khác nhau thật.
     *
     * CHỈ CÓ HIỆU LỰC CHO KỲ TƯƠNG LAI, không hồi tố: các kỳ hoa hồng đã chốt sẽ
     * không được trả bù tự động. Lý do là uq_commission_runs_agent_period_level —
     * cho chạy lại kỳ đã chốt để "trả thêm phần thiếu" sẽ mở đúng cái cửa mà ràng
     * buộc chống chi trùng đang đóng. Đường bù đúng là điều chỉnh ví thủ công,
     * đã có quy trình 4 mắt từ chặng 5.
     */
    CLEARED
}
