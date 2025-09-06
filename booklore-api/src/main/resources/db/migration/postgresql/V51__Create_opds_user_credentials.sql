CREATE TABLE IF NOT EXISTS opds_user_v2
(
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT       NOT NULL,
    username      VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_opds_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uq_userid_username UNIQUE (user_id, username)
);

ALTER TABLE user_permissions
    ADD COLUMN IF NOT EXISTS permission_access_opds BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE user_permissions
SET permission_access_opds = TRUE
WHERE permission_admin = TRUE;

-- Trigger to call the function before update
CREATE TRIGGER trg_update_opds_user_v2_updated_at
BEFORE UPDATE ON opds_user_v2
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();