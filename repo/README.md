# Community Resilience Operations Hub (CROH)

Offline-first local-network web portal for community crisis response, volunteer engagement, benefit fulfillment, and operations oversight.

## Quick Start

### Prerequisites
- Docker and Docker Compose

### Run the System
```
docker compose up --build
```

The system auto-generates local development secrets on first run (DB credentials, encryption key). These are **for local development only** and are not suitable for production secret management. No `.env` files are used or required.

The frontend binds to a **random available host port** on `127.0.0.1` to avoid collisions. To find the assigned port after startup:

```
docker compose port frontend 3000
```

The backend and database are **not exposed to the host**. The frontend Nginx proxy routes `/api/*` to the backend internally.

### Initialize Database
```
./init_db.sh
```

### Run Tests
```
./run_tests.sh
```

---

## Architecture

- **Frontend**: Vue 3 + TypeScript + Pinia + Vue Router (served via Nginx)
- **Backend**: Spring Boot 3.2 modular monolith (Java 17)
- **Database**: MySQL 8.0 with Flyway migrations (V1-V11)
- **File Storage**: Local disk with AES-GCM encryption at rest via `EncryptionService`

## Frontend Route Groups

| Route | Purpose |
|-------|---------|
| `/login` | Authentication |
| `/register` | Account creation |
| `/locked` | Account lockout notice |
| `/appeal` | Blacklist appeal submission |
| `/workspace/admin/*` | Admin dashboard, verification, roles, blacklist, appeals, password resets, registration review, policies, fulfillment, alerts, work orders, analytics, reports, audit logs |
| `/workspace/PARTICIPANT/*` | Verification, roles, events, resources, rewards |
| `/workspace/VOLUNTEER/*` | Verification review queue, registration review |
| `/workspace/ORG_OPERATOR/*` | Event management, resource management |

---

## Security Boundaries

### Authentication and Session
- BCrypt password hashing, 10-attempt lockout (30 min), HttpOnly session cookies
- CSRF protection: `XSRF-TOKEN` cookie + `X-XSRF-TOKEN` header. Only login/register/logout and multipart photo uploads are CSRF-exempt.
- Session fixation: new session created on every login

### Authorization (RBAC + Object Ownership)
- 4 roles: PARTICIPANT, VOLUNTEER, ORG_OPERATOR, ADMIN
- 17 permissions enforced via `@RequirePermission` annotation on all privileged endpoints
- Object ownership enforced in service layer (e.g., users can only cancel their own registrations, download their own report exports)
- Frontend route guards + backend enforcement (frontend is not the security boundary)

### Blacklist and Appeals
- Immediate block with constrained appeal-only session via `BlacklistEnforcementFilter`
- 3-business-day Mon-Fri SLA for appeal review
- Appeals require CSRF tokens (not exempt)

### PII and Encryption
- DOB, shipping address lines encrypted at rest (AES-GCM with key versioning via `EncryptionService`)
- Credential document blobs encrypted on disk with SHA-256 duplicate detection
- API responses mask PII by default; full reveal gated by `VIEW_PII` permission + audit trail
- Upload validation: PDF/JPG only, max 10MB

### State Machines (no-skip enforcement)
- Event registration: SUBMITTED→PENDING_REVIEW|APPROVED|WAITLISTED→CANCELLED
- Reward fulfillment: ORDERED→ALLOCATED→PACKED→SHIPPED→DELIVERED (physical) or →VOUCHER_ISSUED→REDEEMED (voucher)
- Work orders: NEW_ALERT→ACKNOWLEDGED→DISPATCHED→IN_PROGRESS→RESOLVED→CLOSED
- Fulfillment exceptions: OPEN→UNDER_REVIEW→RESOLVED|REJECTED (reopen requires `APPROVE_EXCEPTION_REOPEN` + reason)

### Audit Logging
- All privileged and lifecycle actions logged with: event_id, timestamp, actor, role, action_type, object_type, object_id, before/after state, reason, correlation_id

### Error Contract
- Normalized JSON responses with correlation IDs across 400/401/403/409/423/500
- 500 responses never expose stack traces or internal class names

---

## Project Structure

```
repo/
├── docker-compose.yml         # Runtime orchestration
├── docker-compose.e2e.yml     # E2E test override (fixed port)
├── init_db.sh                 # Database initialization entrypoint
├── run_tests.sh               # Broad test execution (backend + frontend + API E2E)
├── bootstrap/                 # Dev-only secret generation
├── backend/                   # Spring Boot API
│   ├── src/main/java/com/croh/
│   │   ├── auth/              # Login, logout, lockout, password reset
│   │   ├── account/           # Roles, blacklist, appeals
│   │   ├── verification/      # Person verification, org credentials, admin queue
│   │   ├── events/            # Events, registrations, waitlist, roster export
│   │   ├── resources/         # Resources, claims, downloads, usage policies
│   │   ├── rewards/           # Rewards, orders, fulfillment, exceptions, addresses
│   │   ├── alerts/            # Alert rules, events, work orders
│   │   ├── reporting/         # Metrics, templates, execution, export, analytics
│   │   ├── audit/             # Immutable audit log
│   │   ├── security/          # RBAC, permissions, session, encryption
│   │   └── common/            # Error contract, global exception handler
│   └── src/main/resources/db/migration/  # Flyway V1-V11
├── frontend/                  # Vue 3 SPA
│   ├── src/views/
│   │   ├── LoginView.vue, RegisterView.vue, LockedView.vue, AppealView.vue
│   │   ├── WorkspaceShell.vue            # Role-based sidebar + role switcher
│   │   ├── admin/                        # 14 admin panels
│   │   ├── participant/                  # Verification, roles, events, resources, rewards
│   │   └── org/                          # Event management, resource management
│   └── src/views/__tests__/   # 68 component tests (Vitest)
├── e2e/                       # Playwright E2E tests
│   ├── tests/*.api.spec.ts    # 20 API E2E tests
│   └── tests/*.ui.spec.ts     # Browser UI tests (positive flows + navigation)
├── api_tests/                 # API test documentation and runner
│   ├── README.md              # Maps backend integration + Playwright API tests
│   └── run.sh                 # Runs all API-level tests
└── unit_tests/                # Unit/component test documentation and runner
    ├── README.md              # Maps backend unit + frontend component tests
    └── run.sh                 # Runs all unit/component tests
```

## Test Layers

| Layer | Count | Location | What it tests |
|-------|-------|----------|--------------|
| Backend integration | 117 | `backend/src/test/java/com/croh/` | Full HTTP cycle through MockMvc + H2: auth, RBAC, CSRF, state machines, policies, encryption, ownership checks, audit |
| Frontend component | 68 | `frontend/src/views/__tests__/` | Vue components: form behavior, API contracts, state transitions, role gating |
| API E2E | 20 | `e2e/tests/*.api.spec.ts` | HTTP through Nginx→Spring Boot→MySQL: auth lifecycle, permission boundaries |
| Browser E2E | 45+ | `e2e/tests/*.ui.spec.ts` | Chromium through real UI: admin workflows, org publishing, participant registration/claim/order |

### Running specific layers

```bash
./run_tests.sh              # All layers (backend + frontend + API E2E)
./unit_tests/run.sh         # Unit + component only (no Docker stack)
./api_tests/run.sh          # API integration + E2E (starts Docker stack)
```
