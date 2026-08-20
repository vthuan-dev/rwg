-- ============================================================================
-- RWG Backend - V20260820_06: Baccarat support columns & table seeding.
-- ============================================================================

ALTER TABLE rounds ADD COLUMN baccarat_player_cards VARCHAR(64) NULL;
ALTER TABLE rounds ADD COLUMN baccarat_banker_cards VARCHAR(64) NULL;
ALTER TABLE rounds ADD COLUMN baccarat_player_score INT NULL;
ALTER TABLE rounds ADD COLUMN baccarat_banker_score INT NULL;
ALTER TABLE rounds ADD COLUMN baccarat_player_pair BOOLEAN NULL;
ALTER TABLE rounds ADD COLUMN baccarat_banker_pair BOOLEAN NULL;
ALTER TABLE rounds ADD COLUMN baccarat_result VARCHAR(16) NULL;

-- Seed 1 bàn Baccarat (name_i18n đủ 4 ngôn ngữ en/vi/zh/ja).
INSERT INTO game_tables (id, game_type, name_i18n, status, min_bet, max_bet, currency, created_at, updated_at)
VALUES ('22222222-3333-4444-5555-666666666666',
        'BACCARAT',
        CAST('{"en":"Baccarat Premium","vi":"Baccarat Cao Cấp","zh":"豪华百家乐","ja":"プレミアムバカラ"}' AS JSON),
        'ACTIVE', 10, 50000, 'USD', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6));
