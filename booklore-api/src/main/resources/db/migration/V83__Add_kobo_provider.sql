-- Add Kobo metadata identifier and lock columns
ALTER TABLE book_metadata
    ADD COLUMN IF NOT EXISTS kobo_id VARCHAR(150);

ALTER TABLE book_metadata
    ADD COLUMN IF NOT EXISTS kobo_id_locked BOOLEAN DEFAULT FALSE;
