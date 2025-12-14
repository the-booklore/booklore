INSERT INTO app_settings (name, val)
SELECT 'komga_api_enabled', 'false'
WHERE NOT EXISTS (
    SELECT 1 FROM app_settings WHERE name = 'komga_api_enabled'
);
