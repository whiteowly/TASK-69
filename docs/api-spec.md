# Community Resilience Operations Hub — API Specification (Planning)

Status: planning contract for implementation. No endpoints implemented yet.

## 1) API conventions

- Base path: `/api/v1`
- Protocol: HTTP over local network (no external dependencies)
- Auth model (planned): server-managed session with HttpOnly cookie
- Content type: `application/json` unless upload/download endpoints
- Time format: ISO-8601 UTC timestamps
- Pagination: `page`, `size`, `sort` (`field,asc|desc`)
- Error shape: normalized object (`code`, `message`, `fieldErrors[]`, `correlationId`, `timestamp`)

### Auth/permission baseline
- `401` when session missing/expired.
- `403` when role/permission/object scope is insufficient.
- `423` when account lockout or blacklist blocks access.

---

## 2) Auth and account endpoints

## 2.1 Authentication

### `POST /auth/register`
Creates local account credentials.

Request:
```json
{
  "username": "jane.participant",
  "password": "<plaintext-input>",
  "accountType": "PERSON"
}
```

Response `201`:
```json
{
  "accountId": "acc_123",
  "username": "jane.participant",
  "status": "ACTIVE",
  "createdAt": "2026-04-09T10:00:00Z"
}
```

### `POST /auth/login`
Validates credentials, enforces lockout (10 failed attempts => 30 minutes).

Request:
```json
{ "username": "jane.participant", "password": "<plaintext-input>" }
```

Response `200`:
```json
{
  "accountId": "acc_123",
  "displayName": "Jane Participant",
  "approvedRoles": ["PARTICIPANT"],
  "activeRole": "PARTICIPANT",
  "permissions": ["SELF_SERVICE"]
}
```

Failure examples:
- `401 AUTH_INVALID_CREDENTIALS`
- `423 AUTH_ACCOUNT_LOCKED` (includes `lockedUntil`)
- `423 AUTH_ACCOUNT_BLACKLISTED`

### `POST /auth/logout`
Invalidates session.

### `GET /auth/me`
Returns current account summary, roles, permissions, and masking context.

## 2.2 Password reset (admin-mediated)

### `POST /admin/password-resets`
Permission: `ADMIN` + `RESET_PASSWORD`

Request:
```json
{
  "targetAccountId": "acc_123",
  "identityReviewNote": "Verified in person using station ID check."
}
```

Response `202`:
```json
{
  "resetId": "pwdreset_1",
  "status": "ISSUED",
  "temporarySecretIssued": true
}
```

---

## 3) Role memberships, verification, and blacklist/appeals

## 3.1 Role requests and switching

### `POST /accounts/me/role-requests`
Request a role membership approval.

Request:
```json
{
  "role": "VOLUNTEER",
  "scopeType": "ORGANIZATION",
  "scopeId": "org_77"
}
```

### `GET /accounts/me/roles`
List memberships with statuses (`REQUESTED`, `UNDER_REVIEW`, `APPROVED`, `DENIED`, `REVOKED`).

### `PUT /accounts/me/active-role`
Switch active workspace role. Allowed only if role membership is approved.

Request:
```json
{ "role": "VOLUNTEER", "scopeId": "org_77" }
```

## 3.2 Person verification

### `POST /verification/person`
Submits legal name and DOB.

Request:
```json
{
  "legalName": "Jane Alexandra Participant",
  "dateOfBirth": "1992-01-20"
}
```

Response `202`:
```json
{ "status": "UNDER_REVIEW", "submittedAt": "2026-04-09T10:10:00Z" }
```

## 3.3 Organization credential documents

### `POST /verification/org-documents`
Multipart upload (`file`) with type/size validation (PDF/JPG, max 10 MB).

Response `201`:
```json
{
  "documentId": "doc_22",
  "status": "UNDER_REVIEW",
  "duplicateChecksumFlag": true
}
```

## 3.4 Admin verification review

### `GET /admin/verification/queue`
Permission: `REVIEW_VERIFICATION`

### `POST /admin/verification/person/{accountId}/decision`
### `POST /admin/verification/org-document/{documentId}/decision`

Decision request:
```json
{
  "decision": "APPROVE",
  "reasonCode": "DOC_VALID",
  "reviewNote": "Credentials verified by admin desk."
}
```

## 3.5 Blacklist and appeals

### `POST /admin/blacklist`
Immediate block.

Request:
```json
{
  "targetAccountId": "acc_123",
  "reasonCode": "SAFETY_POLICY_BREACH",
  "note": "Escalated after repeated misuse."
}
```

### `POST /appeals`
Blocked user submits appeal.

