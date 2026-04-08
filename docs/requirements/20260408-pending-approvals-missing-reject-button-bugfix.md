# 20260408 - Pending approvals missing reject button bugfix

## 1. User requirement

### Requirement description
Investigate and fix the bug where an approver user cannot see the **Reject** button on the `pending-approvals` screen while trying to process another user's decryption approval request.

The reported case is:
- Requester: Kim Cheolsu
- Approver login user: Hong Gildong
- Screen: Decryption Approval Management (`pending-approvals`)
- Symptom: **Reject button is missing** for the target approval item.

### User scenario
1. User A (Kim Cheolsu) submits a decryption approval request.
2. User B (Hong Gildong), who is expected to approve/reject, logs in.
3. User B opens `pending-approvals` and locates the request from User A.
4. **Problem**: The row does not show the **Reject** button (and may not show approval actions as expected), so User B cannot complete rejection.

### Expected outcome
- For a row that is eligible for approver action, both **Approve** and **Reject** controls are visible to an authorized approver.
- For rows not eligible for action (for example non-`PENDING` status), the UI behavior is consistent with contract and clearly explainable by logs/evidence.
- Visibility and execution authorization are enforced server-side as well as UI-gated, with no privilege escalation.
- Root cause is confirmed from diagnostic evidence (not hypothesis), then fixed.

## 2. Design

### 2.1 Security review (optional; when PII / decryption / access control)
- [x] Security review performed (decryption approval + access control)
- Security invariants (must hold before and after fix):
  - **Invariant S1 (server authority):** UI visibility is advisory only; backend authorization is authoritative for `POST /api/search-history/{id}/approve` and `POST /api/search-history/{id}/reject`.
  - **Invariant S2 (non-approver deny):** A non-approver must always receive `403` with contract-compliant denial code on direct approve/reject API calls.
  - **Invariant S3 (scope boundary):** Cross-department access outside effective scope (`self`/`team`/`all`) must be denied; disallowed cross-department approve/reject attempts must return `403` with contract-compliant code.
  - **Invariant S4 (least disclosure):** The UI must not reveal action affordances that cannot be executed for the current effective permission/scope.
  - **Invariant S5 (contract consistency):** Interceptor/controller/service decisions must remain aligned with `docs/contract.md` and screen/function semantics.
- Diagnostic logging policy (mandatory constraints):
  - **Allowlist fields only:** timestamp, request path, method, actor `userId`, actor `username`, role flags (`isSystemAdmin`, `canApprove`), `screenId`, effective scope, target `searchHistoryId`, normalized `approvalStatus`, final decision (`ALLOW`/`DENY`), denial code.
  - **Denylist (must not log):** raw decrypt payload, plaintext/masked sensitive log contents, request reason full text, approved snapshot row bodies, token/session secrets/cookies, personally sensitive free-text fields.
  - Logs must run at DEBUG level and/or dev-only flag, and temporary verbose diagnostics must be removed or downgraded after verification.
- Security acceptance criteria:
  - Reject button visibility bug is fixed without relaxing permission/scope checks.
  - Negative-path API tests (`non-approver`, `cross-department disallowed`) pass with contract-compliant `403`.
  - Log review confirms denylist safety (no sensitive data leakage).

### Technical design

#### Codebase summary
- Frontend `pending-approvals` action buttons are currently rendered under `canApprove && pendingRow` in `frontend/src/components/PendingApprovals/PendingApprovals.js`.
- `canApprove` is derived from `screenFunctions['pending-approvals'].approve`, and row status is mapped from `approvalStatus`.
- Backend authorization and screen access are enforced in `backend/src/main/java/com/logmng/config/ScreenAccessInterceptor.java` and `backend/src/main/java/com/logmng/controller/SearchHistoryController.java`.
- Recent requirement baseline exists in `docs/requirements/20260407-pending-approvals-history-search-readonly-requester.md`; this bugfix must remain consistent with that behavior and contract.

#### Problem analysis
1. The symptom can originate from multiple layers: frontend visibility gate (`canApprove`, row status detection), payload/status mapping mismatch, or backend/session permission metadata mismatch.
2. Current behavior does not provide enough evidence to determine whether the row was non-actionable by design or incorrectly hidden.
3. Because this is decryption approval + access-control scope, cause must be proven with logs from both UI and backend permission resolution path.

