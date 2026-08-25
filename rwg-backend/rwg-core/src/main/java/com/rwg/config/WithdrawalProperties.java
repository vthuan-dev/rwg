package com.rwg.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * Hạn mức rút tiền (prefix rwg.withdrawal).
 *
 * <h2>KHÔNG GIỚI HẠN = ĐỂ TRỐNG</h2>
 * {@code dailyMaxAmount} nhận {@code null} với nghĩa <b>không giới hạn</b>. Cách bật là
 * để trống biến môi trường:
 * <pre>
 *   RWG_WITHDRAWAL_DAILY_MAX=          # không giới hạn
 *   RWG_WITHDRAWAL_DAILY_MAX=5000      # trần 5.000/ngày
 * </pre>
 *
 * Chọn {@code null} thay vì một con số rất lớn ({@code 999999999}) vì ba lý do:
 * <ol>
 *   <li>Đọc cấu hình là hiểu ngay ý định. Con số lớn khiến người sau phải đoán đó là
 *       hạn mức thật hay cách viết "bỏ hạn mức".</li>
 *   <li>Không có con số lạ nào lọt vào thông báo lỗi hay nhật ký.</li>
 *   <li>Số lớn vẫn là hạn mức — người rút vượt qua nó sẽ nhận thông báo sai lý do.</li>
 * </ol>
 *
 * {@code minAmount} vẫn <b>bắt buộc</b>: mỗi lệnh rút đều cần admin bấm duyệt, nên cho
 * phép rút những khoản vài xu là mở đường làm ngập việc của nhân sự bằng hàng nghìn lệnh
 * vô nghĩa.
 *
 * Dùng {@link BigDecimal}, KHÔNG float/double — sai số nhị phân trên tiền là không chấp
 * nhận được.
 */
@ConfigurationProperties(prefix = "rwg.withdrawal")
public record WithdrawalProperties(
        BigDecimal minAmount,
        BigDecimal dailyMaxAmount
) {

    /**
     * Có áp trần theo ngày hay không.
     *
     * Gọi hàm này thay vì kiểm {@code != null} rải rác ở nơi dùng: ý nghĩa "null là không
     * giới hạn" được phát biểu MỘT lần ở đây, nên người thêm chỗ kiểm hạn mức mới không
     * cần biết quy ước đó để viết đúng.
     */
    public boolean hasDailyMax() {
        return dailyMaxAmount != null;
    }
}
