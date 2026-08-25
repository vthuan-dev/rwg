package com.rwg.payment.dto;

import java.math.BigDecimal;

/**
 * Hạn mức nạp/rút mà giao diện cần biết để kiểm trước khi gọi API.
 *
 * <h2>VÌ SAO CÓ ENDPOINT NÀY</h2>
 * Trước đây frontend viết cứng các con số này ở HAI chỗ ({@code withdraw/page.tsx} và
 * {@code playerApi.ts}). Hệ quả: đổi hạn mức ở backend thì giao diện vẫn chặn theo số cũ,
 * và người dùng bị từ chối bởi chính giao diện trước khi yêu cầu kịp đi. Lỗi kiểu đó
 * không hiện ra trong log của server, nên rất khó lần.
 *
 * Giờ backend là nguồn duy nhất. Hai bên không thể lệch nhau nữa.
 *
 * <h2>DÙNG CHUỖI CHO SỐ TIỀN</h2>
 * Các trường tiền là {@link String}, không phải {@code number}. JSON number ở JavaScript
 * là double 64-bit, mất chính xác từ khoảng 9 triệu tỉ — và quan trọng hơn, làm mọi phép
 * tính trên số dư thành phép tính dấu phẩy động. Trả chuỗi buộc phía nhận phải chọn cách
 * xử lý tường minh.
 *
 * <h2>KHÔNG GIỚI HẠN</h2>
 * {@code withdrawDailyMax} là {@code null} khi không áp trần theo ngày. Giao diện phải
 * phân biệt {@code null} với chuỗi rỗng hay số 0 — xem chú thích ở nơi dùng.
 */
public record PaymentLimitsResponse(
        String depositMin,
        String depositMax,
        String withdrawMin,
        /** {@code null} = không giới hạn. */
        String withdrawDailyMax
) {

    public static PaymentLimitsResponse of(BigDecimal depositMin,
                                           BigDecimal depositMax,
                                           BigDecimal withdrawMin,
                                           BigDecimal withdrawDailyMax) {
        return new PaymentLimitsResponse(
                plain(depositMin),
                plain(depositMax),
                plain(withdrawMin),
                plain(withdrawDailyMax));
    }

    /**
     * {@code toPlainString} chứ KHÔNG phải {@code toString}: {@link BigDecimal#toString()}
     * dùng ký hiệu khoa học cho một số giá trị (ví dụ {@code 1E+8}), và chuỗi đó khi đưa
     * vào {@code parseFloat} ở giao diện vẫn chạy nhưng khi hiển thị trực tiếp thì người
     * dùng đọc được "1E+8" thay vì "100000000".
     */
    private static String plain(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }
}
