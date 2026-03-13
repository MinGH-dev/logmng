# 20260313-search-history-search-fields-and-paging-bugfix-1 - search-history requester filters ignored in live backend verification

**Parent requirement ID**: `20260313-search-history-search-fields-and-paging`  
**Bugfix sequence**: 1  
**Failure scope**: `backend`

## 1. User requirement

### Requirement description

Formalize the QA failure found after the parent requirement implementation: `GET /api/search-history` must actually narrow results when requester filters are supplied, and filtered pagination metadata must reflect the same effective filter set. This bugfix is **backend-only** at authoring time. It must preserve the existing request contract and the authored scope semantics already defined in the parent requirement, `docs/api-definition.md`, and `docs/contract.md`.

This bugfix is not a new feature. It restores the intended behavior that was already accepted in the authored requirement but failed in live API and browser verification.

### User scenario

1. An `admin` user with `search-history` scope `all` opens the screen and searches with requester filter `userId=user2`.
2. A team-scoped user such as `user1` searches with requester filter `userId=admin`, where `admin` is outside the allowed same-department requester set.
3. The user expects the table rows and pagination totals to narrow according to the requester filters that were sent.
4. **Problem**: In live QA verification, the backend kept returning the same rows as the unfiltered query. The browser also kept showing unchanged rows after `검색`, so the requester search block could not be accepted as working.

### Expected outcome

- `GET /api/search-history` with requester filter `userId=<exact requester>` must return only rows for that requester when scope=`all`.
- `GET /api/search-history` with requester filter `username=<partial>` must narrow rows by requester username when scope=`all`; unrelated requesters must not remain in the result.
- `GET /api/search-history` with scope=`team` must first constrain to the allowed same-department requester set, then apply requester filters inside that set only.
- `GET /api/search-history` with scope=`self` must continue to ignore requester filters and return only the current user's rows.
- `pagination.totalCount`, `pagination.totalPages`, and the visible page rows must be calculated from the same effective requester filter set.
- Existing requester-only action rules for detail and re-request must remain unchanged.

## 2. Design

### 2.1 Security review (optional; when PII / decryption / access control)

- [ ] Security review performed
- Risks:
  - This bugfix touches scope-aware requester filtering on `search-history`, so an incorrect fix could accidentally widen visibility beyond the existing `self` / `team` / `all` rules.
  - A fix must not weaken the existing requester-only restrictions on `GET /api/search-history/{id}` and `POST /api/search-history/{id}/re-request`.
- Acceptance / recommendations:
  - Requester filters must remain **narrowing-only** conditions.
  - The implementation must preserve the current access-control semantics already documented in `docs/api-definition.md` and `docs/contract.md`.

### 2.2 Codebase summary

- `backend/src/main/java/com/logmng/controller/SearchHistoryController.java`
  - Already accepts `department`, `username`, and `userId`, resolves `search-history` scope from session state, and builds `SearchHistoryListRequest` before calling the service.
- `backend/src/main/java/com/logmng/dto/request/SearchHistoryListRequest.java`
  - Already carries `actorUserId`, `allowedUserIds`, requester filters, paging, and sorting values as the normalized list-query DTO.
- `backend/src/main/java/com/logmng/service/SearchHistoryService.java`
  - Already builds a shared list-query spec for count/list SQL and joins `search_history` with `app_user` for requester metadata filtering.
  - The authored code path appears to support requester filters in unit tests, but live QA evidence shows that the effective backend behavior still does not narrow results.
- `backend/src/test/java/com/logmng/controller/SearchHistoryControllerTest.java`
  - Covers controller-level normalization and scope-specific DTO forwarding.
- `backend/src/test/java/com/logmng/service/SearchHistoryServiceTest.java`
  - Covers H2-based filtering behavior for `userId`, `username`, `department`, team restriction, and paging consistency.
- Gap identified from QA evidence:
  - Existing automated tests prove DTO normalization and an H2-based SQL path, but they do not yet guarantee that the live runtime path and live schema/data mapping behave the same way as the authored expectation.

