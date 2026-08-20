package com.rwg.identity.api;

import com.rwg.common.web.ClientAddresses;
import com.rwg.identity.dto.ChangePasswordRequest;
import com.rwg.identity.dto.SetWithdrawalPasswordRequest;
import com.rwg.identity.dto.UpdateLocaleRequest;
import com.rwg.identity.dto.UserResponse;
import com.rwg.identity.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * API tài khoản cá nhân (yêu cầu JWT hợp lệ).
 */
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "Hồ sơ cá nhân, mật khẩu rút tiền, ngôn ngữ")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final AuthService authService;

    public UserController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/me")
    @Operation(summary = "Lấy hồ sơ user hiện tại")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return authService.me(UUID.fromString(jwt.getSubject()));
    }

    @PostMapping("/me/password")
    @Operation(summary = "Đổi mật khẩu đăng nhập (thu hồi toàn bộ refresh token sau khi đổi)")
    public UserResponse changePassword(@AuthenticationPrincipal Jwt jwt,
                                       @Valid @RequestBody ChangePasswordRequest request,
                                       HttpServletRequest httpRequest) {
        return authService.changeLoginPassword(UUID.fromString(jwt.getSubject()), request,
                ClientAddresses.clientIp(httpRequest));
    }

    @PostMapping("/me/withdrawal-password")
    @Operation(summary = "Đặt/đổi mật khẩu rút tiền (phải xác nhận mật khẩu đăng nhập)")
    public UserResponse setWithdrawalPassword(@AuthenticationPrincipal Jwt jwt,
                                              @Valid @RequestBody SetWithdrawalPasswordRequest request,
                                              HttpServletRequest httpRequest) {
        return authService.setWithdrawalPassword(UUID.fromString(jwt.getSubject()), request,
                ClientAddresses.clientIp(httpRequest));
    }

    @PatchMapping("/me/locale")
    @Operation(summary = "Đổi ngôn ngữ hiển thị (en/vi/zh/ja)")
    public UserResponse updateLocale(@AuthenticationPrincipal Jwt jwt,
                                     @Valid @RequestBody UpdateLocaleRequest request,
                                     HttpServletRequest httpRequest) {
        return authService.updateLocale(UUID.fromString(jwt.getSubject()), request,
                ClientAddresses.clientIp(httpRequest));
    }
}
