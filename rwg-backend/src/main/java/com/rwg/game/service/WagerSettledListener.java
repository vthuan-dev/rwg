package com.rwg.game.service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * SPI hook (Phase c): SettlementService gọi SAU KHI settle xong mỗi user.
 * Dành cho loyalty/reward phase (e) cài đặt listener mà KHÔNG cần sửa lõi game.
 * Chưa có implementation nào hệ thống vẫn chạy bình thường (danh sách rỗng).
 *
 * KHÔNG ném exception từ listener — SettlementService bắt và log, không làm
 * hỏng nghiệp vụ tiền đã chốt sổ.
 */
public interface WagerSettledListener {

    /**
     * @param userId    user vừa được settle
     * @param gameId    loại game (vd "ROULETTE")
     * @param amountBet tổng stake của user trong round (DECIMAL, KHÔNG float/double)
     * @param amountWon tổng payout stake-inclusive đã credit (0 nếu thua hết)
     */
    void onWagerSettled(UUID userId, String gameId, BigDecimal amountBet, BigDecimal amountWon);
}
