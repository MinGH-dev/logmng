# 20260313 - Activity log and statistics department option source

## 1. User requirement

### Requirement description
Improve the department combo box on the **User Activity Log**, **Activity Statistics**, and **Search History** search forms so that it is populated from the **currently created department data**, instead of showing only the default **All** option. The implementation must first correct any documentation or tool guidance that does not clearly define the authoritative department option source, then proceed with development. The authoritative source is `GET /api/filter-options/departments?screen={screenId}`. In-scope `screenId` values are `activity-log`, `statistics`, and `search-history`. The API response shape is `string[]`, and each screen must add the local **All / 전체** option itself. For `scope=team`, users must see **only their own department** in the department combo box.

**Design doc references (mandatory for this requirement):**

- **Form layout, filter grouping, panel width, compact variant, width by role**: `docs/design/forms-and-filters.md`
- **Per-screen field definitions**: `docs/design/search-fields-by-screen.md`
- **Field definition schema, especially select dataSource and cross-screen consistency**: `docs/design/search-field-definition-items.md`
- **CSS standard and exception handling**: `docs/design/css-standard-and-exceptions.md`
- **Shared search/filter standard CSS**: `frontend/src/styles/search-filter-standard.css`

### User scenario
1. A user opens **User Activity Log**, **Activity Statistics**, or **Search History** and uses the search form.
2. The user opens the **Department** combo box inside the shared user-context filter block.
3. **Problem**: the combo box shows only **All**, even though departments already exist in the current department data.
4. The user expects the department options to be populated from the current department dataset through the shared filter-options API and to remain consistent across the affected screens.
5. When the user has `scope=team`, the user expects to see only the user's own department as the selectable department option in addition to the locally added **All** option.
6. If the current docs or Cursor tooling do not clearly define the correct department option source, those docs/tools must be updated before implementation so the same mistake is not repeated.

### Expected outcome
- The **Department** combo box on **User Activity Log**, **Activity Statistics**, and **Search History** must show the locally added **All / 전체** option plus the departments returned by `GET /api/filter-options/departments?screen={screenId}`.
- The authoritative source for the department select must be documented explicitly in the design docs and, when needed, in Cursor tooling guidance; `departmentList` as a parent prop name is not sufficient documentation for a select field source.
- The API must return `string[]` only; it must not include the **All / 전체** option in the response payload.
- For `scope=self`, the shared API returns `[]` and the affected screens keep the department filter hidden according to existing scope rules.
- For `scope=team`, the Department combo box must show only the current user's own department as the selectable department option source for the affected screens.
- For `scope=all` or `isSystemAdmin=true`, the Department combo box must show all current departments returned by the shared filter-options API.
- The implementation must preserve the existing search/filter standards from `docs/design/forms-and-filters.md`, `docs/design/search-fields-by-screen.md`, `docs/design/search-field-definition-items.md`, and `docs/design/css-standard-and-exceptions.md`.
- The aligned **user block** fields (`department`, `username`, `userId`) must keep the **same width/size** on both screens.
- The aligned search/filter panels on both screens must keep the **same panel/container width** and must not introduce screen-specific sizing regressions while fixing the department source.
- Non-admin users must receive department options only through `GET /api/filter-options/departments?screen={screenId}`; the implementation must not rely on the admin-only `/api/departments` API or the existing statistics-only department endpoint for this behavior.

## 2. Design

### 2.1 Security review (optional; when PII / decryption / access control)
Formal Security consultation is not required for this requirement, but the implementation must preserve access-control boundaries for department data. In particular, the fix must not expose a department-management endpoint to activity-log/statistics/search-history users unless the endpoint contract and access checks are intentionally updated.

### Technical design

#### Codebase summary
- **Frontend screen consumers**
  - `frontend/src/components/UserActivityLog/UserActivityLogList.js` loads department options with `statisticsApi.getDepartmentList()` and passes them to `UserActivityLogSearchForm`.
  - `frontend/src/components/ActivityStatistics.js` loads department options with `statisticsApi.getDepartmentList()` and passes them to `StatisticsFilters`.
  - `frontend/src/components/SearchHistory/SearchHistoryList.js` also loads department options with the same API and is part of the implementation scope.