Request:
```json
{
  "blacklistId": "bl_9",
  "appealText": "Request review; event misunderstanding.",
  "contactNote": "Reach me at station desk hours."
}
```

Response includes computed due date based on local Mon–Fri business days.

### `GET /admin/appeals`
Filters: `status`, `dueBefore`, `overdue=true|false`

### `POST /admin/appeals/{appealId}/decision`
`APPROVE_UNBLOCK` or `DENY`

---

## 4) Events and registrations

## 4.1 Event publishing

### `POST /events`
Permission: `PUBLISH_EVENT` (approved org role required)

Request:
```json
{
  "organizationId": "org_77",
  "title": "Community First Aid Night",
  "mode": "ON_SITE",
  "location": "Station Hall A",
  "startAt": "2026-05-01T17:00:00Z",
  "endAt": "2026-05-01T19:00:00Z",
  "capacity": 50,
  "waitlistEnabled": true,
  "manualReviewRequired": true,
  "registrationForm": {
    "fields": [
      { "id": "q1", "type": "text", "label": "Emergency contact", "required": true }
    ]
  }
}
```

### `GET /events`
Supports role-scoped listing + filter/sort.

### `GET /events/{eventId}`

### `PATCH /events/{eventId}`
Editable fields gated by event state and permissions.

## 4.2 Registrations and waitlist

### `POST /events/{eventId}/registrations`
Submit registration.

Response states include `APPROVED`, `PENDING_REVIEW`, or `WAITLISTED`.

### `POST /registrations/{registrationId}/decision`
Permission: `REVIEW_REGISTRATION`.

Request:
```json
{
  "decision": "APPROVE",
  "reasonCode": "ELIGIBLE"
}
```

### `POST /registrations/{registrationId}/cancel`
Cancellation endpoint. If registration was approved, server auto-promotes oldest waitlist entry in transaction.

### `GET /events/{eventId}/roster`
Role-gated roster view.

### `POST /events/{eventId}/roster/export`
Generates local CSV/PDF export artifact.

---

## 5) Resources, policies, claims, downloads

## 5.1 Resource publishing

### `POST /resources`
Permission: `PUBLISH_RESOURCE`

Request (claimable item):
```json
{
  "type": "CLAIMABLE_ITEM",
  "title": "Emergency Water Kit",
  "inventoryCount": 200,
  "usagePolicyId": "policy_household_30d"
}
```

Request (downloadable file):
```json
{
  "type": "DOWNLOADABLE_FILE",
  "title": "Storm Shelter Guide",
  "fileVersion": "v3",
  "usagePolicyId": "policy_3_per_user_per_version"
}
```

## 5.2 Policy management

### `POST /resource-policies`
Permission: `MANAGE_RESOURCE_POLICY`

Example request:
```json
{
  "name": "One claim per household per 30 days",
  "scope": "HOUSEHOLD",
  "maxActions": 1,
  "windowDays": 30,
  "resourceAction": "CLAIM"
}
```

## 5.3 Claims/downloads

### `POST /resources/{resourceId}/claim`
Evaluates policy and returns allow/deny + printable notice ID.

Response example:
```json
{
  "result": "DENIED_POLICY",
  "reasonCode": "HOUSEHOLD_LIMIT_EXCEEDED",
  "printableNoticeId": "notice_551"
}
```

### `POST /resources/files/{fileId}/download`
Enforces per-user/per-version limits, records attempt, returns local download token/path descriptor.

### `GET /notices/{noticeId}/print`
Returns printable notice content.

---

## 6) Rewards, inventory, fulfillment, exceptions

## 6.1 Catalog and ordering

### `POST /rewards`
Permission: `MANAGE_REWARDS`

### `POST /reward-orders`
Creates order after per-user limit and inventory checks.

Request:
```json
{
  "rewardId": "rew_10",
  "quantity": 1,
  "fulfillmentType": "PHYSICAL_SHIPMENT",
  "shippingAddressId": "addr_90"
}
```

### `POST /accounts/me/addresses`
US-format validation; address lines encrypted at rest.

## 6.2 State transition endpoints

### `POST /reward-orders/{orderId}/transition`
Permission: `MANAGE_REWARD_FULFILLMENT`

Request:
```json
{
  "toState": "PACKED",
  "note": "Packed at station inventory room"
}
```

Conflict response `409` on illegal transition.

### `POST /shipments/{shipmentId}/tracking`
Stores tracking number.

### `POST /vouchers/{voucherId}/issue`
Issues e-voucher.

## 6.3 Exceptions and reopen control

