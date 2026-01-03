ALTER TABLE epub_viewer_preference ADD COLUMN custom_font_id BIGINT NULL;
ALTER TABLE epub_viewer_preference ADD FOREIGN KEY (custom_font_id) REFERENCES custom_font(id) ON DELETE SET NULL;
