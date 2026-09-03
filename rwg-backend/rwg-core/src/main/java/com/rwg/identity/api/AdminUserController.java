package com.rwg.identity.api;

import com.rwg.common.PageResponse;
import com.rwg.common.web.ClientAddresses;
import com.rwg.identity.dto.AdminUserDetailResponse;
import com.rwg.identity.dto.ChangeUserRoleRequest;
import com.rwg.identity.dto.ChangeUserStatusRequest;
import com.rwg.identity.dto.DeleteUserRequest;
import com.rwg.identity.dto.UpdateKycLevelRequest;
import com.rwg.identity.dto.AdminUserListItemResponse;
import com.rwg.identity.dto.LoginHistoryEntryResponse;
import com.rwg.identity.dto.UserResponse;
import com.rwg.identity.service.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * API quản trị người dùng. Toàn bộ /api/v1/admin/** yêu cầu ROLE_ADMIN
 * (enforce tập trung trong SecurityConfig — không dựa annotation từng method).
 * Controller này bị loại khỏi rwg-user-app (xem RwgApplication.excludeFilters).
 *
 * Xóa tài khoản (DELETE /{id}) có hai đường tự chọn: xóa hẳn cho tài khoản sạch,
 * chốt CLOSED cho tài khoản có sổ sách tài chính — xóa cứng sẽ thất bại vì FK
 * ON DELETE RESTRICT. Cần mã xác nhận trong thân request.
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
    @Operation(summary = "Danh sách tài khoản KHÁCH (PLAYER) kèm số dư ví, filter theo status/keyword (username hoặc email)")
    public PageResponse<AdminUserListItemResponse> search(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return adminUserService.search(status, keyword, page, Math.min(size, MAX_PAGE_SIZE));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết user kèm số dư ví, tổng nạp/rút đã hoàn tất, số lệnh rút chờ duyệt")
    public AdminUserDetailResponse detail(@PathVariable UUID id) {
        return adminUserService.detail(id);
    }

    /**
     * Lịch sử đăng nhập của một tài khoản, dựng từ {@code audit_log} (append-only).
     *
     * Trả {@code List} chứ KHÔNG phải {@code PageResponse}: đây là ô "N lần gần nhất" trong
     * modal chi tiết, không có nút sang trang. Bọc bằng PageResponse sẽ hứa một khả năng
     * phân trang mà giao diện không có, và buộc phía hiển thị bóc thêm một lớp vô ích.
     */
    @GetMapping("/{id}/login-history")
    @Operation(summary = "Lịch sử đăng nhập gần nhất (thành công + thất bại) kèm IP và kênh đăng nhập")
    public List<LoginHistoryEntryResponse> loginHistory(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "20") int limit) {
        return adminUserService.loginHistory(id, limit);
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

    @PostMapping("/{id}/password/change")
    @Operation(summary = "Admin tự đổi Mật khẩu đăng nhập (cấp 1) cho người chơi")
    public UserResponse overrideLoginPassword(@PathVariable UUID id,
                                              @Valid @RequestBody com.rwg.identity.dto.AdminOverridePasswordRequest request,
                                              @AuthenticationPrincipal Jwt jwt,
                                              HttpServletRequest httpRequest) {
        return adminUserService.overrideLoginPassword(id, request.newPassword(),
                UUID.fromString(jwt.getSubject()), ClientAddresses.clientIp(httpRequest));
    }

    @PostMapping("/{id}/withdrawal-password/change")
    @Operation(summary = "Admin tự đổi Mật khẩu rút tiền 6 số (cấp 2) cho người chơi")
    public UserResponse overrideWithdrawalPassword(@PathVariable UUID id,
                                                   @Valid @RequestBody com.rwg.identity.dto.AdminOverrideWithdrawalPasswordRequest request,
                                                   @AuthenticationPrincipal Jwt jwt,
                                                   HttpServletRequest httpRequest) {
        return adminUserService.overrideWithdrawalPassword(id, request.newPin(),
                UUID.fromString(jwt.getSubject()), ClientAddresses.clientIp(httpRequest));
    }

    @PostMapping("/{id}/withdrawal-password/reset")
    @Operation(summary = "XÓA mật khẩu rút tiền để user tự đặt lại")
    public UserResponse resetWithdrawalPassword(@PathVariable UUID id,
                                                @AuthenticationPrincipal Jwt jwt,
                                                HttpServletRequest httpRequest) {
        return adminUserService.resetWithdrawalPassword(id, UUID.fromString(jwt.getSubject()),
                ClientAddresses.clientIp(httpRequest));
    }

    /**
     * XÓA TÀI KHOẢN người chơi — thao tác KHÔNG HOÀN TÁC ĐƯỢC.
     *
     * Cần mã xác nhận trong thân request (không phải tham số URL — URL đi vào log nginx).
     * Hệ thống tự chọn: xóa hẳn nếu tài khoản sạch, chốt CLOSED nếu có sổ sách.
     * Response trả đường đã đi (“HARD_DELETE” hoặc “SOFT_DELETE”) để frontend hiện đúng thông báo.
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Xóa tài khoản người chơi (cần mã xác nhận). Không hoàn tác được.")
    public Map<String, String> deleteUser(@PathVariable UUID id,
                                          @Valid @RequestBody DeleteUserRequest request,
                                          @AuthenticationPrincipal Jwt jwt,
                                          HttpServletRequest httpRequest) {
        String method = adminUserService.deleteUser(id, UUID.fromString(jwt.getSubject()),
                request, ClientAddresses.clientIp(httpRequest));
        return Map.of("method", method);
    }
}
