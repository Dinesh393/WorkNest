-- Run once against an existing WorkNest database to enable client organization profiles.
USE worknest_db;

CREATE TABLE IF NOT EXISTS client_profiles (
    client_id BIGINT NOT NULL,
    organization_name VARCHAR(160) NOT NULL,
    organization_type VARCHAR(50) NOT NULL,
    industry VARCHAR(80) NOT NULL,
    website VARCHAR(255) NULL,
    country VARCHAR(100) NOT NULL,
    city VARCHAR(100) NOT NULL,
    business_description TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (client_id),
    CONSTRAINT fk_client_profiles_user FOREIGN KEY (client_id)
        REFERENCES users (user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