- **Frontend shared filter block**
  - `frontend/src/components/common/UserContextFilterBlock.js` renders the department select from `departmentList` and currently assumes a simple string array.
  - `frontend/src/components/UserActivityLog/UserActivityLogSearchForm.js` and `frontend/src/components/StatisticsFilters.js` already align to the shared user-context block and design-standard wrapper (`sf-compact-panel`).
- **Frontend service layer**
  - `frontend/src/services/api.js` defines `statisticsApi.getDepartmentList()` as `GET /api/statistics/departments`.
  - `frontend/src/services/departmentService.js` defines `getDepartments(format)` as `GET /api/departments?format=tree|flat`.
- **Backend source behavior**
  - `backend/src/main/java/com/logmng/controller/ActivityStatisticsController.java` exposes `GET /api/statistics/departments`.
  - `backend/src/main/java/com/logmng/service/ActivityStatisticsService.java` currently returns an empty list from `getDepartments()` because `user_activity_log` has no department column.
  - `backend/src/main/java/com/logmng/controller/DepartmentController.java` exposes `GET /api/departments?format=flat`, but this endpoint is guarded by department-management-related access checks and is not currently documented as the filter-options source for activity-log/statistics.
  - The contract has now fixed the new shared endpoint as `GET /api/filter-options/departments?screen={screenId}` with `string[]` response and screen-specific scope behavior.
- **Documentation / tooling**
  - `docs/design/search-fields-by-screen.md` defines the activity-log department field as `options: departmentList (prop from parent)` instead of naming the actual authoritative source.
  - `docs/design/search-field-definition-items.md` requires a select field to document its API path and response shape, but the current screen-specific design docs do not satisfy that requirement for the affected department fields.
  - `.cursor/rules/search-filter-form-design.mdc` and `.cursor/skills/search-consistency-domain/SKILL.md` guide search/filter consistency, but they do not currently require that a select field's **actual option source** be written clearly enough to prevent this ambiguity.

#### Problem analysis
1. The current frontend on **User Activity Log**, **Activity Statistics**, and **Search History** depends on `GET /api/statistics/departments` for department options, but the finalized contract requires `GET /api/filter-options/departments?screen={screenId}` instead.
2. The current backend implementation of `ActivityStatisticsService.getDepartments()` returns an empty list, so the frontend renders only the default **All** option.
3. `GET /api/departments?format=flat` exists as department master data access, but its current access model and intended usage are different from the end-user filter behavior required for these screens.
4. The design docs describe the department select source only as `departmentList` from the parent component, which hides the real contract decision and makes the wrong source easy to keep using.
5. Because **Search History** also uses the same current department source, this is a **shared option-source issue**, not an isolated single-screen defect.
6. The finalized contract defines exact scope behavior for the new shared API: `self` => `[]` / hidden filter, `team` => current user's own department only, `all` or system admin => all current departments.
7. The fix must preserve the existing search/filter layout and shared user-block sizing so that changing the data source does not create a new alignment regression between activity-log and statistics.

#### Solution approach
**Documentation-first step (must happen before product code):**
- Update `docs/design/search-fields-by-screen.md` so the `department` select on **activity-log**, **statistics**, and **search-history** names `GET /api/filter-options/departments?screen={screenId}` as the authoritative source, documents `string[]` as the response shape, and states that **All / 전체** is added locally.
- Update `docs/design/search-field-definition-items.md` so the `dataSource` rule is explicit that a select field must document the **real API/domain source**, not an intermediate prop name such as `departmentList`.
- Update Cursor search/filter guidance if needed so future requirement and implementation handoffs do not accept a prop-only description for select options.

