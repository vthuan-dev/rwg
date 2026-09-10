-- Seed bo sung cau hinh Jackpot chi dinh: nguong no, muc an, requireBet, phi nuoi hu
INSERT INTO app_settings (setting_key, setting_value, updated_at)
VALUES ('xocdia.jackpot.threshold', '200000000', NOW())
ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value);

INSERT INTO app_settings (setting_key, setting_value, updated_at)
VALUES ('xocdia.jackpot.win_mode', 'FULL_POOL', NOW())
ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value);

INSERT INTO app_settings (setting_key, setting_value, updated_at)
VALUES ('xocdia.jackpot.win_value', '100', NOW())
ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value);

INSERT INTO app_settings (setting_key, setting_value, updated_at)
VALUES ('xocdia.jackpot.require_bet', 'false', NOW())
ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value);

INSERT INTO app_settings (setting_key, setting_value, updated_at)
VALUES ('xocdia.jackpot.fee_rate', '0.01', NOW())
ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value);