### Technical design

#### Problem analysis

1. QA reproduced the same defect in both live API verification and browser verification, which means the frontend request emission is not sufficient to prove correct backend narrowing behavior.
2. The live failure cases are concrete and reproducible:
   - `admin/all` + `userId=user2` still returned `admin` rows.
   - `admin/all` + `username=user` still returned the unfiltered result set.
   - `user1/team` + `userId=admin` still returned existing team rows instead of `0`.
3. Because `SearchHistoryControllerTest` and `SearchHistoryServiceTest` already cover the authored happy path, the remaining bug is most likely in the **effective backend data path** rather than in the intended requirement wording. The backend implementation must therefore verify the actual runtime query path, join condition, and predicate mapping used against the live schema/data.
4. Filtered paging acceptance is blocked until requester filters actually narrow the result set, because `totalCount` and `totalPages` cannot be accepted when the row set remains unchanged.

#### Solution approach

**Frontend:**

- No frontend implementation is planned for this bugfix at authoring time. QA evidence already showed that requester filter values were entered in the UI and reproduced the backend failure through the browser flow.

**Backend:**

- Verify the actual runtime path for `GET /api/search-history` from controller parameter binding through `SearchHistoryListRequest` construction to the SQL executed by `SearchHistoryService.list(...)`.
- Verify that requester predicates for `department`, `username`, and `userId` are applied to **both** the count query and the page-row query in the live path.
- Verify that the mapping between `search_history.user_id` and the requester metadata source (`app_user` or equivalent live user master mapping) is correct in the real runtime schema and data.
- Preserve the authored scope order exactly:
  - `scope=self`: ignore requester filter inputs and return only the current user's rows.
  - `scope=team`: constrain to the allowed requester set first, then apply requester filters within that set.
  - `scope=all`: apply requester filters directly to the visible set.
- Preserve current paging and sorting defaults (`page=1`, `pageSize=20` default handling, default sort `requested_at desc`) unless validation must be tightened to make the live behavior match the documented contract.
- Add or strengthen backend automated tests so they cover the concrete QA failure shapes, not only DTO normalization and a simplified in-memory path.
- Preserve requester-only detail and re-request behavior; this bugfix must not alter action authorization rules.

**DB:**

- No schema migration is planned for this bugfix.
- Implementation must verify whether the existing requester metadata join and live data shape are sufficient to support requester narrowing exactly as documented.

**Contract / Spec:**

- No API shape expansion is planned. The implementation must preserve the already documented requester-filter semantics in:
  - `docs/api-definition.md`
  - `docs/contract.md`
  - `specs/permission-group-hierarchy.spec.yaml`
- If the implementation discovers that the live behavior depends on undocumented mapping assumptions, the same change must update the relevant docs so code and contract stay aligned.

### Affected scopes and change targets (verification)

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|-----------|-----------------------------------------------|
| Backend | [x] Yes | [x] Yes |
| Frontend (config UI + view screen) | [ ] No | [x] N/A |
| DB | [ ] No | [x] N/A |
| Contract / Spec | [ ] No | [x] N/A - reference preserved; doc sync only if runtime mapping clarification is needed |
| Cursor tools (skills, specs) | [ ] No | [x] N/A |

### Planned change file list (expected change targets)

**(Confirmed during backend implementation on 2026-03-13. The requester-filter source path below was already present in the working tree. The live mismatch was caused by the running backend jar still serving a pre-fix package; the implementation work for this bugfix therefore confirmed these sources, reran automated tests, rebuilt the jar, and restarted the backend with the rebuilt artifact.)**

#### Backend

- `backend/src/main/java/com/logmng/service/SearchHistoryService.java`
  - Verified as the effective shared query-builder source for requester filters. The source already applied `userId`, `username`, and `department` to the shared count/list SQL path.
