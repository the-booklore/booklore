-- Migrate file specific data from book to book_file
RENAME TABLE book_additional_file TO book_file_old;
CREATE TABLE book_file LIKE book_file_old;
 ALTER TABLE book_file
 ADD CONSTRAINT fk_book_file_book
 FOREIGN KEY (book_id) REFERENCES book (id) ON DELETE CASCADE;
ALTER TABLE book_file ADD COLUMN is_book boolean DEFAULT false;
ALTER TABLE book_file ADD COLUMN book_type varchar(32);
ALTER TABLE book_file ADD COLUMN archive_type VARCHAR(255);

-- Drop the column before importing the data to avoid duplicate index errors
ALTER TABLE book_file DROP COLUMN alt_format_current_hash;

INSERT INTO book_file (book_id, file_name, file_sub_path, is_book, book_type, archive_type, file_size_kb, initial_hash, added_on, current_hash)
SELECT id, file_name, file_sub_path, true, CASE when book_type = 0 then 'PDF' when book_type = 1 then 'EPUB' when book_type = 2 then 'CBX' end, archive_type, file_size_kb, initial_hash, added_on, current_hash FROM book;

INSERT INTO book_file (book_id, file_name, file_sub_path, file_size_kb, initial_hash, current_hash, description, added_on, additional_file_type)
SELECT book_id, file_name, file_sub_path, file_size_kb, initial_hash, current_hash, description, added_on, additional_file_type FROM book_file_old;

UPDATE book_file SET is_book = true WHERE additional_file_type = 'ALTERNATIVE_FORMAT';
ALTER TABLE book_file DROP COLUMN additional_file_type;

-- Set book_type for existing book files
UPDATE book_file
SET book_type = CASE
    WHEN LOWER(file_name) LIKE '%.epub' THEN 'EPUB'
    WHEN LOWER(file_name) LIKE '%.pdf'  THEN 'PDF'
    WHEN LOWER(file_name) LIKE '%.cbz'  THEN 'CBX'
    WHEN LOWER(file_name) LIKE '%.cbr'  THEN 'CBX'
    WHEN LOWER(file_name) LIKE '%.cb7'  THEN 'CBX'
    WHEN LOWER(file_name) LIKE '%.fb2'  THEN 'FB2'
    ELSE book_type
END
WHERE is_book = 1
  AND book_type IS NULL;


-- Prevent duplicates of book files (is_book=true) by (library_id, library_path_id, file_sub_path, file_name)
-- MariaDB/MySQL do not support UNIQUE constraints across multiple tables, so we need to enforce via triggers.
DELIMITER $$
CREATE OR REPLACE PROCEDURE assert_no_duplicate_book_file(
    IN p_book_file_id BIGINT,
    IN p_book_id BIGINT,
    IN p_file_name VARCHAR(1000),
    IN p_file_sub_path VARCHAR(512),
    IN p_is_book BOOLEAN
)
BEGIN
    DECLARE v_library_id BIGINT;
    DECLARE v_library_path_id BIGINT;

    IF p_is_book = true THEN
        SELECT b.library_id, b.library_path_id
        INTO v_library_id, v_library_path_id
        FROM book b
        WHERE b.id = p_book_id;

        IF v_library_id IS NOT NULL AND EXISTS (
            SELECT 1
            FROM book_file bf
            INNER JOIN book b2 ON b2.id = bf.book_id
            WHERE bf.is_book = true
              AND bf.file_name = p_file_name
              AND bf.file_sub_path = p_file_sub_path
              AND b2.library_id = v_library_id
              AND b2.library_path_id = v_library_path_id
              AND (p_book_file_id IS NULL OR bf.id <> p_book_file_id)
            LIMIT 1
        ) THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'Duplicate book file detected for library/path/subpath/name';
        END IF;
    END IF;
END$$

CREATE OR REPLACE TRIGGER trg_book_file_prevent_duplicate_book_insert
BEFORE INSERT ON book_file
FOR EACH ROW
BEGIN
    CALL assert_no_duplicate_book_file(NULL, NEW.book_id, NEW.file_name, NEW.file_sub_path, NEW.is_book);
END$$

CREATE OR REPLACE TRIGGER trg_book_file_prevent_duplicate_book_update
BEFORE UPDATE ON book_file
FOR EACH ROW
BEGIN
    CALL assert_no_duplicate_book_file(OLD.id, NEW.book_id, NEW.file_name, NEW.file_sub_path, NEW.is_book);
END$$
DELIMITER ;

-- Regenerate virtual column for alternative book format files, create the index without UNIQUE constraint
ALTER TABLE book_file ADD COLUMN alt_format_current_hash VARCHAR(128) AS (CASE WHEN is_book = true THEN current_hash END) STORED;
ALTER TABLE book_file ADD INDEX idx_book_file_current_hash_alt_format (alt_format_current_hash);

-- Remove constraint from book table
ALTER TABLE book DROP INDEX IF EXISTS unique_library_file_path;

-- Remove migrated fields from the book table
ALTER TABLE book DROP COLUMN file_name;
ALTER TABLE book DROP COLUMN file_sub_path;
ALTER TABLE book DROP COLUMN book_type;
ALTER TABLE book DROP COLUMN file_size_kb;
ALTER TABLE book DROP COLUMN initial_hash;
ALTER TABLE book DROP COLUMN current_hash;
ALTER TABLE book DROP COLUMN archive_type;

DROP TABLE book_file_old;