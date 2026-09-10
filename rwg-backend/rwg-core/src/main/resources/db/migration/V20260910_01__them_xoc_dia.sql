-- ============================================================================
-- RWG Backend - V20260910_01: Xóc Đĩa columns & table seeding.
-- ============================================================================

ALTER TABLE rounds ADD COLUMN xoc_dia_coins VARCHAR(32) NULL;
ALTER TABLE rounds ADD COLUMN xoc_dia_red_count INT NULL;
ALTER TABLE rounds ADD COLUMN xoc_dia_seed VARCHAR(64) NULL;
ALTER TABLE rounds ADD COLUMN xoc_dia_seed_hash VARCHAR(64) NULL;

-- Seed 1 bàn Xóc Đĩa VIP (name_i18n đủ 4 ngôn ngữ en/vi/zh/ja).
INSERT INTO game_tables (id, game_type, name_i18n, status, min_bet, max_bet, currency, created_at, updated_at)
VALUES ('77777777-8888-9999-aaaa-bbbbbbbbbbbb',
        'XOC_DIA',
        CAST('{"en":"Xoc Dia VIP","vi":"Xóc Đĩa VIP","zh":"色碟VIP","ja":"ソックディアVIP"}' AS JSON),
        'ACTIVE', 10, 50000, 'USD', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6));
