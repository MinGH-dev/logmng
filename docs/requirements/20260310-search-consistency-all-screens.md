# 20260310 - Search consistency across all screens (re-review and final)

## 1. User requirement

### Requirement description

Ensure **all screens** provide search/filter functionality that complies with the project’s unified rules (user-context axes: department, name, userId; scope=self hiding rule; per-screen specifics). Re-review past requirement and design documents on search, activity log, statistics, user management, permission group, and pending approvals; produce a **final requirement document** that (a) states the rules, (b) lists per-screen required search/filter behaviour, and (c) serves as the single place for “what each screen needs” so implementation and verification can follow it.

### User scenario

1. An administrator or developer needs to verify that every list/search screen (main, search-history, activity-log, statistics, pending-approvals, user-management, permission-group-management) offers consistent, rule-compliant search and filter behaviour.
2. When scope is “self” (본인만), user/department/IP filters must be hidden and APIs must fix to the current user only.
3. When scope is team or all, user-context screens must offer the same three axes (부서, 이름, 사용자 ID) where applicable.
4. **Problem**: Past requirements and the analysis doc (`docs/analysis-search-consistency-by-screen.md`) exist but there is no single, final requirement doc that (i) re-reviews all screens against the rules and (ii) provides a clear per-screen table of required functionality for implementation and QA.

### Expected outcome

- A single requirement document that:
  - Summarises continuity with past requirements (scope rules, search history, activity log, statistics, user/permission/approval screens).
  - States the unified rules (user-context axes, scope=self behaviour, main vs user-context screens).
  - Includes **at least one table** showing **per screen (화면별)** **what search/filter functionality is required (어떤 기능이 필요한지)**.
- Implementation and QA can use this doc and the table(s) to implement and verify search consistency across all screens without guessing.

### Continuity with past requirements (summary)

Past requirements and the analysis doc establish:

- **Scope (self/team/all)**: activity-log, statistics, search-history, pending-approvals use permission-group scope; when scope=**self**, department/userId/username/IP filters must be **hidden** and backend must fix to current user only (e.g. 20250303-activity-statistics-self-only-scope, 20250304-team-scope-default-and-approval, 20260305-pending-approvals-scope-same-as-search-history).
- **Search history**: List with requester/date/conditions/approval status; actions (re-query, re-request, detail) only for the requester (20260224, 20260304).
- **Activity log & statistics**: Filters and scope; statistics has daily/monthly/user views and filter APIs (20260206-user-activity-log, 20260206-activity-log-statistics).
- **User management & permission group**: Department tree, user list, permission group screen access and scope (20250227-user-management-hierarchy-permissions, 20250227-permission-group-screen-menu-access).
- **Unified search axes**: `docs/analysis-search-consistency-by-screen.md` recommends **부서 + 이름(사용자명) + 사용자 ID** for user-context screens and **scope=self → hide user/department filters**; main (log search) uses date + log type + type-specific fields only.

This requirement **re-reviews all screens** against those rules and turns the analysis into a formal requirement with a per-screen functionality table.

---

## 2. Design

### Codebase summary

- **Backend**: Controllers that serve list/search APIs and accept filter parameters include `UserActivityLogController`, `ActivityStatisticsController`, `SearchHistoryController`, `DecryptController` (e.g. listPending), `UserController`, `PermissionGroupController`, `DepartmentController`. Scope is resolved via `ScopeHelper` / `AuthService` (screenScopes, allowedUserIds). Activity log and statistics already support userId, department, IP, etc.; search-history and pending-approvals list APIs may or may not expose requester (userId)/department/username filter params.
- **Frontend**: Screen components and search forms: `UserActivityLogSearchForm.js`, `UserActivityLogList.js`, `ActivityStatistics.js`, `SearchHistoryList.js`, `PendingApprovals.js`, `UserManagement.js`, `PermissionGroupManagement.js`, `UserGroupAssignment.js`. Scope-based hiding of user filters is implemented for activity-log and statistics (e.g. hideUserFilters when scope=self). User management uses department tree only; search-history and pending-approvals may have no requester/department/name filter UI. Main (LogGrid/LogTypeSelector) uses date + log type + type-specific fields only.
- **Single source of rules**: `.cursor/skills/search-consistency-domain/SKILL.md` and `docs/analysis-search-consistency-by-screen.md` define the axes and scope=self rule. No single requirement doc currently consolidates “per-screen required functionality” for implementation and test.

### Problem analysis

