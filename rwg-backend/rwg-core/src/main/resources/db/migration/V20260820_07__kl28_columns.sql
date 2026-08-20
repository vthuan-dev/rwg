-- ============================================================================
-- RWG Backend - V20260820_07: Korean Lucky 28 columns & table seeding.
-- ============================================================================

ALTER TABLE rounds ADD COLUMN kl28_numbers VARCHAR(16) NULL;
ALTER TABLE rounds ADD COLUMN kl28_sum INT NULL;

-- Seed 1 bàn Korean Lucky 28 (name_i18n đủ 4 ngôn ngữ en/vi/zh/ja).
INSERT INTO game_tables (id, game_type, name_i18n, status, min_bet, max_bet, currency, created_at, updated_at)
VALUES ('33333333-4444-5555-6666-777777777777',
        'KL28',
        CAST('{"en":"Korean Lucky 28","vi":"Korean Lucky 28","zh":"韩国幸运28","ja":"韓国ラッキー28"}' AS JSON),
        'ACTIVE', 10, 50000, 'USD', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6));
