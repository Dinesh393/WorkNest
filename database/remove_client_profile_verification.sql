-- Run once against worknest_db if client_profiles was created with verification columns.
-- This removes the old verification status and review metadata.
USE worknest_db;

ALTER TABLE client_profiles
    DROP COLUMN verification_status,
    DROP COLUMN verification_note,
    DROP COLUMN verification_requested_at,
    DROP COLUMN verification_reviewed_at;
