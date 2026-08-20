package com.rwg;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test: xác nhận context app PLAYER (rwg-user-app) khởi động được với cấu hình
 * component-scan đã loại controller admin. Không lộ /api/v1/admin/** ở app này.
 */
@SpringBootTest
@ActiveProfiles("test")
class UserAppContextTest {

    @Test
    void contextLoads() {
    }
}
