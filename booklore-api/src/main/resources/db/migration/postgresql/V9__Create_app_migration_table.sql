CREATE TABLE app_migration
(
    migration_key VARCHAR(100) PRIMARY KEY,
    executed_at   TIMESTAMP NOT NULL,
    description   TEXT
);

COMMENT ON TABLE app_migration IS 'Tracks one-time application-level data migrations';
COMMENT ON COLUMN app_migration.migration_key IS 'Unique identifier for the migration';
COMMENT ON COLUMN app_migration.executed_at IS 'When the migration was executed';
COMMENT ON COLUMN app_migration.description IS 'Optional description of what the migration did';