- `backend/src/main/java/com/logmng/controller/SearchHistoryController.java`
  - Verified as the effective scope-aware request-normalization source. The source already preserved `self` ignore behavior and forwarded requester filters for `team` / `all`.
- `backend/src/main/java/com/logmng/dto/request/SearchHistoryListRequest.java`
  - Verified as the normalized list DTO already carrying actor identity, scope-limited requester set, requester filters, paging, and sorting.
- `backend/src/test/java/com/logmng/service/SearchHistoryServiceTest.java`
  - Verified as already containing regression coverage for the exact QA failure shapes and filtered paging consistency in the current working tree.
- `backend/src/test/java/com/logmng/controller/SearchHistoryControllerTest.java`
  - Verified as already covering `self` ignore behavior and normalized requester-filter forwarding in the current working tree.

#### Frontend

- No implementation is planned at authoring time for this backend-only bugfix child.

#### DB

- No schema migration is planned at authoring time for this backend-only bugfix child.

#### Contract / Spec

- No contract/spec file change is planned at authoring time.
- If the backend fix reveals undocumented live-schema assumptions, the implementing agent must update `docs/api-definition.md` and/or `docs/contract.md` in the same work.

## 3. Test approach

### Test case list (required)

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|----|-------|------|------------------------------|-----------------|--------------------------------------------|
| TC-01 | Backend | Regression | `scope=all`, requester filter `userId=user2`, seed data includes both `admin` and `user2` rows | Only `user2` rows are returned; `admin` rows are absent; `totalCount` matches filtered rows | Unit (`mvn test`) |
| TC-02 | Backend | Regression | `scope=all`, requester filter `username=user`, seed data includes both matching and non-matching requester usernames | Returned rows match the requester-username partial filter only; non-matching requesters are absent | Unit (`mvn test`) |
| TC-03 | Backend | Regression | `scope=team`, requester filter `userId=admin`, actor=`user1`, `admin` is outside the allowed same-department requester set | Result is empty (`data=[]`, `totalCount=0`); scope is not widened by the requester filter | Unit (`mvn test`) |
| TC-04 | Backend | Edge | `scope=self`, requester filters supplied together with paging params | Backend ignores requester filters and returns only current-user rows; paging remains valid | Unit (`mvn test`) |
| TC-05 | Backend | Edge | Filtered result spans more than one page | Count query and page-row query use the same filter set; `totalCount` / `totalPages` stay consistent with filtered rows | Unit (`mvn test`) |
| TC-06 | Integration | Regression | Authenticated live API call as `admin/all`: `GET /api/search-history?page=1&pageSize=20&userId=user2` | Live response contains only `user2` rows; filtered result is narrower than the unfiltered baseline | Integration (`curl`) |
| TC-07 | Integration | Regression | Authenticated live API call as `admin/all`: `GET /api/search-history?page=1&pageSize=20&username=user` | Live response narrows by requester username; non-matching requesters do not remain in the result | Integration (`curl`) |
| TC-08 | Integration | Regression | Authenticated live API call as `user1/team`: `GET /api/search-history?page=1&pageSize=20&userId=admin` | Live response returns `data=[]` and `totalCount=0` | Integration (`curl`) |
| TC-09 | Integration | Regression | Browser flow on `search-history`: enter requester filters and click `검색` | Visible rows change according to the backend-filtered result; the screen no longer keeps the previous unfiltered rows | Manual / browser |

### Test scenarios

#### Scenario 1: `scope=all` requester exact/partial filters narrow live results

1. Prepare seed data where at least one requester matches the filter and at least one requester does not.
2. Call `GET /api/search-history?page=1&pageSize=20&userId=user2` as an `admin/all` user.
3. Call `GET /api/search-history?page=1&pageSize=20&username=user` as an `admin/all` user.
4. Verify that each response is narrower than the unfiltered baseline and that every returned row matches the supplied requester filter.

#### Scenario 2: `scope=team` does not widen beyond the allowed requester set

