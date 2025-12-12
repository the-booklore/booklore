-- Add oidc_subject column for stable OIDC user identity
-- The 'sub' claim is immutable in OIDC, unlike username/preferred_username
-- This allows users to be correctly identified even if they change their username in the IdP

ALTER TABLE users
    ADD COLUMN oidc_subject VARCHAR(512);

-- Create unique index for fast lookups and to prevent duplicate subjects
CREATE UNIQUE INDEX idx_users_oidc_subject ON users(oidc_subject);
