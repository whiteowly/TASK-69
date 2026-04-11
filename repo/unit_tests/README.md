# Unit and Component Tests

Unit-level and component-level tests that run without external services.

## Backend Unit Tests (subset of Spring Boot test suite)

The backend integration tests in `backend/src/test/java/com/croh/` include both pure unit tests (state machine logic, policy evaluators, validators, encryption) and integration tests (full HTTP through MockMvc). All run against in-memory H2 with no external dependencies.

Key unit-focused test areas:

| Area | What it tests |
|------|--------------|
| State machines | Reward fulfillment transitions (ORDERED→ALLOCATED→PACKED→SHIPPED→DELIVERED), work order transitions (NEW_ALERT→ACKNOWLEDGED→...→CLOSED), exception lifecycle |
| Policy evaluation | Household claim limits, per-user download limits, rolling window enforcement |
| Crypto | AES-GCM field encryption/decryption, checksum duplicate detection |
| Validation | Username format, password rules, upload type/size, address format, state-transition preconditions |
| Auth | BCrypt hashing, lockout counter, session lifecycle |
| Audit | Event emission for all privileged actions |

### Running

```bash
docker run --rm -v "$(pwd)/backend":/app -w /app maven:3.9-eclipse-temurin-17 mvn test -Dspring.profiles.active=test
```

## Frontend Component Tests (Vitest + Vue Test Utils)

Located in `frontend/src/views/__tests__/` — 68 tests across 20 test files.

These tests mount individual Vue components with mocked API clients and Pinia stores, verifying:
- Form rendering and validation behavior
- API call contracts (correct paths, payloads, methods)
- Success/error state transitions
- Role-based UI gating
- Navigation guard behavior

### Test files

| File | Component | Key assertions |
|------|-----------|---------------|
| `RegisterView.spec.ts` | Registration form | form fields, submit, success/error states |
| `VerificationSubmit.spec.ts` | Person verification + org upload | form fill, API path, submit states |
| `VerificationQueue.spec.ts` | Admin verification queue | item rendering, decision submit |
| `EventManagement.spec.ts` | Org event creation | form fields, create API, list |
| `EventBrowse.spec.ts` | Participant event list + register | event display, register button |
| `ResourceBrowse.spec.ts` | Participant resource claim/download | claim/download buttons, policy feedback |
| `RewardCatalog.spec.ts` | Reward ordering | order form, fulfillment type, API path |
| `WorkOrderPanel.spec.ts` | Work order management | transition buttons, notes, SLA display |
| `AlertRuleConfig.spec.ts` | Alert rule editing | rule loading, threshold editing |
| `ReportPanel.spec.ts` | Report creation + execution | metric/template creation, execution, download link |
| `FulfillmentPanel.spec.ts` | Order fulfillment management | order listing, transition |
| `RoleManagement.spec.ts` | Role request | request form, role list |
| `RoleApprovalPanel.spec.ts` | Admin role approval | approval queue, decision |
| `PolicyManagement.spec.ts` | Usage policy creation | policy form, API path |
| `RegistrationReview.spec.ts` | Admin registration review | pending list, approve/deny |
| `AppealReviewPanel.spec.ts` | Admin appeal review | appeal queue |
| `AppealView.spec.ts` | Participant appeal submission | appeal form |

### Running

```bash
# Via run_tests.sh (recommended)
./run_tests.sh

# Directly
docker run --rm -v "$(pwd)/frontend":/app -w /app node:20-alpine sh -c "npm ci --ignore-scripts && npx vitest run"
```
