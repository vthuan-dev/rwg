package com.rwg.admin;

import com.rwg.affiliate.api.MyAffiliateController;
import com.rwg.bank.api.BankAccountController;
import com.rwg.chat.api.ChatController;
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
import com.rwg.notification.api.NotificationController;
import com.rwg.payment.api.PaymentController;
import com.rwg.settings.api.AppSettingController;
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
 *
 * WEBSOCKET ĐƯỢC BẬT Ở APP NÀY (chặng 8, chat hỗ trợ). Trước đó WebSocketConfig và
 * WsAuthChannelInterceptor bị loại vì khu quản trị không cần realtime; chat hai chiều
 * thì cần — nhân sự phải thấy tin người chơi gửi ngay, không phải chờ tải lại trang.
 *
 * Broker STOMP là enableSimpleBroker (in-memory theo từng JVM) nên hai app KHÔNG thấy
 * gói của nhau; ChatRelayConfig bắc cầu bằng Redis pub/sub. Nếu để WebSocket tắt ở đây
 * thì tin nhân sự gửi vẫn vào DB nhưng người chơi chỉ thấy khi tự tải lại trang.
 *
 * rwg.websocket.audience=STAFF ở application.yml của app này chỉ cho token quản trị mở
 * phiên STOMP: hai app dùng chung JWT_SECRET nên thiếu cấu hình đó thì token PLAYER cũng
 * kết nối được vào broker của khu quản trị.
 *
 * DANH SÁCH basePackages LÀ THỦ CÔNG: thêm một package mới vào {@code com.rwg} mà
 * quên khai báo ở đây thì controller trong đó KHÔNG ĐƯỢC ĐĂNG KÝ và mọi route
 * của nó trả 404 — không có lỗi khi khởi động, không có cảnh báo nào.
 *
 * TỆ HƠN NỮA, TEST KHÔNG BẮT ĐƯỢC LỖI NÀY: context test trong {@code rwg-core}
 * quét toàn bộ {@code com.rwg}, nên một endpoint có thể có test xanh mà vẫn 404
 * khi chạy thật. Đã xảy ra đúng như vậy với {@code com.rwg.report}.
 *
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
                "com.rwg.risk",
                "com.rwg.banner",
                "com.rwg.media",
                "com.rwg.notification",
                "com.rwg.chat",
                // Báo cáo sổ sách — CHỈ có Ở ĐÂY, không ở app người chơi.
                "com.rwg.report",
                // Nội dung chữ hiện cho khách (lời chào khung chat), soạn ở khu quản trị.
                //
                // PHẢI LIỆT KÊ Ở ĐÂY: khác RwgApplication (quét cả "com.rwg" rồi loại trừ),
                // app này chỉ quét ĐÚNG các package trong danh sách trên. Thiếu một package
                // nghĩa là controller trong đó không được đăng ký, và lời gọi tới nó trả 404
                // "Resource not found" — trông y như lỗi đường dẫn ở frontend, nên rất dễ
                // mất thời gian đi tìm sai chỗ.
                "com.rwg.settings"
        },
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {
                        AuthController.class,
                        UserController.class,
                        WalletController.class,
                        BankAccountController.class,
                        PaymentController.class,
                        NotificationController.class,
                        // API của người chơi — không thuộc khu quản trị.
                        ChatController.class,
                        // Đường ĐỌC công khai của nội dung chữ: thuộc app người chơi, nơi khung
                        // chat lấy lời chào. App này chỉ cần đường SỬA
                        // (AdminAppSettingController) và đường đó ĐƯỢC giữ lại.
                        //
                        // Loại ra để app quản trị không mở thêm một điểm cuối không cần xác thực
                        // nào — bề mặt công khai của backoffice càng nhỏ càng tốt.
                        AppSettingController.class,
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
