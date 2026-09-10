-- Seed cấu hình bot Xóc Đĩa VIP (số lượng bot trên bàn và bật/tắt bot chat)
INSERT INTO app_settings (setting_key, setting_value, updated_at)
VALUES ('xocdia.bot.count', '4', NOW())
ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value);

INSERT INTO app_settings (setting_key, setting_value, updated_at)
VALUES ('xocdia.bot.chat.enabled', 'true', NOW())
ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value);
