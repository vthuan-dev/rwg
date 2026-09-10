-- ============================================================================
-- RWG Backend - V20260911_03: Mo rong cot bet_type cho cac cua Xoc Dia.
--
-- VI SAO: cot bet_type duoc dat VARCHAR(16) tu V20260820_05, kich thuoc du cho ma
-- cua Roulette/Baccarat (BANKER, STRAIGHT, RED, BLACK... dai nhat 8 ky tu). Ma cua
-- Xoc Dia ra doi sau, dai hon nhieu:
--
--     XOC_DIA_THREE_WHITE   19 ky tu
--     XOC_DIA_FOUR_WHITE    18
--     XOC_DIA_THREE_RED     17
--     XOC_DIA_FOUR_RED      16   <- vua khit, khong loi
--
-- MySQL chay o strict mode (mac dinh 8.x) nen INSERT vuot 16 ky tu bi tu choi voi
-- loi 1406 "Data too long for column 'bet_type'" -> POST /games/tables/{id}/bets
-- that bai. Hau qua: 3 trong 6 cua cua Xoc Dia KHONG BAO GIO dat duoc cuoc, trong
-- khi 2 cua Chan/Le van chay binh thuong nen loi trong nhu the ban chi bi loi mot
-- vai cua.
--
-- Cot `bets.bet_type` chi luu ma cua cuoc, khong phai khoa ngoai, nen doi do dai
-- khong anh huong du lieu cu (moi ma cu deu ngan hon 16).
--
-- Dung 32 cho thua: 128 byte voi utf8mb4, van nam gon trong gioi han index
-- (3072 byte o row format DYNAMIC) va con cho cho cac loai cuoc them sau nay.
-- Entity Bet va UserGameOdds da duoc sua length = 16 -> 32 cho khop.
-- ============================================================================

ALTER TABLE bets
    MODIFY COLUMN bet_type VARCHAR(32) NOT NULL;

ALTER TABLE user_game_odds
    MODIFY COLUMN bet_type VARCHAR(32) NOT NULL;
