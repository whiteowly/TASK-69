# Community Resilience Operations Hub — Implementation Design Plan

## 1) Purpose and scope

This document defines the implementation-grade design for an **offline-first fullstack web portal** used by local agencies and partner nonprofits to manage crisis response, volunteer engagement, benefit fulfillment, and operations oversight from one system.

### In scope
- Vue.js frontend over local network.
- Spring Boot backend with REST-style API.
- MySQL durable persistence.
- Local disk file storage for credentials and incident photos.
- No internet dependency, no cloud services, no third-party auth.

### Locked defaults from clarification
1. One account may hold multiple approved roles; role switching occurs within that account.
2. Household-scoped resource limits use the participant's approved primary address as offline household key.
3. Blacklist appeal SLA uses local Monday–Friday business-day calendar unless later configured otherwise.
4. Duplicate credential uploads are flagged for admin review (not auto-rejected).
5. Alert rules have global defaults with optional station/organization overrides.

---

## 2) Runtime and repository contract (must remain stable)

- **Primary runtime command:** `docker compose up --build`
- **Broad verification command:** `./run_tests.sh`
- **Database initialization entrypoint:** `./init_db.sh` (the only project-standard DB init path)
- **No committed `.env` files** and no hardcoded DB bootstrap values in repo.

### Planned Docker-first startup strategy
- Compose includes a **dev-only bootstrap step** that generates local runtime values (DB credentials, encryption key material, app secret) into an ephemeral runtime location/volume.
- API and DB containers consume generated values at startup; no manual `export ...` required.
- `init_db.sh` runs idempotent DB preparation + migrations through containerized tooling.
- README (in repo) will explicitly document that bootstrap generation is for local development, not production secret management.

### README/static-review note (implementation requirement)
Once implementation exists, `README.md` must let a fresh reviewer quickly trace: (a) runtime/test/DB entrypoints (`docker compose up --build`, `./run_tests.sh`, `./init_db.sh`), (b) frontend route groups (`/login`, `/locked`, `/appeal`, `/workspace/*`), and (c) security-sensitive boundaries (auth+lockout, blacklist blocking/appeals, PII masking with `VIEW_PII`, audit logging, and state-machine-enforced transitions).

---

## 3) Architecture overview

## 3.1 High-level shape
- **Frontend (Vue 3 + TS + Pinia + Router):** role-based workspaces, in-page status feedback, masked-PII presentation.
- **Backend (Spring Boot modular monolith):** domain modules with explicit service boundaries and shared security/audit contracts.
- **Persistence (MySQL):** normalized operational tables + audit/event tables + reporting metadata.
- **File layer (local disk):** encrypted document/photo blobs with DB metadata and checksum dedupe flags.

## 3.2 Core cross-cutting constraints
- Authentication local-only with BCrypt hashing and lockout policy.
- Session model is fixed: server-managed session with HttpOnly cookie.
- RBAC + object-level authorization.
- PII masking by default in API DTOs/UI renderers.
- Encrypt at rest: DOB, address lines, uploaded credential documents.
- All privileged and lifecycle actions audit logged.
- State machine enforcement for reward/inventory/exception transitions.

---

## 4) Module map (backend)

1. **auth**
   - login/logout/session introspection
   - lockout policy (10 failed attempts, 30-minute lock)
   - password reset workflow for admins after identity review

2. **accounts**
   - account profile, status, role memberships
   - role switch active-context endpoint
   - blacklist state and block enforcement

3. **verification**
   - real-name verification (legal name + DOB)
   - org credential upload workflow + admin decisioning

4. **files**
   - upload validation (type/size)
   - encrypted-at-rest storage on local disk
   - checksum duplicate detection + metadata retrieval

5. **events**
   - create/manage online or on-site events
   - custom registration forms
   - capacity + waitlist config

6. **registrations**
   - registration submit/review/approve/deny/cancel
   - waitlist timestamp-order auto-promotion
   - roster generation/export

7. **resources**
   - publish claimable/downloadable resources
   - policy-driven usage limits (user, household, version windows)
   - printable local notices