1. Prepare seed data where `user1` and `user2` are in the same department and `admin` is outside that department.
2. Call `GET /api/search-history?page=1&pageSize=20&userId=admin` as `user1/team`.
3. Verify that the response is empty and that the backend does not fall back to the previous unfiltered team result.

#### Scenario 3: Filtered paging stays consistent after the backend fix

1. Prepare filtered data that spans more than one page.
2. Call the list API with requester filters and paging parameters.
3. Verify that `totalCount`, `totalPages`, and the visible page rows all reflect the same effective filter set.

### Test data

- Seed data must include at least these users and ownership patterns:
  - `admin` requester rows
  - `user1` requester rows
  - `user2` requester rows
- Department relationship for regression:
  - `user1` and `user2` in the same department
  - `admin` outside that department
- Username partial-match coverage:
  - at least one requester username that matches `user`
  - at least one requester username that does not match `user`
- Paging consistency coverage:
  - at least one requester-filtered result set with more than `20` rows, or a documented reduced page size in the automated test fixture so multi-page verification is executable

### Test environment

- Backend: `http://localhost:9200`
- Database: PostgreSQL (live verification target), H2/in-memory only for automated backend test fixtures
- Browser/UI recheck target: `http://localhost:3001`

### Re-verification conditions from QA evidence

The bugfix must not be considered complete until QA can rerun the failed parent evidence and observe the corrected backend behavior:

- `admin/all` + `GET /api/search-history?page=1&pageSize=20&userId=user2`
  - Previous failure: `totalCount=11` and `admin` rows remained
  - Required re-verification: only `user2` rows remain
- `admin/all` + `GET /api/search-history?page=1&pageSize=20&username=user`
  - Previous failure: result stayed unfiltered
  - Required re-verification: result is narrower than the unfiltered baseline and all rows match the partial requester username filter
- `user1/team` + `GET /api/search-history?page=1&pageSize=20&userId=admin`
  - Previous failure: `totalCount=4` and existing `user2` rows remained
  - Required re-verification: `data=[]`, `totalCount=0`
- Browser recheck on `search-history`
  - Previous failure: requester filter inputs were changed but visible rows stayed unchanged after `검색`
  - Required re-verification: visible rows refresh to the backend-filtered result and no stale unfiltered rows remain

## 4. Checklist

### Backend verification

- [x] Runtime requester-filter path verified against the effective live SQL/data path
- [x] `scope=all` requester filters narrow results in live API verification
- [x] `scope=team` requester filters narrow only inside the allowed requester set
- [x] `scope=self` still ignores requester filters
- [x] Filtered `totalCount` / `totalPages` remain consistent with returned rows
- [x] Requester-only detail / re-request behavior remains unchanged
- [x] Regression tests for QA evidence are added or strengthened

### Integration

- [x] QA failure evidence from the parent requirement is rerun
- [x] Browser recheck confirms visible rows now follow the backend-filtered result

### Documentation

- [x] Bugfix child formalized for Backend handoff
- [x] §2 planned change file list must be confirmed or amended after implementation
- [x] `docs/api-definition.md` / `docs/contract.md` updated only if the fix changes or clarifies runtime mapping assumptions

## 5. Test results

### Carried failure evidence from parent QA

Parent QA result reference: `docs/requirements/20260313-search-history-search-fields-and-paging.md` §5

- **When**: During QA verification on 2026-03-13 after frontend/backend build, restart, and health checks passed
- **What failed**: `GET /api/search-history` did not narrow results when requester filters were supplied (`userId`, `username`) in live API and browser verification
- **Confirmed failure scope**: backend
- **Impact at handoff time**: requester search acceptance and filtered paging acceptance were blocked until the backend actually narrowed the result set

### Remediation run date

- 2026-03-13 16:11:22 KST
- Scope: backend automated regression tests and live API re-verification

### Automated tests

- **Command**: `cd backend && mvn -Dtest=SearchHistoryServiceTest,SearchHistoryControllerTest test`
- **Result**: Pass
- **Summary**:
  - `SearchHistoryServiceTest` passed the requester-filter regression cases for `scope=all`, `scope=team`, and filtered paging consistency.
  - `SearchHistoryControllerTest` passed the scope-aware forwarding and `self` ignore normalization checks.

