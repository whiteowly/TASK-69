## 1. Test Coverage Audit

- Audit mode: **static inspection only** (no test/runtime execution).
- Scope inspected: controllers under `backend/src/main/java/**`, API tests under `e2e/tests/*.api.spec.ts`, backend HTTP tests under `backend/src/test/java/**`, plus `run_tests.sh`.

### Backend Endpoint Inventory

- Total endpoints inventoried: **80** (resolved as `METHOD + full path`, including prefixes and path params).

### API Test Mapping Table

Legend:
- `TNH` = true no-mock HTTP (real HTTP via Playwright API request context)
- `HWM` = HTTP with mocking/simulated transport (Spring MockMvc)
- `U` = unit-only/indirect (not applicable here now)

| Endpoint | Covered | Type | Evidence |
|---|---:|---|---|
| POST `/api/v1/auth/register` | yes | TNH | `e2e/tests/helpers.ts:11` |
| POST `/api/v1/auth/login` | yes | TNH | `e2e/tests/helpers.ts:26` |
| POST `/api/v1/auth/logout` | yes | TNH | `e2e/tests/auth-account.api.spec.ts:47` |
| GET `/api/v1/auth/me` | yes | TNH | `e2e/tests/helpers.ts:103` |
| POST `/api/v1/admin/password-resets` | yes | TNH | `e2e/tests/policies-audit-passwordreset.api.spec.ts:90` |
| POST `/api/v1/accounts/me/role-requests` | yes | TNH | `e2e/tests/admin-roles.api.spec.ts:19` |
| GET `/api/v1/accounts/me/roles` | yes | TNH | `e2e/tests/admin-roles.api.spec.ts:118` |
| PUT `/api/v1/accounts/me/active-role` | yes | HWM | `backend/src/test/java/com/croh/account/RoleSwitchIntegrationTest.java:108` |
| GET `/api/v1/admin/roles/pending` | yes | TNH | `e2e/tests/admin-roles.api.spec.ts:31` |
| POST `/api/v1/admin/roles/{membershipId}/decision` | yes | TNH | `e2e/tests/admin-roles.api.spec.ts:68` |
| POST `/api/v1/admin/blacklist` | yes | TNH | `e2e/tests/admin-blacklist-appeals.api.spec.ts:26` |
| GET `/api/v1/admin/appeals` | yes | TNH | `e2e/tests/admin-blacklist-appeals.api.spec.ts:71` |
| POST `/api/v1/admin/appeals/{appealId}/decision` | yes | TNH | `e2e/tests/admin-blacklist-appeals.api.spec.ts:79` |
| GET `/api/v1/appeals/my-blacklist` | yes | TNH | `e2e/tests/admin-blacklist-appeals.api.spec.ts:43` |
| POST `/api/v1/appeals` | yes | TNH | `e2e/tests/admin-blacklist-appeals.api.spec.ts:59` |
| POST `/api/v1/verification/person` | yes | TNH | `e2e/tests/admin-verification.api.spec.ts:17` |
| POST `/api/v1/verification/org-documents` | yes | HWM | `backend/src/test/java/com/croh/verification/VerificationIntegrationTest.java:97` |
| GET `/api/v1/admin/verification/queue` | yes | TNH | `e2e/tests/admin-verification.api.spec.ts:28` |
| POST `/api/v1/admin/verification/person/{id}/decision` | yes | TNH | `e2e/tests/admin-verification.api.spec.ts:48` |
| POST `/api/v1/admin/verification/org-document/{id}/decision` | yes | TNH | `e2e/tests/admin-verification.api.spec.ts:63` |
| GET `/api/v1/admin/verification/org-document/{id}/download` | yes | HWM | `backend/src/test/java/com/croh/verification/VerificationIntegrationTest.java:152` |
| POST `/api/v1/events` | yes | TNH | `e2e/tests/events-detail.api.spec.ts:17` |
| GET `/api/v1/events` | yes | TNH | `e2e/tests/events-registrations.api.spec.ts:30` |
| GET `/api/v1/events/{id}` | yes | TNH | `e2e/tests/events-detail.api.spec.ts:43` |
| PATCH `/api/v1/events/{id}` | yes | TNH | `e2e/tests/events-detail.api.spec.ts:75` |
| POST `/api/v1/events/{id}/registrations` | yes | TNH | `e2e/tests/events-detail.api.spec.ts:115` |
| GET `/api/v1/events/{id}/roster` | yes | TNH | `e2e/tests/events-detail.api.spec.ts:126` |
| POST `/api/v1/events/{id}/roster/export` | yes | TNH | `e2e/tests/events-detail.api.spec.ts:157` |
| GET `/api/v1/registrations/pending` | yes | TNH | `e2e/tests/registrations.api.spec.ts:20` |
| POST `/api/v1/registrations/{id}/decision` | yes | TNH | `e2e/tests/registrations.api.spec.ts:56` |
| POST `/api/v1/registrations/{id}/cancel` | yes | TNH | `e2e/tests/registrations.api.spec.ts:90` |
| POST `/api/v1/resources` | yes | TNH | `e2e/tests/resources-detail.api.spec.ts:18` |
| POST `/api/v1/resources/upload` | yes | HWM | `backend/src/test/java/com/croh/audit/AuditFixesIntegrationTest.java:117` |
| GET `/api/v1/resources` | yes | TNH | `e2e/tests/resources-rewards.api.spec.ts:8` |
| GET `/api/v1/resources/{id}` | yes | TNH | `e2e/tests/resources-detail.api.spec.ts:39` |
| POST `/api/v1/resources/{id}/claim` | yes | TNH | `e2e/tests/resources-detail.api.spec.ts:71` |
| POST `/api/v1/resources/files/{id}/download` | yes | HWM | `backend/src/test/java/com/croh/resources/ResourceFileDownloadIntegrationTest.java:88` |
| GET `/api/v1/resources/{id}/file` | yes | HWM | `backend/src/test/java/com/croh/resources/ResourceFileDownloadIntegrationTest.java:97` |
| GET `/api/v1/resource-policies` | yes | TNH | `e2e/tests/policies-audit-passwordreset.api.spec.ts:31` |
| POST `/api/v1/resource-policies` | yes | TNH | `e2e/tests/policies-audit-passwordreset.api.spec.ts:14` |
| GET `/api/v1/notices/{id}/print` | yes | TNH | `e2e/tests/notices.api.spec.ts:46` |
| POST `/api/v1/rewards` | yes | TNH | `e2e/tests/rewards.api.spec.ts:17` |
| GET `/api/v1/rewards` | yes | TNH | `e2e/tests/rewards.api.spec.ts:42` |
| GET `/api/v1/rewards/{id}` | yes | TNH | `e2e/tests/rewards.api.spec.ts:49` |
| GET `/api/v1/reward-orders` | yes | TNH | `e2e/tests/reward-orders.api.spec.ts:49` |
| POST `/api/v1/reward-orders` | yes | TNH | `e2e/tests/reward-orders.api.spec.ts:36` |
| POST `/api/v1/reward-orders/{id}/transition` | yes | TNH | `e2e/tests/reward-orders.api.spec.ts:56` |
| POST `/api/v1/reward-orders/{id}/tracking` | yes | TNH | `e2e/tests/reward-orders.api.spec.ts:101` |
| POST `/api/v1/reward-orders/{id}/voucher` | yes | TNH | `e2e/tests/reward-orders.api.spec.ts:71` |
| GET `/api/v1/accounts/me/addresses` | yes | TNH | `e2e/tests/reward-orders.api.spec.ts:144` |
| POST `/api/v1/accounts/me/addresses` | yes | TNH | `e2e/tests/reward-orders.api.spec.ts:133` |
| PUT `/api/v1/accounts/me/addresses/{id}/primary` | yes | TNH | `e2e/tests/reward-orders.api.spec.ts:150` |
| POST `/api/v1/fulfillment-exceptions` | yes | TNH | `e2e/tests/fulfillment-exceptions.api.spec.ts:43` |
| POST `/api/v1/fulfillment-exceptions/{id}/transition` | yes | TNH | `e2e/tests/fulfillment-exceptions.api.spec.ts:55` |
| POST `/api/v1/fulfillment-exceptions/{id}/reopen` | yes | TNH | `e2e/tests/fulfillment-exceptions.api.spec.ts:72` |
| POST `/api/v1/alerts/events` | yes | TNH | `e2e/tests/alerts-rules-events.api.spec.ts:106` |
| GET `/api/v1/alerts/rules` | yes | TNH | `e2e/tests/alerts-rules-events.api.spec.ts:14` |
| PUT `/api/v1/alerts/rules/defaults/{alertType}` | yes | TNH | `e2e/tests/alerts-rules-events.api.spec.ts:30` |
| PUT `/api/v1/alerts/rules/overrides/{scopeType}/{scopeId}/{alertType}` | yes | TNH | `e2e/tests/alerts-rules-events.api.spec.ts:53` |
| POST `/api/v1/work-orders` | yes | TNH | `e2e/tests/work-orders.api.spec.ts:19` |
| GET `/api/v1/work-orders` | yes | TNH | `e2e/tests/work-orders.api.spec.ts:37` |
| GET `/api/v1/work-orders/{id}` | yes | TNH | `e2e/tests/work-orders.api.spec.ts:48` |
| POST `/api/v1/work-orders/{id}/transition` | yes | TNH | `e2e/tests/work-orders.api.spec.ts:56` |
| POST `/api/v1/work-orders/{id}/assign` | yes | TNH | `e2e/tests/work-orders.api.spec.ts:118` |
| POST `/api/v1/work-orders/{id}/notes` | yes | TNH | `e2e/tests/work-orders.api.spec.ts:65` |
| POST `/api/v1/work-orders/{id}/photos` | yes | HWM | `backend/src/test/java/com/croh/security/OrgScopeAuthorizationIntegrationTest.java:313` |
| POST `/api/v1/work-orders/{id}/post-incident-review` | yes | TNH | `e2e/tests/work-orders.api.spec.ts:82` |
| GET `/api/v1/reports/metric-definitions` | yes | TNH | `e2e/tests/reports-exports.api.spec.ts:23` |
| GET `/api/v1/reports/templates` | yes | TNH | `e2e/tests/reports-exports.api.spec.ts:55` |
| GET `/api/v1/reports/executions` | yes | TNH | `e2e/tests/reports-exports.api.spec.ts:102` |
| POST `/api/v1/reports/metric-definitions` | yes | TNH | `e2e/tests/reports-exports.api.spec.ts:32` |
| POST `/api/v1/reports/templates` | yes | TNH | `e2e/tests/reports-exports.api.spec.ts:75` |
| POST `/api/v1/reports/templates/{id}/execute` | yes | TNH | `e2e/tests/reports-exports.api.spec.ts:91` |
| GET `/api/v1/reports/executions/{id}/download` | yes | TNH (conditional) | `e2e/tests/reports-exports.api.spec.ts:117` |
| GET `/api/v1/reports/data-quality` | yes | TNH | `e2e/tests/reports-exports.api.spec.ts:163` |
| GET `/api/v1/exports/{id}` | yes | TNH | `e2e/tests/reports-exports.api.spec.ts:109` |
| GET `/api/v1/analytics/operations-summary` | yes | TNH | `e2e/tests/reports-exports.api.spec.ts:171` |
| GET `/api/v1/admin/audit-logs` | yes | TNH | `e2e/tests/policies-audit-passwordreset.api.spec.ts:58` |

