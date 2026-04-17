## 1. Test Coverage Audit

- Audit mode: **static inspection only**.
- Scope inspected: `backend/src/main/java/**/*Controller.java`, `backend/src/test/java/**/*.java`, `e2e/tests/*.api.spec.ts`, `README.md`, `run_tests.sh`, `api_tests/run.sh`, `e2e/seed-e2e-data.sh`.

### Backend Endpoint Inventory

- Total endpoints inventoried: **80**.

### API Test Mapping Table

Legend:
- `TNH` = true no-mock HTTP (real HTTP request to running app)
- `HWM` = HTTP with transport simulation (MockMvc)

| Endpoint | Covered | Type | Evidence |
|---|---:|---|---|
| POST `/api/v1/auth/register` | yes | TNH | `e2e/tests/helpers.ts:11` |
| POST `/api/v1/auth/login` | yes | TNH | `e2e/tests/helpers.ts:41` |
| POST `/api/v1/auth/logout` | yes | TNH | `e2e/tests/auth-account.api.spec.ts:47` |
| GET `/api/v1/auth/me` | yes | TNH | `e2e/tests/helpers.ts:118` |
| POST `/api/v1/admin/password-resets` | yes | TNH | `e2e/tests/policies-audit-passwordreset.api.spec.ts:90` |
| POST `/api/v1/accounts/me/role-requests` | yes | TNH | `e2e/tests/admin-roles.api.spec.ts:19` |
| GET `/api/v1/accounts/me/roles` | yes | TNH | `e2e/tests/admin-roles.api.spec.ts:118` |
| PUT `/api/v1/accounts/me/active-role` | yes | TNH | `e2e/tests/remaining-endpoints.api.spec.ts:33` |
| GET `/api/v1/admin/roles/pending` | yes | TNH | `e2e/tests/admin-roles.api.spec.ts:31` |
| POST `/api/v1/admin/roles/{membershipId}/decision` | yes | TNH | `e2e/tests/admin-roles.api.spec.ts:68` |
| POST `/api/v1/admin/blacklist` | yes | TNH | `e2e/tests/admin-blacklist-appeals.api.spec.ts:26` |
| GET `/api/v1/admin/appeals` | yes | TNH | `e2e/tests/admin-blacklist-appeals.api.spec.ts:71` |
| POST `/api/v1/admin/appeals/{appealId}/decision` | yes | TNH | `e2e/tests/admin-blacklist-appeals.api.spec.ts:79` |
| GET `/api/v1/appeals/my-blacklist` | yes | TNH | `e2e/tests/admin-blacklist-appeals.api.spec.ts:43` |
| POST `/api/v1/appeals` | yes | TNH | `e2e/tests/admin-blacklist-appeals.api.spec.ts:59` |
| POST `/api/v1/verification/person` | yes | TNH | `e2e/tests/admin-verification.api.spec.ts:17` |
| POST `/api/v1/verification/org-documents` | yes | TNH | `e2e/tests/remaining-endpoints.api.spec.ts:52` |
| GET `/api/v1/admin/verification/queue` | yes | TNH | `e2e/tests/admin-verification.api.spec.ts:28` |
| POST `/api/v1/admin/verification/person/{verificationId}/decision` | yes | TNH | `e2e/tests/admin-verification.api.spec.ts:48` |
| POST `/api/v1/admin/verification/org-document/{documentId}/decision` | yes | TNH | `e2e/tests/admin-verification.api.spec.ts:63` |
| GET `/api/v1/admin/verification/org-document/{documentId}/download` | yes | TNH | `e2e/tests/remaining-endpoints.api.spec.ts:67` |
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
| POST `/api/v1/resources/upload` | yes | TNH | `e2e/tests/remaining-endpoints.api.spec.ts:77` |
| GET `/api/v1/resources` | yes | TNH | `e2e/tests/resources-rewards.api.spec.ts:8` |
| GET `/api/v1/resources/{id}` | yes | TNH | `e2e/tests/resources-detail.api.spec.ts:39` |
| POST `/api/v1/resources/{id}/claim` | yes | TNH | `e2e/tests/resources-detail.api.spec.ts:71` |
| POST `/api/v1/resources/files/{id}/download` | yes | TNH | `e2e/tests/remaining-endpoints.api.spec.ts:101` |
| GET `/api/v1/resources/{id}/file` | yes | TNH | `e2e/tests/remaining-endpoints.api.spec.ts:109` |
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
| POST `/api/v1/work-orders/{id}/photos` | yes | TNH | `e2e/tests/remaining-endpoints.api.spec.ts:131` |
| POST `/api/v1/work-orders/{id}/post-incident-review` | yes | TNH | `e2e/tests/work-orders.api.spec.ts:82` |
| GET `/api/v1/reports/metric-definitions` | yes | TNH | `e2e/tests/reports-exports.api.spec.ts:23` |
| GET `/api/v1/reports/templates` | yes | TNH | `e2e/tests/reports-exports.api.spec.ts:55` |
| GET `/api/v1/reports/executions` | yes | TNH | `e2e/tests/reports-exports.api.spec.ts:102` |
| POST `/api/v1/reports/metric-definitions` | yes | TNH | `e2e/tests/reports-exports.api.spec.ts:32` |
| POST `/api/v1/reports/templates` | yes | TNH | `e2e/tests/reports-exports.api.spec.ts:75` |
| POST `/api/v1/reports/templates/{id}/execute` | yes | TNH | `e2e/tests/reports-exports.api.spec.ts:91` |
| GET `/api/v1/reports/executions/{id}/download` | yes | TNH | `e2e/tests/reports-exports.api.spec.ts:156` |
| GET `/api/v1/reports/data-quality` | yes | TNH | `e2e/tests/reports-exports.api.spec.ts:169` |
| GET `/api/v1/exports/{id}` | yes | TNH | `e2e/tests/reports-exports.api.spec.ts:163` |
| GET `/api/v1/analytics/operations-summary` | yes | TNH | `e2e/tests/reports-exports.api.spec.ts:177` |
| GET `/api/v1/admin/audit-logs` | yes | TNH | `e2e/tests/policies-audit-passwordreset.api.spec.ts:58` |

