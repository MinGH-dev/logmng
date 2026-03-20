# 20260313-activity-log-statistics-department-option-source-bugfix-1 - backend runtime does not serve shared department filter-options route

**Parent requirement ID**: `20260313-activity-log-statistics-department-option-source`  
**Bugfix sequence**: 1  
**Failure scope**: `backend`

## 1. User requirement

### Requirement description

Formalize the QA verification failure found after the parent requirement implementation: the backend currently running on `http://localhost:9200` must actually serve `GET /api/filter-options/departments?screen={screenId}` for `screenId=activity-log|statistics|search-history` at runtime. This bugfix is **backend-only** at authoring time. It does not change the accepted contract from the parent requirement; it restores the backend runtime so the authored contract is reachable and behaves as already documented.

This bugfix is not a new feature. The parent requirement, design docs, and updated frontend already depend on the shared filter-options route. The failed QA result showed that the runtime backend artifact still responded with `404 Not Found`, which blocked acceptance of the authored behavior.

### User scenario

1. A scoped user opens **User Activity Log**, **Activity Statistics**, or **Search History** after the parent change was implemented.
2. The frontend requests `GET /api/filter-options/departments?screen={screenId}` to populate the Department combo box.
3. The user expects the backend on port `9200` to respond with the shared department option payload defined by the parent requirement.
4. **Problem**: both browser/runtime verification and direct authenticated API probes returned `404 Not Found` for `activity-log`, `statistics`, and `search-history`.
5. Because the shared route was missing at runtime, each affected screen rendered only the local `All / 전체` option and the parent requirement could not be accepted.
6. On `statistics=self`, the user block was hidden visually as expected, but runtime still attempted the new endpoint and received `404`, so the backend contract remained broken even where the UI hid the filter.

### Expected outcome

- The backend process running on `http://localhost:9200` must return `200 OK` for `GET /api/filter-options/departments?screen=activity-log`.
- The backend process running on `http://localhost:9200` must return `200 OK` for `GET /api/filter-options/departments?screen=statistics`.
- The backend process running on `http://localhost:9200` must return `200 OK` for `GET /api/filter-options/departments?screen=search-history`.
- The response payload must match the parent contract: JSON success envelope with `data` as `string[]`, and the backend payload must not contain the local `All / 전체` option.
- The route must remain available to authorized non-admin users of the three in-scope screens; the implementation must not require switching the UI to admin-only `/api/departments`.
- The runtime behavior must satisfy parent scope rules after restart: `self => []`, `team => current user's own department only`, `all` or system admin => all current departments.
- After backend rebuild/restart, QA must be able to rerun the parent failures for TC-02, TC-04, TC-05, TC-06, and TC-07 without receiving `404`.

## 2. Design

### 2.1 Security review (optional; when PII / decryption / access control)

- [ ] Security review performed
- Risks:
  - This bugfix touches a scope-aware shared endpoint. An incorrect runtime fix could widen access, bypass screen checks, or accidentally redirect consumers to an admin-only department API.
  - A stale-artifact fix must not hide a real authorization or routing bug by bypassing the normal authenticated runtime path.
- Acceptance / recommendations:
  - The route must continue to use authenticated screen access validation for `activity-log`, `statistics`, and `search-history`.
  - The fix must preserve the existing contract and scope behavior already authored in the parent requirement and related API docs.

### 2.2 Codebase summary

- `backend/src/main/java/com/logmng/controller/FilterOptionsController.java`
  - Already declares `@RequestMapping("/api/filter-options")` and `@GetMapping("/departments")`, validates `screen`, checks screen access through `AuthService`, and delegates to `FilterOptionsService`.
- `backend/src/main/java/com/logmng/service/FilterOptionsService.java`
  - Already declares scope-aware department option logic: `self => []`, `team => own department only`, `all/system admin => all current departments`.
- `backend/src/test/java/com/logmng/controller/FilterOptionsControllerTest.java`
  - Already covers supported screen routing, invalid screen rejection, and forbidden access for disallowed screens.
- `backend/src/test/java/com/logmng/service/FilterOptionsServiceTest.java`
  - Already covers all/team/self/system-admin behavior using H2-backed test data.
- Parent QA evidence showed a mismatch between authored backend sources and the live runtime behavior on port `9200`.
- The legacy endpoint `GET /api/statistics/departments` still responded with `200` and `[]` during QA, which means the running backend process was alive but did not expose the new shared route expected by the updated frontend.

