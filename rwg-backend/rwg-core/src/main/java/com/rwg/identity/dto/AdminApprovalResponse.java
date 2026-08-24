package com.rwg.identity.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.rwg.identity.domain.AdminApprovalRequest;

/**
 * Đề nghị thao tác admin chờ/đã phê duyệt.
 *
 * Trả cả makerId và checkerId để màn quản trị hiển thị rõ AI đề nghị và AI duyệt —
 * đây là thông tin cốt lõi của quy trình 4 mắt, không phải chi tiết phụ.
 */
public record AdminApprovalResponse(
        UUID id,
        String type,
        String status,
        UUID targetUserId,
        String targetUsername,
        String direction,
        String amount,
        String reason,
        UUID makerId,
        String makerUsername,
        UUID checkerId,
        String decisionNote,
        Instant decidedAt,
        Instant createdAt
) {
    public static AdminApprovalResponse from(AdminApprovalRequest request, String targetUsername, String makerUsername) {
        return new AdminApprovalResponse(
                request.getId(),
                request.getType(),
                request.getStatus(),
                request.getTargetUserId(),
                targetUsername,
                request.getDirection(),
                nullSafe(request.getAmount()),
                request.getReason(),
                request.getMakerId(),
                makerUsername,
                request.getCheckerId(),
                request.getDecisionNote(),
                request.getDecidedAt(),
                request.getCreatedAt());
    }

    private static String nullSafe(BigDecimal value) {
        return value == null ? "0" : value.toPlainString();
    }
}