1. **Inconsistent axes across user-context screens**: Activity-log has userId and name but not department; statistics has department and userId but not name; user-management has department tree only; permission-group-management has userId selection only; search-history and pending-approvals have no requester/department/name filter UI. The analysis doc recommends adding the missing axes so all user-context screens share 부서 + 이름 + 사용자 ID.
2. **scope=self behaviour**: Activity-log and statistics already hide user/department/IP filters when scope=self. The rule must be explicitly stated and verified for every scope-supporting screen; backend must ignore filter params and fix to current user when scope=self.
3. **No single “contract” for per-screen search**: Implementers and QA need one table that states, per screen, which axes are required and whether scope=self hiding applies, so gaps are implemented and tested systematically.

### Solution approach

- **Unified rules (already in analysis/skill)**:
  - **User-context screens** (activity-log, statistics, user-management, permission-group-management, search-history, pending-approvals): Provide **부서, 이름(사용자명), 사용자 ID** as the common base; add screen-specific axes (IP, action type, log type) as needed.
  - **scope=self**: Hide the whole user/department filter block; do not send userId/username/department/ip to API; backend must override and return only current user.
  - **main (log search)**: No user 3 axes; keep date + log type + type-specific fields only.
- **Deliverable**: This requirement doc plus the **per-screen table** below. Implementation (Backend/Frontend) will add or adjust filter params and UI per screen according to the table; Contract/spec will document any new or changed API params.

Structured by scope for handoff:

**Frontend**

- For each screen in the table below that requires “부서 / 이름 / 사용자ID” or “scope=self 시 필터 숨김”: ensure the view component has the corresponding filter UI (or hides it when scope=self). Reuse a shared “user-context filter” component or hook where possible.
- **Configuration UI**: No change for “search consistency” itself; scope is already configured in permission group edit (ScreenSelectionTree, PermissionGroupPanel). View screens that consume scope (activity-log, statistics, search-history, pending-approvals) must already respect `user.screenScopes[screenId]` for visibility of user/department filters.
- **View screens**: activity-log (add department; keep scope=self hide), statistics (add name; keep scope=self hide), user-management (add search form: department, name, userId), permission-group-management (add department/name filter for user picker), search-history (add requester: userId, department, name filters), pending-approvals (add requester: userId, department, name filters), main (no user axes; keep as-is).

**Backend**

- Ensure list/search APIs for activity-log, statistics, search-history, pending-approvals accept and apply **department, username (or name), userId** where specified in the table. When scope=self, ignore those params and fix to current user.
- Activity log and statistics already support filters and scope; add or document params for search-history list and pending-approvals list (requester userId, department, username) if not present.

**DB**

- No schema change required for filter parameters (existing tables already support user, department, etc.).

**Contract / Spec**

- Document in `docs/api-definition.md` (or relevant spec) the filter query parameters for search-history list and pending-approvals list (e.g. requesterUserId, departmentCode, username) and that they are ignored when scope=self.

**Cursor tools**

- Keep `.cursor/skills/search-consistency-domain/SKILL.md` and `docs/analysis-search-consistency-by-screen.md` aligned with this requirement; the **per-screen table** in this doc is the authoritative “what each screen needs” and can be referenced from the skill and analysis doc.

### Affected scopes and change targets (verification)

Before finalizing §2, the checklist in `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md` was applied.

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|-----------|-----------------------------------------------|
| Backend | Yes | Yes — controllers and scope handling listed. |
| Frontend (config UI + view screen) | Yes | Yes — view screens and filter UI per screen; config UI (scope) already exists. |
| DB | No | N/A — no schema change. |
| Contract / Spec | Yes | Yes — api-definition or specs for new/changed filter params. |
| Cursor tools (skills, specs) | Yes | Yes — search-consistency-domain, analysis doc referenced. |

**Domain pattern**: This requirement matches “search/filter consistency” and “scope-supporting screens.” Touchpoints: Backend (controllers, scope resolution), Frontend (each view screen’s filter UI and scope-based hiding), Contract (API params), Cursor (skill + analysis doc). All are covered in §2 and in the change file list below.

### Per-screen required search/filter functionality (화면별 필요 검색/필터 기능)

The following table is the **single place** for “what functionality is needed per screen.” Implementation and QA should use it to close gaps and verify behaviour.

