ALTER TABLE book ADD COLUMN purchase_date DATETIME(6) NULL;
UPDATE book SET purchase_date = added_on WHERE purchase_date IS NULL;
