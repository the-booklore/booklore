ALTER TABLE public_book_review 
    CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

ALTER TABLE public_book_review 
    MODIFY COLUMN body TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    MODIFY COLUMN reviewer_name VARCHAR(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    MODIFY COLUMN title VARCHAR(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    MODIFY COLUMN country VARCHAR(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

ALTER TABLE book_notes 
    CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

ALTER TABLE book_notes 
    MODIFY COLUMN title VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    MODIFY COLUMN content TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

ALTER TABLE book_metadata 
    CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

ALTER TABLE book_metadata 
    MODIFY COLUMN title VARCHAR(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    MODIFY COLUMN subtitle VARCHAR(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    MODIFY COLUMN publisher VARCHAR(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    MODIFY COLUMN description TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    MODIFY COLUMN series_name VARCHAR(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

ALTER TABLE author 
    CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

ALTER TABLE author 
    MODIFY COLUMN name VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

ALTER TABLE category 
    CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

ALTER TABLE category 
    MODIFY COLUMN name VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

ALTER TABLE users 
    CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

ALTER TABLE users 
    MODIFY COLUMN username VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    MODIFY COLUMN name VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    MODIFY COLUMN email VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    MODIFY COLUMN book_preferences TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

ALTER TABLE library 
    CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

ALTER TABLE library 
    MODIFY COLUMN name VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    MODIFY COLUMN sort VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

ALTER TABLE shelf 
    CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

ALTER TABLE shelf 
    MODIFY COLUMN name VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    MODIFY COLUMN sort VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

ALTER TABLE app_settings 
    CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

ALTER TABLE app_settings 
    MODIFY COLUMN category VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    MODIFY COLUMN name VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci,
    MODIFY COLUMN val TEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;