| 화면 ID | 메뉴 라벨 | 부서 | 이름 | 사용자ID | scope=self 시 필터 숨김 | 기타 화면별 축 | 비고 |
|---------|-----------|------|------|----------|--------------------------|----------------|------|
| main | 검색하기 | — | — | — | — | 날짜(시작·종료), 로그 타입, 타입별 필드 | 사용자 3축 불적용. 로그 검색 전용. |
| search-history | 검색 이력 | Y | Y | Y | Y | (없음) | 요청자 기준: 부서·이름·사용자ID. scope=self이면 해당 필터 숨김. |
| activity-log | 활동 이력 | Y | Y | Y | Y | IP, 액션 타입 | 현재 이름·사용자ID 있음; **부서 추가**. scope=self 시 필터 블록 숨김. |
| statistics | 활동로그 통계 | Y | Y | Y | Y | 로그 타입, IP | 현재 부서·사용자ID 있음; **이름 추가**. scope=self 시 필터 블록 숨김. |
| pending-approvals | 승인 대기 | Y | Y | Y | Y | (없음) | 요청자 기준: 부서·이름·사용자ID. scope=self 시 필터 숨김. |
| user-management | 사용자 관리 | Y | Y | Y | N | (없음) | scope 미적용(관리 화면). 부서 트리 + **부서·이름·사용자ID 검색 폼** 추가. |
| permission-group-management | 권한 그룹 관리 | Y | Y | Y | N | (없음) | 그룹별 사용자 추가 시 **부서·이름**으로 후보 필터 후 사용자ID 선택. |

**Abbreviations**: Y = required; N = not applicable (no scope-based hiding for that screen). “—” = not applicable (e.g. main does not use user-context axes).

### Change file list

**(Tentative. Implementing agent (Step 4) confirms or updates with actual files changed.)**

#### Frontend

- `frontend/src/components/UserActivityLog/UserActivityLogSearchForm.js`
  - Add department filter (and ensure scope=self hides user/department block).
- `frontend/src/components/ActivityStatistics.js` (or StatisticsFilters / filter component)
  - Add name (username) filter; keep scope=self hide.
- `frontend/src/components/UserManagement/UserManagement.js` (or sibling search form)
  - Add search form: department, name, userId to filter tree/list.
- `frontend/src/components/PermissionGroupManagement/UserGroupAssignment.js` (or user picker)
  - Add department and name filters for user candidate list.
- `frontend/src/components/SearchHistory/SearchHistoryList.js` (or new search form)
  - Add requester filters: userId, department, name; hide when scope=self.
- `frontend/src/components/PendingApprovals/PendingApprovals.js` (or search form)
  - Add requester filters: userId, department, name; hide when scope=self.
- Shared component or hook (optional, per Architecture)
  - Reusable “user-context filter” (부서, 이름, 사용자ID) for consistent UX; list only if created.

#### Backend

- `backend/src/main/java/com/logmng/controller/UserActivityLogController.java`
  - Accept department param if not present; ensure scope=self overrides all user/department params.
- `backend/src/main/java/com/logmng/controller/ActivityStatisticsController.java`
  - Accept username/name param if not present; ensure scope=self overrides.
- `backend/src/main/java/com/logmng/controller/SearchHistoryController.java` (or service)
  - Support filter params: requester userId, department, username for list API; ignore when scope=self.
- `backend/src/main/java/com/logmng/controller/DecryptController.java` (or service for listPending)
  - Support filter params for pending list: requester userId, department, username; ignore when scope=self.
- `backend/src/main/java/com/logmng/controller/UserController.java` (or hierarchy/list API)
  - If user-management list is filtered by search form, accept department, name, userId (or document existing behaviour).
- PermissionGroupController / user-list-for-group (if applicable)
  - Support department/name for filtering user candidates when adding users to a group.

#### DB

- None (no schema change).

#### Contract / Spec

- `docs/api-definition.md`
  - Document filter query parameters for search-history list and pending-approvals list (e.g. requesterUserId, departmentCode, username) and that they are ignored when scope=self.
- `specs/permission-group-hierarchy.spec.yaml` (if scope or screen list is extended)
  - No change expected; scope rules already documented.

#### Cursor tools

- `.cursor/skills/search-consistency-domain/SKILL.md`
  - Reference this requirement doc and the per-screen table as the authoritative “what each screen needs.”
- `docs/analysis-search-consistency-by-screen.md`
  - Optional: add a short pointer to this requirement doc and the table for implementation/QA.

---

## 3. Test approach

### Test case list (required)

