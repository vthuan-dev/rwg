-- ============================================================================
-- RWG Backend - V20260820_08: Seed remaining Lucky 28 game tables.
-- ============================================================================

-- 1. Lucky 28 (LUCKY28)
INSERT INTO game_tables (id, game_type, name_i18n, status, min_bet, max_bet, currency, created_at, updated_at)
VALUES ('44444444-5555-6666-7777-888888888888',
        'LUCKY28',
        CAST('{"en":"Lucky 28","vi":"Lucky 28","zh":"幸运28","ja":"ラッキー28"}' AS JSON),
        'ACTIVE', 10, 50000, 'USD', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6));

-- 2. British Lucky 28 (BRITISH_LUCKY28)
INSERT INTO game_tables (id, game_type, name_i18n, status, min_bet, max_bet, currency, created_at, updated_at)
VALUES ('55555555-6666-7777-8888-999999999999',
        'BRITISH_LUCKY28',
        CAST('{"en":"British Lucky 28","vi":"British Lucky 28","zh":"英国幸运28","ja":"英国ラッキー28"}' AS JSON),
        'ACTIVE', 10, 50000, 'USD', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6));

-- 3. Taiwan Times (TAIWAN_TIMES)
INSERT INTO game_tables (id, game_type, name_i18n, status, min_bet, max_bet, currency, created_at, updated_at)
VALUES ('66666666-7777-8888-9999-aaaaaaaaaaaa',
        'TAIWAN_TIMES',
        CAST('{"en":"Taiwan Times","vi":"Taiwan Times","zh":"台湾时时彩","ja":"台湾タイムズ"}' AS JSON),
        'ACTIVE', 10, 50000, 'USD', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6));