#### Diagnostic phase (mandatory for error/bug fix only)
Phase 0 must be completed before any logic fix:
1. Add temporary diagnostic logging at suspected points:
   - Frontend: per-row action visibility decision (`canApprove`, `approvalStatus`, computed `pendingRow`, user screenFunctions snapshot).
   - Backend: effective user identity, screen access decision, approve/reject eligibility checks, and relevant denial reasons/codes.
2. Reproduce the reported scenario (Kim Cheolsu request + Hong Gildong approver login) and capture logs.
3. Analyze logs to confirm the actual root cause (for example: wrong permission metadata, wrong status mapping, stale session payload, incorrect UI condition).
4. Only after cause confirmation from evidence, implement the fix.

Production safety for diagnostics:
- Diagnostic logs must be DEBUG-level and/or behind a dev-only feature flag.
- Any temporary verbose diagnostics must be removed or downgraded after fix verification.
- No sensitive request payload values beyond minimum troubleshooting fields should be logged.

#### Solution approach
**Frontend:**
- Add diagnostic instrumentation for action-button rendering decision path.
- Verify and, if needed, correct action visibility conditions so authorized approvers see both approve/reject on actionable rows.
- Preserve read-only behavior for non-approvers exactly as contract/previous requirement intended.
- Add/extend tests covering:
  - Approver sees both buttons on actionable row.
  - Non-approver does not see approval actions.
  - Status mapping edge cases do not incorrectly hide reject action.

**Backend:**
- Add diagnostic instrumentation for access and permission resolution for pending-approvals approval actions.
- Verify `screenFunctions`/effective permission derivation and endpoint authorization consistency with contract.
- If root cause is backend-side permission/session metadata inconsistency, implement minimal fix and keep denial codes consistent.

**DB:**
- No schema change expected.
- If evidence shows data inconsistency in approval status/source fields, define a separate DB-focused follow-up requirement.

### Affected scopes and change targets (verification)
| Scope | Affected? | §2 subsection and change file list complete? |
|-------|------------|-----------------------------------------------|
| Backend | [x] Yes | [x] |
| Frontend (config UI + view screen) | [x] Yes (view screen) | [x] |
| DB | [ ] Yes / [x] No (initial expectation) | [x] |
| Contract / Spec | [ ] Yes / [x] No (unless root cause requires contract update) | [x] |
| Cursor tools (skills, specs) | [ ] Yes / [x] No | [x] |

### Planned change file list (expected change targets)
(Planned at authoring. Implementing agent confirms or amends when implementation is complete.)

#### Frontend
- `frontend/src/components/PendingApprovals/PendingApprovals.js`
  - Add temporary diagnostic logs for button visibility decisions and apply minimal fix after root-cause confirmation.
- `frontend/src/components/PendingApprovals/PendingApprovals.test.js`
  - Add/adjust regression tests for reject-button visibility and role/status permutations.
- `frontend/src/utils/logger.js` (optional)
  - Reuse existing logger behavior for debug-only diagnostics if needed.
- Frontend implementation confirmation (2026-04-08):
  - Changed: `frontend/src/components/PendingApprovals/PendingApprovals.js`
  - Changed: `frontend/src/components/PendingApprovals/PendingApprovals.test.js`
  - Not changed: `frontend/src/utils/logger.js` (existing logger reused)

#### Backend
- `backend/src/main/java/com/logmng/controller/SearchHistoryController.java`
  - Add diagnostic logs around approval/reject eligibility and request-user context resolution.
- `backend/src/main/java/com/logmng/config/ScreenAccessInterceptor.java`
  - Add diagnostic logs for screen access decision path relevant to pending approvals.
- `backend/src/main/java/com/logmng/service/AuthService.java` (optional, if root cause points here)
  - Verify/adjust effective approve/read resolution used by pending-approvals.
- `backend/src/test/java/com/logmng/config/ScreenAccessInterceptorTest.java`
  - Add/adjust regression tests for permission/access behavior tied to this bug.
- `backend/src/test/java/com/logmng/controller/SearchHistoryControllerTest.java`
  - Add/adjust tests for approve/reject eligibility and response path.

#### DB
- None planned.

## 3. Test approach

