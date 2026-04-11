-- Slice 5: resources and usage policies

CREATE TABLE usage_policy (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    scope VARCHAR(30) NOT NULL,
    max_actions INT NOT NULL,
    window_days INT NOT NULL,
    resource_action VARCHAR(30) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE resource_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    type VARCHAR(30) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT NULL,
    inventory_count INT NULL,
    file_version VARCHAR(50) NULL,
    usage_policy_id BIGINT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PUBLISHED',
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_resource_policy FOREIGN KEY (usage_policy_id) REFERENCES usage_policy(id),
    CONSTRAINT fk_resource_creator FOREIGN KEY (created_by) REFERENCES account(id),
    INDEX idx_resource_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE claim_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    resource_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    household_key VARCHAR(255) NULL,
    result VARCHAR(30) NOT NULL,
    reason_code VARCHAR(100) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_claim_resource FOREIGN KEY (resource_id) REFERENCES resource_item(id),
    CONSTRAINT fk_claim_account FOREIGN KEY (account_id) REFERENCES account(id),
    INDEX idx_claim_resource (resource_id),
    INDEX idx_claim_account (account_id),
    INDEX idx_claim_household (household_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE download_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    resource_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    file_version VARCHAR(50) NULL,
    result VARCHAR(30) NOT NULL,
    reason_code VARCHAR(100) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_download_resource FOREIGN KEY (resource_id) REFERENCES resource_item(id),
    CONSTRAINT fk_download_account FOREIGN KEY (account_id) REFERENCES account(id),
    INDEX idx_download_resource (resource_id),
    INDEX idx_download_account (account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE printable_notice (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT NOT NULL,
    resource_id BIGINT NULL,
    notice_type VARCHAR(30) NOT NULL,
    content TEXT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notice_account FOREIGN KEY (account_id) REFERENCES account(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
