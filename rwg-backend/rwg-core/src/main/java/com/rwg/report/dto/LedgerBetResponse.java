package com.rwg.report.dto;

import com.rwg.game.domain.Bet;

import java.time.Instant;

/**
 * Một ván cược trong danh sách chi tiết của sổ sách (mức 2).
 *
 * KHÁC {@code BetResponse} phía người chơi ở chỗ có thêm {@code net}: admin làm sổ cần
 * thấy ngay lãi/lỗ từng ván mà không phải tự trừ trong đầu qua vài chục dòng.
 *
 * Số tiền là {@code String} — xem lý do trong {@link PlayerLedgerResponse}.
 *
 * @param betId mã ván cược
 * @param betType loại cược, ví dụ {@code KL28_BIG}
 * @param selection cửa đã chọn; rỗng với các cược kết hợp
 * @param stake tiền đã cược
 * @param odds tỷ lệ đã chốt lúc đặt; null với cược đặt trước khi có tính năng tỷ lệ riêng
 * @param payout tiền nhận về, ĐÃ GỒM TIỀN GỐC
 * @param net lãi/lỗ ván này = {@code payout - stake}
 * @param status {@code SETTLED} / {@code PENDING} / {@code VOIDED}
 * @param createdAt thời điểm đặt cược
 */
public record LedgerBetResponse(
        String betId,
        String betType,
        String selection,
        String stake,
        String odds,
        String payout,
        String net,
        String status,
        Instant createdAt) {

    public static LedgerBetResponse of(Bet bet) {
        // payout mặc định là 0 chứ không null (cột NOT NULL DEFAULT 0), nên phép trừ
        // dưới đây an toàn với cả cược PENDING chưa có kết quả.
        return new LedgerBetResponse(
                bet.getId().toString(),
                bet.getBetType().name(),
                bet.getSelection(),
                bet.getStake().toPlainString(),
                bet.getOdds() != null ? bet.getOdds().toPlainString() : null,
                bet.getPayout().toPlainString(),
                bet.getPayout().subtract(bet.getStake()).toPlainString(),
                bet.getStatus().name(),
                bet.getCreatedAt());
    }
}
