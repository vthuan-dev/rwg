package com.rwg.admin;

import com.rwg.bank.api.BankAccountController;
import com.rwg.config.WebSocketConfig;
import com.rwg.config.WsAuthChannelInterceptor;
import com.rwg.identity.api.AuthController;
import com.rwg.identity.api.UserController;
import com.rwg.payment.api.PaymentController;
import com.rwg.wallet.api.WalletController;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * RWG Casino Platform - APP ADMIN (BackOffice).
 * KHÔNG quét package com.rwg.game -> tránh chạy trùng RoundScheduler / LedgerReconciliationJob /
 * GameEventBroadcaster / WebSocket trên cùng DB (các background job chỉ chạy ở rwg-user-app).
 * Loại thêm các controller player để app admin chỉ expose khu quản trị.
 * Giữ các controller admin: AdminController (/api/v1/admin/health),
 * AdminUserController (/api/v1/admin/users/**), AdminWalletController
 * (/api/v1/admin/users/{id}/wallet/**), AdminPaymentController (/api/v1/admin/deposits,
 * /api/v1/admin/withdrawals), AdminAuditController (/api/v1/admin/audit/**) và
 * AdminWithdrawalController (/api/v1/admin/withdrawals/{id}/approve|reject).
 * Quy ước bắt buộc: xem DECISIONS.md ở root repository.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@EnableJpaRepositories(basePackages = "com.rwg")
@EntityScan(basePackages = "com.rwg")
@ConfigurationPropertiesScan("com.rwg")
@ComponentScan(
        basePackages = {
                "com.rwg.admin",
                "com.rwg.common",
                "com.rwg.config",
                "com.rwg.identity",
                "com.rwg.payment",
                "com.rwg.wallet",
                "com.rwg.bank"
        },
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {
                        AuthController.class,
                        UserController.class,
                        WalletController.class,
                        BankAccountController.class,
                        PaymentController.class,
                        WebSocketConfig.class,
                        WsAuthChannelInterceptor.class
                }))
public class AdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminApplication.class, args);
    }
}
