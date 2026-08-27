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

    /**
     * Route SỬA nội dung chữ hiện cho khách PHẢI được map ở app này.
     *
     * VÌ SAO CÓ TEST NÀY: {@code AdminApplication} chỉ quét ĐÚNG danh sách package liệt
     * kê tay, khác {@code RwgApplication} (quét cả "com.rwg" rồi loại trừ). Thêm một
     * package mới mà quên thêm vào danh sách đó thì controller trong nó không được đăng
     * ký, và lời gọi trả 404 "Resource not found" — trông y như lỗi đường dẫn ở frontend,
     * nên rất dễ mất thời gian đi tìm sai chỗ. Đúng lỗi đã xảy ra một lần.
     */
    @Test
    void adminSettingsRoutesAreMapped() {
        List<String> routes = mappedRoutes();

        assertThat(routes)
                .as("thiếu route sửa nội dung chữ — kiểm AdminApplication.basePackages")
                .anyMatch(route -> route.contains("/api/v1/admin/settings"));
    }

    /**
     * Đường ĐỌC công khai của nội dung chữ KHÔNG được map ở app quản trị.
     *
     * Đường đó không cần xác thực và chỉ phục vụ khung chat của người chơi. Để nó lọt vào
     * đây là mở thêm một điểm cuối công khai trên backoffice mà không ai cần tới.
     */
    @Test
    void publicSettingsRouteIsNotMapped() {
        List<String> routes = mappedRoutes();

        // KHÔNG dùng contains("/api/v1/settings") trần: chuỗi đó là chuỗi con của
        // "/api/v1/admin/settings", nên assert sẽ đỏ ngay cả khi cấu hình đúng.
        assertThat(routes).noneMatch(route -> route.contains("[/api/v1/settings/"));
    }

    /**
     * Nhân sự quản trị PHẢI đăng nhập được ở app này. Trước đây không có route nào
     * để lấy token nên toàn bộ khu quản trị không thể truy cập từ trình duyệt.
     */
    @Test
    void backofficeAuthRoutesAreMapped() {
        List<String> routes = mappedRoutes();

        assertThat(routes).anyMatch(route -> route.contains("/api/v1/admin/auth/login"));
        assertThat(routes).anyMatch(route -> route.contains("/api/v1/admin/auth/refresh"));
        assertThat(routes).anyMatch(route -> route.contains("/api/v1/admin/auth/logout"));
    }

    @Test
    void playerRoutesAreNotMapped() {
        List<String> routes = mappedRoutes();

        // Các route player phải bị loại (xem AdminApplication.excludeFilters).
        // Hai đường dẫn "/api/v1/auth/login" và "/api/v1/admin/auth/login" KHÔNG chồng
        // nhau về chuỗi con, nên assert dưới đây không bắt nhầm cửa đăng nhập nhân sự.
        assertThat(routes).noneMatch(route -> route.contains("/api/v1/auth/login"));
        assertThat(routes).noneMatch(route -> route.contains("/api/v1/auth/register"));
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
