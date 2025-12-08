CREATE TABLE book_marks
(
    id         BIGINT AUTO_INCREMENT NOT NULL,
    user_id    BIGINT                NOT NULL,
    book_id    BIGINT                NOT NULL,
    cfi        VARCHAR(1000)         NOT NULL,
    title      VARCHAR(255)          NULL,
    created_at datetime              NOT NULL,
    CONSTRAINT pk_book_marks PRIMARY KEY (id)
);

CREATE INDEX idx_book_marks_user_id ON book_marks (user_id);
CREATE INDEX idx_book_marks_book_id ON book_marks (book_id);

ALTER TABLE book_marks
    ADD CONSTRAINT fk_book_marks_user_id FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;

ALTER TABLE book_marks
    ADD CONSTRAINT fk_book_marks_book_id FOREIGN KEY (book_id) REFERENCES book (id) ON DELETE CASCADE;