8. **rewards**
   - reward tiers, inventory, per-user purchase limits
   - address management and order placement

9. **fulfillment**
   - voucher issuance or physical shipment tracking
   - overdue detection (>7 days in Packed/Shipped)
   - exception capture and controlled reopen

10. **alerts**
   - alert rule config (threshold/cooldown)
   - event ingestion/evaluation (offline/temperature/leakage/overcurrent)

11. **workorders**
   - acknowledge/dispatch/notes/photos/close/post-incident review
   - SLA metric capture (first response, time-to-close)

12. **reporting-analytics**
   - dashboards: volume, completion/cancellation, workload, categories, retention
   - metric definitions, report templates, row-filtered exports (CSV/PDF)
   - data-quality reports (missingness/anomaly/duplicate)

13. **audit**
   - immutable action logs for account, approval, fulfillment, alert, exception, export, and PII view events

14. **shared/security/common**
   - permission checks, error contracts, pagination/filter/sort contracts, transaction + clock utilities

---

## 5) Frontend application map

### Route groups
- `/login`, `/locked`, `/appeal`
- `/workspace/participant/*`
- `/workspace/volunteer/*`
- `/workspace/org/*`
- `/workspace/admin/*`

### Core UI subsystems
1. **Workspace shell** (role switcher, permission-aware nav, global status banners)
2. **Verification center** (identity/org credential status and admin queue)
3. **Events center** (publish/manage/register/review/waitlist/roster export)
4. **Resources center** (publish/claim/download/usage feedback/print notices)
5. **Rewards center** (catalog/order/address/fulfillment/exception handling)
6. **Alert center + work orders** (rule config, tickets, notes/photos, SLA view)
7. **Analytics & reporting** (filters, template execution, export controls)

### UI state contract (required across prompt-critical flows)
For each major action: `idle -> loading -> success|error`, with duplicate-submit prevention and disabled-state handling. All status-changing actions must show in-page feedback within current route (no email/SMS dependence).

---

## 6) Domain model and data model (MySQL)

## 6.1 Core entities
- **Account** (credential owner)
- **RoleMembership** (role + approval status + validity)
- **PersonVerification** (legal_name, dob_encrypted, status)
- **OrganizationProfile** (org metadata)
- **OrganizationCredentialDocument** (encrypted file metadata + review status + duplicate flag)
- **BlacklistRecord** and **BlacklistAppeal**
- **Event**, **EventFormSchema**, **EventRegistration**, **EventWaitlistEntry**
- **ResourceItem**, **ResourceFileVersion**, **UsagePolicy**, **ClaimRecord**, **DownloadRecord**, **PrintableNotice**
- **RewardTier**, **RewardItem**, **InventoryLedger**, **RewardOrder**, **ShippingAddress**, **Shipment**, **Voucher**, **FulfillmentException**
- **AlertRuleDefault**, **AlertRuleOverride**, **AlertEvent**, **WorkOrder**, **WorkOrderNote**, **WorkOrderPhoto**, **PostIncidentReview**
- **DashboardSnapshot** (optional derived cache), **MetricDefinition**, **ReportTemplate**, **ReportExecution**, **ExportFile**
- **AuditLog**

## 6.2 Sensitive fields and encryption
- Encrypt at rest:
  - `person_verification.date_of_birth`
  - `shipping_address.address_line1/address_line2`
  - organization credential document blobs (file-level encryption on disk)
- Recommended approach:
  - application-layer AES-GCM encryption with key versioning
  - JPA converters for DB fields
  - streaming encryption for file blobs
- Duplicate detection:
  - checksum on uploaded content for duplicate signaling (flag only, not rejection)

## 6.3 Key relational constraints
- Unique username (case-normalized)
- One active primary address per account
- Role membership uniqueness per `(account_id, role_type, scope_id)`
- Event seat accounting invariants: `approved_count <= capacity`
- Waitlist strict ordering by `(created_at, id)`
- Reward purchase limit checks per user per reward definition

---

## 7) Lifecycle state machines (authoritative)

