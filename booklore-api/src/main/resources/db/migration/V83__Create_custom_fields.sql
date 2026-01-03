CREATE TABLE IF NOT EXISTS library_custom_field
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    library_id    BIGINT NOT NULL,
    name          VARCHAR(128) NOT NULL,
    field_type    VARCHAR(16) NOT NULL,
    default_value TEXT,

    CONSTRAINT fk_library_custom_field_library FOREIGN KEY (library_id) REFERENCES library (id) ON DELETE CASCADE,
    CONSTRAINT unique_library_custom_field_name UNIQUE (library_id, name)
);

CREATE INDEX idx_library_custom_field_library_id ON library_custom_field (library_id);


CREATE TABLE IF NOT EXISTS book_custom_field_value
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    book_id         BIGINT NOT NULL,
    custom_field_id BIGINT NOT NULL,
    value_string    TEXT,
    value_number    DOUBLE,
    value_date      DATE,

    CONSTRAINT fk_book_custom_field_value_book FOREIGN KEY (book_id) REFERENCES book (id) ON DELETE CASCADE,
    CONSTRAINT fk_book_custom_field_value_custom_field FOREIGN KEY (custom_field_id) REFERENCES library_custom_field (id) ON DELETE CASCADE,
    CONSTRAINT unique_book_custom_field UNIQUE (book_id, custom_field_id)
);

CREATE INDEX idx_book_custom_field_value_book_id ON book_custom_field_value (book_id);
CREATE INDEX idx_book_custom_field_value_custom_field_id ON book_custom_field_value (custom_field_id);
