#!/usr/bin/env bash
# Seeds E2E test data: creates admin and org_operator accounts with approved roles.
set -euo pipefail

DB_CONTAINER="repo-db-1"
DB_NAME=$(docker exec "$DB_CONTAINER" sh -c 'cat /run/secrets/db_name')
DB_USER=$(docker exec "$DB_CONTAINER" sh -c 'cat /run/secrets/db_username')
DB_PASS=$(docker exec "$DB_CONTAINER" sh -c 'cat /run/secrets/db_password')

run_sql() {
  docker exec "$DB_CONTAINER" mysql -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" -e "$1" 2>/dev/null
}

BASE_URL="${BASE_URL:-http://frontend:3000}"

register() {
  local username="$1"
  local res
  res=$(docker run --rm --network repo_default curlimages/curl:latest \
    --max-time 10 -sS -X POST \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"$username\",\"password\":\"SecurePass99\",\"accountType\":\"PERSON\"}" \
    "$BASE_URL/api/v1/auth/register" 2>/dev/null)
  # Extract accountId — works for both 201 (new) and 409 (existing)
  local id
  id=$(echo "$res" | grep -o '"accountId":[0-9]*' | grep -o '[0-9]*' || true)
  if [ -z "$id" ]; then
    # Account exists — look up by username
    id=$(run_sql "SELECT id FROM account WHERE username='$username'" 2>/dev/null | tail -1)
  fi
  echo "$id"
}

echo "=== Seeding E2E data ==="

ADMIN_ID=$(register "e2e_admin")
echo "Admin account: id=$ADMIN_ID"
ORG_ID=$(register "e2e_org")
echo "Org account: id=$ORG_ID"
PART_ID=$(register "e2e_participant")
echo "Participant account: id=$PART_ID"

run_sql "INSERT INTO role_membership (account_id, role_type, status, created_at, updated_at) VALUES ($ADMIN_ID, 'ADMIN', 'APPROVED', NOW(), NOW()) ON DUPLICATE KEY UPDATE status='APPROVED';"
run_sql "INSERT INTO role_membership (account_id, role_type, status, created_at, updated_at) VALUES ($ORG_ID, 'ORG_OPERATOR', 'APPROVED', NOW(), NOW()) ON DUPLICATE KEY UPDATE status='APPROVED';"
run_sql "INSERT INTO role_membership (account_id, role_type, status, created_at, updated_at) VALUES ($PART_ID, 'PARTICIPANT', 'APPROVED', NOW(), NOW()) ON DUPLICATE KEY UPDATE status='APPROVED';"

echo "Roles assigned."

# Seed a reward via API as admin
echo "Seeding reward..."
ADMIN_COOKIE=$(docker run --rm --network repo_default curlimages/curl:latest \
  --max-time 10 -sS -c - -X POST \
  -H "Content-Type: application/json" \
  -d '{"username":"e2e_admin","password":"SecurePass99"}' \
  "$BASE_URL/api/v1/auth/login" 2>/dev/null | grep -v "^#" | grep -v "^$" || true)

# Get XSRF token
XSRF=$(docker run --rm --network repo_default curlimages/curl:latest \
  --max-time 10 -sS -c - -b - -X GET \
  "$BASE_URL/api/v1/auth/me" 2>/dev/null | grep "XSRF-TOKEN" | awk '{print $NF}' || true)

# Create reward (ignore errors if already exists)
docker run --rm --network repo_default curlimages/curl:latest \
  --max-time 10 -sS -X POST \
  -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: $XSRF" \
  -d '{"title":"E2E Gift Card","tier":"SILVER","inventoryCount":10,"perUserLimit":2,"fulfillmentType":"VOUCHER","status":"PUBLISHED"}' \
  "$BASE_URL/api/v1/rewards" 2>/dev/null || true

# Insert reward directly via SQL as fallback
run_sql "INSERT IGNORE INTO reward_item (title, tier, inventory_count, per_user_limit, fulfillment_type, status, created_by, created_at, updated_at) VALUES ('E2E Gift Card', 'SILVER', 10, 2, 'VOUCHER', 'ACTIVE', $ADMIN_ID, NOW(), NOW());" || true

echo "=== E2E seed complete ==="
