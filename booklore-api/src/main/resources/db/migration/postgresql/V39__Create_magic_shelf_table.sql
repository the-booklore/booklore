CREATE TABLE magic_shelf
(
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    name        VARCHAR(255) NOT NULL,
    icon        VARCHAR(64)  NOT NULL,
    filter_json JSON         NOT NULL,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_user_name UNIQUE (user_id, name)
);

-- Trigger to call the function before update
CREATE TRIGGER trg_update_magic_shelf_updated_at
BEFORE UPDATE ON magic_shelf
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();