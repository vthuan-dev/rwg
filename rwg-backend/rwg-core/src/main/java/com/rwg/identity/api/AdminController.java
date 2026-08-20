package com.rwg.identity.api;

import com.rwg.common.PageResponse;
import com.rwg.identity.dto.UserResponse;
import com.rwg.identity.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * API quản trị (read-only, tối thiểu). Toàn bộ /api/v1/admin/** yêu cầu ROLE_ADMIN
 * (enforce tập trung trong SecurityConfig — không dựa annotation từng method).
 */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin", description = "Khu vực quản trị - yêu cầu ROLE_ADMIN")
public class AdminController {

    private final AuthService authService;

    public AdminController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/health")
    @Operation(summary = "Health check nội bộ khu admin (xác minh phân quyền ADMIN)")
    public Map<String, String> health() {
        return Map.of("status", "OK", "area", "admin");
    }

    @GetMapping("/users")
    @Operation(summary = "Danh sách user phân trang (read-only)")
    public PageResponse<UserResponse> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return authService.listUsers(page, Math.min(size, 100));
    }
}
