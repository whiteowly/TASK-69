-- Slice 3: verification and role approvals

CREATE TABLE person_verification (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT NOT NULL,
    legal_name VARCHAR(255) NOT NULL,
    dob_encrypted TEXT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'UNDER_REVIEW',
    review_note TEXT NULL,
    reviewed_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_personverif_account FOREIGN KEY (account_id) REFERENCES account(id),
    INDEX idx_personverif_account (account_id),
    INDEX idx_personverif_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE organization_credential_document (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    checksum VARCHAR(128) NOT NULL,
    duplicate_flag BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(30) NOT NULL DEFAULT 'UNDER_REVIEW',
    review_note TEXT NULL,
    reviewed_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_orgcred_account FOREIGN KEY (account_id) REFERENCES account(id),
    INDEX idx_orgcred_account (account_id),
    INDEX idx_orgcred_status (status),
    INDEX idx_orgcred_checksum (checksum)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