### Technical design

#### Problem analysis

1. QA reproduced the same failure through both browser/runtime checks and direct authenticated API probes, so this is not a frontend-only request issue.
2. The new backend controller and service already exist in the working tree, but the running backend on port `9200` still returned `404` for all three supported `screen` values. This indicates a runtime-route availability gap, not a parent-requirement wording gap.
3. Because the legacy statistics-specific endpoint still responded, the backend process was not fully down; instead, the running artifact/version likely did not include or register the shared filter-options controller.
4. The self-scope UI hiding on statistics does not close the backend contract. Even if the filter is hidden visually, the runtime endpoint must still exist and return the documented result when requested.
5. Parent acceptance is blocked at the backend layer until the runtime process on port `9200` serves the shared route consistently after rebuild/restart.

#### Likely causes (estimated)

- The backend process on port `9200` is serving a stale artifact or an older packaged jar built before `FilterOptionsController` was added.
- The restart path reused an outdated backend artifact, so the authored sources in the working tree were not the same sources being executed at runtime.
- The shared controller bean was not included in the effective runtime classpath or was not registered during application startup, even though the source file exists.
- The runtime environment may still be booting a legacy backend package/version where only `/api/statistics/departments` is present.

#### Solution approach

**Frontend:**

- No frontend product-code change is planned for this bugfix at authoring time. QA already confirmed that the frontend is calling the authored shared endpoint and reproducing the backend `404`.

**Backend:**

- Verify that the effective runtime artifact used on port `9200` includes `FilterOptionsController` and `FilterOptionsService` from the current working tree.
- Verify that application startup registers the shared mapping `/api/filter-options/departments`.
- Verify that the running backend process is built/restarted from the current sources rather than a stale packaged artifact.
- Preserve the authored contract and scope logic already present in source:
  - `screen=activity-log|statistics|search-history` only
  - `self => []`
  - `team => current user's own department only`
  - `all` or system admin => all current departments
- Preserve screen access validation through the authenticated runtime path; do not replace this with the admin-only `/api/departments` endpoint.
- Add or strengthen backend verification so runtime-route availability after build/restart is covered, not only controller/service unit logic in isolation.

**DB:**

- No schema migration is planned for this bugfix.
- The implementation must use the existing current department dataset and must not regress the authored option-source semantics.

**Contract / Spec:**

- No new API shape is planned. The implementation must preserve the authored contract in:
  - `docs/api-definition.md`
  - `docs/contract.md`
  - `docs/requirements/20260313-activity-log-statistics-department-option-source.md`
- If the backend implementation discovers that runtime packaging or startup behavior needs an explicit workflow/documentation note, the relevant docs must be updated in the same work.

### Affected scopes and change targets (verification)

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|-----------|-----------------------------------------------|
| Backend | [x] Yes | [x] Yes |
| Frontend (config UI + view screen) | [ ] No | [x] N/A |
| DB | [ ] No | [x] N/A |
| Contract / Spec | [ ] No | [x] N/A - preserve existing contract unless runtime packaging clarification is needed |
| Cursor tools (skills, specs) | [ ] No | [x] N/A |

### Planned change file list (confirmed after implementation)

#### Backend

- `backend/src/test/java/com/logmng/LogManagementApplicationTests.java`
  - Added an application-context mapping registration assertion so `/api/filter-options/departments` is validated through the real Spring Boot route table, not only standalone controller tests.
- `backend/src/main/java/com/logmng/controller/FilterOptionsController.java`
  - Verified as the intended controller source for the shared route; no product-code change was required in this bugfix child.
- `backend/src/main/java/com/logmng/service/FilterOptionsService.java`
  - Verified as the effective scope-aware source logic after the rebuilt artifact was deployed; no product-code change was required in this bugfix child.
- `backend/src/main/java/com/logmng/service/AuthService.java`
  - Verified as the effective screen-access guard for the shared route; no product-code change was required in this bugfix child.

#### Frontend

- No implementation in this backend-only bugfix child.

#### DB

- No schema migration and no DB file change in this backend-only bugfix child.

#### Contract / Spec

- No contract/spec shape change.
- Runtime finding confirmed: the 404 was caused by a stale packaged backend artifact that was restarted without rebuilding, not by a contract mismatch.

