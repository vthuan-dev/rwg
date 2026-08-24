package com.rwg.report.dto;

import java.util.List;

/**
 * Bảng tổng quan sổ sách: MỘT DÒNG MỖI NGƯỜI CHƠI có hoạt động trong kỳ.
 *
 * VÌ SAO CẦN BẢNG NÀY thay vì chỉ có báo cáo từng người: admin làm sổ không biết
 * trước phải xem ai. Bắt chọn một người chơi trước khi thấy bất cứ số nào biến trang
 * thành ô nhập liệu chứ không phải báo cáo — người vận hành phải đoán tên rồi thử từng
 * tài khoản một. Bảng tổng quan cho thấy ngay ai hoạt động, ai lỗ nặng, ai được cộng
 * tiền nhiều, rồi mới bấm vào xem chi tiết.
 *
 * Số tiền là {@code String} — xem lý do trong {@link PlayerLedgerResponse}.
 *
 * @param periodFrom ngày đầu kỳ, dạng {@code yyyy-MM-dd}
 * @param periodTo ngày cuối kỳ (bao gồm)
 * @param timezone múi giờ dùng để cắt kỳ
 * @param rows danh sách người chơi trong trang hiện tại
 * @param page trang hiện tại, đếm từ 0
 * @param size số dòng mỗi trang
 * @param totalElements tổng số người chơi có hoạt động trong kỳ
 * @param totalPages tổng số trang
 * @param totalDeposit tổng nạp qua cổng của TOÀN kỳ, không chỉ trang hiện tại
 * @param totalAdminCredit tổng admin cộng của toàn kỳ
 * @param totalAdminDebit tổng admin trừ của toàn kỳ
 * @param totalWithdrawal tổng rút thành công của toàn kỳ
 * @param totalNet tổng lãi/lỗ NGƯỜI CHƠI của toàn kỳ
 */
public record LedgerOverviewResponse(
        String periodFrom,
        String periodTo,
        String timezone,
        List<Row> rows,
        int page,
        int size,
        long totalElements,
        int totalPages,
        String totalDeposit,
        String totalAdminCredit,
        String totalAdminDebit,
        String totalWithdrawal,
        String totalNet) {

    /**
     * Một người chơi trong bảng tổng quan.
     *
     * @param userId để frontend dựng liên kết sang báo cáo chi tiết
     * @param username tên hiển thị
     * @param currency đơn vị tiền của ví
     * @param betCount số ván đã kết toán trong kỳ
     * @param stake tổng tiền cược
     * @param net lãi/lỗ người chơi; âm là người chơi lỗ
     * @param deposit nạp qua cổng
     * @param adminCredit admin cộng tay
     * @param adminDebit admin trừ tay
     * @param withdrawal rút thành công
     * @param balance số dư hiện tại của ví
     */
    public record Row(
            String userId,
            String username,
            String currency,
            long betCount,
            String stake,
            String net,
            String deposit,
            String adminCredit,
            String adminDebit,
            String withdrawal,
            String balance) {
    }
}
