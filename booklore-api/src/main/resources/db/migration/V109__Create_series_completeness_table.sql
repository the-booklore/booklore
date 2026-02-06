-- Create series_completeness table to track incomplete series for better performance
-- This table stores pre-calculated series completeness information
CREATE TABLE series_completeness (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    library_id BIGINT NOT NULL,
    series_name VARCHAR(500) NOT NULL,
    series_name_normalized VARCHAR(500) NOT NULL, -- Lowercase trimmed version for lookups
    book_count INT NOT NULL DEFAULT 0,
    min_series_number DOUBLE,
    max_series_number DOUBLE,
    is_complete BOOLEAN NOT NULL DEFAULT FALSE,
    is_incomplete BOOLEAN NOT NULL DEFAULT FALSE, -- Denormalized for query performance
    last_calculated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_series_completeness_library FOREIGN KEY (library_id) REFERENCES library(id) ON DELETE CASCADE,
    CONSTRAINT uk_series_completeness_library_series UNIQUE (library_id, series_name_normalized),
    INDEX idx_series_completeness_incomplete (is_incomplete),
    INDEX idx_series_completeness_library (library_id),
    INDEX idx_series_completeness_series_name (series_name_normalized),
    INDEX idx_series_completeness_last_calculated (last_calculated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- Add index on book_metadata for series queries
-- Use prefix index (255 chars) to avoid exceeding MariaDB's max key length (3072 bytes) with utf8mb4
CREATE INDEX IF NOT EXISTS idx_book_metadata_series ON book_metadata(series_name(255), series_number);

-- Create task cron configuration for series completeness calculation
INSERT INTO task_cron_configuration (task_type, cron_expression, enabled, created_by)
VALUES ('CALCULATE_SERIES_COMPLETENESS', '0 0 2 * * ?', TRUE, -1)
ON DUPLICATE KEY UPDATE updated_at = NOW();
