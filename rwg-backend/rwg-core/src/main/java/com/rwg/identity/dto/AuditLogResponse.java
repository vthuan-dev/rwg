package com.rwg.identity.dto;

import com.rwg.identity.domain.AuditLog;

import java.time.Instant;

/**
 * Một dòng nhật ký hệ thống trả về cho khu quản trị.
 * details là chuỗi JSON thô đã lưu (không parse lại) — bundle audit là append-only.
 */
public record AuditLogResponse(
        Long id,
        String actorId,
        String actorUsername,
        String action,
        String targetType,
        String targetId,
        String details,
        String ipAddress,
        Instant createdAt
) {

    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                log.getActorId() == null ? null : log.getActorId().toString(),
                log.getActorUsername(),
                log.getAction(),
                log.getTargetType(),
                log.getTargetId(),
                log.getDetails(),
                log.getIpAddress(),
                log.getCreatedAt());
    }
}