## 7.1 Role membership state machine
`REQUESTED -> UNDER_REVIEW -> APPROVED | DENIED`

Additional transitions:
- `APPROVED -> REVOKED` (admin action)
- `DENIED -> UNDER_REVIEW` (resubmission/reconsideration)

## 7.2 Event registration state machine
`SUBMITTED -> PENDING_REVIEW | APPROVED | WAITLISTED -> CANCELLED | COMPLETED | NO_SHOW`

Rules:
- If `manual_review=false` and seat available: direct `APPROVED`.
- If full and waitlist enabled: `WAITLISTED`.
- Cancellation of approved registration triggers waitlist auto-promotion oldest-first.

## 7.3 Resource claim/download policy flow
- Claim/download attempts are evaluated against policy engine.
- Outcomes: `ALLOWED` (record created + notice) or `DENIED_POLICY` (denial record + printable notice).
- Policy dimensions: per-user, per-household, per-file-version, rolling windows.

## 7.4 Reward fulfillment state machine
`ORDERED -> ALLOCATED -> PACKED -> SHIPPED -> DELIVERED` (physical)
`ORDERED -> ALLOCATED -> VOUCHER_ISSUED -> REDEEMED` (voucher)

Constraints:
- No state skipping.
- Overdue alert if `PACKED` or `SHIPPED` exceeds 7 days without terminal completion.

## 7.5 Fulfillment exception state machine
`OPEN -> UNDER_REVIEW -> RESOLVED | REJECTED`

Reopen rule:
- `RESOLVED|REJECTED -> REOPENED` only with `reason_code` + `supervisor_approval=true`.

## 7.6 Work order state machine
`NEW_ALERT -> ACKNOWLEDGED -> DISPATCHED -> IN_PROGRESS -> RESOLVED -> CLOSED`

SLA timestamps:
- `first_response_at` at first ACKNOWLEDGED
- `closed_at` at CLOSED

---

## 8) Permissions and security boundaries

## 8.1 Role set
- `PARTICIPANT`
- `VOLUNTEER`
- `ORG_OPERATOR`
- `ADMIN`

## 8.2 Privilege model
- Roles grant base capability.
- Fine-grained permissions gate sensitive operations (examples):
  - `VIEW_PII`
  - `REVIEW_VERIFICATION`
  - `MANAGE_BLACKLIST`
  - `PUBLISH_EVENT`
  - `REVIEW_REGISTRATION`
  - `MANAGE_RESOURCE_POLICY`
  - `MANAGE_REWARD_FULFILLMENT`
  - `APPROVE_EXCEPTION_REOPEN`
  - `CONFIGURE_ALERT_RULES`
  - `EXPORT_REPORTS`

## 8.3 Enforcement layers
1. API authorization annotations + service-layer object checks.
2. Query-level scoping (organization/station ownership).
3. Frontend route guards + UI action gating (not security boundary by itself).

## 8.4 Sensitive data display
- Default masked DTO projection for PII fields.
- Full value returned only when caller has `VIEW_PII` and object-level access.
- PII reads emit audit entries.

---

## 9) Logging and audit rules

## 9.1 Application logging (operational)
- Structured logs with correlation ID.
- Redaction policy: never log passwords, full DOB, full address lines, or raw document bytes.
- Security-relevant events logged at WARN/INFO with actor and object references.

## 9.2 Audit logging (compliance trail)
Audit events required for:
- account registration/login failures/lockouts/password resets
- verification submissions and decisions
- role approvals/revocations/switches
- blacklist actions and appeals
- event and resource publishing changes
- reward fulfillment state transitions and exceptions
- alert/work order transitions and SLA-impact events
- report export executions
- PII view events

Audit schema baseline:
- `event_id`, `timestamp`, `actor_account_id`, `actor_role`, `action_type`, `object_type`, `object_id`, `before_state`, `after_state`, `reason_code`, `correlation_id`

---

## 10) Validation and API error contract

## 10.1 Request validation
- Bean validation + custom validators for:
  - username format and uniqueness
  - password rules
  - upload type (`application/pdf`, `image/jpeg`) and max 10 MB
  - US address format checks
  - state-transition preconditions

