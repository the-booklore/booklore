ALTER TABLE book_metadata
    ADD COLUMN ranobedb_id VARCHAR(100),
    ADD COLUMN ranobedb_rating FLOAT,
    ADD COLUMN ranobedb_review_count INT,
    ADD COLUMN ranobedb_id_locked BOOLEAN DEFAULT FALSE,
    ADD COLUMN ranobedb_rating_locked BOOLEAN DEFAULT FALSE,
    ADD COLUMN ranobedb_review_count_locked BOOLEAN DEFAULT FALSE;