**Frontend:**
- The department option loading for **User Activity Log**, **Activity Statistics**, and **Search History** must be moved to `GET /api/filter-options/departments?screen={screenId}` instead of relying on the current statistics-specific endpoint.
- `frontend/src/services/api.js` or a shared filter-options wrapper must provide one department-options client for all three screens, using `screenId=activity-log`, `statistics`, or `search-history`.
- `frontend/src/components/UserActivityLog/UserActivityLogList.js` and `frontend/src/components/ActivityStatistics.js` must both consume that shared authoritative source.
- `frontend/src/components/SearchHistory/SearchHistoryList.js` must also consume that same shared authoritative source as part of the implementation scope.
- `frontend/src/components/common/UserContextFilterBlock.js` must continue to render the locally added **All / 전체** option plus the loaded department strings from the shared API without changing the existing width/size standards.
- `frontend/src/components/UserActivityLog/UserActivityLogSearchForm.js` and `frontend/src/components/StatisticsFilters.js` must keep the same user-block width/size, panel width, and standard wrapper behavior while the department data source is corrected.

**Backend:**
- The implementation must add the shared department filter-options API at `GET /api/filter-options/departments?screen={screenId}` for `screenId=activity-log|statistics|search-history`.
- The shared API must return `string[]` and must not include the **All / 전체** option in the response payload.
- The shared API must return departments from the current created department dataset rather than deriving them from `user_activity_log`.
- The shared API must enforce the finalized scope behavior: `self` => `[]`, `team` => current user's own department only, `all` or system admin => all current departments.
- The implementation must not continue to rely on `GET /api/statistics/departments` for this behavior, and must not switch the screens to the admin-only `/api/departments` endpoint.

**Contract / Spec / Design docs:**
- `docs/api-definition.md` must document `GET /api/filter-options/departments?screen={screenId}`, in-scope `screenId` values, `string[]` response shape, local **All / 전체** handling, scope behavior, and separation from admin-only `/api/departments`.
- `docs/design/search-fields-by-screen.md` must document the department option source for the affected screens using the exact contract path and local **All / 전체** handling.
- `docs/design/search-field-definition-items.md` must reinforce that `dataSource` for a select field requires a real API/domain source description.
- `docs/design/forms-and-filters.md` and `docs/design/css-standard-and-exceptions.md` must remain the governing references for layout and CSS behavior; if any exception becomes necessary, it must be documented there per existing rules.

**Cursor tool update targets:**
- `.cursor/rules/search-filter-form-design.mdc`
  - Must instruct implementers that search/filter select fields require the actual authoritative option source in docs/handoffs, not only a prop name.
- `.cursor/skills/search-consistency-domain/SKILL.md`
  - Must remind requirement authors and implementers to keep user-context filter option sources explicit and consistent across aligned screens.
- `.cursor/skills/activity-statistics-domain/SKILL.md`
  - Must stay aligned with the new shared department filter-options API contract for statistics-related filters.

**Implementation note for Frontend (pattern §2.4):** Implementer must read and apply field-level and layout values from `docs/design/search-field-definition-items.md`, `docs/design/search-fields-by-screen.md`, `docs/design/forms-and-filters.md`, and `docs/design/css-standard-and-exceptions.md` when changing the department option source on aligned search/filter screens. Requirement §2 describes the expected source and shared-consumer behavior, but the actual layout, width-by-role, compact spacing, panel width, and CSS exception handling must be read from those design docs and from `frontend/src/styles/search-filter-standard.css`. For CSS, use the standard wrapper and shared variables; if any screen-specific exception becomes necessary, implement it only in component CSS with a comment and add it to the Exception index. **If any required standard for layout, field sizing, spacing, label placement, option labeling, or control semantics is undefined or ambiguous in the design docs, the implementer must not infer or hardcode a solution. The implementer must first inform the user of the undefined standard items, explain why each is needed, propose a recommended standard draft, and request feedback before implementation proceeds.**

### Affected scopes and change targets (verification)
| Scope | Affected? | §2 subsection and change file list complete? |
|-------|-----------|-----------------------------------------------|
| Backend | Yes | Yes |
| Frontend (config UI + view screen) | Yes (view screens + shared consumer implementation) | Yes |
| DB | No | N/A |
| Contract / Spec | Yes | Yes |
| Cursor tools (skills, specs) | Yes | Yes |

**Pattern §2.4 (Search/filter UI consistency)** applies because the issue touches the shared user-context filter block on aligned screens and extends the same department-option source to Search History. This document includes: explicit design-doc references, panel-width preservation, explicit user-block width/size preservation, frontend implementation note, shared-consumer implementation scope, and CSS-standard references.