## 10.2 Error response shape (normalized)
```json
{
  "code": "VALIDATION_ERROR",
  "message": "One or more fields are invalid.",
  "fieldErrors": [
    { "field": "file", "reason": "MAX_SIZE_EXCEEDED", "message": "Maximum size is 10 MB." }
  ],
  "correlationId": "f44a2d88-...",
  "timestamp": "2026-04-09T12:00:00Z"
}
```

Required status handling:
- `400` validation errors
- `401` unauthenticated
- `403` permission/object-scope denied
- `404` not found
- `409` conflict (duplicate/invalid transition/concurrency)
- `423` locked account

---

## 11) Frontend-backend crosswalk

| Frontend surface | Backend module(s) | Core API surfaces |
|---|---|---|
| Login + lockout UI | auth | `/auth/login`, `/auth/me`, `/auth/logout`, `/admin/password-resets` |
| Role switcher + workspace shell | accounts/security | `/accounts/me/roles`, `/accounts/me/active-role` |
| Verification center | verification/files | `/verification/person`, `/verification/org-documents`, `/admin/verification/*` |
| Blacklist appeal page | accounts/admin | `/appeals`, `/admin/appeals/*` |
| Events center | events/registrations | `/events`, `/events/{id}/registrations`, `/registrations/{id}/decision` |
| Resource center | resources | `/resources`, `/resources/{id}/claim`, `/resources/files/{id}/download` |
| Rewards center | rewards/fulfillment | `/rewards`, `/reward-orders`, `/fulfillment/*` |
| Alert center + tickets | alerts/workorders | `/alerts/rules`, `/alerts/events`, `/work-orders/*` |
| Dashboards/reports | reporting-analytics | `/analytics/*`, `/reports/*`, `/exports/*` |

---

## 12) Ordered delivery slices (for follow-on implementation)

### Slice 1 — platform skeleton + cross-cutting contracts
- Docker compose baseline, backend/frontend skeletons, MySQL + Flyway.
- Auth skeleton, RBAC primitives, error contract, audit helper.

### Slice 2 — auth security and account lifecycle
- Register/login/logout/me, lockout logic, password reset flow, blacklist blocking.

### Slice 3 — verification and role approvals
- Person verification, org credential upload + admin review, role approval and switch.

### Slice 4 — events and registrations
- Event CRUD, custom forms, capacity, waitlist auto-promotion, manual review, roster export.

### Slice 5 — resources and usage policies
- Resource publish, claim/download enforcement, household/user/version limits, printable notices.

### Slice 6 — rewards and fulfillment state machines
- Inventory, limits, addresses, shipment/voucher paths, exception and supervisor reopen rules, overdue alerts.

### Slice 7 — alert center and work orders
- Rule config with overrides, alert ingestion/evaluation, ticket flow, notes/photos, SLA computation.

### Slice 8 — analytics, reporting, exports, data quality
- KPI dashboards, metric definitions, templates, CSV/PDF export permissions, data-quality reports.

### Slice 9 — hardening and acceptance verification
- Security edge cases, object-level auth tests, audit completeness checks, performance sanity.

---

## 13) Open high-risk assumptions and decisions to reconfirm early

1. **Business-day SLA** currently excludes weekends only; holiday calendar support deferred until explicit requirement.
2. **Retention rules** for uploaded docs/photos/exports are not specified; requires explicit policy before production-readiness.
3. **Anomaly detection method** for data-quality report needs deterministic rule definitions (z-score/IQR/rule-based).
4. **PDF rendering stack** must be chosen for local/offline operation and font packaging.

---

## 14) Handoff summary for next implementation session

Start with **Slice 1 -> Slice 2 -> Slice 3** as the critical security and access foundation. Then implement **Slice 4/5/6** in order (events -> resources -> rewards) because they share approval, policy, and state-transition primitives. Finish with **Slice 7/8/9** (alerts/work orders, analytics/reporting, hardening) once transactional workflows and audit patterns are stable.
