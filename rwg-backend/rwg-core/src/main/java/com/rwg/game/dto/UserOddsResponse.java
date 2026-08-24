package com.rwg.game.dto;

import java.util.List;

/**
 * Bảng tỷ lệ cược của một người chơi, dùng cho màn hình quản trị.
 *
 * Trả về MỌI bàn và MỌI loại cược, kèm mức chung và mức riêng nếu có. Cách này thay cho
 * việc chỉ trả các dòng đã ghi đè: người vận hành cần thấy mức chung để biết đang đổi từ
 * đâu sang đâu, không thì họ gõ số vào một ô trống mà không có gì đối chiếu.
 *
 * @param userId người chơi
 * @param username để giao diện xác nhận đang sửa đúng người
 * @param tables từng bàn kèm các loại cược của bàn đó
 */
public record UserOddsResponse(
        String userId,
        String username,
        List<UserTableOddsResponse> tables
) {
}
