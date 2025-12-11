ALTER TABLE user_book_progress ADD COLUMN IF NOT EXISTS personal_rating TINYINT;

-- Copies existing personal ratings to all users with progress records for matching books
UPDATE user_book_progress ubp
JOIN book_metadata bm ON ubp.book_id = bm.book_id
SET ubp.personal_rating = bm.personal_rating
WHERE bm.personal_rating IS NOT NULL;

-- Inserts new progress records for books with existing ratings but no matching progress records
INSERT INTO user_book_progress (user_id, book_id, personal_rating)
SELECT u.id, bm.book_id, bm.personal_rating
FROM users u
INNER JOIN book_metadata bm
LEFT JOIN user_book_progress ubp 
       ON ubp.book_id = bm.book_id
      AND ubp.user_id = u.id
WHERE bm.personal_rating IS NOT NULL
  AND ubp.book_id IS NULL;

-- Drops obsolete columns
ALTER TABLE book_metadata DROP COLUMN personal_rating;
ALTER TABLE book_metadata DROP COLUMN personal_rating_locked;