**Change target verification:** Completed against `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md` before finalizing §2.

### Planned change file list (expected change targets)
**(Planned at authoring. Implementing agent (Step 4) confirms or amends this list when implementation is complete.)**

#### Frontend
- `frontend/src/components/UserActivityLog/UserActivityLogList.js`
  - Updated to load department options from `GET /api/filter-options/departments?screen=activity-log` via the shared filter-options frontend service.
- `frontend/src/components/ActivityStatistics.js`
  - Updated to load department options from `GET /api/filter-options/departments?screen=statistics` via the shared filter-options frontend service.
- `frontend/src/components/SearchHistory/SearchHistoryList.js`
  - Updated to load department options from `GET /api/filter-options/departments?screen=search-history` via the shared filter-options frontend service.
- `frontend/src/services/filterOptionsService.js`
  - Added a narrow shared frontend wrapper for `GET /api/filter-options/departments?screen={screenId}` with supported-screen validation.
- `frontend/src/components/SearchHistory/SearchHistoryList.test.js`
  - Updated the regression test to verify Search History uses the shared filter-options service and still renders local **All / 전체** plus loaded department options.
- `frontend/src/components/UserActivityLog/UserActivityLogList.test.js`
  - Added a frontend test to verify User Activity Log requests department options with `screen=activity-log` and passes them into the shared user-context search form.
- `frontend/src/components/ActivityStatistics.test.js`
  - Added a frontend test to verify Activity Statistics requests department options with `screen=statistics` and passes them into `StatisticsFilters`.
- `frontend/src/services/filterOptionsService.test.js`
  - Added a service test to verify the shared filter-options client uses the correct endpoint/query parameter and rejects unsupported screen IDs.

#### Backend
- `backend/src/main/java/com/logmng/controller/FilterOptionsController.java`
  - Added the shared `GET /api/filter-options/departments?screen={screenId}` endpoint for `activity-log`, `statistics`, and `search-history`, with request validation and per-screen access checks.
- `backend/src/main/java/com/logmng/service/FilterOptionsService.java`
  - Added shared scope-aware department option resolution: `self => []`, `team => current user's own department only`, `all/system admin => all current departments`.
- `backend/src/main/java/com/logmng/service/AuthService.java`
  - Added explicit screen-access enforcement for query-parameter-driven shared APIs so the requested `screen` is validated against the caller's allowed screens.
- `backend/src/main/java/com/logmng/service/DepartmentService.java`
  - Added current department-name queries used as the authoritative source for shared department options.
- `backend/src/main/java/com/logmng/controller/ActivityStatisticsController.java`
  - Updated the legacy `/api/statistics/departments` endpoint to act as a minimal compatibility alias for `screen=statistics`, while keeping the new shared filter-options API as the authoritative contract.
- `backend/src/test/java/com/logmng/service/FilterOptionsServiceTest.java`
  - Added automated tests for authoritative source behavior, scope handling, system-admin behavior, and shared consumer consistency across `activity-log`, `statistics`, and `search-history`.
- `backend/src/test/java/com/logmng/controller/FilterOptionsControllerTest.java`
  - Added automated tests for supported-screen routing, invalid-screen rejection, and non-admin access behavior.
- `backend/src/test/java/com/logmng/controller/ActivityStatisticsControllerTest.java`
  - Updated the controller test setup to match the new shared filter-options dependency.

#### Contract / Spec / Design docs
- `docs/api-definition.md`
  - Must document `GET /api/filter-options/departments?screen={screenId}`, `screenId=activity-log|statistics|search-history`, `string[]` response shape, local **All / 전체** handling, scope behavior, and separation from admin-only `/api/departments`.
- `docs/design/search-fields-by-screen.md`
  - Must replace prop-only department source wording with `GET /api/filter-options/departments?screen={screenId}` for the affected screens, including Search History, and state that **All / 전체** is added locally.
- `docs/design/search-field-definition-items.md`
  - Must clarify that select `dataSource` requires the real API/domain source, not a parent prop name.
