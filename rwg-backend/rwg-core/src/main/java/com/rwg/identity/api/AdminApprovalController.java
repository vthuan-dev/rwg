package com.rwg.identity.api;

import com.rwg.common.PageResponse;
import com.rwg.common.web.ClientAddresses;
import com.rwg.identity.dto.AdminApprovalResponse;
import com.rwg.identity.dto.DecideApprovalRequest;
import com.rwg.identity.service.AdminApprovalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Hàng đợi phê duyệt thao tác admin (quy trình 4 mắt).
 *
 * Thao tác điều chỉnh ví vượt hạn mức không thực thi ngay mà tạo đề nghị ở đây; phải
 * có admin THỨ HAI bấm approve thì tiền mới thực sự chuyển. Người tạo đề nghị không
 * duyệt được đề nghị của chính mình.
 *
 * Phân quyền theo route trong SecurityConfig: chỉ ADMIN và FINANCE được quyết định.
 */
@RestController
@RequestMapping("/api/v1/admin/approvals")
@Tag(name = "Admin - Approvals",
        description = "Quy trình 4 mắt cho thao tác tiền vượt hạn mức - yêu cầu ADMIN/FINANCE")
@SecurityRequirement(name = "bearerAuth")
public class AdminApprovalController {

    private static final int MAX_PAGE_SIZE = 100;

    private final AdminApprovalService approvalService;

    public AdminApprovalController(AdminApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @GetMapping
    @Operation(summary = "Danh sách đề nghị; status optional (PENDING/APPROVED/REJECTED)")
    public PageResponse<AdminApprovalResponse> list(@RequestParam(required = false) String status,
                                                   @RequestParam(required = false) UUID makerId,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        return approvalService.search(status, makerId, page, Math.min(size, MAX_PAGE_SIZE));
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Phê duyệt và THỰC THI chuyển tiền. Người tạo đề nghị không tự duyệt được.")
    public AdminApprovalResponse approve(@PathVariable UUID id,
                                         @Valid @RequestBody(required = false) DecideApprovalRequest request,
                                         @AuthenticationPrincipal Jwt jwt,
                                         HttpServletRequest httpRequest) {
        return approvalService.approve(id, UUID.fromString(jwt.getSubject()),
                request == null ? null : request.note(),
                ClientAddresses.clientIp(httpRequest));
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Từ chối đề nghị — KHÔNG chuyển tiền")
    public AdminApprovalResponse reject(@PathVariable UUID id,
                                        @Valid @RequestBody(required = false) DecideApprovalRequest request,
                                        @AuthenticationPrincipal Jwt jwt,
                                        HttpServletRequest httpRequest) {
        return approvalService.reject(id, UUID.fromString(jwt.getSubject()),
                request == null ? null : request.note(),
                ClientAddresses.clientIp(httpRequest));
    }
}
