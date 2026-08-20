package com.rwg.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * Hạn mức thao tác của admin (prefix rwg.admin) — lớp chặn thiệt hại khi tài khoản
 * admin bị lạm dụng hoặc bị chiếm.
 *
 * - adjustMaxPerTransaction: vượt trần này thì điều chỉnh ví KHÔNG thực thi ngay mà
 *   phải qua quy trình 4 mắt (admin thứ hai phê duyệt).
 * - adjustDailyMaxPerAdmin: trần TỔNG mỗi admin mỗi ngày UTC, tính CẢ các lần đã
 *   được phê duyệt. Đây là lớp chặn kiểu "rút rỉa": quy trình 4 mắt ngăn hành động
 *   đơn độc, nhưng hai admin thông đồng vẫn có thể chia nhỏ nhiều lần dưới ngưỡng —
 *   trần ngày đặt giới hạn cứng cho thiệt hại tối đa trong 24 giờ.
 *
 * Dùng BigDecimal, KHÔNG float/double (DECISIONS.md).
 */
@ConfigurationProperties(prefix = "rwg.admin")
public record AdminLimitProperties(
        BigDecimal adjustMaxPerTransaction,
        BigDecimal adjustDailyMaxPerAdmin
) {
}
