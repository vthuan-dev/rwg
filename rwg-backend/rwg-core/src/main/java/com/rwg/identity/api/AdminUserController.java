package com.rwg.identity.api;

import com.rwg.common.PageResponse;
import com.rwg.common.web.ClientAddresses;
import com.rwg.identity.dto.AdminUserDetailResponse;
import com.rwg.identity.dto.ChangeUserRoleRequest;
import com.rwg.identity.dto.ChangeUserStatusRequest;
import com.rwg.identity.dto.UpdateKycLevelRequest;
import com.rwg.identity.dto.AdminUserListItemResponse;
import com.rwg.identity.dto.UserResponse;
import com.rwg.identity.service.AdminUserService;
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
 * API quản trị người dùng. Toàn bộ /api/v1/admin/** yêu cầu ROLE_ADMIN
 * (enforce tập trung trong SecurityConfig — không dựa annotation từng method).
 * Controller này bị loại khỏi rwg-user-app (xem RwgApplication.excludeFilters).
 *
 * KHÔNG có endpoint xóa cứng user: wallets có FK ON DELETE RESTRICT và ledger là
 * nguồn sự thật tài chính — "xóa" là PATCH status=CLOSED.
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@Tag(name = "Admin - Users", description = "Quản lý người dùng: khóa/cấm, phân quyền, KYC - yêu cầu ROLE_ADMIN")
@SecurityRequirement(name = "bearerAuth")
public class AdminUserController {

    /** Chặn client yêu cầu trang quá lớn (bảo vệ DB). */
    private static final int MAX_PAGE_SIZE = 100;

    private final AdminUserService adminUserService;

    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    @GetMapping
    @Operation(summary = "Danh sách user kèm số dư ví, filter theo status/role/keyword (username hoặc email)")
    public PageResponse<AdminUserListItemResponse> search(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return adminUserService.search(status, role, keyword, page, Math.min(size, MAX_PAGE_SIZE));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết user kèm số dư ví, tổng nạp/rút đã hoàn tất, số lệnh rút chờ duyệt")
    public AdminUserDetailResponse detail(@PathVariable UUID id) {
        return adminUserService.detail(id);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Khóa/cấm/mở lại/đóng tài khoản. Rời ACTIVE sẽ thu hồi toàn bộ refresh token")
    public UserResponse changeStatus(@PathVariable UUID id,
                                     @Valid @RequestBody ChangeUserStatusRequest request,
                                     @AuthenticationPrincipal Jwt jwt,
                                     HttpServletRequest httpRequest) {
        return adminUserService.changeStatus(id, request, UUID.fromString(jwt.getSubject()),
                ClientAddresses.clientIp(httpRequest));
    }

    @PatchMapping("/{id}/role")
    @Operation(summary = "Nâng/hạ quyền (PLAYER|ADMIN). Thu hồi refresh token vì claim roles cũ đã lệch")
    public UserResponse changeRole(@PathVariable UUID id,
                                   @Valid @RequestBody ChangeUserRoleRequest request,
                                   @AuthenticationPrincipal Jwt jwt,
                                   HttpServletRequest httpRequest) {
        return adminUserService.changeRole(id, request, UUID.fromString(jwt.getSubject()),
                ClientAddresses.clientIp(httpRequest));
    }

    @PatchMapping("/{id}/kyc")
    @Operation(summary = "Duyệt KYC (NONE|LEVEL_1|LEVEL_2|LEVEL_3)")
    public UserResponse updateKyc(@PathVariable UUID id,
                                  @Valid @RequestBody UpdateKycLevelRequest request,
                                  @AuthenticationPrincipal Jwt jwt,
                                  HttpServletRequest httpRequest) {
        return adminUserService.updateKycLevel(id, request, UUID.fromString(jwt.getSubject()),
                ClientAddresses.clientIp(httpRequest));
    }

    @PostMapping("/{id}/withdrawal-password/reset")
    @Operation(summary = "XÓA mật khẩu rút tiền để user tự đặt lại (admin KHÔNG đặt thay user)")
    public UserResponse resetWithdrawalPassword(@PathVariable UUID id,
                                                @AuthenticationPrincipal Jwt jwt,
                                                HttpServletRequest httpRequest) {
        return adminUserService.resetWithdrawalPassword(id, UUID.fromString(jwt.getSubject()),
                ClientAddresses.clientIp(httpRequest));
    }
}
