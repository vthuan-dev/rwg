package com.rwg.report.dto;

/**
 * Một người chơi trong bảng tổng quan sổ sách.
 *
 * TÁCH THÀNH TỆP RIÊNG, không lồng trong {@link LedgerOverviewResponse}: quy ước của
 * dự án (kiểm bằng ArchitectureTest) buộc mọi class trong package {@code dto} phải có
 * hậu tố Request/Response/Event/Payload, và luật đó áp cả cho record lồng.
 *
 * Mọi số tiền là {@code String} — xem lý do trong {@link PlayerLedgerResponse}.
 *
 * @param userId để frontend dựng liên kết sang báo cáo chi tiết
 * @param username tên hiển thị
 * @param currency đơn vị tiền của ví
 * @param betCount số ván đã kết toán trong kỳ
 * @param stake tổng tiền cược
 * @param net lãi/lỗ người chơi; âm là người chơi lỗ
 * @param moneyIn TỔNG tiền vào ví = {@code deposit} + {@code adminCredit}. Giao diện hiện
 *     số này thay vì hai cột riêng: tính năng nạp tự động đã gỡ, giờ mọi đồng vào ví
 *     đều do admin cộng tay, nên tách hai cột chỉ làm người đọc phải tự cộng
 * @param deposit nạp qua cổng thanh toán — GIỮ LẠI để xuất CSV và đối soát dữ liệu cũ
 * @param adminCredit admin cộng tay
 * @param adminDebit admin trừ tay
 * @param withdrawal rút thành công
 * @param balance số dư hiện tại của ví
 */
public record LedgerPlayerRowResponse(
        String userId,
        String username,
        String currency,
        long betCount,
        String stake,
        String net,
        String moneyIn,
        String deposit,
        String adminCredit,
        String adminDebit,
        String withdrawal,
        String balance) {
}
