CREATE TABLE IF NOT EXISTS reading_session
(
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id          BIGINT       NOT NULL,
    book_id          BIGINT       NOT NULL,
    source           VARCHAR(100) NOT NULL,
    page_number      INT          NOT NULL,
    start_time       TIMESTAMP    NOT NULL,
    duration_seconds INT          NOT NULL,
    total_pages      INT          NOT NULL,
    created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_rs_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_rs_book FOREIGN KEY (book_id) REFERENCES book (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_rs_user_book ON reading_session (user_id, book_id);
CREATE INDEX IF NOT EXISTS idx_rs_source ON reading_session (source);
CREATE INDEX IF NOT EXISTS idx_rs_start_time ON reading_session (start_time);
CREATE INDEX IF NOT EXISTS idx_rs_user_start_time ON reading_session (user_id, start_time);
CREATE INDEX IF NOT EXISTS idx_rs_book_start_time ON reading_session (book_id, start_time);
