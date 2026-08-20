package com.rwg.affiliate.api;

import com.rwg.affiliate.dto.DashboardSummaryResponse;
import com.rwg.affiliate.service.AdminDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dashboard số liệu tổng hợp cho khu quản trị. Yêu cầu ROLE_ADMIN
 * (enforce tập trung ở SecurityConfig).
 * Controller này bị loại khỏi rwg-user-app (xem RwgApplication.excludeFilters).
 */
@RestController
@RequestMapping("/api/v1/admin/dashboard")
@Tag(name = "Admin - Dashboard", description = "Số liệu tổng hợp - yêu cầu ROLE_ADMIN")
@SecurityRequirement(name = "bearerAuth")
public class AdminDashboardController {

    private final AdminDashboardService service;

    public AdminDashboardController(AdminDashboardService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    @Operation(summary = "Tổng nạp/rút/turnover/hoa hồng + user mới trong khoảng ngày "
            + "(mặc định 30 ngày gần nhất, tối đa 366 ngày)")
    public DashboardSummaryResponse summary(@RequestParam(required = false) String from,
                                            @RequestParam(required = false) String to) {
        return service.summary(from, to);
    }
}
