-- Migrate file specific data from book to book_file

ALTER TABLE book_additional_file RENAME TO book_file;
ALTER TABLE book_file ADD COLUMN is_book boolean DEFAULT false;

UPDATE book_file SET is_book = true WHERE additional_file_type = 'ALTERNATIVE_FORMAT';

ALTER TABLE book_file ADD column book_type varchar(32);
ALTER TABLE book_file DROP COLUMN alt_format_current_hash;
ALTER TABLE book_file DROP COLUMN additional_file_type;

-- Insert data from the book table
INSERT INTO book_file (book_id, file_name, file_sub_path, is_book, book_type, file_size_kb, initial_hash, added_on, current_hash)
SELECT id, file_name, file_sub_path, true, CASE when book_type = 0 then 'PDF' when book_type = 1 then 'EPUB' when book_type = 2 then 'CBX' end, file_size_kb, initial_hash, added_on, current_hash FROM book;

-- Regenerate virtual column for alternative book format files
ALTER TABLE book_file ADD COLUMN alt_format_current_hash VARCHAR(128) AS (CASE WHEN is_book = true THEN current_hash END) STORED;

-- Remove constraing
ALTER TABLE book DROP CONSTRAINT unique_library_file_path;

-- Remove migrated fields from the book table
ALTER TABLE book DROP COLUMN file_name;
ALTER TABLE book DROP COLUMN file_sub_path;
ALTER TABLE book DROP COLUMN book_type;
ALTER TABLE book DROP COLUMN file_size_kb;
ALTER TABLE book DROP COLUMN initial_hash;
ALTER TABLE book DROP COLUMN current_hash;