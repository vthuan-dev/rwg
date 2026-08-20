package com.rwg.affiliate.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Cấu hình % hoa hồng hiện hành. */
public record CommissionSettingsResponse(
        String level1Rate,
        String level2Rate,
        Instant updatedAt,
        UUID updatedBy
) {
    public static CommissionSettingsResponse of(BigDecimal level1Rate, BigDecimal level2Rate,
                                                Instant updatedAt, UUID updatedBy) {
        return new CommissionSettingsResponse(level1Rate.toPlainString(),
                level2Rate.toPlainString(), updatedAt, updatedBy);
    }
}
