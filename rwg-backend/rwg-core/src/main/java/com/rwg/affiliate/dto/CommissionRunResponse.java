package com.rwg.affiliate.dto;

import com.rwg.affiliate.domain.CommissionRun;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Một chứng từ chi hoa hồng. */
public record CommissionRunResponse(
        UUID id,
        UUID agentId,
        LocalDate periodDate,
        int level,
        String turnover,
        String rate,
        String amount,
        Instant createdAt
) {
    public static CommissionRunResponse from(CommissionRun run) {
        return new CommissionRunResponse(
                run.getId(),
                run.getAgentId(),
                run.getPeriodDate(),
                run.getLevel(),
                run.getTurnover().toPlainString(),
                run.getRate().toPlainString(),
                run.getAmount().toPlainString(),
                run.getCreatedAt());
    }
}
