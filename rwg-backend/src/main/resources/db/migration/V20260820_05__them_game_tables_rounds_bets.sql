-- ============================================================================
-- RWG Backend - V20260820_05: game_tables / rounds / bets (chặng 2 Phase c).
-- Quy ước tiền tệ (DECISIONS.md): DECIMAL(20,8), BigDecimal, HALF_UP.
-- Bảng volume (rounds, bets) PK composite (id, created_at) partition-ready
-- (DECISIONS.md mục b); UNIQUE kèm created_at.
-- name_i18n kiểu JSON lưu {"en","vi","zh","ja"} (DECISIONS.md: cấu hình JSON).
-- ============================================================================

CREATE TABLE game_tables (
    id          CHAR(36)       NOT NULL,
    game_type   VARCHAR(16)    NOT NULL,                 -- ROULETTE | (BACCARAT/SLOTS chặng sau)
    name_i18n   JSON           NOT NULL,                 -- {"en": "...", "vi": "...", "zh": "...", "ja": "..."}
    status      VARCHAR(16)    NOT NULL DEFAULT 'ACTIVE',-- ACTIVE | DISABLED
    min_bet     DECIMAL(20, 8) NOT NULL DEFAULT 1,
    max_bet     DECIMAL(20, 8) NOT NULL DEFAULT 10000,
    currency    VARCHAR(8)     NOT NULL DEFAULT 'USD',
    created_at  DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at  DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    -- Bảng cấu hình (KHÔNG phải bảng volume) -> PK đơn, không partition.
    PRIMARY KEY (id),
    CONSTRAINT chk_game_tables_min_bet CHECK (min_bet >= 0),
    CONSTRAINT chk_game_tables_max_bet CHECK (max_bet > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE rounds (
    id             CHAR(36)      NOT NULL,
    table_id       CHAR(36)      NOT NULL,
    round_seq      BIGINT        NOT NULL,               -- số thứ tự round của bàn
    phase          VARCHAR(16)   NOT NULL,               -- BETTING_OPEN | BETTING_CLOSED | SPINNING | RESULT | SETTLE
    status         VARCHAR(16)   NOT NULL DEFAULT 'OPEN',-- OPEN | SETTLED | VOIDED
    winning_number INT           NULL,                   -- 0-36 (Roulette), có khi vào RESULT
    result_at      DATETIME(6)   NULL,                   -- thời điểm công bố kết quả (đo settlement_lag)
    created_at     DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at     DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    -- PK composite (id, created_at) theo DECISIONS.md mục b (bảng volume).
    PRIMARY KEY (id, created_at),
    -- UNIQUE kèm created_at để hợp lệ với PK composite khi partition.
    CONSTRAINT uq_rounds_table_seq UNIQUE (table_id, round_seq, created_at),
    CONSTRAINT fk_rounds_table FOREIGN KEY (table_id) REFERENCES game_tables (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_rounds_table_status ON rounds (table_id, status);

CREATE TABLE bets (
    id              CHAR(36)       NOT NULL,
    round_id        CHAR(36)       NOT NULL,
    table_id        CHAR(36)       NOT NULL,
    user_id         CHAR(36)       NOT NULL,
    bet_type        VARCHAR(16)    NOT NULL,             -- STRAIGHT | SPLIT | STREET | CORNER | SIX_LINE | COLUMN | DOZEN | RED | BLACK | ODD | EVEN | LOW | HIGH
    selection       VARCHAR(64)    NOT NULL DEFAULT '',  -- mô tả cửa cược (vd "17", "17-20", "2")
    stake           DECIMAL(20, 8) NOT NULL,             -- tiền cược (đã trừ ví khi đặt - M1)
    status          VARCHAR(16)    NOT NULL DEFAULT 'PENDING', -- PENDING | SETTLED | VOIDED
    payout          DECIMAL(20, 8) NOT NULL DEFAULT 0,   -- tiền trả stake-inclusive (M2); 0 nếu thua/hủy
    idempotency_key VARCHAR(128)   NOT NULL,             -- "BET:{roundId}:{userId}:{seq}"
    created_at      DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    -- PK composite (id, created_at) theo DECISIONS.md mục b (bảng volume).
    PRIMARY KEY (id, created_at),
    CONSTRAINT uq_bets_idempotency UNIQUE (idempotency_key, created_at),
    CONSTRAINT chk_bets_stake_positive CHECK (stake > 0),
    CONSTRAINT fk_bets_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT
    -- round_id/table_id KHÔNG đặt FK vì rounds PK composite (id, created_at) -
    -- FK cần tham chiếu đủ cột PK; toàn vẹn đảm bảo ở tầng service.
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE INDEX idx_bets_round ON bets (round_id);
CREATE INDEX idx_bets_user  ON bets (user_id, created_at);

-- ============================================================================
-- Seed 1 bàn Roulette (name_i18n đủ 4 ngôn ngữ en/vi/zh/ja).
-- CAST(... AS JSON) tương thích cả MySQL 8.x và H2 MODE=MySQL.
-- ============================================================================
INSERT INTO game_tables (id, game_type, name_i18n, status, min_bet, max_bet, currency, created_at, updated_at)
VALUES ('11111111-2222-3333-4444-555555555555',
        'ROULETTE',
        CAST('{"en":"Roulette European","vi":"Roulette Châu Âu","zh":"欧洲轮盘","ja":"ヨーロピアンルーレット"}' AS JSON),
        'ACTIVE', 1, 10000, 'USD', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6));
