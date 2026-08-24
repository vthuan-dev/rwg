-- Tỷ lệ cược riêng theo từng người chơi ở từng bàn.
--
-- KHÔNG có bản ghi = dùng tỷ lệ mặc định của engine. Cách này thay cho việc chèn sẵn
-- đủ bản ghi cho mọi người chơi: 6 bàn × 5 loại cược × số người chơi sẽ phình rất
-- nhanh, mà đa số người chơi dùng mức chung.

CREATE TABLE user_game_odds (
    id           CHAR(36)      NOT NULL,
    user_id      CHAR(36)      NOT NULL,
    table_id     CHAR(36)      NOT NULL,
    bet_type     VARCHAR(16)   NOT NULL,

    -- Odds LỜI, cùng quy ước với engine: 0.98 nghĩa là cược 100 thắng nhận 198.
    -- scale 4 đủ cho mọi tỷ lệ thực tế (0.9800) mà không cần scale 8 như tiền.
    odds         DECIMAL(10,4) NOT NULL,

    -- Bắt buộc, giống AdjustWalletRequest. Không cho đổi tỷ lệ mà không nói vì sao.
    reason       VARCHAR(255)  NOT NULL,

    created_by   CHAR(36)      NOT NULL,
    created_at   DATETIME(6)   NOT NULL,
    updated_at   DATETIME(6)   NOT NULL,

    PRIMARY KEY (id),

    -- Mỗi tổ hợp chỉ MỘT bản ghi. Thiếu ràng buộc này thì hai dòng cùng áp cho một
    -- người và không xác định được dòng nào có hiệu lực.
    UNIQUE KEY uk_user_game_odds (user_id, table_id, bet_type),

    -- Tra cứu lúc thanh toán luôn theo (user_id, table_id).
    KEY idx_user_game_odds_lookup (user_id, table_id),

    CONSTRAINT fk_user_game_odds_user
        FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_user_game_odds_table
        FOREIGN KEY (table_id) REFERENCES game_tables (id)
-- COLLATE phải TRÙNG với users.id và game_tables.id: MySQL từ chối khoá ngoại giữa hai
-- cột chuỗi khác collation (mã lỗi 3780). Toàn bộ schema dùng utf8mb4_0900_ai_ci.
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;


-- Odds ĐÃ CHỐT lúc nhận cược.
--
-- Người chơi đồng ý với con số họ THẤY lúc đặt. Nếu thanh toán tra lại bảng odds thì
-- quản trị đổi tỷ lệ sau khi đã biết kết quả vẫn ảnh hưởng được tới cược cũ — nghĩa là
-- có thể hạ tỷ lệ đúng lúc người chơi thắng.
--
-- NULL = cược đặt TRƯỚC khi có tính năng này. Thanh toán rơi về mặc định engine, nên
-- các cược đang chờ lúc triển khai vẫn trả đúng như cũ.
ALTER TABLE bets
    ADD COLUMN odds DECIMAL(10,4) NULL AFTER stake;
