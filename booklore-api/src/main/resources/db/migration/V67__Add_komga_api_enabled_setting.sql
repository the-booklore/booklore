INSERT INTO app_setting (name, val)
SELECT 'komga_api_enabled', 'false'
WHERE NOT EXISTS (
    SELECT 1 FROM app_setting WHERE name = 'komga_api_enabled'
);
