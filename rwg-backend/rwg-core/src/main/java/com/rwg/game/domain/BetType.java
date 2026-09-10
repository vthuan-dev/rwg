package com.rwg.game.domain;

/**
 * Loại cược Roulette (odds chuẩn European single-zero).
 * Odds trả thưởng theo quy ước stake-inclusive M2 (DECISIONS.md):
 * thắng nhận stake + stake × odds.
 */
public enum BetType {
    STRAIGHT,   // 35:1 - cược 1 số (0-36)
    SPLIT,      // 17:1 - cược 2 số kề nhau
    STREET,     // 11:1 - cược hàng ngang 3 số
    CORNER,     // 8:1  - cược 4 số giao nhau
    SIX_LINE,   // 5:1  - cược 2 hàng ngang (6 số)
    COLUMN,     // 2:1  - cược cột dọc 12 số
    DOZEN,      // 2:1  - cược tá (1-12 / 13-24 / 25-36)
    RED,        // 1:1
    BLACK,      // 1:1
    ODD,        // 1:1
    EVEN,       // 1:1
    LOW,        // 1:1 - 1-18
    HIGH,       // 1:1 - 19-36

    // Baccarat (DECISIONS.md)
    PLAYER,      // 1:1
    BANKER,      // 1:1 (trừ 5% commission)
    TIE,         // 8:1
    PLAYER_PAIR, // 11:1
    BANKER_PAIR, // 11:1

    // Korean Lucky 28
    KL28_BIG,    // 0.98:1
    KL28_SMALL,  // 0.98:1
    KL28_SINGLE, // 0.98:1
    KL28_DOUBLE, // 0.98:1

    // Special Code (exact sum bet: 0-27)
    KL28_NUMBER,

    // Xóc Đĩa (M2 stake-inclusive odds)
    XOC_DIA_EVEN,        // 0.98:1 (1 ăn 1.98: 4 đỏ, 4 trắng, 2 đỏ 2 trắng)
    XOC_DIA_ODD,         // 0.98:1 (1 ăn 1.98: 3 đỏ 1 trắng, 3 trắng 1 đỏ)
    XOC_DIA_FOUR_RED,    // 15:1   (1 ăn 16: 4 đỏ)
    XOC_DIA_FOUR_WHITE,  // 15:1   (1 ăn 16: 4 trắng)
    XOC_DIA_THREE_RED,   // 3:1    (1 ăn 4: 3 đỏ 1 trắng)
    XOC_DIA_THREE_WHITE  // 3:1    (1 ăn 4: 3 trắng 1 đỏ)
}
