package com.rwg.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * Hạn mức rút tiền (prefix rwg.withdrawal) — giá trị theo KE-HOACH-CHUC-NANG-CASINO:
 * minimum $20, maximum $5,000/ngày (standard). Dùng BigDecimal, KHÔNG float/double.
 */
@ConfigurationProperties(prefix = "rwg.withdrawal")
public record WithdrawalProperties(
        BigDecimal minAmount,
        BigDecimal dailyMaxAmount
) {
}
