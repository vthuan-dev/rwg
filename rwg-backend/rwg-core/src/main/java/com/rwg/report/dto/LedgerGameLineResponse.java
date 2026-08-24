package com.rwg.report.dto;

import java.math.BigDecimal;

/**
 * Một dòng thắng/thua của một loại game trong sổ sách người chơi.
 *
 * TÁCH THÀNH TỆP RIÊNG, không lồng trong {@link PlayerLedgerResponse}: quy ước của dự
 * án (kiểm bằng ArchitectureTest) buộc mọi class trong package {@code dto} phải có hậu
 * tố Request/Response/Event/Payload, và luật đó áp cả cho record lồng.
 *
 * Mọi số tiền là {@code String} — xem lý do trong {@link PlayerLedgerResponse}.
 *
 * @param gameType mã loại game trong DB, ví dụ {@code BRITISH_LUCKY28}
 * @param betCount số ván đã kết toán
 * @param stake tổng tiền cược
 * @param payout tổng tiền nhận về; ĐÃ GỒM tiền gốc
 * @param net lãi/lỗ thật = payout − stake; âm là người chơi lỗ
 * @param pendingStake tiền đang treo ở các ván chưa kết toán
 */
public record LedgerGameLineResponse(
        String gameType,
        long betCount,
        String stake,
        String payout,
        String net,
        String pendingStake) {

    /** Dựng một dòng, tự tính {@code net} để nơi gọi không thể quên trừ tiền gốc. */
    public static LedgerGameLineResponse of(String gameType, long betCount,
                                            BigDecimal stake, BigDecimal payout,
                                            BigDecimal pendingStake) {
        return new LedgerGameLineResponse(
                gameType, betCount,
                stake.toPlainString(),
                payout.toPlainString(),
                payout.subtract(stake).toPlainString(),
                pendingStake.toPlainString());
    }
}
