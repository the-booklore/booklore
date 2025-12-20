CREATE INDEX idx_bookmark_book_user_priority 
ON book_marks(book_id, user_id, priority, created_at);