### Test case list (required)
| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|----|-------|------|------------------------------|-----------------|--------------------------------------------|
| TC-01 | Frontend | Normal | Approver user with `pending-approvals.approve=true`, row `approvalStatus=PENDING` | Both Approve and Reject buttons are visible | Unit (npm test) |
| TC-02 | Frontend | Exception | Non-approver user (`approve=false`) on same row | No Approve/Reject buttons shown | Unit (npm test) |
| TC-03 | Frontend | Edge | Row status mapping variant (snake/camel case field permutations) | Actionability determination remains correct; reject is not hidden by mapping bug | Unit (npm test) |
| TC-04 | Backend | Normal | Approver calls reject endpoint for actionable request | 200 success with contract-compliant response | Unit/Integration (mvn test) |
| TC-05 | Backend | Exception | **Direct API call by non-approver** to `/api/search-history/{id}/reject` (and `/approve`) | `403` with contract-compliant denial code | Unit/Integration (mvn test) |
| TC-06 | Backend | Exception | **Cross-department disallowed** actor attempts approve/reject for out-of-scope request | `403` with contract-compliant denial code; no state change | Unit/Integration (mvn test) |
| TC-07 | Integration | Normal | Reproduce reported flow: Kim request, Hong login, pending-approvals list row | Reject button is visible and rejection can be completed | Manual/Browser |
| TC-08 | Integration | Diagnostic evidence | Same reproduction with diagnostics enabled | Logs show confirmed root cause path and post-fix expected decision path | Manual (log review) |
| TC-09 | Security | Exception | Inspect diagnostic logs produced during TC-05/TC-06/TC-08 | No denylist-sensitive data appears in logs | Manual (log review checklist) |

### Test scenarios
#### Scenario 1: Reported user flow reproduction
1. Prepare a pending decryption approval request for Kim Cheolsu.
2. Log in as Hong Gildong (approver profile).
3. Open `pending-approvals` and locate Kim's row.
4. Confirm Reject button visibility and rejection completion.

#### Scenario 2: Authorization boundary
1. Log in as a user without approve permission.
2. Open the same screen and row set.
3. Confirm approval actions are not visible and backend denies direct approve/reject attempts.

#### Scenario 3: Cross-department deny
1. Prepare a target request outside actor's effective department scope.
2. Attempt direct approve/reject API call with authenticated but out-of-scope actor.
3. Verify `403` with contract-compliant code and unchanged request state.

#### Scenario 4: Diagnostic log safety
1. Enable diagnostic mode in dev.
2. Run negative-path tests (non-approver direct API, cross-department deny).
3. Verify logs include only allowlist fields and exclude denylist-sensitive content.

### Test data
- At least one `PENDING` request row owned by Kim Cheolsu that is within Hong Gildong's allowed scope.
- At least one non-`PENDING` row to verify non-actionable rendering remains consistent.
- TODO: Provide deterministic seed SQL/data setup commands for QA if current dataset is unstable.

### Test environment
- Frontend: `http://localhost:3001`
- Backend: `http://localhost:9200`
- Database: PostgreSQL

## 4. Checklist

### Frontend verification
- [x] API parameters validated
- [x] UI behavior confirmed
- [x] Error handling verified

### Backend verification
- [x] API test cases written and run
- [x] Logs checked
- [ ] Performance checked (if applicable)

### Integration
- [ ] End-to-end flow tested
- [ ] Edge cases tested

### Documentation
- [x] Requirement doc completed
- [ ] Code comments added (if applicable)

### Implementation gate (Step 1 handoff readiness)
- [x] PASS — §1 (user requirement), §2 (design/security/diagnostic constraints), and §3 (test case list) are complete for implementation handoff.

## 5. Test results
### Test run date
- 2026-04-08