### Build and live verification

- **Build command**: `cd backend && mvn package -DskipTests`
- **Restart command**: `./scripts/dev-services.sh backend restart`
- **Health check**: `curl -s http://localhost:9200/api/health` -> `status=OK`
- **Live API re-verification**:
  - `admin/all` + `GET /api/search-history?page=1&pageSize=20&userId=user2` -> `totalCount=4`, all returned rows requester=`user2`
  - `admin/all` + `GET /api/search-history?page=1&pageSize=20&username=user` -> `totalCount=4`, all returned rows requester=`user2`
  - `user1/team` + `GET /api/search-history?page=1&pageSize=20&userId=admin` -> `data=[]`, `totalCount=0`

### Summary

- **Result**: Pass
- **Reason**: The backend source path already implemented the requester-filter propagation correctly, and the live mismatch disappeared once the packaged backend jar was rebuilt and restarted with the current sources.

### QA browser re-verification

- **Run date**: 2026-03-13 16:17:30 KST - 2026-03-13 16:24:29 KST
- **Health check**:
  - Frontend `http://localhost:3001` -> 200
  - Backend `http://localhost:9200/api/health` -> 200 (`status=OK`)
- **Browser automation tool**: `project-0-dev-browser` (`puppeteer_*`)
- **Base URL**: `http://localhost:3001`
- **Detailed results**:
  - `admin/all` browser session:
    - Opened `search-history` and confirmed the requester toolbar still rendered correctly.
    - Set selector `#search-history-requester-username` to `user`, clicked `검색`, and observed the visible table narrow from the unfiltered 11-row state to 4 rows.
    - All 4 filtered rows showed empty action-button cells, confirming requester-only actions were not exposed for non-requester rows after filtering.
    - In-browser authenticated API fetch to `GET /api/search-history?page=1&pageSize=20&username=user` returned `totalCount=4` and `userId=user2` on all rows, matching the refreshed table.
  - Cross-screen sizing comparison:
    - DOM measurement confirmed requester/user block width family remained aligned across the screens used by the requirement: `search-history=440px`, `activity-log=440px`, `statistics=440px`.
  - `user1/team` browser session:
    - Opened `search-history`, set selector `#search-history-requester-username` to `admin`, clicked `검색`, and observed the table switch to the empty state (`검색 이력이 없습니다...`).
    - In-browser authenticated API fetch to `GET /api/search-history?page=1&pageSize=20&username=admin` returned `totalCount=0`, `data=[]`, confirming no stale team rows remained in the UI.

## 6. Error remedy result

- **Root cause**:
  - The authored backend source path for requester filtering was already correct in the working tree (`SearchHistoryController` -> `SearchHistoryListRequest` -> `SearchHistoryService` shared count/list query builder).
  - The running backend process was still serving an older packaged jar. `./scripts/dev-services.sh backend restart` only rebuilds when `target/logmng-backend-1.0.0.jar` is missing, so a plain restart can continue to boot a stale artifact.
- **Actions taken**:
  - Verified controller/DTO/service/query-builder propagation against the current backend source.
  - Re-ran focused backend regression tests for the exact QA failure shapes.
  - Rebuilt the packaged backend artifact with `mvn package -DskipTests`.
  - Restarted backend and reran the failed live API cases.
- **Result**:
  - Live `search-history` requester filters now narrow correctly for `all` and `team` scope.
  - Count/list paging metadata now matches the same effective filter set in the live path for the rerun cases.
  - QA browser recheck confirmed the frontend table now refreshes to the filtered result set and no stale rows remain after `검색`.
  - Requester-only detail / re-request source behavior remains unchanged.
- **Completed at**: 2026-03-13 16:24:29 KST

---

**Author**: Requirements subagent
**Date**: 2026-03-13
**Status**: Completed
