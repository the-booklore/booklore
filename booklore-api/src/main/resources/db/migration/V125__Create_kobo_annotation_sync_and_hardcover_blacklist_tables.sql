CREATE TABLE kobo_annotation_sync (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id              BIGINT       NOT NULL,
    annotation_id        VARCHAR(255) NOT NULL,
    book_id              BIGINT       NOT NULL,
    synced_to_hardcover  BOOLEAN      NOT NULL DEFAULT FALSE,
    hardcover_journal_id INT          NULL,
    highlighted_text     TEXT         NULL,
    note_text            TEXT         NULL,
    highlight_color      VARCHAR(50)  NULL,
    version              BIGINT       NOT NULL DEFAULT 0,
    created_at           DATETIME     NOT NULL,
    updated_at           DATETIME     NOT NULL,
    CONSTRAINT uq_kobo_annotation_user_annotation UNIQUE (user_id, annotation_id),
    CONSTRAINT fk_kobo_annotation_sync_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_kobo_annotation_sync_book FOREIGN KEY (book_id) REFERENCES book(id) ON DELETE CASCADE
);

CREATE INDEX idx_kobo_annotation_sync_user_id ON kobo_annotation_sync(user_id);
CREATE INDEX idx_kobo_annotation_sync_book_id ON kobo_annotation_sync(book_id);
CREATE INDEX idx_kobo_annotation_sync_user_book ON kobo_annotation_sync(user_id, book_id);