### Test results
- Frontend scope (pending-approvals visibility bugfix):
  - Added dev-only DEBUG diagnostics around per-row action visibility decisions
    (`REACT_APP_PA_DEBUG_VISIBILITY=true`).
  - Root-cause evidence (diagnostic-first): prior strict pending check hid actions when payload used
    lowercase/snake-case status variant (`approval_status='pending'`) despite approver permission.
  - Minimal fix: normalize status (`trim().toUpperCase()`) before actionable check.
  - Frontend test command:
    - `cd frontend && npm test -- --watchAll=false --runInBand src/components/PendingApprovals/PendingApprovals.test.js` → PASS (1 suite, 8 tests)
  - Frontend covered cases:
    - TC-01: approver + pending row -> Approve/Reject visible
    - TC-02: non-approver -> actions hidden
    - TC-03: status field/casing variant -> actions still visible for approver
  - Additional finding/update (2026-04-08, follow-up diagnosis for "still hidden reject button"):
    - Root cause was also confirmed as a **CSS collision**: `PendingApprovals` inherits `.search-history-list .log-table td:nth-child(9)` width cap (`max-width: 72px`) from `SearchHistory.css`.
    - In `pending-approvals`, column 9 is the action column (상세/승인/반려). Combined with DataTable base cell clipping (`overflow: hidden; text-overflow: ellipsis; white-space: nowrap`), the Reject button could be visually clipped even when rendered in DOM.
    - Minimal frontend fix: add `PendingApprovals.css` scoped override for action column width and clipping behavior.
  - Frontend test update:
    - Added TC-03-2: `approvalStatus=' pending '` (trim + uppercase normalization) still renders Approve/Reject for approver.

- Backend scope (authorization path + approval/reject endpoints):
  - Added DEBUG/dev-gated diagnostic authz decision logs on approve/reject/listPending + interceptor deny/allow path (`app.diagnostic.approval-flow`).
  - Added/updated unit tests for §3 backend negative-path cases:
    - Non-approver direct reject API call returns `403 FUNCTION_NOT_ALLOWED` (TC-05).
    - Out-of-scope/cross-department-equivalent service deny propagation on reject returns `403 FUNCTION_NOT_ALLOWED` (TC-06 boundary).
  - Test commands:
    - `cd backend && mvn -Dtest=SearchHistoryControllerTest,ScreenAccessInterceptorTest test` → PASS
    - `cd backend && mvn test` → PASS
  - Verify commands:
    - `./scripts/dev-services.sh backend restart` → done
    - `curl -s http://localhost:9200/api/health` → `200`, `status=OK`

### QA validation notes (2026-04-08)
- Coverage assessment against §3:
  - TC-01/TC-02/TC-03 (Frontend unit): **Covered** by `PendingApprovals.test.js` and reported as PASS.
  - TC-05/TC-06 (Backend deny boundary): **Covered** by `SearchHistoryControllerTest` and reported as PASS.
  - TC-04 (Backend approver reject success 200): **Partially evidenced** (approve success path exists; reject success-path evidence is not explicitly reported in §5).
  - TC-07/TC-08/TC-09 (Integration/manual/log review): **Not evidenced yet** in §5.
- Missing verification evidence:
  - No browser/manual replay record for reported real-user flow (Kim request + Hong approver login) with pass/fail artifacts (screenshot/video/step log).
  - No captured diagnostic log excerpt showing pre-fix decision path vs post-fix decision path for the same scenario.
  - No explicit denylist safety checklist result (log content review) proving TC-09.
- QA conclusion (current):
  - Unit-level regression signal is positive and fix direction is technically plausible.
  - Requirement cannot be marked fully resolved until TC-07/TC-08/TC-09 evidence is added.

## 6. Error remedy result (cause and action) — for error/bug fix requirements only
- Requirement ID: `20260408-pending-approvals-missing-reject-button-bugfix`
- Root cause: Two frontend-side causes were confirmed. (1) Status-normalization gap in pending-row visibility gate (`approval_status='pending'`/casing variants). (2) CSS collision in shared table styles: `pending-approvals` action column inherited a narrow 9th-column rule from search-history, and base table-cell clipping hid the Reject button visually.
- Actions taken: Added temporary DEBUG/dev-only diagnostic logs, implemented minimal status normalization + action-column CSS override in `PendingApprovals`, and added/updated regression tests; no API/contract behavior change.
- Result: Authorized approver now sees Reject/Approve on actionable rows across status variants, and Reject is no longer clipped by shared table CSS. Backend deny boundaries preserved.
- Completed: Backend + frontend unit scope completed.
- QA status update: **Partially resolved**. Final closure pending TC-07/TC-08/TC-09 integration/manual verification evidence.

## 7. Final version (Korean) — add after all verification is complete
- Pending

---

**Author**: Requirements subagent  
**Date**: 2026-04-08  
**Status**: In progress
