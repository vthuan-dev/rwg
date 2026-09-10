-- Seed tỷ giá quy đổi tiền tệ USD -> VND (mặc định 1 USD = 25,000 VND)
INSERT INTO app_settings (setting_key, setting_value, updated_at)
VALUES ('exchange.rate.usd_vnd', '25000', NOW())
ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value);
