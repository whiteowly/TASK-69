-- Slice 2: auth security and account lifecycle

CREATE TABLE blacklist_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT NOT NULL,
    reason_code VARCHAR(100) NOT NULL,
    note TEXT NULL,
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_blacklist_account FOREIGN KEY (account_id) REFERENCES account(id),
    CONSTRAINT fk_blacklist_creator FOREIGN KEY (created_by) REFERENCES account(id),
    INDEX idx_blacklist_account (account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE blacklist_appeal (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    blacklist_record_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    appeal_text TEXT NOT NULL,
    contact_note TEXT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    due_date DATE NOT NULL,
    decision_note TEXT NULL,
    decided_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    decided_at DATETIME NULL,
    CONSTRAINT fk_appeal_blacklist FOREIGN KEY (blacklist_record_id) REFERENCES blacklist_record(id),
    CONSTRAINT fk_appeal_account FOREIGN KEY (account_id) REFERENCES account(id),
    INDEX idx_appeal_account (account_id),
    INDEX idx_appeal_status (status),
    INDEX idx_appeal_due (due_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE password_reset (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    target_account_id BIGINT NOT NULL,
    identity_review_note TEXT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ISSUED',
    temporary_secret VARCHAR(255) NOT NULL,
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_pwdreset_target FOREIGN KEY (target_account_id) REFERENCES account(id),
    CONSTRAINT fk_pwdreset_creator FOREIGN KEY (created_by) REFERENCES account(id),
    INDEX idx_pwdreset_target (target_account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
