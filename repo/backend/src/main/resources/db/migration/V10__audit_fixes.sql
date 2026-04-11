-- Audit fixes: per-version download index, overdue status tracking, resource org scoping

-- Issue 3: index for per-file-version download limit enforcement
ALTER TABLE download_record ADD INDEX idx_download_version (resource_id, account_id, file_version);

-- Issue 4: organization scope for resource publishing
ALTER TABLE resource_item ADD COLUMN organization_id VARCHAR(100) NULL AFTER type;
ALTER TABLE resource_item ADD INDEX idx_resource_org (organization_id);

-- Issue 6: track when status last changed for overdue detection
ALTER TABLE reward_order ADD COLUMN status_changed_at DATETIME NULL AFTER status;
UPDATE reward_order SET status_changed_at = updated_at WHERE status_changed_at IS NULL;
