ALTER TABLE app_settings
    ADD CONSTRAINT uq_app_settings_name UNIQUE (name);

ALTER TABLE app_settings
    ALTER COLUMN val DROP NOT NULL;
