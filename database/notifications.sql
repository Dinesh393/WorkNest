USE worknest_db;

CREATE TABLE IF NOT EXISTS notifications (
    notification_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    project_id BIGINT NULL,
    type VARCHAR(40) NOT NULL,
    title VARCHAR(140) NOT NULL,
    message VARCHAR(500) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at TIMESTAMP NULL DEFAULT NULL,
    PRIMARY KEY (notification_id),
    INDEX idx_notifications_user_recent (user_id, created_at, notification_id),
    INDEX idx_notifications_user_unread (user_id, read_at, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
