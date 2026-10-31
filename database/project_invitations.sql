-- Stores direct project offers sent by clients to freelancers.
-- A freelancer must accept before a project can move to IN_PROGRESS.
USE worknest_db;

CREATE TABLE IF NOT EXISTS project_invitations (
    invitation_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT NOT NULL,
    client_id BIGINT NOT NULL,
    freelancer_id BIGINT NOT NULL,
    offered_amount DECIMAL(12, 2) NOT NULL,
    client_message VARCHAR(1000) NOT NULL DEFAULT '',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_invitation_freelancer_status (freelancer_id, status),
    INDEX idx_invitation_project_status (project_id, status),
    INDEX idx_invitation_client_status (client_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
