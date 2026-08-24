package com.rwg.game.dto;

/**
 * Tỷ lệ khi đoán đúng MỘT tổng cụ thể của Lucky 28.
 *
 * Tách khỏi {@link BetTypeOddsResponse} vì đây là cược có {@code selection}: cùng loại
 * {@code KL28_NUMBER} nhưng 28 tổng khác nhau có 28 tỷ lệ khác nhau, từ 12 tới 999. Nhồi
 * vào danh sách theo loại cược sẽ cho 28 dòng cùng tên loại mà khác tỷ lệ.
 *
 * Hai trường tỷ lệ là {@code String} — xem lý do ở {@link BetTypeOddsResponse}.
 *
 * @param sum tổng cần đoán, 0 tới 27
 * @param odds odds LỢI, cùng quy ước engine: "11" nghĩa là cược 100 thắng nhận 1200
 * @param multiplier hệ số hiển thị, đã gồm tiền gốc ({@code odds + 1})
 */
public record NumberOddsResponse(
        int sum,
        String odds,
        String multiplier
) {
}
