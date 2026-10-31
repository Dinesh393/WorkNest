USE worknest_db;

CREATE TABLE IF NOT EXISTS freelancer_profiles (
    user_id BIGINT NOT NULL,
    headline VARCHAR(120) NOT NULL DEFAULT '',
    overview TEXT NOT NULL,
    skills VARCHAR(1000) NOT NULL DEFAULT '',
    experience_years TINYINT UNSIGNED NULL,
    experience TEXT NOT NULL,
    education TEXT NOT NULL,
    certifications TEXT NOT NULL,
    languages VARCHAR(500) NOT NULL DEFAULT '',
    location VARCHAR(120) NOT NULL DEFAULT '',
    availability VARCHAR(20) NOT NULL DEFAULT '',
    phone VARCHAR(30) NOT NULL DEFAULT '',
    hourly_rate DECIMAL(10, 2) NULL,
    portfolio_url VARCHAR(2048) NULL,
    linkedin_url VARCHAR(2048) NULL,
    github_url VARCHAR(2048) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
