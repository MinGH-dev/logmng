# 20260310 - Search consistency Phase 1 (activity-log + statistics)

## 1. User requirement

### Parent reference

This requirement implements **Phase 1** of the behaviour specified in **docs/requirements/20260310-search-consistency-all-screens.md**. That document defines unified search/filter rules (user-context axes: 부서, 이름, 사용자 ID; scope=self hiding rule) and a per-screen table of required functionality. This doc defines the **concrete, implementable scope** for the first delivery so that Step 4 (Backend/Frontend) can be invoked with a clear scope and test plan.

### Scope of this requirement (Phase 1)

**In scope for this document:**

- **activity-log (활동 이력)**: Add **department (부서)** filter; keep existing name and userId; ensure scope=self hides the user/department filter block and backend fixes to current user only.
- **statistics (활동로그 통계)**: Add **name (이름/사용자명)** filter; keep existing department and userId; ensure scope=self hides the user/department filter block and backend fixes to current user only.

**Out of scope for this requirement (later phases):**

- search-history (검색 이력) — requester filters and scope=self hide
- pending-approvals (승인 대기) — requester filters and scope=self hide
- user-management (사용자 관리) — search form (부서, 이름, 사용자ID)
- permission-group-management (권한 그룹 관리) — department/name filters for user picker
- main (검색하기) — no change (user axes not applicable)

One **Backend** handoff and one **Frontend** handoff suffice for Phase 1.

### Requirement description

Implement search/filter consistency for **activity-log** and **statistics** only, per the parent doc’s per-screen table and unified rules: (1) activity-log gains a department filter and keeps scope=self hiding; (2) statistics gains a name filter and keeps scope=self hiding. All other screens remain unchanged in this phase.

### User scenario

1. An administrator or tester needs to verify that **activity-log** and **statistics** offer the full user-context axes (부서, 이름, 사용자 ID) when scope is team or all, and that when scope is self those filters are hidden and only the current user’s data is shown.
2. On **activity-log**: with scope=team or all, the search form shows 부서, 이름, 사용자ID (and existing IP, action type). With scope=self, the user/department filter block is hidden and the list shows only the current user’s activity.
3. On **statistics**: with scope=team or all, the filter form shows 부서, 이름, 사용자ID (and existing log type, IP). With scope=self, the user/department filter block is hidden and data is limited to the current user.
4. **Problem**: Today activity-log has name and userId but not department; statistics has department and userId but not name. Phase 1 closes these two gaps and confirms scope=self behaviour for both screens.

### Expected outcome

- **activity-log**: Department filter added; form shows 부서, 이름, 사용자ID when scope ≠ self; scope=self hides user/department block; backend accepts department and enforces scope=self.
- **statistics**: Name (username) filter added; form shows 부서, 이름, 사용자ID when scope ≠ self; scope=self hides user/department block; backend accepts name/username and enforces scope=self.
- Verification uses the test cases in §3 (subset of parent doc TCs that apply to activity-log and statistics only). Implementation and QA can use this doc as the single handoff scope for Phase 1.

---

## 2. Design

### Codebase summary (in-scope only)

- **Backend**: `UserActivityLogController` serves activity-log list/export and accepts filter parameters (userId, username, IP, action type, etc.); scope is resolved via `ScopeHelper` / `AuthService` (screenScopes, allowedUserIds). `ActivityStatisticsController` serves statistics list/export and accepts filters (userId, department, IP, log type, etc.); scope resolution same. Both already support scope=self (filter to current user) and team/all. Activity log may not accept a **department** query param; statistics may not accept a **username/name** query param — to be confirmed and added if missing.
- **Frontend**: `UserActivityLogSearchForm.js` (activity-log) and `ActivityStatistics.js` (or embedded filter component) (statistics) render filters and call list/export APIs. Both already hide user/department filters when scope=self (e.g. hideUserFilters). Activity-log has name and userId but is missing **department**; statistics has department and userId but is missing **name**.
- **Contract/Spec**: Filter query parameters for activity-log and statistics are documented in `docs/contract.md` or `docs/api-definition.md`; any new param (department for activity-log, username/name for statistics) must be documented when added.
- **Cursor**: `.cursor/skills/search-consistency-domain/SKILL.md` and `docs/analysis-search-consistency-by-screen.md` reference the parent requirement and per-screen table; no change required for Phase 1 except optional pointer to this phase doc.

### Problem analysis (in-scope screens only)

