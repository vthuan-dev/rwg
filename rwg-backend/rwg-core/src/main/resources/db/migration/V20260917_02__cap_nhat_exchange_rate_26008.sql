-- Cap nhat ty gia quy doi USD sang VND thanh 26008
UPDATE app_settings
SET setting_value = '26008', updated_at = NOW()
WHERE setting_key = 'exchange.rate.usd_vnd';
