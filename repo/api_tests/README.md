# API Tests

API-level integration and end-to-end tests for the CROH backend.

## Backend Integration Tests (Spring Boot + H2)

Located in `backend/src/test/java/com/croh/` — 117 tests across 19 test classes.

### Test classes by module

| Module | Test class | Tests | What it covers |
|--------|-----------|-------|----------------|
| auth | `AuthIntegrationTest` | 7 | register, login, logout, lockout, CSRF enforcement |
| auth | `CsrfTokenIntegrationTest` | 3 | Real CSRF token cookie issuance and enforcement |
| auth | `RegisterIntegrationTest` | 3 | Registration validation, duplicate username |
| auth | `LoginAuditIntegrationTest` | 3 | Login success/failure audit events |
| auth | `AdminPasswordResetIntegrationTest` | 4 | Admin-only password reset with identity review |
| auth | `BlacklistLoginAppealFlowTest` | 6 | Blacklist login→appeal full flow |
| auth | `AuthServiceTest` | 3 | BCrypt hashing, service-layer auth logic |
| account | `RoleSwitchIntegrationTest` | 12 | Role request, approval, switching, verification prerequisites |
| account | `BlacklistEnforcementIntegrationTest` | 6 | Blacklist blocking, appeal submission, admin decisioning |
| account | `AppealSlaComputationTest` | 3 | 3-business-day Mon-Fri SLA computation |
| verification | `VerificationIntegrationTest` | 12 | Person verification, org credential upload, admin queue, PII masking, duplicate checksum |
| events | `EventIntegrationTest` | 10 | Event CRUD, registration, manual review, waitlist auto-promotion, roster export |
| resources | `ResourceClaimIntegrationTest` | 6 | Resource publish, claim, download, policy enforcement, household limits |
| rewards | `RewardFulfillmentTest` | 5 | Reward orders, state machine transitions, fulfillment exceptions |
| alerts | `AlertWorkOrderIntegrationTest` | 6 | Alert rules, overrides, cooldown, work orders, SLA timestamps |
| reporting | `ReportExportIntegrationTest` | 6 | Metric definitions, templates, execution, export, data quality |
| audit | `AuditServiceTest` | 2 | Audit log creation and retrieval |
| common | `ErrorContractTest` | 3 | Normalized error response contract |
| crypto | `EncryptionServiceTest` | 3 | AES-GCM encrypt/decrypt, key derivation |

### Running

```bash
./run_tests.sh              # All layers including backend
./unit_tests/run.sh          # Backend + frontend unit/component
./api_tests/run.sh           # Backend integration + Playwright API E2E
```

## Playwright E2E API Tests

Located in `e2e/tests/*.api.spec.ts` — 20 tests against the full Docker stack.

| Test file | Tests | Coverage |
|-----------|-------|----------|
| `auth-account.api.spec.ts` | 7 | register, login, logout, lockout (10 attempts), 401/409 |
| `verification-roles.api.spec.ts` | 3 | person verification, role request, admin guard |
| `events-registrations.api.spec.ts` | 2 | event registration, event list |
| `resources-rewards.api.spec.ts` | 3 | resource list, resource publish guard, reward guard |
| `alerts-workorders.api.spec.ts` | 2 | alert rule guard, work order guard |
| `analytics-audit.api.spec.ts` | 3 | audit log guard, report guard, operations summary guard |
