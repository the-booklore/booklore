-- Create comic_metadata table for storing comic-specific metadata
CREATE TABLE IF NOT EXISTS comic_metadata
(
    book_id              BIGINT PRIMARY KEY,
    issue_number         VARCHAR(50),
    volume_name          VARCHAR(255),
    volume_number        INTEGER,
    story_arc            VARCHAR(255),
    story_arc_number     INTEGER,
    alternate_series     VARCHAR(255),
    alternate_issue      VARCHAR(50),
    penciller            VARCHAR(500),
    inker                VARCHAR(500),
    colorist             VARCHAR(500),
    letterer             VARCHAR(500),
    cover_artist         VARCHAR(500),
    editor               VARCHAR(500),
    imprint              VARCHAR(255),
    format               VARCHAR(50),
    black_and_white      BOOLEAN     DEFAULT FALSE,
    manga                BOOLEAN     DEFAULT FALSE,
    reading_direction    VARCHAR(10) DEFAULT 'ltr',
    characters           TEXT,
    teams                TEXT,
    locations            TEXT,
    web_link             VARCHAR(1000),
    notes                TEXT,
    issue_number_locked  BOOLEAN     DEFAULT FALSE,
    volume_name_locked   BOOLEAN     DEFAULT FALSE,
    volume_number_locked BOOLEAN     DEFAULT FALSE,
    story_arc_locked     BOOLEAN     DEFAULT FALSE,
    penciller_locked     BOOLEAN     DEFAULT FALSE,
    inker_locked         BOOLEAN     DEFAULT FALSE,
    colorist_locked      BOOLEAN     DEFAULT FALSE,
    letterer_locked      BOOLEAN     DEFAULT FALSE,
    cover_artist_locked  BOOLEAN     DEFAULT FALSE,
    editor_locked        BOOLEAN     DEFAULT FALSE,
    characters_locked    BOOLEAN     DEFAULT FALSE,
    teams_locked         BOOLEAN     DEFAULT FALSE,
    locations_locked     BOOLEAN     DEFAULT FALSE,
    CONSTRAINT fk_comic_metadata_book FOREIGN KEY (book_id) REFERENCES book_metadata (book_id) ON DELETE CASCADE
);

-- Create index for faster lookups
CREATE INDEX IF NOT EXISTS idx_comic_metadata_story_arc ON comic_metadata (story_arc);
CREATE INDEX IF NOT EXISTS idx_comic_metadata_volume_name ON comic_metadata (volume_name);
