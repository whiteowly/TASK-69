# Community Resilience Operations Hub (CROH)

Project type: **fullstack web**

CROH is an offline-first crisis operations platform for community response teams. It provides role-based workflows for account security, verification, events, resources, rewards fulfillment, alert/work-order operations, analytics/reporting, and audit visibility.

## Runtime (Primary)

Use Docker Compose for the full stack:

```bash
docker compose up --build
```

Compatibility note: if your environment only provides the legacy command name, use:

```bash
docker-compose up
```

This repo is Docker-contained for runtime. You do **not** need host-level `npm install`, `mvn install`, `pip install`, `apt-get`, or manual `.env` setup.

## Access

The frontend binds to a random available localhost port to avoid collisions.

Get the assigned port:

```bash
docker compose port frontend 3000
```

Open the app at:

```text
http://127.0.0.1:<PORT>
```

The backend and DB are internal to Docker networking; frontend proxies `/api/*` to backend.

## Authentication and Demo Credentials

Authentication is required.

### Seeded demo accounts (recommended)

From repo root, seed baseline demo users:

```bash
bash e2e/seed-e2e-data.sh
```

Seeded credentials:

| Role | Username | Password |
|---|---|---|
| ADMIN | `e2e_admin` | `SecurePass99` |
| ORG_OPERATOR | `e2e_org` | `SecurePass99` |
| PARTICIPANT | `e2e_participant` | `SecurePass99` |

### VOLUNTEER role credential

Create a volunteer login using the Register screen or API:

- Username: `e2e_volunteer`
- Password: `SecurePass99`

Then grant VOLUNTEER role from admin flow:

1. Log in as `e2e_volunteer`, submit role request for `VOLUNTEER`.
2. Log in as `e2e_admin`, approve request in Admin Role Approvals.
3. Log back in as `e2e_volunteer` and switch active role to `VOLUNTEER`.

## Verification (How to Confirm the System Works)

### API smoke check with curl

Register user:

```bash
curl -i -X POST http://127.0.0.1:<PORT>/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"smoke_user","password":"TestPass123!","accountType":"PERSON"}'
```

Login:

```bash
curl -i -X POST http://127.0.0.1:<PORT>/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"smoke_user","password":"TestPass123!"}'
```

Expected: `201` for register, `200` for login, JSON response body, and session cookies set.

### UI smoke check

1. Open `http://127.0.0.1:<PORT>`.
2. Confirm Login page loads.
3. Log in with `e2e_admin / SecurePass99`.
4. Open Admin workspace and verify navigation loads (verification/roles/analytics panels).

## Tests

Broad test command:

```bash
./run_tests.sh
```

This script runs backend tests, frontend tests, and API E2E using Dockerized commands.

Additional wrappers:

```bash
./unit_tests/run.sh
./api_tests/run.sh
```

## Main Repository Contents

```text
repo/
├── docker-compose.yml         # Runtime orchestration
├── docker-compose.e2e.yml     # E2E override (fixed host port mapping)
├── run_tests.sh               # Broad test command
├── init_db.sh                 # Optional DB/bootstrap helper script
├── bootstrap/                 # Dev-only secret generation scripts
├── backend/                   # Spring Boot 3.2 API (Java 17)
├── frontend/                  # Vue 3 + TypeScript SPA
├── e2e/                       # Playwright API and UI suites
├── api_tests/                 # API-layer test wrapper docs/scripts
└── unit_tests/                # Unit/component test wrapper docs/scripts
```

## Architecture Snapshot

- Frontend: Vue 3, TypeScript, Pinia, Vue Router, served by Nginx.
- Backend: Spring Boot modular monolith with RBAC and permission interceptors.
- Database: MySQL 8 with Flyway migrations.
- Storage: local encrypted file storage for sensitive documents.

## Security and Runtime Notes

- Local secrets are generated at runtime by `bootstrap/` for development only.
- No checked-in `.env` files are used for startup.
- CSRF protection uses cookie/header pairing (`XSRF-TOKEN` + `X-XSRF-TOKEN`) for state-changing endpoints.
