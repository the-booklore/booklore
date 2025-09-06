ALTER TABLE user_settings
    ALTER COLUMN setting_value TYPE TEXT,
    ALTER COLUMN setting_value SET NOT NULL;