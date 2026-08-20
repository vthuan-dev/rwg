package com.rwg.identity.domain;

/**
 * Vai trò người dùng — phân quyền qua Spring Security authorities
 * (ROLE_PLAYER / ROLE_ADMIN / ROLE_FINANCE / ROLE_SUPPORT / ROLE_RISK).
 *
 * TÁCH VAI TRÒ ADMIN (chặng 5): trước đây chỉ có PLAYER/ADMIN nên MỌI admin đều vừa
 * cộng được tiền vào ví vừa tự duyệt được lệnh rút — một người có thể chuyển tiền ra
 * khỏi sàn trong 2 request. Bốn vai trò dưới đây thu hẹp quyền theo đúng công việc:
 *
 * - ADMIN   : toàn quyền (super admin). CHỈ role này được phân quyền và đổi cấu hình
 *             hoa hồng — nếu FINANCE tự nâng mình thành ADMIN thì việc tách vai trò
 *             trở nên vô nghĩa.
 * - FINANCE : duyệt/từ chối rút, điều chỉnh ví, tra soát nạp/rút. KHÔNG phân quyền.
 * - SUPPORT : xem user, khóa/mở, KYC, xem ví/ledger/audit. KHÔNG chạm tiền. Đây là
 *             nhóm nhân sự đông nhất nên tách khỏi quyền chạm tiền có giá trị cao nhất.
 * - RISK    : chỉ đọc (dashboard, báo cáo, audit, ledger).
 *
 * Cột DB users.role là VARCHAR(16) nên thêm giá trị KHÔNG cần migration.
 * Tài khoản ADMIN hiện có giữ nguyên toàn quyền — không hạ quyền tự động.
 *
 * Quy ước bắt buộc: xem DECISIONS.md ở root repository.
 */
public enum UserRole {
    PLAYER,
    ADMIN,
    FINANCE,
    SUPPORT,
    RISK;

    /** Các vai trò thuộc khu quản trị (mọi role trừ PLAYER). */
    public boolean isStaff() {
        return this != PLAYER;
    }
}
