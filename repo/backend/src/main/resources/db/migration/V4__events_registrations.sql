-- Slice 4: events and registrations

CREATE TABLE event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    organization_id VARCHAR(100) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT NULL,
    mode VARCHAR(20) NOT NULL DEFAULT 'ON_SITE',
    location VARCHAR(255) NULL,
    start_at DATETIME NOT NULL,
    end_at DATETIME NOT NULL,
    capacity INT NOT NULL DEFAULT 50,
    waitlist_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    manual_review_required BOOLEAN NOT NULL DEFAULT FALSE,
    registration_form_schema TEXT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_event_creator FOREIGN KEY (created_by) REFERENCES account(id),
    INDEX idx_event_org (organization_id),
    INDEX idx_event_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE event_registration (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'SUBMITTED',
    form_responses TEXT NULL,
    review_note TEXT NULL,
    reviewed_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_reg_event FOREIGN KEY (event_id) REFERENCES event(id),
    CONSTRAINT fk_reg_account FOREIGN KEY (account_id) REFERENCES account(id),
    INDEX idx_reg_event (event_id),
    INDEX idx_reg_account (account_id),
    INDEX idx_reg_status (status),
    INDEX idx_reg_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
