-- Add organization_mode column to library table
-- Determines how book files are grouped: BOOK_PER_FOLDER or AUTO_DETECT
ALTER TABLE library ADD COLUMN organization_mode VARCHAR(50) DEFAULT 'AUTO_DETECT';