- `docs/design/forms-and-filters.md`
  - Must remain the governing reference for panel width and user-block alignment; update only if clarification is required for this pattern.
- `docs/design/css-standard-and-exceptions.md`
  - Must remain the governing reference for CSS exceptions if any screen-specific override is needed during the fix.

#### Cursor tools
- `.cursor/rules/search-filter-form-design.mdc`
  - Must require explicit authoritative option-source documentation for search/filter select fields.
- `.cursor/skills/search-consistency-domain/SKILL.md`
  - Must align search/filter consistency guidance with the explicit option-source rule for user-context filters.
- `.cursor/skills/activity-statistics-domain/SKILL.md`
  - Must align statistics-domain guidance with the new shared department filter-options contract.

#### DB
- None.

## 3. Test approach

### Test case list (required)
| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|----|-------|------|------------------------------|-----------------|--------------------------------------------|
| TC-01 | Backend | Normal | Call `GET /api/filter-options/departments?screen=activity-log` when departments exist in the current department dataset and the user has `scope=all` or `isSystemAdmin=true` | Response is `string[]` containing all current departments and does not include **All / 전체** | Unit / integration |
| TC-02 | Backend / Integration | Normal | Call `GET /api/filter-options/departments?screen=statistics` with a `scope=team` user | Response is `string[]` containing only the current user's own department and does not include **All / 전체** | Unit / integration |
| TC-03 | Backend / Integration | Normal | Call `GET /api/filter-options/departments?screen=search-history` with a `scope=self` user | Response is `[]`; the screen keeps the department filter hidden according to scope rules | Unit / integration |
| TC-04 | Frontend | Normal | Open **User Activity Log** with scope != self and open the Department combo box | The combo box shows locally added **All / 전체** plus the department strings from `GET /api/filter-options/departments?screen=activity-log` | Manual / browser |
| TC-05 | Frontend | Normal | Open **Activity Statistics** with scope != self and open the Department combo box | The combo box shows locally added **All / 전체** plus the department strings from `GET /api/filter-options/departments?screen=statistics` | Manual / browser |
| TC-06 | Frontend | Normal | Open **Search History** with requester filters visible and open the Department combo box | The combo box shows locally added **All / 전체** plus the department strings from `GET /api/filter-options/departments?screen=search-history` | Manual / browser |
| TC-07 | Integration | Regression | Compare the Department options on User Activity Log, Activity Statistics, and Search History with the same test account and dataset | All included screens use the same contract, add **All / 전체** locally, and show the correct option set for the same access scope | Integration / browser |
| TC-08 | Frontend | Regression | Compare the user block (`department`, `username`, `userId`) on User Activity Log and Activity Statistics after the fix | The three fields keep the same width/size and visual alignment on both screens | Manual / browser |
| TC-09 | Frontend | Regression | Compare the filter panel width and shared search/filter layout on both screens after the fix | The panel/container width and shared layout remain aligned to the design docs; no screen-specific sizing regression is introduced | Manual / browser |
| TC-10 | Contract / Docs | Documentation | Review `docs/design/search-fields-by-screen.md`, `docs/design/search-field-definition-items.md`, and `docs/api-definition.md` after the documentation-first step | The department select source is explicit, uses the exact contract path, states `string[]`, and states that **All / 전체** is added locally | Manual review |
| TC-11 | Backend / Integration | Edge | Verify access behavior for `GET /api/filter-options/departments?screen={screenId}` with a non-admin activity-log/statistics/search-history user | The screens load department options through the approved contract without requiring access to admin-only `/api/departments` | Integration |

### Test scenarios

#### Scenario 1: Current department data appears on both screens
1. Prepare at least one account that can open **User Activity Log**, **Activity Statistics**, and **Search History** with department filters visible.
2. Ensure the current department dataset contains multiple created departments.
3. Open each screen and inspect the Department combo box.
4. Verification: all in-scope screens call `GET /api/filter-options/departments?screen={screenId}` and show locally added **All / 전체** plus the expected department strings.

#### Scenario 2: Team scope returns only own department
1. Prepare a user whose screen scope is `team`.
2. Open the Department combo box on each in-scope screen where the filter is visible.
3. Verification: the API returns only the user's own department and each screen shows only locally added **All / 전체** plus that department.