## 3. Test approach

### Test case list (required)

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|----|-------|------|------------------------------|-----------------|--------------------------------------------|
| TC-01 | Backend / Integration | Regression | After backend build and restart, authenticate as a permitted non-admin user and call `GET /api/filter-options/departments?screen=activity-log` on port `9200` | Response is `200 OK`; route is served at runtime; payload is a success envelope with `data` as `string[]`; no `404` | Integration (`curl`) |
| TC-02 | Backend / Integration | Regression | After backend build and restart, authenticate as a permitted non-admin user and call `GET /api/filter-options/departments?screen=statistics` on port `9200` | Response is `200 OK`; route is served at runtime; payload is a success envelope with `data` as `string[]`; no `404` | Integration (`curl`) |
| TC-03 | Backend / Integration | Regression | After backend build and restart, authenticate as a permitted non-admin user and call `GET /api/filter-options/departments?screen=search-history` on port `9200` | Response is `200 OK`; route is served at runtime; payload is a success envelope with `data` as `string[]`; no `404` | Integration (`curl`) |
| TC-04 | Backend | Normal | `scope=team` user calls `GET /api/filter-options/departments?screen=statistics` and has a known current department | Response contains exactly the user's own department and does not contain local `All / 전체` | Unit + integration |
| TC-05 | Backend | Normal | `scope=self` user calls `GET /api/filter-options/departments?screen=statistics` after restart | Response is `200 OK` with `data=[]`; runtime must not return `404` even if the UI hides the filter | Unit + integration |
| TC-06 | Backend | Normal | `scope=all` or `isSystemAdmin=true` user calls `GET /api/filter-options/departments?screen=activity-log` with multiple current departments present | Response contains all current departments in `string[]` form and does not contain local `All / 전체` | Unit + integration |
| TC-07 | Backend | Exception | Authenticated caller requests `GET /api/filter-options/departments?screen=department-approvers` | Response is `400 Bad Request` with the documented invalid-screen error, not `404` from a missing route | Unit + integration |
| TC-08 | Integration | Regression | Rerun the parent QA evidence with authenticated direct probes for `activity-log`, `statistics`, and `search-history` on the rebuilt backend | All three shared endpoint probes no longer return `404`; parent TC-02 / TC-04 / TC-05 / TC-06 / TC-07 can proceed to behavioral verification | Integration (`curl` + QA rerun) |

### Test scenarios

#### Scenario 1: Runtime route availability on the rebuilt backend

1. Build the backend from the current working tree and restart the backend service that listens on `9200`.
2. Authenticate with a user who is allowed to access `activity-log`, `statistics`, and `search-history`.
3. Call the shared endpoint separately for `screen=activity-log`, `statistics`, and `search-history`.
4. Verify that all three responses are `200 OK` and that none of them return `404 Not Found`.

#### Scenario 2: Scope-aware payloads still match the parent contract

1. Prepare one user with `scope=team`, one user with `scope=self`, and one `all` or system-admin user.
2. Call the shared endpoint for a supported screen after restart.
3. Verify `team => own department only`, `self => []`, `all/system admin => all current departments`.
4. Verify that the backend payload never contains local `All / 전체`.

#### Scenario 3: Invalid screen validation uses the shared controller path

1. Call `GET /api/filter-options/departments?screen=department-approvers` after backend restart.
2. Verify that the request reaches the shared controller path and is rejected as an invalid supported screen.
3. Verify that the result is the documented validation error, not a missing-route `404`.

### Test data

- One authenticated non-admin user who can access all three in-scope screens (`activity-log`, `statistics`, `search-history`).
- One `scope=team` user with a known current department.
- One `scope=self` user for the empty-list contract.
- One `all` or system-admin user for the full department-list contract.
- At least two current departments in the department dataset so `all` behavior is observable.

### Test environment

- Backend: `http://localhost:9200`
- Frontend recheck target after backend fix: `http://localhost:3001`
- Database: project default PostgreSQL dataset with current department data present

### Re-verification conditions from parent QA evidence

The backend bugfix must not be considered complete until the exact parent failures below no longer fail due to route absence:

