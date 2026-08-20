package com.rwg;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * App PLAYER (rwg-user-app) KHÔNG được expose khu quản trị.
 *
 * Kiểm chứng bằng cách soi trực tiếp bảng route của Spring MVC thay vì chỉ smoke-test
 * context: nếu ai thêm controller admin mới mà quên cập nhật excludeFilters trong
 * {@link RwgApplication}, test này FAIL ngay — không im lặng expose route ra app công khai.
 *
 * Lưu ý: /api/v1/admin/** vẫn có hasRole("ADMIN") chặn ở SecurityConfig; test này bảo vệ
 * lớp phòng thủ thứ hai (giảm bề mặt tấn công), không thay thế phân quyền.
 */
@SpringBootTest
@ActiveProfiles("test")
class UserAppContextTest {

    // Actuator cũng đăng ký controllerEndpointHandlerMapping (cùng kiểu) -> phải
    // chỉ rõ bean bảng route của MVC, không inject theo type.
    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping handlerMapping;

    @Test
    void contextLoads() {
    }

    @Test
    void noAdminRouteIsMappedInPlayerApp() {
        List<String> adminRoutes = handlerMapping.getHandlerMethods().entrySet().stream()
                .filter(entry -> entry.getKey().toString().contains("/api/v1/admin"))
                .map(entry -> {
                    HandlerMethod method = entry.getValue();
                    return entry.getKey() + " -> " + method.getBeanType().getSimpleName()
                            + "#" + method.getMethod().getName();
                })
                .toList();

        assertThat(adminRoutes)
                .as("App player KHÔNG được map route /api/v1/admin/**. "
                        + "Nếu vừa thêm controller admin mới, hãy thêm nó vào "
                        + "RwgApplication.excludeFilters.")
                .isEmpty();
    }
}