1. **activity-log**: Has 이름 and 사용자ID; **부서 (department)** filter is missing. scope=self hiding is already implemented; backend must accept department when scope ≠ self and ignore it when scope=self.
2. **statistics**: Has 부서 and 사용자ID; **이름 (name/username)** filter is missing. scope=self hiding is already implemented; backend must accept username/name when scope ≠ self and ignore it when scope=self.

### Solution approach

**Frontend**

- **activity-log**: In `UserActivityLogSearchForm.js`, add a department filter control (e.g. department dropdown or code input) that is visible when scope ≠ self and hidden when scope=self (reuse existing hideUserFilters logic). Send department in the list/export API request when present.
- **statistics**: In the statistics filter component (e.g. inside `ActivityStatistics.js` or a dedicated filter component), add a name (username) filter control that is visible when scope ≠ self and hidden when scope=self. Send username/name in the list/export API request when present.

**Backend**

- **UserActivityLogController**: Accept an optional `department` (or `departmentCode`) query parameter for list/export. When scope=self, ignore department (and all other user/department params) and fix to current user. When scope=team/all, apply department filter if provided.
- **ActivityStatisticsController**: Accept an optional `username` or `name` query parameter for list/export. When scope=self, ignore it and fix to current user. When scope=team/all, apply name filter if provided.

**DB**

- No schema change. Filtering uses existing user/department data.

**Contract / Spec**

- Document the new query parameter(s): activity-log list/export `department` (or `departmentCode`); statistics list/export `username` or `name`. State that they are ignored when scope=self.

**Cursor tools**

- Optional: add a one-line reference in `.cursor/skills/search-consistency-domain/SKILL.md` or `docs/analysis-search-consistency-by-screen.md` that Phase 1 implementation is defined in this doc. No mandatory change.

### Affected scopes and change targets (verification)

Before finalizing §2, the checklist in `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md` was applied.

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|-----------|-----------------------------------------------|
| Backend | Yes | Yes — UserActivityLogController, ActivityStatisticsController. |
| Frontend (view screen only) | Yes | Yes — UserActivityLogSearchForm, ActivityStatistics (filter). No config UI change in Phase 1 (scope is already configured in permission group). |
| DB | No | N/A — no schema change. |
| Contract / Spec | Yes | Yes — document new filter param(s). |
| Cursor tools | No (optional) | Optional reference only. |

**Domain pattern**: This requirement matches “scope-supporting screen” and “search/filter consistency” for two screens only. Touchpoints: Backend (two controllers), Frontend (two view components), Contract (API params). All are covered in §2 and in the change file list below.

### Change file list

**(Frontend and Backend Step 4. Actual files changed.)**

#### Frontend

- `frontend/src/components/UserActivityLog/UserActivityLogSearchForm.js`
  - Added department filter (dropdown); visible when scope ≠ self (hideUserFilters=false); send department in API request when scope ≠ self.
- `frontend/src/components/UserActivityLog/UserActivityLogList.js`
  - Fetch department list via statisticsApi.getDepartmentList(); pass departmentList to search form; strip department (with userId, username, ipAddress) from request when scope=self.
- `frontend/src/components/ActivityStatistics.js`
  - Added `username` to filters state; effectiveFilters (when scope ≠ self) already sends full filters including username to list/export API.
- `frontend/src/components/StatisticsFilters.js`
  - Added name (사용자명) filter control (text input); visible when scope ≠ self; bound to filters.username, sent in list/export when scope ≠ self.

#### Backend

- `backend/src/main/java/com/logmng/controller/UserActivityLogController.java`
  - Accept optional department in request body (POST /search); when scope=self ignore and fix to current user; when scope=team keep department from request and apply filter.
- `backend/src/main/java/com/logmng/controller/ActivityStatisticsController.java`
  - Accept optional `username` or `name` query param for daily/monthly/users/all/export; when scope=self ignore and fix to current user; when scope=team/all apply name filter if provided.
- `backend/src/main/java/com/logmng/dto/request/UserActivityLogSearchRequest.java`
  - Added `department` field and `departmentCode` (JSON alias).
- `backend/src/main/java/com/logmng/service/UserActivityLogService.java`
  - Apply department filter via INNER JOIN app_user when department present.
- `backend/src/main/java/com/logmng/service/ActivityStatisticsService.java`
  - Added `username` parameter to getDailyStatistics, getMonthlyStatistics, getAllUserStatistics, exportCsv, buildDailyMonthlyWhere; apply `username LIKE ?` when provided.