- Parent QA direct API probes:
  - `GET /api/filter-options/departments?screen=activity-log` -> must no longer return `404`
  - `GET /api/filter-options/departments?screen=statistics` -> must no longer return `404`
  - `GET /api/filter-options/departments?screen=search-history` -> must no longer return `404`
- Parent blocked/failed test cases:
  - `TC-02` must be re-runnable against a live `200` response path
  - `TC-04` must be re-runnable against a live `200` response path
  - `TC-05` must be re-runnable against a live `200` response path
  - `TC-06` must be re-runnable against a live `200` response path
  - `TC-07` must be re-runnable against a live `200` response path

## 4. Checklist

### Backend verification

- [x] Shared route `/api/filter-options/departments` is reachable on the rebuilt backend runtime
- [x] Supported `screen` values (`activity-log`, `statistics`, `search-history`) no longer return `404`
- [x] Scope behavior still matches the parent requirement after restart
- [x] Invalid screen is rejected by controller validation, not by missing route
- [x] Regression tests cover runtime-route availability expectations and parent QA failure shapes

### Integration

- [x] Parent direct API failure evidence rerun on the rebuilt backend
- [x] Parent blocked browser checks are unblocked for QA rerun

### Documentation

- [x] Bugfix child formalized for backend handoff
- [x] §2 planned change file list confirmed or amended after backend implementation
- [x] Parent requirement §5 can reference this bugfix child for backend re-verification status

## 5. Test results

### Carried failure evidence from parent QA

Parent QA result reference: `docs/requirements/20260313-activity-log-statistics-department-option-source.md` §5

- **When**: During QA verification on 2026-03-13 after frontend/backend tests and runtime checks
- **What failed**:
  - Browser/runtime and direct authenticated API probes both returned `404 Not Found` for `GET /api/filter-options/departments?screen={screenId}` on `activity-log`, `statistics`, and `search-history`
  - Department combo boxes therefore rendered only the local `All / 전체` option
  - `statistics=self` hid the user block visually, but runtime still attempted the new endpoint and received `404`
- **Confirmed failure scope**: `backend`
- **Impact at handoff time**:
  - Parent `TC-02`, `TC-04`, `TC-05`, `TC-06`, and `TC-07` failed or remained blocked because the shared backend route was not available at runtime
  - Parent acceptance is blocked until backend rebuild/restart exposes the authored shared endpoint on port `9200`

### Backend bugfix implementation result

#### Root cause confirmed

- The backend source tree already contained `FilterOptionsController` and `FilterOptionsService`, but the running artifact on port `9200` was an older packaged `backend/target/logmng-backend-1.0.0.jar` that did not include those classes.
- `./scripts/dev-services.sh backend restart` only rebuilds when the packaged jar is missing. Because an older jar already existed, restart reused the stale artifact and never packaged the new controller into runtime.
- Evidence:
  - Before rebuild, the running process was `/usr/bin/java -jar target/logmng-backend-1.0.0.jar`.
  - Before rebuild, runtime returned `404` for `GET /api/filter-options/departments?screen=activity-log` while legacy `GET /api/statistics/departments` still returned `200`.
  - Before rebuild, the packaged jar contents did not include `BOOT-INF/classes/com/logmng/controller/FilterOptionsController.class` or `BOOT-INF/classes/com/logmng/service/FilterOptionsService.class`.
  - After `mvn clean package -DskipTests`, the rebuilt jar included both classes and the runtime route responded successfully after restart.

#### Code/test changes

- Added `backend/src/test/java/com/logmng/LogManagementApplicationTests.java` assertion that the real Spring application context registers `/api/filter-options/departments`.
- No backend product-code change was required for `FilterOptionsController`, `FilterOptionsService`, or `AuthService`; the runtime failure was caused by the stale packaged artifact rather than by Java source logic.

#### Commands run

