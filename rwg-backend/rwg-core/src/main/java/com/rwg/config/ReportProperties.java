package com.rwg.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.ZoneId;
import java.time.zone.ZoneRulesException;

/**
 * Cấu hình báo cáo sổ sách ({@code rwg.report}).
 *
 * VÌ SAO MÚI GIỜ PHẢI LÀ CẤU HÌNH: các truy vấn tổng hợp khác trong hệ thống
 * ({@code AdminDashboardService}) cắt khoảng theo UTC. Với dashboard xem nhanh thì
 * chấp nhận được, nhưng với sổ sách kế toán thì không: "tháng 8" theo UTC là từ
 * 8/1 07:00 giờ Việt Nam đến 9/1 07:00, tức <em>7 giờ đầu ngày 1/8 bị tính sang
 * tháng 7</em>. Admin đối chiếu sổ sẽ thấy con số không khớp với những gì họ
 * quan sát trong ngày.
 *
 * Đặt qua cấu hình thay vì gán cứng {@code Asia/Ho_Chi_Minh} để sau này mở thị
 * trường khác không phải sửa mã và biên dịch lại.
 *
 * @param timezone mã múi giờ IANA, ví dụ {@code Asia/Ho_Chi_Minh}
 * @param maxRangeDays trần độ dài một kỳ báo cáo, tính theo ngày
 */
@ConfigurationProperties(prefix = "rwg.report")
public record ReportProperties(String timezone, Integer maxRangeDays) {

    /** Trần mặc định 366 ngày: đủ cho báo cáo năm kể cả năm nhuận. */
    private static final int DEFAULT_MAX_RANGE_DAYS = 366;

    private static final String DEFAULT_TIMEZONE = "Asia/Ho_Chi_Minh";

    public ReportProperties {
        if (timezone == null || timezone.isBlank()) {
            timezone = DEFAULT_TIMEZONE;
        }
        if (maxRangeDays == null || maxRangeDays <= 0) {
            maxRangeDays = DEFAULT_MAX_RANGE_DAYS;
        }
    }

    /**
     * Múi giờ đã phân giải.
     *
     * FAIL-FAST NGAY LÚC GỌI, KHÔNG ÂM THẦM RƠI VỀ UTC: một mã múi giờ sai chính tả
     * ({@code Asia/Hochiminh}) mà rơi về UTC sẽ tạo ra báo cáo <em>trông hợp lý</em>
     * nhưng lệch 7 giờ ở mọi ranh giới kỳ. Kiểu sai lệch đó rất khó phát hiện vì
     * không có lỗi nào và phần lớn con số vẫn đúng.
     *
     * @throws ZoneRulesException nếu mã múi giờ không tồn tại
     */
    public ZoneId zone() {
        return ZoneId.of(timezone);
    }
}