- `backend/src/test/java/com/logmng/controller/UserActivityLogControllerTest.java` (new) — TC-12.
- `backend/src/test/java/com/logmng/controller/ActivityStatisticsControllerTest.java` (new) — TC-13.
- `backend/src/test/java/com/logmng/service/StubAuthServiceForActivityLog.java`, `StubAuthServiceForStatistics.java`, `StubUserActivityLogServiceCapture.java`, `StubActivityStatisticsServiceCapture.java`, `backend/src/test/java/com/logmng/util/StubDataSource.java` (new).

#### DB

- None.

#### Contract / Spec

- `docs/api-definition.md`
  - §8.1: Added request body parameter `department` for activity-log search; noted scope=self ignores it.
  - §8.3: Added Activity Statistics API query params including `username` (or `name`); scope=self일 때 사용자·부서 관련 파라미터 무시.

#### Cursor tools

- Optional: `.cursor/skills/search-consistency-domain/SKILL.md` or `docs/analysis-search-consistency-by-screen.md` — add one-line pointer to this requirement (Phase 1 implementation scope).

---

## 3. Test approach

### Test case list (required)

Only the test cases that **apply to the in-scope screens** (activity-log and statistics) for this requirement. Sourced from parent doc `docs/requirements/20260310-search-consistency-all-screens.md` §3; Scope tags support Backend/Frontend handoff and QA verification.

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification |
|----|-------|------|------------------------------|-----------------|--------------|
| TC-01 | Frontend | Normal | activity-log: scope=team (or all), user opens search form | Form shows 부서, 이름, 사용자ID (and IP, action type). | Manual / browser |
| TC-02 | Frontend | Normal | activity-log: scope=self, user opens screen | User/department filter block is hidden; only non-user filters (e.g. date, action type) visible if any. | Manual / browser |
| TC-03 | Frontend | Normal | statistics: scope=all (or team), user opens filters | Form shows 부서, 이름, 사용자ID (and log type, IP). | Manual / browser |
| TC-04 | Frontend | Normal | statistics: scope=self | User/department filter block hidden. | Manual / browser |
| TC-12 | Backend | Normal | activity-log list API with scope=self and query params userId, department | Response contains only current user’s data; params ignored. | Unit or integration |
| TC-13 | Backend | Normal | statistics list/export API with scope=self and userId, department, username | Response contains only current user’s data; params ignored. | Unit or integration |
| TC-16 | Integration | Normal | E2E: Set scope=self for activity-log → open activity-log → change filters | No user/department controls visible; list shows only current user. | Manual / browser or integration |

### Test scenarios

#### Scenario 1: scope=self hiding on activity-log and statistics

1. Set permission group so activity-log and statistics scope is “본인” (self).
2. Log in as non-admin user; open activity-log and statistics.
3. Verify user/department filter block is not shown and list/data shows only current user.

#### Scenario 2: User-context axes on activity-log and statistics (scope ≠ self)

1. Set scope=team or all for activity-log and statistics.
2. Open activity-log: verify 부서, 이름, 사용자ID are present; apply department filter and confirm list updates.
3. Open statistics: verify 부서, 이름, 사용자ID are present; apply name filter and confirm data updates.

### Test data

- At least two non-admin users in different departments; one or more permission groups with scope self and team (or all) for activity-log and statistics. Activity log and statistics data for multiple users so that filtering by department and name can be verified.

### Test environment

- Frontend: http://localhost:3001 (or per contract).
- Backend: http://localhost:9200 (per contract).
- Database: PostgreSQL (logmng).

---

## 4. Checklist

### Frontend verification

- [ ] activity-log: department filter present when scope ≠ self; hidden when scope=self.
- [ ] statistics: name filter present when scope ≠ self; hidden when scope=self.

### Backend verification

- [ ] activity-log list/export accepts department; scope=self overrides and returns only current user.
- [ ] statistics list/export accepts username/name; scope=self overrides and returns only current user.

### Integration

- [ ] E2E scope=self and scope=team behaviour verified for activity-log and statistics.

### Documentation

- [ ] Requirement doc completed; contract or api-definition updated with new filter param(s).

---

## 5. Test results

### Test run date

- (To be filled when tests are run.)

### Test results

(To be filled after implementation and QA verification.)

---

**Author**: Requirements subagent  
**Date**: 2026-03-10  
**Status**: In progress (handoff-ready for Step 4 Backend/Frontend; §5 to be filled by QA after verification)
