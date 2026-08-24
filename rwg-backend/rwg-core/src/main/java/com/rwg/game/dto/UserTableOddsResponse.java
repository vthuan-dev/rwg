package com.rwg.game.dto;

import java.util.List;

/**
 * Tỷ lệ của một bàn trong bảng quản trị.
 *
 * @param tableId bàn
 * @param gameType loại game
 * @param nameI18n tên bàn theo ngôn ngữ, để giao diện hiện tên người dùng đọc được
 * @param options các loại cược của bàn
 */
public record UserTableOddsResponse(
        String tableId,
        String gameType,
        String nameI18n,
        List<UserOddsOptionResponse> options
) {
}
