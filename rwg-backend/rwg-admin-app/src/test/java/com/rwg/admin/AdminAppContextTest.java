package com.rwg.admin;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test: xác nhận context app ADMIN (rwg-admin-app) khởi động được KHÔNG cần package game
 * (không chạy RoundScheduler/LedgerReconciliationJob/WebSocket) và chỉ giữ khu quản trị.
 */
@SpringBootTest
@ActiveProfiles("test")
class AdminAppContextTest {

    @Test
    void contextLoads() {
    }
}