#### Scenario 3: Self scope hides the filter and API returns empty list
1. Prepare a user whose screen scope is `self`.
2. Open each in-scope screen and confirm the department filter is hidden where existing scope rules require it.
3. Verify the contract behavior for `GET /api/filter-options/departments?screen={screenId}` returns `[]`.

#### Scenario 4: Shared option source does not break screen alignment
1. Open **User Activity Log** and **Activity Statistics** after the fix.
2. Compare the user-context block fields (`department`, `username`, `userId`) and the search/filter panel width.
3. Verification: width-by-role and panel width remain aligned to the design docs.

#### Scenario 5: Documentation-first step is complete
1. Review the updated design docs and API doc before product-code verification.
2. Confirm the Department select source is written as `GET /api/filter-options/departments?screen={screenId}`, not a parent prop placeholder or the old statistics-only endpoint.
3. Verification: implementation handoff can identify the correct option source without inferring from code.

### Test data
- At least three created departments in the current department dataset.
- One non-admin or scoped user who can open **User Activity Log**, **Activity Statistics**, and **Search History** with department filters visible.
- One `scope=team` user whose own department is known and distinct for scope validation.
- One `scope=self` user for hidden-filter and empty-response verification.

### Test environment
- Frontend: `http://localhost:3001`
- Backend: `http://localhost:9200`
- Database: project default database with current `department` data present

### 3.5 Browser automation verification (optional — for frontend-heavy requirements)
- **Applicable TCs**: TC-04, TC-05, TC-06, TC-07, TC-08, TC-09
- **Procedure per TC**: navigate to the screen, open the relevant search form, inspect the Department combo box, and compare user-block sizing and panel width across the aligned screens.

## 4. Checklist

### Frontend verification
- [x] Shared authoritative department option source applied to User Activity Log
- [x] Shared authoritative department option source applied to Activity Statistics
- [x] Shared authoritative department option source applied to Search History
- [x] Each screen adds **All / 전체** locally instead of expecting it from the API
- [x] User block width/size preserved across aligned screens

### Backend verification
- [x] `GET /api/filter-options/departments?screen={screenId}` returns `string[]`
- [x] New shared department filter-options API returns current department data instead of an empty list for `all` or system admin
- [x] `scope=self` returns `[]`
- [x] `scope=team` returns only the current user's own department
- [x] Access model is valid for activity-log/statistics/search-history users

### Integration
- [x] Same option source confirmed across User Activity Log, Activity Statistics, and Search History
- [x] Panel width and user-block alignment regression check completed

### Documentation
- [x] Requirement doc completed
- [x] Design docs updated first
- [x] Cursor tool guidance updated if needed
- [x] API documentation updated for `GET /api/filter-options/departments?screen={screenId}`

## 5. Test results

### Test run date
- 2026-03-13 19:09 KST (initial QA failure - stale backend jar)
- 2026-03-13 19:24 KST (QA re-verification after bugfix child)

### Test results
#### Frontend
Pass
- Browser automation tool: `cursor-ide-browser`
- Base URL: `http://localhost:3001`
- Re-verification used `user2` (`activity-log=team`, `statistics=team`, `search-history=team`) for the three affected screens and `user1` (`statistics=self`) for self-scope UI behavior.
- **User Activity Log**: Department combobox showed `전체, 영업1팀`; network request `GET /api/filter-options/departments?screen=activity-log` returned `200`.
- **Activity Statistics**: Department combobox showed `전체, 영업1팀`; network request `GET /api/filter-options/departments?screen=statistics` returned `200`.
- **Search History**: Department combobox showed `전체, 영업1팀`; network request `GET /api/filter-options/departments?screen=search-history` returned `200`.
- **scope=self** visual check: on `user1` **Activity Statistics**, the shared user-context filter block (`department`, `username`, `userId`) was hidden as expected.
- Regression/layout check: the aligned user-block controls on **User Activity Log** and **Activity Statistics** remained visually aligned, and the measured field widths stayed consistent at `140px` for `department`, `username`, and `userId` on both screens.
- Regression/layout check: filter panels remained aligned without a screen-specific width regression. The action-row search button positions stayed effectively aligned (`x=1613` on Activity Log, `x=1597` on Activity Statistics) and no visible panel squeeze/regression was observed in the browser rerun.
- Console during the rerun showed no new `부서 필터 옵션 조회 실패` messages after the backend rebuild/restart.

