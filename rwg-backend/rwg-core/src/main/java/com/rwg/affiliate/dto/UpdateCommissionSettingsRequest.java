package com.rwg.affiliate.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Cập nhật % hoa hồng.
 *
 * Dùng String + regex thay vì BigDecimal/double: giá trị sai định dạng sẽ trả
 * message i18n rõ ràng thay vì lỗi deserialize thô, và tránh mọi khả năng
 * float/double lọt vào đường tiền tệ.
 *
 * Regex chỉ nhận 0..1 với tối đa 6 chữ số thập phân (khớp DECIMAL(9,6) và CHECK
 * rate BETWEEN 0 AND 1 ở DB).
 */
public record UpdateCommissionSettingsRequest(
        @NotBlank(message = "{validation.commission.rate.not_blank}")
        @Pattern(regexp = "^(0(\\.\\d{1,6})?|1(\\.0{1,6})?)$",
                message = "{validation.commission.rate.invalid}")
        String level1Rate,

        @NotBlank(message = "{validation.commission.rate.not_blank}")
        @Pattern(regexp = "^(0(\\.\\d{1,6})?|1(\\.0{1,6})?)$",
                message = "{validation.commission.rate.invalid}")
        String level2Rate
) {
}
