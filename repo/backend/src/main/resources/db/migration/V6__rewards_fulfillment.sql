-- Slice 6: rewards and fulfillment

CREATE TABLE reward_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description TEXT NULL,
    tier VARCHAR(50) NULL,
    inventory_count INT NOT NULL DEFAULT 0,
    per_user_limit INT NOT NULL DEFAULT 1,
    fulfillment_type VARCHAR(30) NOT NULL DEFAULT 'PHYSICAL_SHIPMENT',
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_reward_creator FOREIGN KEY (created_by) REFERENCES account(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE shipping_address (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id BIGINT NOT NULL,
    address_line1_encrypted TEXT NOT NULL,
    address_line2_encrypted TEXT NULL,
    city VARCHAR(100) NOT NULL,
    state_code VARCHAR(2) NOT NULL,
    zip_code VARCHAR(10) NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_addr_account FOREIGN KEY (account_id) REFERENCES account(id),
    INDEX idx_addr_account (account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE reward_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    reward_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    quantity INT NOT NULL DEFAULT 1,
    fulfillment_type VARCHAR(30) NOT NULL,
    shipping_address_id BIGINT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ORDERED',
    tracking_number VARCHAR(100) NULL,
    voucher_code VARCHAR(100) NULL,
    note TEXT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_order_reward FOREIGN KEY (reward_id) REFERENCES reward_item(id),
    CONSTRAINT fk_order_account FOREIGN KEY (account_id) REFERENCES account(id),
    CONSTRAINT fk_order_address FOREIGN KEY (shipping_address_id) REFERENCES shipping_address(id),
    INDEX idx_order_account (account_id),
    INDEX idx_order_status (status),
    INDEX idx_order_reward (reward_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE fulfillment_exception (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    reason_code VARCHAR(100) NOT NULL,
    description TEXT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    supervisor_approval BOOLEAN NOT NULL DEFAULT FALSE,
    reopen_reason TEXT NULL,
    created_by BIGINT NOT NULL,
    resolved_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_exception_order FOREIGN KEY (order_id) REFERENCES reward_order(id),
    CONSTRAINT fk_exception_creator FOREIGN KEY (created_by) REFERENCES account(id),
    INDEX idx_exception_order (order_id),
    INDEX idx_exception_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
