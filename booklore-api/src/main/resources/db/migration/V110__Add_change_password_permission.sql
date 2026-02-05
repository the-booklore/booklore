ALTER TABLE user_permissions
    ADD COLUMN permission_change_password BOOLEAN NOT NULL DEFAULT TRUE;
