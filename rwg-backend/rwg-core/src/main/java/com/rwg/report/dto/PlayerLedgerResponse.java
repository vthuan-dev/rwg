package com.rwg.report.dto;

import java.util.List;

/**
 * Sổ sách một người chơi trong một kỳ (thường là một tháng).
 *
 * MỌI SỐ TIỀN LÀ {@code String}, KHÔNG PHẢI {@code BigDecimal}. Jackson tuần tự hoá
 * {@code BigDecimal} thành <em>số</em> JSON, và trình duyệt đọc số JSON thành
 * {@code double} — mất chính xác ngay với giá trị {@code DECIMAL(20,8)} mà hệ thống
 * đang dùng cho tiền. Dự án đã có tiền lệ được ghi lại: chú thích trong {@code money.ts}
 * kể một sự cố thật khi một {@code BigDecimal} về dạng số JSON làm sập trang cược.
 *
 * VÌ SAO TÁCH {@code depositViaGateway} VÀ {@code adminCredit}: về kế toán đây là hai
 * loại hoàn toàn khác nhau — một là tiền thật vào hệ thống, một là tiền do admin tạo ra.
 * Gộp thành một cột "Nạp" sẽ che mất chính điều mà sổ sách cần thấy nhất.
 *
 * @param userId người chơi
 * @param username tên đăng nhập, để admin không phải tra lại từ UUID
 * @param periodFrom ngày đầu kỳ (bao gồm), dạng {@code yyyy-MM-dd}
 * @param periodTo ngày cuối kỳ (bao gồm), dạng {@code yyyy-MM-dd}
 * @param timezone múi giờ dùng để cắt kỳ, hiển thị trên giao diện để admin biết
 *     mình đang đọc theo giờ nào
 * @param currency đơn vị tiền của ví
 * @param openingBalance số dư ngay trước đầu kỳ
 * @param closingBalance số dư tại cuối kỳ
 * @param depositViaGateway người chơi tự nạp qua cổng, đã hoàn tất
 * @param adminCredit admin cộng tay vào ví
 * @param adminDebit admin trừ tay khỏi ví
 * @param withdrawalSettled rút đã chi thành công
 * @param games thắng/thua theo từng loại game
 * @param totalStake tổng tiền cược đã kết toán, mọi game
 * @param totalPayout tổng tiền nhận về (đã gồm gốc), mọi game
 * @param totalNet lãi/lỗ của NGƯỜI CHƠI, mọi game — âm là người chơi lỗ
 * @param totalPending tổng tiền cược còn treo, mọi game
 */
public record PlayerLedgerResponse(
        String userId,
        String username,
        String periodFrom,
        String periodTo,
        String timezone,
        String currency,
        String openingBalance,
        String closingBalance,
        String depositViaGateway,
        String adminCredit,
        String adminDebit,
        String withdrawalSettled,
        List<GameLine> games,
        String totalStake,
        String totalPayout,
        String totalNet,
        String totalPending) {

    /**
     * Một dòng thắng/thua của một loại game.
     *
     * @param gameType mã game trong DB, ví dụ {@code LUCKY28}. Frontend tự dịch sang
     *     tên hiển thị; trả mã thô để backend không phải biết ngôn ngữ nào đang bật.
     * @param betCount số ván đã kết toán
     * @param stake tổng tiền đã cược
     * @param payout tổng tiền nhận về, ĐÃ GỒM TIỀN GỐC (quy ước stake-inclusive)
     * @param net lãi/lỗ người chơi = {@code payout - stake}
     * @param pendingStake tiền cược còn treo ở game này
     */
    public record GameLine(
            String gameType,
            long betCount,
            String stake,
            String payout,
            String net,
            String pendingStake) {
    }
}
