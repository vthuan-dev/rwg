package com.rwg.identity.api;

import com.rwg.common.PageResponse;
import com.rwg.identity.dto.AuditLogResponse;
import com.rwg.identity.service.AdminAuditQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * API tra cứu nhật ký hệ thống (READ-ONLY — audit_log là append-only).
 * /api/v1/admin/** yêu cầu ROLE_ADMIN (enforce tập trung trong SecurityConfig).
 *
 * Lọc thao tác của admin: truyền action bắt đầu bằng ADMIN_ (vd ADMIN_WALLET_ADJUSTED).
 * fromDate/toDate dạng yyyy-MM-dd theo UTC; không truyền -> 7 ngày gần nhất.
 */
@RestController
@RequestMapping("/api/v1/admin/audit")
@Tag(name = "Admin - Audit", description = "Tra cứu nhật ký hệ thống - yêu cầu ROLE_ADMIN")
@SecurityRequirement(name = "bearerAuth")
public class AdminAuditController {

    private static final int MAX_PAGE_SIZE = 100;

    private final AdminAuditQueryService auditQueryService;

    public AdminAuditController(AdminAuditQueryService auditQueryService) {
        this.auditQueryService = auditQueryService;
    }

    @GetMapping("/logs")
    @Operation(summary = "Nhật ký hệ thống, filter actorId/action/targetId/khoảng ngày (UTC)")
    public PageResponse<AuditLogResponse> logs(@RequestParam(required = false) UUID actorId,
                                               @RequestParam(required = false) String action,
                                               @RequestParam(required = false) String targetId,
                                               @RequestParam(required = false) String fromDate,
                                               @RequestParam(required = false) String toDate,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return auditQueryService.search(actorId, action, targetId, fromDate, toDate,
                page, Math.min(size, MAX_PAGE_SIZE));
    }
}
