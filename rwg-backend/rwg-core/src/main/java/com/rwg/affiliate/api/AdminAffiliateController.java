package com.rwg.affiliate.api;

import com.rwg.affiliate.dto.CommissionRunResponse;
import com.rwg.affiliate.dto.CommissionRunSummaryResponse;
import com.rwg.affiliate.dto.CommissionSettingsResponse;
import com.rwg.affiliate.dto.DownlineMemberResponse;
import com.rwg.affiliate.dto.UpdateCommissionSettingsRequest;
import com.rwg.affiliate.service.AdminAffiliateService;
import com.rwg.common.PageResponse;
import com.rwg.common.web.ClientAddresses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * API quản trị hệ thống đại lý. Yêu cầu ROLE_ADMIN (enforce tập trung ở SecurityConfig).
 * Controller này bị loại khỏi rwg-user-app (xem RwgApplication.excludeFilters).
 */
@RestController
@RequestMapping("/api/v1/admin/affiliate")
@Tag(name = "Admin - Affiliate", description = "Đại lý & hoa hồng - yêu cầu ROLE_ADMIN")
@SecurityRequirement(name = "bearerAuth")
public class AdminAffiliateController {

    private static final int MAX_PAGE_SIZE = 100;

    private final AdminAffiliateService service;

    public AdminAffiliateController(AdminAffiliateService service) {
        this.service = service;
    }

    @GetMapping("/users/{id}/downline")
    @Operation(summary = "Tuyến dưới của một đại lý theo cấp (1 hoặc 2)")
    public PageResponse<DownlineMemberResponse> downline(@PathVariable UUID id,
                                                        @RequestParam(defaultValue = "1") int level,
                                                        @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "20") int size) {
        return service.downline(id, level, page, Math.min(size, MAX_PAGE_SIZE));
    }

    @GetMapping("/commissions")
    @Operation(summary = "Lịch sử chi hoa hồng; mặc định 30 ngày gần nhất")
    public PageResponse<CommissionRunResponse> commissions(@RequestParam(required = false) UUID agentId,
                                                          @RequestParam(required = false) String from,
                                                          @RequestParam(required = false) String to,
                                                          @RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "20") int size) {
        return service.commissions(agentId, from, to, page, Math.min(size, MAX_PAGE_SIZE));
    }

    @GetMapping("/config")
    @Operation(summary = "Xem % hoa hồng hiện hành")
    public CommissionSettingsResponse config() {
        return service.settings();
    }

    @PatchMapping("/config")
    @Operation(summary = "Đổi % hoa hồng. Chỉ áp dụng cho các đợt chi từ nay; chứng từ cũ giữ nguyên rate")
    public CommissionSettingsResponse updateConfig(@Valid @RequestBody UpdateCommissionSettingsRequest request,
                                                   @AuthenticationPrincipal Jwt jwt,
                                                   HttpServletRequest httpRequest) {
        return service.updateSettings(request, UUID.fromString(jwt.getSubject()),
                ClientAddresses.clientIp(httpRequest));
    }

    @PostMapping("/commissions/run")
    @Operation(summary = "Chạy lại đợt chi hoa hồng cho một ngày đã kết thúc. An toàn bấm nhiều lần")
    public CommissionRunSummaryResponse run(@RequestParam String periodDate,
                                            @AuthenticationPrincipal Jwt jwt,
                                            HttpServletRequest httpRequest) {
        return service.triggerRun(periodDate, UUID.fromString(jwt.getSubject()),
                ClientAddresses.clientIp(httpRequest));
    }
}
