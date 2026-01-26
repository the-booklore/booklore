ALTER TABLE library
    ADD COLUMN IF NOT EXISTS preferred_book_format_order TEXT;
