BEGIN;

INSERT INTO user_settings (user_id, setting_key, setting_value)
SELECT
    id AS user_id,
    'perBookSetting' AS setting_key,
    (book_preferences::jsonb -> 'perBookSetting') AS setting_value
FROM users
WHERE (book_preferences::jsonb -> 'perBookSetting') IS NOT NULL
  AND (book_preferences::jsonb ->> 'perBookSetting') != '{}'
  AND (book_preferences::jsonb ->> 'perBookSetting') != ''
ON CONFLICT (user_id, setting_key) DO NOTHING;

INSERT INTO user_settings (user_id, setting_key, setting_value)
SELECT
    id AS user_id,
    'pdfReaderSetting' AS setting_key,
    (book_preferences::jsonb -> 'pdfReaderSetting') AS setting_value
FROM users
WHERE (book_preferences::jsonb -> 'pdfReaderSetting') IS NOT NULL
  AND (book_preferences::jsonb ->> 'pdfReaderSetting') != '{}'
  AND (book_preferences::jsonb ->> 'pdfReaderSetting') != ''
ON CONFLICT (user_id, setting_key) DO NOTHING;

INSERT INTO user_settings (user_id, setting_key, setting_value)
SELECT
    id AS user_id,
    'epubReaderSetting' AS setting_key,
    (book_preferences::jsonb -> 'epubReaderSetting') AS setting_value
FROM users
WHERE (book_preferences::jsonb -> 'epubReaderSetting') IS NOT NULL
  AND (book_preferences::jsonb ->> 'epubReaderSetting') != '{}'
  AND (book_preferences::jsonb ->> 'epubReaderSetting') != ''
ON CONFLICT (user_id, setting_key) DO NOTHING;

COMMIT;