### API Test Classification

1) **True No-Mock HTTP**
- All `e2e/tests/*.api.spec.ts` files (new and existing), e.g.:
  - `e2e/tests/admin-roles.api.spec.ts`
  - `e2e/tests/reports-exports.api.spec.ts`
  - `e2e/tests/work-orders.api.spec.ts`
- Transport is real HTTP through Playwright request context (`e2e/playwright.config.ts:11`), no API interception found.

2) **HTTP with Mocking**
- Spring Boot + MockMvc integration tests, e.g.:
  - `backend/src/test/java/com/croh/auth/AuthIntegrationTest.java:42`
  - `backend/src/test/java/com/croh/resources/ResourceFileDownloadIntegrationTest.java:35`

3) **Non-HTTP unit/integration**
- Service/unit tests, e.g.:
  - `backend/src/test/java/com/croh/auth/AuthServiceTest.java:37`
  - `backend/src/test/java/com/croh/audit/AuditServiceTest.java:16`
  - `backend/src/test/java/com/croh/crypto/EncryptionServiceTest.java:11`

### Mock Detection

- **Backend unit mocks (expected for unit tests):**
  - Mockito extension + mocks in `backend/src/test/java/com/croh/auth/AuthServiceTest.java:37`
  - Mockito mocks in `backend/src/test/java/com/croh/audit/AuditServiceTest.java:16`
