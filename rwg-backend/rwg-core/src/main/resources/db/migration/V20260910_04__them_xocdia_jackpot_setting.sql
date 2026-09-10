-- Seed cấu hình tính năng Jackpot Xóc Đĩa (Nổ Hũ & Xúc xắc 3D)
INSERT INTO app_settings (setting_key, setting_value, updated_at)
VALUES ('xocdia.jackpot.pool', '295320203', NOW())
ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value);

INSERT INTO app_settings (setting_key, setting_value, updated_at)
VALUES ('xocdia.jackpot.min_pool', '100000000', NOW())
ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value);

INSERT INTO app_settings (setting_key, setting_value, updated_at)
VALUES ('xocdia.jackpot.trigger_mode', 'AUTO', NOW())
ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value);

INSERT INTO app_settings (setting_key, setting_value, updated_at)
VALUES ('xocdia.jackpot.auto_rate', '0.001', NOW())
ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value);

INSERT INTO app_settings (setting_key, setting_value, updated_at)
VALUES ('xocdia.jackpot.target_door', 'RANDOM', NOW())
ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value);

INSERT INTO app_settings (setting_key, setting_value, updated_at)
VALUES ('xocdia.jackpot.winner_mode', 'ALL_BETTOR_SHARE', NOW())
ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value);

INSERT INTO app_settings (setting_key, setting_value, updated_at)
VALUES ('xocdia.jackpot.target_user', '', NOW())
ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value);

INSERT INTO app_settings (setting_key, setting_value, updated_at)
VALUES ('xocdia.jackpot.last_won', '', NOW())
ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value);
