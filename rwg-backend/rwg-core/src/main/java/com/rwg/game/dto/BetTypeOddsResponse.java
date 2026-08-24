package com.rwg.game.dto;

/**
 * Tỷ lệ của một loại cược, đã áp tỷ lệ riêng của người chơi nếu có.
 *
 * Các trường tỷ lệ là {@code String} chứ không {@code BigDecimal}, giống
 * {@code WalletResponse.balance} và {@code PlayerBetResponse.stake}. Jackson tuần tự hoá
 * {@code BigDecimal} thành SỐ JSON, mà số của JavaScript là số thực nhị phân 64-bit —
 * không biểu diễn đúng mọi giá trị thập phân. Chuỗi đi qua nguyên vẹn.
 *
 * @param betType tên enum, ví dụ KL28_BIG
 * @param odds odds LỢI trước hoa hồng, ví dụ "0.9800"
 * @param multiplier hệ số THỰC NHẬN gồm tiền gốc — đây là con số hiện cho người chơi và
 *     bằng đúng số tiền họ nhận về trên mỗi đồng cược. Với cửa Nhà băng Baccarat, hoa hồng
 *     5% ĐÃ được trừ ở đây: odds lợi 1 cho ra "1.95", không phải "2". Tính ở server để
 *     frontend không phải tự suy ra, việc mà làm sai một lần là hiển thị lệch toàn bộ.
 * @param commissionRate tỷ lệ hoa hồng bị trừ, ví dụ "0.05"; null nếu cược này không chịu
 *     hoa hồng. Giao diện dùng để chú thích vì sao hệ số thấp hơn tỷ lệ niêm yết thông
 *     thường, thay vì đưa ra một con số lạ không giải thích.
 * @param personalized true nếu người chơi này có tỷ lệ riêng khác mức chung. Frontend
 *     dùng để nói rõ cho người chơi biết, thay vì đổi con số mà không giải thích.
 */
public record BetTypeOddsResponse(
        String betType,
        String odds,
        String multiplier,
        String commissionRate,
        boolean personalized
) {
}