- **Backend HTTP transport simulation (not true wire HTTP):**
  - MockMvc usage in many integration tests, e.g. `backend/src/test/java/com/croh/auth/AuthIntegrationTest.java:48`
- **API E2E mocking:** none detected in `.api.spec.ts` (no `page.route`, `route.fulfill`, `vi.mock`, `jest.mock`, `sinon.stub`).

### Coverage Summary

- Total endpoints: **80**
- Endpoints with HTTP tests (any): **80**
- Endpoints with true no-mock HTTP tests: **72**
  - (`GET /api/v1/reports/executions/{id}/download` counted as conditional/weak true-HTTP due runtime branch)
- HTTP coverage: **100.0%**
- True API coverage: **90.0%**

### Unit Test Summary

- Unit/non-HTTP test coverage present for:
  - auth service (`backend/src/test/java/com/croh/auth/AuthServiceTest.java`)
  - audit service (`backend/src/test/java/com/croh/audit/AuditServiceTest.java`)
  - encryption (`backend/src/test/java/com/croh/crypto/EncryptionServiceTest.java`)
  - SLA/date logic (`backend/src/test/java/com/croh/account/AppealSlaComputationTest.java`)
  - error contract (`backend/src/test/java/com/croh/common/ErrorContractTest.java`)
  - alert duration algorithm (`backend/src/test/java/com/croh/alerts/DurationEvaluationUnitTest.java`)
