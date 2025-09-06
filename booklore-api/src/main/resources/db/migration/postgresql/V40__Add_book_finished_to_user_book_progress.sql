ALTER TABLE "user_book_progress"
ADD COLUMN "date_finished" timestamp NULL DEFAULT NULL;
CREATE INDEX IF NOT EXISTS "idx_user_book_progress_date_finished" ON "user_book_progress"("date_finished");