Scope tags are set so that scope-specific TCs can be extracted for Backend/Frontend handoff.

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification |
|----|-------|------|------------------------------|-----------------|--------------|
| TC-01 | Frontend | Normal | activity-log: scope=team, user opens search form | Form shows 부서, 이름, 사용자ID (and IP, action type). | Manual / browser |
| TC-02 | Frontend | Normal | activity-log: scope=self, user opens screen | User/department filter block is hidden; only non-user filters (e.g. date, action type) visible if any. | Manual / browser |
| TC-03 | Frontend | Normal | statistics: scope=all, user opens filters | Form shows 부서, 이름, 사용자ID (and log type, IP). | Manual / browser |
| TC-04 | Frontend | Normal | statistics: scope=self | User/department filter block hidden. | Manual / browser |
| TC-05 | Frontend | Normal | user-management: user opens screen | Search form with 부서, 이름, 사용자ID is present; list/tree can be filtered. | Manual / browser |
| TC-06 | Frontend | Normal | permission-group-management: add user to group | User picker has department and name filters before selecting userId. | Manual / browser |
| TC-07 | Frontend | Normal | search-history: scope=team, user opens list | Requester filters (부서, 이름, 사용자ID) visible and applicable. | Manual / browser |
| TC-08 | Frontend | Normal | search-history: scope=self | Requester filter block hidden. | Manual / browser |
| TC-09 | Frontend | Normal | pending-approvals: scope=team, user opens list | Requester filters (부서, 이름, 사용자ID) visible and applicable. | Manual / browser |
| TC-10 | Frontend | Normal | pending-approvals: scope=self | Requester filter block hidden. | Manual / browser |
| TC-11 | Frontend | Normal | main (검색하기) | No 부서/이름/사용자ID filters; only date, log type, type-specific fields. | Manual / browser |
| TC-12 | Backend | Normal | activity-log list API with scope=self and query params userId, department | Response contains only current user’s data; params ignored. | Unit or integration |
| TC-13 | Backend | Normal | statistics list/export API with scope=self and userId, department, username | Response contains only current user’s data; params ignored. | Unit or integration |
| TC-14 | Backend | Normal | search-history list API with scope=team and requesterUserId, departmentCode | Response filtered by those params within team. | Unit or integration |
| TC-15 | Backend | Normal | pending-approvals list API with scope=team and requester filters | Response filtered by requester within team. | Unit or integration |
| TC-16 | Integration | Normal | E2E: Set scope=self for activity-log → open activity-log → change filters | No user/department controls visible; list shows only current user. | Manual / browser or integration |

### Test scenarios

#### Scenario 1: scope=self hiding on activity-log and statistics

1. Set permission group so activity-log (and statistics) scope is “본인”.
2. Log in as non-admin user, open activity-log and statistics.
3. Verify user/department/IP filter block is not shown and list shows only current user’s data.

#### Scenario 2: User-context axes on user-management and permission-group-management

1. Log in as user with user-management (and permission-group-management) access.
2. On user-management, use search form with 부서, 이름, 사용자ID and verify list/tree filters.
3. On permission-group-management, open “add user to group” and verify department/name filters for user picker.

#### Scenario 3: Requester filters on search-history and pending-approvals

1. Set scope=team for search-history and pending-approvals.
2. Open search-history list and pending-approvals list; verify requester (부서, 이름, 사용자ID) filters are present and apply correctly.
3. Set scope=self; verify requester filter block is hidden.

### Test data

- At least two non-admin users in different departments; one or more permission groups with different scope settings (self, team, all) for activity-log, statistics, search-history, pending-approvals.
- Activity log and search history rows for multiple users so that filtering by department/name/userId can be verified.

### Test environment

- Frontend: http://localhost:3001 (or per contract).
- Backend: http://localhost:9200 (per contract).
- Database: PostgreSQL (logmng).

---

## 4. Checklist

### Frontend verification

- [ ] Filter UI per screen matches the per-screen table (부서, 이름, 사용자ID where required).
- [ ] scope=self hides user/department filter block on activity-log, statistics, search-history, pending-approvals.
- [ ] main has no user-context axes.

### Backend verification

- [ ] List APIs accept and apply filter params per table; scope=self overrides and returns only current user.

### Integration

- [ ] E2E scope=self and scope=team behaviour verified for all scope-supporting screens.

### Documentation

- [ ] Requirement doc completed; api-definition (or spec) updated with new/changed filter params.
- [ ] Cursor skill and analysis doc reference this requirement and table.

---

## 5. Test results

### Test run date

- (To be filled when tests are run.)

### Test results

(To be filled after implementation and QA verification.)

---

**Author**: Requirements subagent  
**Date**: 2026-03-10  
**Status**: In progress (awaiting Step 4 implementation and §5 recording)