```bash
cd backend && mvn test
cd backend && mvn clean package -DskipTests
./scripts/dev-services.sh backend restart
curl -s -i http://127.0.0.1:9200/api/health
curl -s -c /tmp/user2-filter.cookie -H 'Content-Type: application/json' -d '{"username":"user2","password":"user123"}' http://127.0.0.1:9200/api/auth/login
curl -s -i -b /tmp/user2-filter.cookie 'http://127.0.0.1:9200/api/filter-options/departments?screen=activity-log'
curl -s -i -b /tmp/user2-filter.cookie 'http://127.0.0.1:9200/api/filter-options/departments?screen=statistics'
curl -s -i -b /tmp/user2-filter.cookie 'http://127.0.0.1:9200/api/filter-options/departments?screen=search-history'
curl -s -i -b /tmp/user2-filter.cookie 'http://127.0.0.1:9200/api/filter-options/departments?screen=department-approvers'
curl -s -c /tmp/user1-filter.cookie -H 'Content-Type: application/json' -d '{"username":"user1","password":"user123"}' http://127.0.0.1:9200/api/auth/login
curl -s -i -b /tmp/user1-filter.cookie 'http://127.0.0.1:9200/api/filter-options/departments?screen=statistics'
curl -s -c /tmp/admin-filter.cookie -H 'Content-Type: application/json' -d '{"username":"admin","password":"admin123"}' http://127.0.0.1:9200/api/auth/login
curl -s -i -b /tmp/admin-filter.cookie 'http://127.0.0.1:9200/api/filter-options/departments?screen=activity-log'
```

#### Results

- `mvn test`: Pass
- `mvn clean package -DskipTests`: Pass
- Backend restart: Pass
- Health check `GET /api/health`: `200 OK`
- Rebuilt jar contents: `FilterOptionsController.class` and `FilterOptionsService.class` present
- Runtime API probes:
  - `user2` (`team`) `screen=activity-log` -> `200 OK`, `{"success":true,"data":["영업1팀"]}`
  - `user2` (`team`) `screen=statistics` -> `200 OK`, `{"success":true,"data":["영업1팀"]}`
  - `user2` (`team`) `screen=search-history` -> `200 OK`, `{"success":true,"data":["영업1팀"]}`
  - `user1` (`statistics=self`) `screen=statistics` -> `200 OK`, `{"success":true,"data":[]}`
  - `admin` (`isSystemAdmin=true`) `screen=activity-log` -> `200 OK`, full current department list returned
  - Invalid `screen=department-approvers` -> `400 Bad Request`, code `INVALID_SCREEN_ID` (no longer `404`)

| ID | Result | Note | Detail |
|----|--------|------|--------|
| TC-01 | Pass | Supported route served at runtime after rebuild | `screen=activity-log` returned `200 OK` for authenticated `user2` |
| TC-02 | Pass | Supported route served at runtime after rebuild | `screen=statistics` returned `200 OK` for authenticated `user2` |
| TC-03 | Pass | Supported route served at runtime after rebuild | `screen=search-history` returned `200 OK` for authenticated `user2` |
| TC-04 | Pass | `team` scope behavior preserved | `user2` statistics response contained exactly `["영업1팀"]` |
| TC-05 | Pass | `self` scope behavior preserved | `user1` statistics response returned `data=[]` with `200 OK` |
| TC-06 | Pass | `all/system admin` behavior preserved | `admin` activity-log response returned the full current department list with no local `All / 전체` entry |
| TC-07 | Pass | Invalid screen hits controller validation path | Response was `400` with `INVALID_SCREEN_ID`, not `404` |
| TC-08 | Pass | Parent direct API failure evidence rerun | All three supported probes no longer returned `404` after rebuild/restart |

#### QA handoff note

- Build: `cd backend && mvn test` and `cd backend && mvn clean package -DskipTests` - exit 0.
- Restart: `./scripts/dev-services.sh backend restart` - done.
- §2 change file list confirmed.
- QA verification requested for parent browser checks (`TC-04` through `TC-09`) against the rebuilt backend on port `9200`.

#### Parent QA re-verification outcome

- **When**: 2026-03-13 19:24 KST
- **Result**: Pass
- **What QA confirmed in the parent requirement rerun**:
  - `user2` (`team`) saw `전체 + 영업1팀` on **User Activity Log**, **Activity Statistics**, and **Search History**
  - The browser network log showed `GET /api/filter-options/departments?screen=activity-log|statistics|search-history` returning `200`
  - `user1` (`statistics=self`) saw the shared user-context filter block hidden in **Activity Statistics**
  - Layout regression checks for the aligned user block and compact filter panel passed in the browser rerun
- **Parent doc updated**: `docs/requirements/20260313-activity-log-statistics-department-option-source.md` §4, §5, §6

---

**Author**: Requirements subagent
**Date**: 2026-03-13
**Status**: Completed - backend runtime bug fixed and parent QA re-verified
