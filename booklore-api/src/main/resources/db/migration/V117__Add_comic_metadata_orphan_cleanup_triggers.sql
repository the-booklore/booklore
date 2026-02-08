CREATE TRIGGER trg_cleanup_orphaned_comic_character
    AFTER DELETE
    ON comic_metadata_character_mapping
    FOR EACH ROW
    DELETE
    FROM comic_character
    WHERE id = OLD.character_id
      AND NOT EXISTS (SELECT 1 FROM comic_metadata_character_mapping WHERE character_id = OLD.character_id);

CREATE TRIGGER trg_cleanup_orphaned_comic_team
    AFTER DELETE
    ON comic_metadata_team_mapping
    FOR EACH ROW
    DELETE
    FROM comic_team
    WHERE id = OLD.team_id
      AND NOT EXISTS (SELECT 1 FROM comic_metadata_team_mapping WHERE team_id = OLD.team_id);

CREATE TRIGGER trg_cleanup_orphaned_comic_location
    AFTER DELETE
    ON comic_metadata_location_mapping
    FOR EACH ROW
    DELETE
    FROM comic_location
    WHERE id = OLD.location_id
      AND NOT EXISTS (SELECT 1 FROM comic_metadata_location_mapping WHERE location_id = OLD.location_id);

CREATE TRIGGER trg_cleanup_orphaned_comic_creator
    AFTER DELETE
    ON comic_metadata_creator_mapping
    FOR EACH ROW
    DELETE
    FROM comic_creator
    WHERE id = OLD.creator_id
      AND NOT EXISTS (SELECT 1 FROM comic_metadata_creator_mapping WHERE creator_id = OLD.creator_id);
