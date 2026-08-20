package com.rwg;

import com.rwg.identity.api.AdminController;
import com.rwg.payment.api.AdminWithdrawalController;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * RWG Casino Platform - APP PLAYER (user-facing).
 * Quét toàn bộ com.rwg (kể cả module game: round scheduler, settlement, websocket realtime)
 * NHƯNG loại các controller admin (chỉ chạy trong rwg-admin-app) để không lộ /api/v1/admin/** ở app player.
 * Quy ước bắt buộc: xem DECISIONS.md ở root repository.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@ConfigurationPropertiesScan("com.rwg")
@ComponentScan(basePackages = "com.rwg",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {AdminController.class, AdminWithdrawalController.class}))
public class RwgApplication {

    public static void main(String[] args) {
        SpringApplication.run(RwgApplication.class, args);
    }
}
