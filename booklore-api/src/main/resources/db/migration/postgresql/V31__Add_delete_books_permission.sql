ALTER TABLE user_permissions
    ADD COLUMN permission_delete_book BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE user_permissions
SET permission_delete_book = TRUE
WHERE permission_admin = TRUE;