### `POST /fulfillment-exceptions`
Create exception (`LOST_SHIPMENT`, `DAMAGED_ITEM`, `VOUCHER_INVALID`, etc.).

### `POST /fulfillment-exceptions/{exceptionId}/reopen`
Requires reason code + supervisor approval permission.

Request:
```json
{
  "reasonCode": "NEW_EVIDENCE",
  "supervisorApprovalId": "sup_approval_7",
  "note": "Package was returned undelivered."
}
```

---

## 7) Alert center and work orders

## 7.1 Alert rules

### `GET /alerts/rules`
Global defaults + overrides by station/org.

### `PUT /alerts/rules/defaults/{alertType}`
Permission: `CONFIGURE_ALERT_RULES`

Request example:
```json
{
  "severity": "HIGH",
  "threshold": { "operator": "GT", "value": 120, "unit": "F" },
  "durationSeconds": 300,
  "cooldownSeconds": 900
}
```

### `PUT /alerts/rules/overrides/{scopeType}/{scopeId}/{alertType}`

## 7.2 Alert ingestion and ticketing

### `POST /alerts/events`
Ingests local sensor/equipment event; applies threshold/cooldown rules.

### `POST /work-orders`
Create or dispatch ticket manually.

### `POST /work-orders/{id}/transition`
Transitions: `ACKNOWLEDGED`, `DISPATCHED`, `IN_PROGRESS`, `RESOLVED`, `CLOSED`.

### `POST /work-orders/{id}/notes`
### `POST /work-orders/{id}/photos` (multipart)
### `POST /work-orders/{id}/post-incident-review`

SLA fields returned with work order detail:
```json
{
  "workOrderId": "wo_19",
  "severity": "CRITICAL",
  "firstResponseSeconds": 420,
  "timeToCloseSeconds": 7300
}
```

---

## 8) Analytics, reporting, exports, data quality

## 8.1 Dashboards

### `GET /analytics/operations-summary`
Filters: `from`, `to`, `organizationId`, `stationId`

Response includes booking/registration volume, completion/cancellation rates, workload, category popularity, retention indicators.

## 8.2 Metric definitions and templates

### `POST /reports/metric-definitions`
Permission: `MANAGE_METRICS`

### `POST /reports/templates`
Permission: `MANAGE_REPORT_TEMPLATES`

### `POST /reports/templates/{templateId}/execute`
Runs report with row filters and output format.

Request:
```json
{
  "format": "CSV",
  "filters": {
    "organizationId": "org_77",
    "from": "2026-01-01",
    "to": "2026-03-31"
  }
}
```

Response:
```json
{
  "executionId": "rex_77",
  "status": "COMPLETED",
  "exportFileId": "exp_991"
}
```

### `GET /exports/{exportFileId}`
Permission: `EXPORT_REPORTS` and row-scope authorization.

## 8.3 Data-quality reporting

### `GET /reports/data-quality`
Filters: domain, time range, organization/station.

Returns:
- missing rates
- anomaly distributions
- duplicate-record counts

---

## 9) Audit API surface

### `GET /admin/audit-logs`
Permission: `VIEW_AUDIT_LOGS`

Query params:
- `actorAccountId`
- `actionType`
- `objectType`
- `from`, `to`
- `page`, `size`, `sort`

Response sample:
```json
{
  "items": [
    {
      "eventId": "aud_1",
      "timestamp": "2026-04-09T11:00:00Z",
      "actorAccountId": "acc_admin",
      "actionType": "BLACKLIST_APPEAL_DECISION",
      "objectType": "BLACKLIST_APPEAL",
      "objectId": "appeal_77",
      "afterState": "APPROVE_UNBLOCK"
    }
  ],
  "page": 0,
  "size": 50,
  "total": 1
}
```

---

## 10) Critical transition and exception guards

1. **Registration capacity guard**
   - registration approval and waitlist promotion occur in one transaction.
2. **Reward state machine guard**
   - transition endpoint validates allowed edges only; rejects skipped states.
3. **Reopen guard for exceptions**
   - reopen endpoint requires reason code and supervisor approval reference.
4. **PII access guard**
   - full sensitive fields only when `VIEW_PII`; otherwise masked projection.
5. **Blacklist guard**
   - blacklisted accounts blocked across API except allowed appeal/logout endpoints.

---

## 11) Planned status codes by risk-sensitive flow

- Login lockout: `423`
- Blacklisted action attempt: `423`
- Forbidden PII view without permission: `403`
- Invalid transition (reward/work order): `409`
- Duplicate username / duplicate idempotency key conflict: `409`
- Missing object in scoped domain: `404`
