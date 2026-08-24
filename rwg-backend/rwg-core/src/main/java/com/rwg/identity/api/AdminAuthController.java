package com.rwg.identity.api;

import com.rwg.common.web.ClientAddresses;
import com.rwg.identity.dto.LoginRequest;
import com.rwg.identity.dto.LogoutRequest;
import com.rwg.identity.dto.RefreshRequest;
import com.rwg.identity.dto.TokenResponse;
import com.rwg.identity.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Xác thực KHU QUẢN TRỊ (backoffice) — chạy trong rwg-admin-app.
 *
 * VÌ SAO TÁCH KHỎI {@link AuthController}: app admin CỐ TÌNH loại AuthController
 * khỏi component scan để không expose API người chơi (đăng ký, đổi mật khẩu, hồ sơ)
 * trên cổng quản trị. Nhưng nhân sự quản trị vẫn phải đăng nhập được ở đó. Nếu đăng
 * ký lại AuthController thì toàn bộ API người chơi cũng lộ theo — nên phải có một
 * controller riêng chỉ chứa đúng những gì khu quản trị cần.
 *
 * KHÔNG có /register: tài khoản nhân sự do người khác cấp qua khu quản trị
 * (AdminUserController), không tự đăng ký.
 *
 * Route /api/v1/admin/auth/** là CÔNG KHAI (permitAll trong SecurityConfig) — bắt
 * buộc phải vậy vì đây là nơi đi lấy token, chưa thể có token. Việc chặn PLAYER nằm
 * trong AuthService.loginStaff, không nằm ở tầng route.
 *
 * Quy ước bắt buộc: mỗi controller admin mới PHẢI được thêm vào excludeFilters của
 * RwgApplication, nếu không app người chơi cũng expose route này.
 */
@RestController
@RequestMapping("/api/v1/admin/auth")
@Tag(name = "Admin Auth", description = "Đăng nhập khu quản trị - chỉ nhân sự (không phải PLAYER)")
public class AdminAuthController {

    private final AuthService authService;

    public AdminAuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @Operation(summary = "Đăng nhập backoffice; tài khoản PLAYER bị từ chối dù mật khẩu đúng")
    public TokenResponse login(@Valid @RequestBody LoginRequest request,
                               HttpServletRequest httpRequest) {
        return authService.loginStaff(request, ClientAddresses.clientIp(httpRequest));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Xoay vòng refresh token cho phiên quản trị")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request,
                                 HttpServletRequest httpRequest) {
        // Dùng chung đường refresh với người chơi: token là opaque và gắn với userId,
        // nên không có đường nào để một refresh token của PLAYER biến thành phiên
        // quản trị — access token mới vẫn mang đúng role đã lưu trong DB.
        return authService.refresh(request.refreshToken(), ClientAddresses.clientIp(httpRequest));
    }

    @PostMapping("/logout")
    @Operation(summary = "Đăng xuất khu quản trị: thu hồi refresh token")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request,
                                       HttpServletRequest httpRequest) {
        authService.logout(request.refreshToken(), ClientAddresses.clientIp(httpRequest));
        return ResponseEntity.noContent().build();
    }
}