- Important modules still relatively light on isolated unit tests:
  - `ReportService` execution/export edge cases
  - `WorkOrderService` transitions/assignment logic in pure unit form
  - `VerificationService` org-doc decision/download paths (outside HTTP tests)

### Tests Check

- `run_tests.sh` remains Docker-based for backend, frontend, and e2e (`run_tests.sh:11`, `run_tests.sh:23`, `run_tests.sh:36`, `run_tests.sh:45`) -> **OK** for Docker-contained policy.

### Test Coverage Score (0–100)

- **92/100**

### Score Rationale

- Major improvement: previously missing core endpoints now have real HTTP API tests.
- Coverage depth improved with positive + negative + validation assertions across new API specs.
- All endpoints now have at least HTTP-level coverage.
- Deduction applied for:
  - a small set still only MockMvc for true-wire criteria (`active-role`, multipart/media endpoints, file endpoints, photo upload)
  - one conditional call path (`/reports/executions/{id}/download`) that may not always execute.

### Key Gaps

- True no-mock gaps (still HWM-only):
  - `PUT /api/v1/accounts/me/active-role`
  - `POST /api/v1/verification/org-documents`
  - `GET /api/v1/admin/verification/org-document/{id}/download`
  - `POST /api/v1/resources/upload`
  - `POST /api/v1/resources/files/{id}/download`
  - `GET /api/v1/resources/{id}/file`
  - `POST /api/v1/work-orders/{id}/photos`
- Conditional/weak path:
  - `GET /api/v1/reports/executions/{id}/download` in `e2e/tests/reports-exports.api.spec.ts:117` runs only when `exportFilePath` exists.

### Confidence & Assumptions

- Confidence: **high** on endpoint-to-test static mapping.
- Assumptions:
  - Controller annotations remain source-of-truth for endpoint surface.
  - Playwright API requests hit running backend via configured baseURL.
  - Conditional branches were evaluated conservatively where runtime outcome is uncertain.

---

## 2. README Audit

### High Priority Issues

- None.

### Medium Priority Issues

- None.

### Low Priority Issues

- Optional clarity improvement only: explicitly note that `init_db.sh` is optional and not required for normal Docker startup (it is already not in startup steps).

### Hard Gate Failures

- None detected.

### README Verdict (PASS / PARTIAL PASS / FAIL)

- **PASS**

Evidence highlights:
- Project type declared at top: `README.md:3`
- Required startup command form included: `README.md:18`
- Access URL/port method documented: `README.md:30`, `README.md:36`
- Verification method included (API + UI): `README.md:76`, `README.md:96`
- Auth credentials/roles documented: `README.md:55`

---

**Final Verdicts**
- **Test Coverage Audit:** **PASS** (score **92/100**, true no-mock coverage at **90.0%**).
- **README Audit:** **PASS**.
