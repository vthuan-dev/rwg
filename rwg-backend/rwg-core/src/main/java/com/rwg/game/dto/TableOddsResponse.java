package com.rwg.game.dto;

import java.util.List;

/**
 * Tỷ lệ cược HIỆU LỰC của người đang đăng nhập ở một bàn.
 *
 * Frontend đọc từ đây để vẽ lưới cược, thay vì giữ bảng tỷ lệ viết cứng riêng. Con số hiện
 * trên màn hình PHẢI là con số dùng để trả tiền — nếu hai bên giữ bảng riêng thì người
 * chơi thấy một tỷ lệ và nhận một tỷ lệ khác.
 *
 * @param tableId bàn chơi
 * @param gameType loại game, để frontend biết vẽ bộ cược nào
 * @param options tỷ lệ từng loại cược, đã áp tỷ lệ riêng nếu có
 * @param numberOdds tỷ lệ từng tổng 0–27, chỉ có ở bàn Lucky 28 và biến thể; rỗng ở bàn
 *        khác. Tách khỏi {@code options} vì đây là cược có {@code selection}: một loại
 *        cược duy nhất ({@code KL28_NUMBER}) nhưng 28 tỷ lệ khác nhau.
 */
public record TableOddsResponse(
        String tableId,
        String gameType,
        List<BetTypeOddsResponse> options,
        List<NumberOddsResponse> numberOdds
) {
}
