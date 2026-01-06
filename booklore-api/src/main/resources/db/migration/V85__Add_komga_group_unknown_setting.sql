INSERT INTO app_settings (name, val)
SELECT 'komga_group_unknown', 'true'
WHERE NOT EXISTS (
    SELECT 1 FROM app_settings WHERE name = 'komga_group_unknown'
);
