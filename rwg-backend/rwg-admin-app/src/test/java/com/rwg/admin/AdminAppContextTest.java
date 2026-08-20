package com.rwg.admin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * App ADMIN (rwg-admin-app) khởi động được KHÔNG cần package game
 * (không chạy RoundScheduler / LedgerReconciliationJob / WebSocket) và phải map
 * đầy đủ khu quản trị.
 *
 * Kiểm chứng cả hai chiều: có đủ route admin, và KHÔNG lẫn route dành cho player.
 */
@SpringBootTest
@ActiveProfiles("test")
class AdminAppContextTest {

    // Actuator cũng đăng ký controllerEndpointHandlerMapping (cùng kiểu) -> phải
    // chỉ rõ bean bảng route của MVC, không inject theo type.
    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping handlerMapping;

    @Test
    void contextLoads() {
    }

    @Test
    void adminRoutesAreMapped() {
        List<String> routes = mappedRoutes();

        assertThat(routes).anyMatch(route -> route.contains("/api/v1/admin/users"));
        assertThat(routes).anyMatch(route -> route.contains("/api/v1/admin/audit/logs"));
        assertThat(routes).anyMatch(route -> route.contains("/api/v1/admin/deposits"));
        assertThat(routes).anyMatch(route -> route.contains("/api/v1/admin/withdrawals"));
        assertThat(routes).anyMatch(route -> route.contains("/wallet/adjust"));
    }

    @Test
    void playerRoutesAreNotMapped() {
        List<String> routes = mappedRoutes();

        // Các route player phải bị loại (xem AdminApplication.excludeFilters).
        assertThat(routes).noneMatch(route -> route.contains("/api/v1/auth/login"));
        assertThat(routes).noneMatch(route -> route.contains("/api/v1/users/me"));
        assertThat(routes).noneMatch(route -> route.contains("/api/v1/wallet/deposits"));
        assertThat(routes).noneMatch(route -> route.contains("/api/v1/wallet/withdrawals"));
    }

    private List<String> mappedRoutes() {
        return handlerMapping.getHandlerMethods().keySet().stream()
                .map(Object::toString)
                .toList();
    }
}
