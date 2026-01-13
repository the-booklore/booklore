CREATE TABLE IF NOT EXISTS epub_viewer_preference_v2
(
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id          BIGINT      NOT NULL,
    book_id          BIGINT      NOT NULL,
    font_family      VARCHAR(128),
    font_size        INT         NOT NULL DEFAULT 16,
    gap              FLOAT       NOT NULL DEFAULT 0.05,
    hyphenate        BOOLEAN     NOT NULL DEFAULT FALSE,
    is_dark          BOOLEAN     NOT NULL DEFAULT FALSE,
    justify          BOOLEAN     NOT NULL DEFAULT FALSE,
    line_height      FLOAT       NOT NULL DEFAULT 1.5,
    max_block_size   INT         NOT NULL DEFAULT 1440,
    max_column_count INT         NOT NULL DEFAULT 2,
    max_inline_size  INT         NOT NULL DEFAULT 720,
    theme            VARCHAR(64) NOT NULL DEFAULT 'gray',
    flow             VARCHAR(32) NOT NULL DEFAULT 'Paginated',
    UNIQUE (user_id, book_id),
    CONSTRAINT fk_epub_viewer_preference_v2_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_epub_viewer_preference_v2_book FOREIGN KEY (book_id) REFERENCES book (id) ON DELETE CASCADE
);

ALTER TABLE user_book_progress
    ADD COLUMN IF NOT EXISTS epub_progress_href VARCHAR(1000);