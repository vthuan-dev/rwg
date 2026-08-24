package com.rwg.identity.api;

import com.rwg.common.web.ClientAddresses;
import com.rwg.identity.dto.ChangePasswordRequest;
import com.rwg.identity.dto.SetWithdrawalPasswordRequest;
import com.rwg.identity.dto.UpdateLocaleRequest;
import com.rwg.identity.dto.UpdateProfileRequest;
import com.rwg.identity.dto.UserResponse;
import com.rwg.identity.dto.VerifyWithdrawalPasswordRequest;
import com.rwg.identity.dto.WithdrawalPasswordCheckResponse;
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

    /**
     * Kiểm mật khẩu rút tiền mà KHÔNG tạo lệnh rút.
     *
     * Trang rút tiền gọi ngầm endpoint này trong lúc người chơi gõ, để chỉ bật nút gửi lệnh khi
     * mật khẩu đã đúng.
     *
     * DÙNG {@code POST} KHÔNG {@code GET} dù đây là thao tác kiểm:
     *   1. Mật khẩu không được nằm trong URL. URL đi vào lịch sử duyệt web, log proxy và
     *      access log của server; response của GET còn có thể bị cache.
     *   2. Có tác dụng phụ thật — trừ một lượt trong bộ đếm chống dò khi gõ sai.
     */
    @PostMapping("/me/withdrawal-password/verify")
    @Operation(summary = "Kiểm mật khẩu rút tiền (không tạo lệnh); dùng chung bộ đếm chống dò "
            + "với POST /wallet/withdrawals")
    public WithdrawalPasswordCheckResponse verifyWithdrawalPassword(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody VerifyWithdrawalPasswordRequest request,
            HttpServletRequest httpRequest) {
        return authService.verifyWithdrawalPassword(UUID.fromString(jwt.getSubject()), request,
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

    /**
     * Cập nhật họ tên, quốc gia, số điện thoại.
     *
     * Dùng {@code PATCH} không {@code PUT}: client gửi đúng các ô đang sửa, trường không
     * gửi thì giữ nguyên. Với {@code PUT} thì theo đúng nghĩa là thay toàn bộ, nên thiếu
     * một trường đồng nghĩa với xoá nó.
     */
    @PatchMapping("/me/profile")
    @Operation(summary = "Cập nhật hồ sơ: họ tên, quốc gia, số điện thoại")
    public UserResponse updateProfile(@AuthenticationPrincipal Jwt jwt,
                                      @Valid @RequestBody UpdateProfileRequest request,
                                      HttpServletRequest httpRequest) {
        return authService.updateProfile(UUID.fromString(jwt.getSubject()), request,
                ClientAddresses.clientIp(httpRequest));
    }
}
