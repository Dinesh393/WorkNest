-- Core WorkNest schema for a fresh local database.
-- This script creates tables only and inserts no account or demo data.

CREATE DATABASE IF NOT EXISTS worknest_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE worknest_db;

CREATE TABLE IF NOT EXISTS users (
    user_id BIGINT NOT NULL AUTO_INCREMENT,
    full_name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id),
    UNIQUE KEY uq_users_email (email),
    CONSTRAINT chk_users_role CHECK (role IN ('CLIENT', 'FREELANCER'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS projects (
    project_id BIGINT NOT NULL AUTO_INCREMENT,
    client_id BIGINT NOT NULL,
    title VARCHAR(150) NOT NULL,
    description TEXT NOT NULL,
    required_skills VARCHAR(500) NOT NULL,
    project_type VARCHAR(20) NOT NULL,
    budget_min DECIMAL(12, 2) NOT NULL,
    budget_max DECIMAL(12, 2) NOT NULL,
    deadline DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (project_id),
    KEY idx_projects_client_created (client_id, created_at),
    KEY idx_projects_status_deadline (status, deadline),
    CONSTRAINT fk_projects_client
        FOREIGN KEY (client_id) REFERENCES users (user_id),
    CONSTRAINT chk_projects_type CHECK (project_type IN ('FIXED', 'HOURLY')),
    CONSTRAINT chk_projects_budget CHECK (budget_min > 0 AND budget_max >= budget_min),
    CONSTRAINT chk_projects_status CHECK (
        status IN ('OPEN', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS proposals (
    proposal_id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    freelancer_id BIGINT NOT NULL,
    cover_letter TEXT NOT NULL,
    proposed_amount DECIMAL(12, 2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (proposal_id),
    UNIQUE KEY uq_proposals_project_freelancer (project_id, freelancer_id),
    KEY idx_proposals_freelancer_created (freelancer_id, created_at),
    KEY idx_proposals_project_status (project_id, status),
    CONSTRAINT fk_proposals_project
        FOREIGN KEY (project_id) REFERENCES projects (project_id),
    CONSTRAINT fk_proposals_freelancer
        FOREIGN KEY (freelancer_id) REFERENCES users (user_id),
    CONSTRAINT chk_proposals_amount CHECK (proposed_amount > 0),
    CONSTRAINT chk_proposals_status CHECK (
        status IN ('PENDING', 'ACCEPTED', 'REJECTED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS project_updates (
    update_id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    freelancer_id BIGINT NOT NULL,
    update_note TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (update_id),
    KEY idx_project_updates_project_created (project_id, created_at),
    CONSTRAINT fk_project_updates_project
        FOREIGN KEY (project_id) REFERENCES projects (project_id),
    CONSTRAINT fk_project_updates_freelancer
        FOREIGN KEY (freelancer_id) REFERENCES users (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS project_submissions (
    submission_id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    freelancer_id BIGINT NOT NULL,
    submission_note TEXT NOT NULL,
    delivery_url VARCHAR(2048) NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'SUBMITTED',
    client_feedback TEXT NULL,
    submitted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMP NULL DEFAULT NULL,
    PRIMARY KEY (submission_id),
    KEY idx_project_submissions_project (project_id, submission_id),
    CONSTRAINT fk_project_submissions_project
        FOREIGN KEY (project_id) REFERENCES projects (project_id),
    CONSTRAINT fk_project_submissions_freelancer
        FOREIGN KEY (freelancer_id) REFERENCES users (user_id),
    CONSTRAINT chk_project_submissions_status CHECK (
        status IN ('SUBMITTED', 'APPROVED', 'REVISION_REQUESTED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