#### Backend
Pass
- Direct authenticated API re-probes after the bugfix child confirmed:
  - `user2` (`team`) `activity-log` -> `200 OK`, `["영업1팀"]`
  - `user2` (`team`) `statistics` -> `200 OK`, `["영업1팀"]`
  - `user2` (`team`) `search-history` -> `200 OK`, `["영업1팀"]`
  - `user1` (`statistics=self`) -> `200 OK`, `[]`
  - `admin` (`isSystemAdmin=true`) `activity-log` -> `200 OK`, full current department list
  - invalid `screen=department-approvers` -> `400 INVALID_SCREEN_ID`
- The earlier runtime `404` failure was resolved by bugfix child `20260313-activity-log-statistics-department-option-source-bugfix-1`, which rebuilt the packaged backend jar and restarted the runtime on port `9200`.
- Current running data still has no seeded user with `search-history=self`; exact self-scope browser coverage for that specific screen remains a test-data gap, but the shared self-scope contract itself now returns `200` with `[]` and the shared UI hiding rule was revalidated on statistics.

**Commands:**
```bash
cd backend && mvn test
cd backend && mvn clean package -DskipTests
cd frontend && npm test -- --watchAll=false
cd frontend && npm run build
curl -s -c /tmp/user2.cookie -H 'Content-Type: application/json' -d '{"username":"user2","password":"user123"}' http://127.0.0.1:9200/api/auth/login
curl -s -b /tmp/user2.cookie 'http://127.0.0.1:9200/api/filter-options/departments?screen=activity-log'
curl -s -b /tmp/user2.cookie 'http://127.0.0.1:9200/api/filter-options/departments?screen=statistics'
curl -s -b /tmp/user2.cookie 'http://127.0.0.1:9200/api/filter-options/departments?screen=search-history'
curl -s -c /tmp/user1.cookie -H 'Content-Type: application/json' -d '{"username":"user1","password":"user123"}' http://127.0.0.1:9200/api/auth/login
curl -s -b /tmp/user1.cookie 'http://127.0.0.1:9200/api/filter-options/departments?screen=statistics'
curl -s -c /tmp/admin.cookie -H 'Content-Type: application/json' -d '{"username":"admin","password":"admin123"}' http://127.0.0.1:9200/api/auth/login
curl -s -b /tmp/admin.cookie 'http://127.0.0.1:9200/api/filter-options/departments?screen=activity-log'
curl -s -b /tmp/user2.cookie 'http://127.0.0.1:9200/api/filter-options/departments?screen=department-approvers'
PGPASSWORD=logmng123 psql -h localhost -U logmng -d logmng -At -F $'\t' -c "SELECT u.username, pgs.screen_id, COALESCE(pgs.scope,'<null>') AS scope FROM app_user u JOIN app_user_permission_group aupg ON aupg.user_id = u.username JOIN permission_group_screen pgs ON pgs.permission_group_id = aupg.permission_group_id WHERE pgs.screen_id IN ('activity-log','statistics','search-history') ORDER BY u.username, pgs.screen_id;"
```

**Outcome:**
- Final QA re-verification passed.
- The earlier backend runtime blocker is resolved and the parent requirement is behaviorally accepted.
- No git commit was created in this QA task.

