package com.rwg.identity.api;

import com.rwg.common.web.ClientAddresses;
import com.rwg.identity.dto.LoginRequest;
import com.rwg.identity.dto.LogoutRequest;
import com.rwg.identity.dto.RefreshRequest;
import com.rwg.identity.dto.RegisterRequest;
import com.rwg.identity.dto.TokenResponse;
import com.rwg.identity.dto.UserResponse;
import com.rwg.identity.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API xác thực công khai (không cần JWT): đăng ký / đăng nhập / refresh / logout.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth", description = "Đăng ký, đăng nhập, refresh token rotation")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @Operation(summary = "Đăng ký tài khoản PLAYER mới")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request,
                                                 HttpServletRequest httpRequest) {
        UserResponse user = authService.register(request, ClientAddresses.clientIp(httpRequest));
        return ResponseEntity.status(HttpStatus.CREATED).body(user);
    }

    @PostMapping("/login")
    @Operation(summary = "Đăng nhập bằng username/email + password; trả JWT access 15 phút + refresh token")
    public TokenResponse login(@Valid @RequestBody LoginRequest request,
                               HttpServletRequest httpRequest) {
        return authService.login(request, ClientAddresses.clientIp(httpRequest));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Xoay vòng refresh token: token cũ bị thu hồi, trả cặp token mới")
    public TokenResponse refresh(@Valid @RequestBody RefreshRequest request,
                                 HttpServletRequest httpRequest) {
        return authService.refresh(request.refreshToken(), ClientAddresses.clientIp(httpRequest));
    }

    @PostMapping("/logout")
    @Operation(summary = "Đăng xuất: thu hồi refresh token phía client")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request,
                                       HttpServletRequest httpRequest) {
        authService.logout(request.refreshToken(), ClientAddresses.clientIp(httpRequest));
        return ResponseEntity.noContent().build();
    }
}
