CREATE TABLE bookdrop_file
(
    id                BIGSERIAL PRIMARY KEY,
    file_path         TEXT         NOT NULL,
    file_name         VARCHAR(512) NOT NULL,
    file_size         BIGINT,
    status            VARCHAR(20)  NOT NULL DEFAULT 'PENDING_REVIEW',
    original_metadata JSON,
    fetched_metadata  JSON,
    created_at        TIMESTAMP             DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP             DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uq_file_path ON bookdrop_file(LEFT(file_path, 255));

-- Trigger to call the function before update
CREATE TRIGGER trg_update_bookdrop_file_updated_at
BEFORE UPDATE ON bookdrop_file
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();