| ID | Result | Note | Detail |
|----|--------|------|--------|
| TC-02 | Pass | `team` scope API behavior verified | Direct authenticated probe for `user2` on `screen=statistics` returned `200 OK` with `["영업1팀"]` and no API-provided `전체` |
| TC-03 | Pass | Shared self-scope contract revalidated | `user1` on `statistics=self` returned `200 OK` with `[]`, and the shared user-context block was hidden in the statistics UI. No seeded `search-history=self` account was available for an additional screen-specific browser login |
| TC-04 | Pass | Browser check on **User Activity Log** with `user2` | Combobox ref `e20` showed `[전체, 영업1팀]`; network request `/api/filter-options/departments?screen=activity-log` returned `200` |
| TC-05 | Pass | Browser check on **Activity Statistics** with `user2` | Combobox ref `e23` showed `[전체, 영업1팀]`; network request `/api/filter-options/departments?screen=statistics` returned `200` |
| TC-06 | Pass | Browser check on **Search History** with `user2` | Combobox ref `e18` showed `[전체, 영업1팀]`; network request `/api/filter-options/departments?screen=search-history` returned `200` |
| TC-07 | Pass | Shared option source confirmed across three screens | All three screens used the shared endpoint and rendered the same team-scoped option set for the same user |
| TC-08 | Pass | Width/size regression check completed | User-block field widths matched across aligned screens: Activity Log `140/140/140px`, Statistics `140/140/140px` for `department/username/userId` |
| TC-09 | Pass | Panel width/layout regression check completed | Browser rerun showed no visible filter-panel width regression between Activity Log and Statistics; action rows remained aligned and both screens preserved the shared compact-panel layout |

### Issues found and resolution
#### Issue 1: Department combo shows only All
**Cause**: Initial QA failed because the running backend instance on `http://127.0.0.1:9200` was serving a stale packaged jar that did not expose `GET /api/filter-options/departments?screen={screenId}` at runtime. The updated frontend on **User Activity Log**, **Activity Statistics**, and **Search History** was already calling the correct shared contract, so each request initially fell back to the local `전체` option only.

**Resolution**:
1. Backend bugfix child `20260313-activity-log-statistics-department-option-source-bugfix-1` rebuilt the packaged jar with `mvn clean package -DskipTests` and restarted the backend runtime so the shared route is now served on port `9200`.
2. QA reran browser/manual verification and direct authenticated API probes. The three screens now load `전체 + 영업1팀` for the team-scoped user, and the self-scope contract returns `[]` while the shared filter block remains hidden in the self-scope UI.

#### Backend re-verification update
- Follow-up bugfix document: `docs/requirements/20260313-activity-log-statistics-department-option-source-bugfix-1.md`
- Backend root cause was confirmed as a stale packaged `backend/target/logmng-backend-1.0.0.jar` that had been restarted without rebuilding, so the running artifact did not include `FilterOptionsController` / `FilterOptionsService` even though the working tree already had them.
- Backend fix result: after `mvn clean package -DskipTests` and backend restart, `GET /api/filter-options/departments?screen=activity-log|statistics|search-history` all returned `200 OK` at runtime, `statistics=self` returned `data=[]`, and invalid `screen=department-approvers` returned `400 INVALID_SCREEN_ID` instead of `404`.
- Parent browser/layout checks were rerun successfully in this QA pass, so the earlier backend runtime blocker is now fully closed.

### Next steps
1. No additional product-code action is required for this requirement based on the current rerun.
2. Optional future hardening: add a seeded `search-history=self` QA user so the exact screen-specific self-scope browser path can be exercised without inference from the shared self-scope contract.

## 6. Error remedy result (cause and action) — for error/bug fix requirements only
- **Requirement ID**: `20260313-activity-log-statistics-department-option-source`
- **Root cause**: The runtime failure was caused by a stale packaged backend jar that was restarted without rebuilding. The source tree already contained the shared filter-options controller/service, but the running artifact did not.
- **Actions taken**: Reproduced the original runtime `404`, formalized backend bugfix child `20260313-activity-log-statistics-department-option-source-bugfix-1`, confirmed the stale-jar cause, rebuilt the backend package, restarted the runtime, re-probed the shared endpoint directly, and reran browser/manual QA across the three affected screens plus self-scope UI behavior.
- **Result**: QA rerun passed. The shared endpoint now responds with the expected scope-aware data, the three affected screens load the correct team-scoped department options from the shared API, the self-scope UI hides the shared filter block, and no commit was created in this task.
- **Completed**: 2026-03-13 19:24

---

**Author**: Requirements subagent
**Date**: 2026-03-13
**Status**: QA passed - no commit requested
