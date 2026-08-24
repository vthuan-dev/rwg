package com.rwg.game.dto;

import java.time.Instant;

/**
 * Một dòng trong bảng tỷ lệ của màn hình quản trị.
 *
 * Các trường tỷ lệ là {@code String} — xem lý do ở {@link BetTypeOddsResponse}.
 *
 * @param betType tên enum
 * @param defaultOdds odds lợi mức CHUNG, trước hoa hồng
 * @param effectiveOdds odds lợi đang có HIỆU LỰC với người này, trước hoa hồng
 * @param netMultiplier hệ số THỰC NHẬN của người chơi, gồm tiền gốc và đã trừ hoa hồng.
 *     Người vận hành gõ vào ô nhập theo hệ số gộp (odds + 1) cho MỌI bàn để đơn vị ô nhập
 *     không đổi giữa các bàn, nên với cửa có hoa hồng thì con số họ gõ KHÁC con số người
 *     chơi nhận — trường này để hiện rõ khoảng chênh đó ngay dưới ô.
 * @param commissionRate tỷ lệ hoa hồng bị trừ, ví dụ "0.05"; null nếu không chịu hoa hồng
 * @param overridden true nếu có bản ghi riêng
 * @param reason lý do lần đổi gần nhất, null nếu chưa từng đổi
 * @param updatedAt thời điểm đổi gần nhất, null nếu chưa từng đổi
 */
public record UserOddsOptionResponse(
        String betType,
        String defaultOdds,
        String effectiveOdds,
        String netMultiplier,
        String commissionRate,
        boolean overridden,
        String reason,
        Instant updatedAt
) {
}