### API Test Classification

1) **True No-Mock HTTP**
- `e2e/tests/*.api.spec.ts` uses real HTTP over Playwright request context.
- Evidence: `e2e/playwright.config.ts` baseURL configuration and direct `request.get/post/put/patch` calls.

2) **HTTP with Mocking**
- Backend MockMvc tests still exist (transport simulated), e.g. `backend/src/test/java/com/croh/auth/AuthIntegrationTest.java:48`.

3) **Non-HTTP (unit/integration without HTTP)**
- Unit/service tests still exist, e.g. `backend/src/test/java/com/croh/auth/AuthServiceTest.java:37`.

### Mock Detection

- Detected in backend/unit layers:
  - Mockito usage in `backend/src/test/java/com/croh/auth/AuthServiceTest.java:37`
  - Mockito usage in `backend/src/test/java/com/croh/audit/AuditServiceTest.java:16`
- API E2E suite (`e2e/tests/*.api.spec.ts`): no request interception mocking detected.

### Coverage Summary

- Total endpoints: **80**
- Endpoints with HTTP tests: **80**
- Endpoints with true no-mock API tests: **80**
- HTTP coverage: **100.0%**
- True API coverage: **100.0%**

### Unit Test Summary

- Unit-focused coverage present in:
  - `backend/src/test/java/com/croh/auth/AuthServiceTest.java`
  - `backend/src/test/java/com/croh/audit/AuditServiceTest.java`
  - `backend/src/test/java/com/croh/crypto/EncryptionServiceTest.java`
  - `backend/src/test/java/com/croh/account/AppealSlaComputationTest.java`
  - `backend/src/test/java/com/croh/common/ErrorContractTest.java`
  - `backend/src/test/java/com/croh/alerts/DurationEvaluationUnitTest.java`
- Important modules still relatively lighter in pure unit isolation (but API-covered):
  - multipart/file workflows in `ResourceService`
  - report execution/export edge branches in `ReportService`

### Tests Check

- `run_tests.sh` and `api_tests/run.sh` now include explicit E2E seed step before Playwright:
  - `run_tests.sh:45`
  - `api_tests/run.sh:20`
- `e2e/seed-e2e-data.sh` now dynamically resolves compose db container + network.

### Test Coverage Score (0–100)

- **97/100**

### Score Rationale

- Full endpoint map has true no-mock HTTP coverage (80/80).
- Strong positive + negative + validation assertions across new API specs.
- Remaining deduction is for maintainability risk from heavier seeded/admin coupling and multipart test complexity, not endpoint sufficiency.

### Key Gaps

- No endpoint coverage gaps remain.
- Operational risk (not coverage risk): seeded-account dependency must stay stable for E2E reliability.

### Confidence & Assumptions

- Confidence: **high** for static coverage mapping.
- Assumptions:
  - Controller annotations remain the authoritative endpoint surface.
  - True-no-mock definition is satisfied by Playwright request-layer tests against live stack.

---

## 2. README Audit

### High Priority Issues

- None.

### Medium Priority Issues

- None.

### Low Priority Issues

- None material.

### Hard Gate Failures

- None detected.

### README Verdict (PASS / PARTIAL PASS / FAIL)

- **PASS**

Evidence highlights:
- Type declaration: `README.md:3`
- Startup instruction includes required form: `README.md:18`
- Access URL + port method: `README.md:30`, `README.md:36`
- Verification method: `README.md:76`, `README.md:96`
- Auth credentials and role coverage: `README.md:55`, `README.md:61`

---

**Final Verdicts**
- **Test Coverage Audit:** **PASS** (true API coverage **100.0%**, score **97/100**).
- **README Audit:** **PASS**.
