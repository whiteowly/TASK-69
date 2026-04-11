-- Add explicit organization_id to reward_item and work_order for object-centric org authorization.
-- Previously, org scope was inferred from the creator's role membership, which is ambiguous
-- when creators hold memberships in multiple organizations.

ALTER TABLE reward_item ADD COLUMN organization_id VARCHAR(100) NULL AFTER status;
ALTER TABLE reward_item ADD INDEX idx_reward_item_org (organization_id);

ALTER TABLE work_order ADD COLUMN organization_id VARCHAR(100) NULL AFTER description;
ALTER TABLE work_order ADD INDEX idx_work_order_org (organization_id);

-- Backfill: derive organization_id from the creator's ORG_OPERATOR role membership.
-- If a creator has multiple memberships, picks the first alphabetically (deterministic).
-- Rows that cannot be resolved remain NULL and will be rejected for non-admin access (fail-safe).
UPDATE reward_item ri
  JOIN (
    SELECT rm.account_id, MIN(rm.scope_id) AS scope_id
    FROM role_membership rm
    WHERE rm.role_type = 'ORG_OPERATOR' AND rm.status = 'APPROVED' AND rm.scope_id IS NOT NULL
    GROUP BY rm.account_id
  ) rm ON ri.created_by = rm.account_id
SET ri.organization_id = rm.scope_id
WHERE ri.organization_id IS NULL;

UPDATE work_order wo
  JOIN (
    SELECT rm.account_id, MIN(rm.scope_id) AS scope_id
    FROM role_membership rm
    WHERE rm.role_type = 'ORG_OPERATOR' AND rm.status = 'APPROVED' AND rm.scope_id IS NOT NULL
    GROUP BY rm.account_id
  ) rm ON wo.created_by = rm.account_id
SET wo.organization_id = rm.scope_id
WHERE wo.organization_id IS NULL;
