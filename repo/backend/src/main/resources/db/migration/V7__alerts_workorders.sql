-- Slice 7: alerts and work orders

CREATE TABLE alert_rule_default (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    alert_type VARCHAR(50) NOT NULL,
    severity VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    threshold_operator VARCHAR(10) NOT NULL,
    threshold_value DOUBLE NOT NULL,
    threshold_unit VARCHAR(20) NULL,
    duration_seconds INT NOT NULL DEFAULT 0,
    cooldown_seconds INT NOT NULL DEFAULT 900,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_alert_type (alert_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE alert_rule_override (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    alert_type VARCHAR(50) NOT NULL,
    scope_type VARCHAR(30) NOT NULL,
    scope_id VARCHAR(100) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    threshold_operator VARCHAR(10) NOT NULL,
    threshold_value DOUBLE NOT NULL,
    threshold_unit VARCHAR(20) NULL,
    duration_seconds INT NOT NULL DEFAULT 0,
    cooldown_seconds INT NOT NULL DEFAULT 900,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_override (alert_type, scope_type, scope_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE alert_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    alert_type VARCHAR(50) NOT NULL,
    scope_type VARCHAR(30) NULL,
    scope_id VARCHAR(100) NULL,
    severity VARCHAR(20) NOT NULL,
    measured_value DOUBLE NOT NULL,
    measured_unit VARCHAR(20) NULL,
    suppressed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_alert_type (alert_type),
    INDEX idx_alert_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE work_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    alert_event_id BIGINT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT NULL,
    severity VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
    status VARCHAR(30) NOT NULL DEFAULT 'NEW_ALERT',
    assigned_to BIGINT NULL,
    first_response_at DATETIME NULL,
    closed_at DATETIME NULL,
    created_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_wo_alert FOREIGN KEY (alert_event_id) REFERENCES alert_event(id),
    CONSTRAINT fk_wo_assignee FOREIGN KEY (assigned_to) REFERENCES account(id),
    CONSTRAINT fk_wo_creator FOREIGN KEY (created_by) REFERENCES account(id),
    INDEX idx_wo_status (status),
    INDEX idx_wo_severity (severity)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE work_order_note (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    work_order_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_wonote_wo FOREIGN KEY (work_order_id) REFERENCES work_order(id),
    CONSTRAINT fk_wonote_author FOREIGN KEY (author_id) REFERENCES account(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE work_order_photo (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    work_order_id BIGINT NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    uploaded_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_wophoto_wo FOREIGN KEY (work_order_id) REFERENCES work_order(id),
    CONSTRAINT fk_wophoto_uploader FOREIGN KEY (uploaded_by) REFERENCES account(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE post_incident_review (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    work_order_id BIGINT NOT NULL,
    summary TEXT NOT NULL,
    lessons_learned TEXT NULL,
    corrective_actions TEXT NULL,
    reviewed_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_pir_wo FOREIGN KEY (work_order_id) REFERENCES work_order(id),
    CONSTRAINT fk_pir_reviewer FOREIGN KEY (reviewed_by) REFERENCES account(id),
    UNIQUE KEY uk_pir_wo (work_order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
