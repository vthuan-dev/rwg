package com.rwg;

import com.rwg.affiliate.api.AdminAffiliateController;
import com.rwg.affiliate.api.AdminDashboardController;
import com.rwg.bank.api.AdminPayoutMethodController;
import com.rwg.chat.api.AdminChatController;
import com.rwg.game.api.AdminGameController;
import com.rwg.game.api.AdminUserOddsController;
import com.rwg.identity.api.AdminApprovalController;
import com.rwg.identity.api.AdminAuthController;
import com.rwg.identity.api.AdminAuditController;
import com.rwg.identity.api.AdminController;
import com.rwg.identity.api.AdminUserController;
import com.rwg.payment.api.AdminPaymentController;
import com.rwg.payment.api.AdminWithdrawalController;
import com.rwg.banner.api.AdminBannerController;
import com.rwg.risk.api.AdminRiskController;
import com.rwg.wallet.api.AdminWalletController;
import com.rwg.report.api.AdminReportController;
import com.rwg.settings.api.AdminAppSettingController;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * RWG Casino Platform - APP PLAYER (user-facing).
 * Quét toàn bộ com.rwg (kể cả module game: round scheduler, settlement, websocket realtime)
 * NHƯNG loại TOÀN BỘ controller admin (chỉ chạy trong rwg-admin-app) để không lộ
 * /api/v1/admin/** ở app player.
 *
 * Danh sách exclude này phải được cập nhật MỖI KHI thêm controller admin mới — nếu
 * quên, app player sẽ tự động expose route admin đó (vẫn có hasRole("ADMIN") chặn,
 * nhưng làm tăng bề mặt tấn công của app công khai). Test UserAppContextTest kiểm
 * chứng không còn handler nào map vào /api/v1/admin/**.
 *
 * Quy ước bắt buộc: xem DECISIONS.md ở root repository.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@ConfigurationPropertiesScan("com.rwg")
@ComponentScan(basePackages = "com.rwg",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {
                        AdminController.class,
                        // Đăng nhập backoffice chỉ tồn tại ở rwg-admin-app: app người chơi đã
                        // có /api/v1/auth/login riêng, không cần thêm cửa đăng nhập thứ hai.
                        AdminAuthController.class,
                        AdminWithdrawalController.class,
                        AdminUserController.class,
                        AdminWalletController.class,
                        AdminPaymentController.class,
                        AdminAuditController.class,
                        AdminAffiliateController.class,
                        AdminDashboardController.class,
                        AdminApprovalController.class,
                        AdminGameController.class,
                        AdminRiskController.class,
                        AdminBannerController.class,
                        // Xem số tài khoản / địa chỉ ví của người chơi: chỉ thuộc khu quản trị.
                        AdminPayoutMethodController.class,
                        // Đặt tỷ lệ cược riêng cho từng người chơi: nếu lọt vào app người chơi thì
                        // chính người chơi có thể gọi tới điểm cuối sửa tỷ lệ chi trả của mình.
                        AdminUserOddsController.class,
                        // Hộp thư hỗ trợ: nếu lọt vào app người chơi thì có một đường dẫn
                        // công khai tới toàn bộ lịch sử trò chuyện của mọi người chơi khác.
                        AdminChatController.class,
                        // Báo cáo người chơi/đại lý, sao kê ví, thống kê doanh thu cược: chỉ thuộc khu quản trị.
                        AdminReportController.class,
                        // Sửa nội dung chữ hiện cho khách (lời chào khung chat): nếu lọt vào app
                        // người chơi thì có một đường dẫn công khai tới điểm cuối đổi nội dung mà
                        // MỌI khách đang đọc.
                        //
                        // Chỉ loại controller QUẢN TRỊ. AppSettingController (đường đọc) PHẢI ở
                        // lại app này — đó là nơi khung chat của người chơi lấy lời chào.
                        AdminAppSettingController.class
                }))
public class RwgApplication {

    public static void main(String[] args) {
        SpringApplication.run(RwgApplication.class, args);
    }
}
