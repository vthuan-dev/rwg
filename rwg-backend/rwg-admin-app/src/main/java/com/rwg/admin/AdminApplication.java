package com.rwg.admin;

import com.rwg.affiliate.api.MyAffiliateController;
import com.rwg.bank.api.BankAccountController;
import com.rwg.config.WebSocketConfig;
import com.rwg.config.WsAuthChannelInterceptor;
import com.rwg.game.api.GameController;
import com.rwg.game.service.BetService;
import com.rwg.game.service.GameEventBroadcaster;
import com.rwg.game.service.GameQueryService;
import com.rwg.game.service.LedgerReconciliationJob;
import com.rwg.game.service.RoundScheduler;
import com.rwg.game.service.SettlementService;
import com.rwg.game.service.WagerSettledListener;
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
 *
 * Quét com.rwg.game để có AdminGameController (bật/tắt bàn, hạn mức cược), NHƯNG
 * loại TƯỜNG MINH toàn bộ thành phần runtime của game: RoundScheduler,
 * SettlementService, LedgerReconciliationJob, GameEventBroadcaster,
 * WagerSettledListener, BetService, GameQueryService và GameController.
 *
 * VÌ SAO PHẢI LIỆT KÊ TỪNG CLASS thay vì bỏ luôn cả package: khu quản trị cần
 * chạm tới bàn chơi, nhưng nếu app admin cũng chạy RoundScheduler thì HAI tiến
 * trình cùng quay vòng trên cùng một bàn — vi phạm nguyên tắc single-writer và
 * sinh round trùng. Tương tự, LedgerReconciliationJob chạy hai nơi sẽ nhân đôi
 * cảnh báo lệch sổ.
 *
 * Loại thêm các controller player để app admin chỉ expose khu quản trị.
 * Giữ các controller admin: AdminController (/api/v1/admin/health),
 * AdminUserController (/api/v1/admin/users/**), AdminWalletController
 * (/api/v1/admin/users/{id}/wallet/**), AdminPaymentController (/api/v1/admin/deposits,
 * /api/v1/admin/withdrawals), AdminAuditController (/api/v1/admin/audit/**),
 * AdminWithdrawalController (/api/v1/admin/withdrawals/{id}/approve|reject),
 * AdminAffiliateController (/api/v1/admin/affiliate/**), AdminDashboardController
 * (/api/v1/admin/dashboard/**), AdminApprovalController (/api/v1/admin/approvals/**),
 * AdminGameController (/api/v1/admin/games/**) và AdminRiskController
 * (/api/v1/admin/risk/**).
 *
 * Quét com.rwg.affiliate để phục vụ khu đại lý, NHƯNG CommissionScheduler KHÔNG được
 * tạo ở app này: nó có @ConditionalOnProperty(rwg.commission.scheduler-enabled=true)
 * và chỉ rwg-user-app bật cờ đó — hai instance cùng chi hoa hồng là rủi ro thật.
 * Cùng lý do, MyAffiliateController là API của người chơi nên cũng bị loại.
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
                "com.rwg.bank",
                "com.rwg.affiliate",
                "com.rwg.game",
                "com.rwg.risk"
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
                        WsAuthChannelInterceptor.class,
                        // API của người chơi — không thuộc khu quản trị.
                        MyAffiliateController.class,
                        GameController.class,
                        // Runtime game: CHỈ chạy ở rwg-user-app (xem javadoc ở trên).
                        RoundScheduler.class,
                        SettlementService.class,
                        LedgerReconciliationJob.class,
                        GameEventBroadcaster.class,
                        WagerSettledListener.class,
                        BetService.class,
                        GameQueryService.class
                }))
public class AdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminApplication.class, args);
    }
}
