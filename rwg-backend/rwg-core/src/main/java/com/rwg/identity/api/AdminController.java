package com.rwg.identity.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Health check khu quản trị. Toàn bộ /api/v1/admin/** yêu cầu ROLE_ADMIN
 * (enforce tập trung trong SecurityConfig — không dựa annotation từng method).
 *
 * Endpoint GET /api/v1/admin/users trước đây ở controller này đã được thay bằng
 * {@link AdminUserController} (có filter status/role/keyword). Giữ hai bản sẽ gây
 * "Ambiguous mapping" khi khởi động context, nên bản cũ đã được bỏ.
 */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin", description = "Khu vực quản trị - yêu cầu ROLE_ADMIN")
public class AdminController {

    @GetMapping("/health")
    @Operation(summary = "Health check nội bộ khu admin (xác minh phân quyền ADMIN)")
    public Map<String, String> health() {
        return Map.of("status", "OK", "area", "admin");
    }
}
