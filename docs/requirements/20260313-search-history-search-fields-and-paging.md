# 20260313 - Search history search fields and paging

## 1. User requirement

### Requirement description

Add a standard-compliant search/filter area to the `search-history` screen and align paging behavior with the project paging standard. The screen is a **requester-context** list screen, so its search/filter UI must follow the same requester/user block pattern used by other user-context screens while preserving existing search-history scope and requester-only action rules.

This requirement must be implemented with the following design sources as the single source of truth:

- `docs/design/forms-and-filters.md`
- `docs/design/grid-and-table.md`
- `docs/design/search-fields-by-screen.md`
- `docs/design/search-field-definition-items.md`
- `docs/design/css-standard-and-exceptions.md`

### User scenario

1. A user opens the `search-history` screen and needs to narrow the list by **requester context** instead of scanning all visible rows page by page.
2. The user expects the search/filter area to look and behave like other user-context screens: a dedicated requester block, standard field order, standard spacing, and standard paging controls.
3. When the user changes requester filters or page size, the list must refresh with the same server-side filtering and the pagination result must remain consistent with the filtered count.
4. When `screenScopes['search-history'] === 'self'`, the user expects the requester block to be hidden and the list to stay fixed to the current requester only.
5. **Problem**: The current `search-history` screen provides sorting and list paging only. It does not provide requester search fields, its API contract does not accept requester filters, and its requirement/design coverage does not yet define the search-history field set in the shared search/filter standard documents.

### Expected outcome

- The `search-history` screen must add a **requester search block** with the order **department -> username -> user ID**, and group titles must be placed **above** the fields rather than inline, per `docs/design/forms-and-filters.md`.
- The requester block fields (**department, username, user ID**) must have the **same width/size** as the same-role fields on aligned user-context screens such as `activity-log` and `statistics`, per `docs/design/search-fields-by-screen.md` and `docs/design/search-field-definition-items.md`.
- The search/filter panel on `search-history` must use the **same fixed panel-width family** as `activity-log` and `statistics`, and must use the same compact spacing family, using design-doc values rather than screen-local hardcoded numbers.
- The toolbar structure must be a **single compact row** that contains only the **requester block** and **Search / Reset** actions.
- Paging on `search-history` must follow `docs/design/grid-and-table.md`: default page size **20**, paging below the table, standard rows-per-page control, and server-driven pagination that stays consistent with the filtered result set.
- Paging control on `search-history` must inherit the existing shared `DataTable` paging UX without introducing a screen-specific paging variant.
- Changing search filters or page size must reset the current page to **1**, and the backend must calculate `totalCount` and `totalPages` from the **same filter set** used for the list query.
- Existing scope and action rules must remain intact: `scope=self` must hide the requester UI, clear requester local state, omit requester API parameters, and requester-only actions (`re-search`, `re-request`, `detail`) must remain requester-only after search/paging changes.

## 2. Design

### 2.1 Security review (optional; when PII / decryption / access control)

- [ ] Security review performed
- Risks:
  - The requirement adds requester filter inputs for data that is already visible only within the existing `search-history` scope (`self` / `team` / `all`).
  - The implementation must not weaken the existing requester-only action restriction for `GET /api/search-history/{id}` and `POST /api/search-history/{id}/re-request`.
- Acceptance / recommendations:
  - The list API must keep existing scope enforcement and treat requester filters as **additional narrowing filters**, never as scope-expanding parameters.
  - Requester-only row actions must remain unchanged after filtering and paging changes.

### 2.2 Codebase summary

- **Frontend view screen**:
  - `frontend/src/components/SearchHistory/SearchHistoryList.js` currently renders header, error area, `DataTable`, action buttons, and the detail modal. It calls the list API with `page`, `pageSize`, `sortField`, and `sortDirection`, but it does not render any search/filter toolbar.
  - `frontend/src/components/SearchHistory/SearchHistory.css` defines screen-local layout for the current list and modal, but it does not yet define the standard requester filter layout for this screen.
  - `frontend/src/services/searchHistoryService.js` exposes `getSearchHistoryList(page, pageSize, sortField, sortDirection)` and does not yet send requester filter parameters.
  - `frontend/src/components/common/UserContextFilterBlock.js` and `frontend/src/components/common/UserContextFilterBlock.css` already provide the unified user-context block structure (`department -> username -> user ID`) and are reusable for requester-context screens.
  - `frontend/src/components/DataTable.js` already supports standard table sorting, pagination, and rows-per-page control aligned with `docs/design/grid-and-table.md`.
- **Backend list API**:
  - `backend/src/main/java/com/logmng/controller/SearchHistoryController.java` resolves `search-history` scope and exposes `GET /api/search-history` with `page`, `pageSize`, `sortField`, and `sortDirection`.
  - `backend/src/main/java/com/logmng/service/SearchHistoryService.java` applies scope filtering only and does not yet support requester filters such as `department`, `username`, or `userId`. Count and list are generated inside the same service method, so this is the main place where filter and pagination consistency must be enforced.
  - `backend/src/main/java/com/logmng/dto/response/SearchHistoryListResponse.java` already provides `data[]` and `pagination`, so the response shape can remain stable while request-side filtering expands.
- **Contract and scope references**:
  - `docs/api-definition.md` documents the current query contract for `GET /api/search-history` without requester filters.
  - `docs/contract.md` and `specs/permission-group-hierarchy.spec.yaml` are the source of truth for `search-history` scope semantics (`self` / `team` / `all`).
- **Design-standard coverage gap**:
  - `docs/design/search-fields-by-screen.md` does not yet define the field set for the `search-history` screen, so this requirement must add `search-history` as a requester-context search/filter target in the shared design documentation.

### Technical design

#### Problem analysis

1. The current `search-history` screen lacks requester search fields, so users cannot narrow visible rows by department, username, or user ID even though the screen belongs to the requester-context search/filter family.
2. The current `GET /api/search-history` contract supports paging and sorting only. The backend cannot yet filter by requester department, requester username, or requester user ID.
3. Search/filter design coverage is incomplete for this screen: `search-history` is mentioned in consistency analysis, but the screen is not yet defined in `docs/design/search-fields-by-screen.md` as a first-class requester-context form.
4. Paging consistency must remain server-driven. If the count query and the list query do not apply the same requester filters, the UI will show incorrect totals or invalid pages.
5. The requirement must align `search-history` with `activity-log` and `statistics` on shared user/requester block sizing and panel-width rules without introducing screen-specific sizing standards outside the design documents.

#### Solution approach

**Frontend:**

- The `search-history` screen must add a search/filter toolbar between the header and the table, following the `header -> toolbar -> table` order in `docs/design/grid-and-table.md`.
- The requester filter block must use the label **"Requester"** in the requirement meaning and **"요청자"** in the UI. The field order must be **department -> username -> user ID**.
- The implementation should prefer reusing `frontend/src/components/common/UserContextFilterBlock.js` with `blockLabel="요청자"` rather than creating a new requester-block pattern. If reuse is not sufficient, any new requester-block component must preserve the same field order, field sizing, and accessibility structure.
- The search/filter root must use the standard wrapper class from `frontend/src/styles/search-filter-standard.css` so control sizing, spacing, and panel background come from the shared standard rather than from screen-local CSS.
- The toolbar must use a **single compact row** layout and must contain only the requester block and the **Search / Reset** action group. No additional screen-specific toolbar controls are part of this requirement.
- The panel width must be fixed to the **same panel-width family** already used by `activity-log` and `statistics`.
- The requester block must use the same role-based field width and block-level width family as aligned user-context screens. The layout must give the requester block its own width budget and must **not** squeeze it by sharing a single `1fr` cell with unrelated controls.
- `scope=self` must hide the requester block, clear requester local state (`department`, `username`, `userId`), and omit requester filter parameters from the API call.
- Search and reset actions must be explicit. When filters change through Search or Reset, the current page must reset to `1`.
- Paging must continue to use the existing shared `DataTable` contract and paging UX unchanged. The screen must inherit the common table standard in `docs/design/grid-and-table.md`, including default page size `20`, rows-per-page control, and pagination placed directly below the table.
- Filtering must remain **server-side**. Frontend must send filter and paging parameters to the API and must not perform client-side filtering on the current page data.

**Backend:**

- `GET /api/search-history` must support optional requester filter parameters:
  - `department`
  - `username`
  - `userId`
  - existing `page`, `pageSize`, `sortField`, `sortDirection`
- The service layer must apply scope and requester filters in the correct order:
  - `scope=self`: ignore requester filter inputs and return only the current user's rows.
  - `scope=team`: first constrain to the allowed requester set from the same department, then apply requester filters inside that allowed set.
  - `scope=all`: apply requester filters directly to the full visible set.
- To align with existing requester/user filtering semantics already used on other screens, the authored requirement assumes:
  - `department`: exact filter against requester department code/value used by the common department list.
  - `username`: partial match (`LIKE`) against requester username/display-name field.
  - `userId`: exact match against requester user ID.
- The backend must keep `requested_at desc` as the default sort unless a validated supported sort is explicitly provided.
- The backend must ensure that the count query and the list query use the **same** requester filter set so `totalCount`, `totalPages`, and list rows remain consistent.
- The implementation should introduce a dedicated request/query DTO for the list endpoint if that keeps the controller/service contract maintainable as query parameters increase.
- Existing requester-only detail and re-request rules must remain unchanged.

**DB:**

- No schema migration is planned at authoring time.
- Implementation must verify that requester filtering can be satisfied by joining existing user/department sources (for example `app_user`) to `search_history.user_id`.
- If performance concerns are observed during implementation, indexing review may be handled as a follow-up rather than as part of this initial requirement.

**Contract / Spec:**

- `docs/api-definition.md` must be updated so `GET /api/search-history` documents the new requester filter parameters and the paging/filter interaction.
- `docs/contract.md` must be verified and updated if the `search-history` scope wording needs explicit alignment with requester filter semantics.
- `specs/permission-group-hierarchy.spec.yaml` remains the scope source of truth and must be verified for consistency with the authored scope behavior of `search-history`.
- `docs/design/search-fields-by-screen.md` must add the `search-history` screen as a requester-context filter definition.

**Cursor tool update targets:**

- `.cursor/skills/search-consistency-domain/SKILL.md`
  - Must remain aligned with `search-history` requester filter behavior and design-doc references.
- `.cursor/skills/search-history-decrypt-domain/SKILL.md`
  - Must remain aligned with the list API contract when requester filters are added to `GET /api/search-history`.

### Implementation note for Frontend (mandatory for search/filter consistency)

Implementer must read and apply field-level and layout values from `docs/design/search-field-definition-items.md`, `docs/design/search-fields-by-screen.md`, `docs/design/forms-and-filters.md`, and `docs/design/grid-and-table.md` when changing search/filter CSS or table paging behavior for `search-history`. Layout and structural rules must come from the design documents, including group-title placement, compact spacing, width by role, fixed panel-width family, and the table pagination standard. `frontend/src/styles/search-filter-standard.css` must remain the single source for shared search/filter sizing, and `docs/design/css-standard-and-exceptions.md` must be followed for any exception handling.

For this requirement, the confirmed UI decisions are:

- toolbar structure = **single compact row**
- contents = **requester block + Search / Reset only**
- panel width = **same fixed panel-width family as `activity-log` / `statistics`**
- paging control = **inherit existing `DataTable` common UX without screen-specific variant**
- `scope=self` = **hide requester UI + clear requester local state + omit requester API parameters**

If any required standard for layout, requester-block width, field sizing, spacing, icon usage, label placement, control semantics, or paging behavior is not defined or is ambiguous in the design documents, the implementer must **not** infer or hardcode a solution. The implementer must first list the undefined standard items, explain why each is needed, propose a recommended standard draft, and request product/user feedback before implementation proceeds.

### Affected scopes and change targets (verification)

The checklist in `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md` was applied before finalizing this requirement.

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|-----------|-----------------------------------------------|
| Backend | [x] Yes | [x] Yes |
| Frontend (config UI + view screen) | [x] Yes | [x] Yes |
| DB | [ ] No | [x] N/A - no schema migration planned at authoring time |
| Contract / Spec | [x] Yes | [x] Yes |
| Cursor tools (skills, specs) | [x] Yes | [x] Yes |

- **Frontend configuration UI check**: This requirement does **not** add a new screen, permission, or scope-setting workflow. `ScreenSelectionTree` / `PermissionGroupPanel` are therefore out of implementation scope, and the affected frontend scope is the **view screen** (`search-history`) plus reusable shared requester/user-context UI pieces only.
- **Domain patterns applied**:
  - `§2.3 API contract or error code change`: yes, for `GET /api/search-history` query contract expansion.
  - `§2.4 Search/filter UI consistency`: yes, because `search-history` must align with the existing user-context/requester-context search/filter standard and shared field sizing rules.

### §2.4 verification (mandatory when pattern applies)

- **User block field size**:
  - `§1 Expected outcome` explicitly states that requester block fields must have the same width/size as aligned screens.
  - `§2 Solution approach` and the change file list require the same role-based width/block-width rules and explicitly prevent squeezing the requester block into a shared `1fr` cell.
  - `§3 Test approach` includes a cross-screen comparison test for requester/user block field size and panel width.
- **Form/panel width**:
  - `§1 Expected outcome` explicitly requires the same fixed panel-width family and compact spacing family as `activity-log` and `statistics`.
  - `§2 Solution approach` and the change file list include panel-width and standard wrapper alignment.
- **Layout / spacing / accessibility**:
  - `§1 Expected outcome` explicitly requires group titles above fields, single compact-row toolbar structure, standard spacing, and table paging alignment.
  - `§2 Solution approach` and the implementation note reference the required design docs and standard CSS handling.

### Planned change file list (expected change targets)

**(Confirmed during frontend implementation on 2026-03-13. Planned items below were reviewed against the actual frontend working-tree result and amended where no additional code change was needed.)**

#### Frontend

- `frontend/src/components/SearchHistory/SearchHistoryList.js`
  - Updated and confirmed: requester search/filter toolbar uses the single compact-row structure (`requester block + Search / Reset`), explicit draft/applied filter state, `scope=self` hide/clear/omit behavior, and page reset to `1` on Search / Reset / page-size change.
- `frontend/src/components/SearchHistory/SearchHistory.css`
  - Updated and confirmed: screen-specific layout for the single compact-row requester toolbar and shared panel-width token usage.
- `frontend/src/services/searchHistoryService.js`
  - Updated and confirmed: requester filter query parameters are sent together with paging and sorting params, and empty requester params are omitted.
- `frontend/src/components/SearchHistory/SearchHistoryList.test.js`
  - Added and refined: automated coverage for requester toolbar rendering, explicit Search behavior, page-size reset to `1`, and `scope=self` hide/clear/omit behavior.
- `frontend/src/services/searchHistoryService.test.js`
  - Added and confirmed: automated coverage for requester query param serialization / omission.
- Verified already aligned in the working tree; no additional frontend code change required:
  - `frontend/src/styles/search-filter-standard.css`
    - Shared panel-width token and user-context sizing variables already matched the authored standard (`max-width: 1400px` family).
  - `docs/design/search-fields-by-screen.md`
    - `search-history` requester-context field definition was already synchronized with activity-log/statistics width and paging rules.
  - `frontend/src/components/common/UserContextFilterBlock.js`
    - Reuse remained sufficient; no prop or structure extension was required for this requirement.
  - `frontend/src/components/common/UserContextFilterBlock.css`
    - Existing shared requester/user block layout already matched the standard width-by-role rules.
  - `frontend/src/components/DataTable.js`
    - Existing shared pagination and rows-per-page UX already satisfied this requirement without screen-specific changes.

#### Backend

- Backend bugfix child `20260313-search-history-search-fields-and-paging-bugfix-1` later confirmed that the source files below already contained the intended requester-filter implementation. The live QA mismatch came from a stale packaged backend jar being restarted without a rebuild; after `mvn package -DskipTests` and backend restart, the same live API cases passed.
- `backend/src/main/java/com/logmng/controller/SearchHistoryController.java`
  - Changed. Accepts requester filter query parameters, resolves `search-history` scope, and passes a normalized list DTO to the service. `self` scope now explicitly ignores requester filters before the service layer.
- `backend/src/main/java/com/logmng/service/SearchHistoryService.java`
  - Changed. Applies scope-aware requester filters with a shared query builder so count/list use the same filter set, keeps default sort `requested_at desc`, and preserves requester-only detail / re-request behavior.
- `backend/src/main/java/com/logmng/dto/request/SearchHistoryListRequest.java`
  - Added as the normalized request/query DTO for the expanded `GET /api/search-history` contract.
- `backend/src/main/java/com/logmng/dto/response/SearchHistoryListResponse.java`
  - Verified only. Response shape remained compatible, so no code change was required.
- `backend/src/test/java/com/logmng/service/SearchHistoryServiceTest.java`
  - Extended with automated coverage for requester `department` / `username` / `userId` filters, team-scope narrowing, and count/list paging consistency.
- `backend/src/test/java/com/logmng/controller/SearchHistoryControllerTest.java`
  - Added to cover controller-level query normalization for `self` ignore behavior and `all` scope filter/default-value forwarding.

#### DB

- No schema migration is planned. Implementation must verify that existing tables and joins are sufficient for requester filtering without expanding database scope in this requirement.

#### Contract / Spec

- `docs/api-definition.md`
  - Verified as already aligned in the working tree with the expanded query contract and filtered paging behavior of `GET /api/search-history`.
- `docs/contract.md`
  - Verified as already aligned in the working tree with the authored requester-filter narrowing semantics.
- `specs/permission-group-hierarchy.spec.yaml`
  - Must be verified for consistent `search-history` scope semantics and updated if clarification is required.
- `docs/design/search-fields-by-screen.md`
  - Must add the field-definition row(s) for the `search-history` requester filter block and any screen-specific extra filter block.
- `docs/design/forms-and-filters.md`
  - Must be referenced and updated only if a missing common rule for requester-context search/filter layout is discovered during implementation.
- `docs/design/css-standard-and-exceptions.md`
  - Must be updated only if implementation requires a documented screen-specific exception.

#### Cursor tools

- `.cursor/skills/search-consistency-domain/SKILL.md`
  - Must be verified and updated if the search-history requester-filter contract or shared-screen coverage changes.
- `.cursor/skills/search-history-decrypt-domain/SKILL.md`
  - Must be verified and updated if the authored list API contract changes what the skill documents for search-history list behavior.

## 3. Test approach

### Test case list (required)

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|----|-------|------|------------------------------|-----------------|--------------------------------------------|
| TC-01 | Backend | Normal | `GET /api/search-history` with `userId=<exact requester>`, `page=1`, `pageSize=20`, scope=`all` | Only rows for that requester are returned; `pagination.totalCount` and `totalPages` reflect the same filtered result set | Unit (`mvn test`) |
| TC-02 | Backend | Normal | `GET /api/search-history` with `username=<partial>` and scope=`all` | Rows whose requester username matches the partial filter are returned; count/list consistency is preserved | Unit (`mvn test`) |
| TC-03 | Backend | Normal | `GET /api/search-history` with `department=<department code/value>` and scope=`all` | Only rows whose requester belongs to the selected department are returned | Unit (`mvn test`) |
| TC-04 | Backend | Edge | `GET /api/search-history` with requester filters present while scope=`self` | Backend ignores requester filter inputs and returns only the current user's rows | Unit (`mvn test`) |
| TC-05 | Backend | Edge | `GET /api/search-history` with requester filters while scope=`team` | Backend restricts rows to the allowed same-department requester set first, then applies requester filters without widening scope | Unit (`mvn test`) |
| TC-06 | Backend | Edge | Filtered result with `page`, `pageSize`, default sort, and a page-size change | `totalCount`, `totalPages`, and page rows are computed from the same filter set; default sort remains `requested_at desc` when not overridden | Unit (`mvn test`) |
| TC-07 | Frontend | Normal | Open `search-history` with scope not `self` | Search/filter toolbar is shown as a **single compact row** with group title above fields and requester field order `department -> username -> user ID`; the row contains only requester block + Search / Reset | Unit (`npm test`) or Manual / browser |
| TC-08 | Frontend | Normal | Compare the requester block on `search-history` with the user block on `activity-log` and `statistics` | Requester/user block fields use the same visible width/size family and the same fixed panel-width family across aligned screens | Manual / browser |
| TC-09 | Frontend | Normal | Change requester filter, click Search, then change page size from the rows-per-page control | Current page resets to `1`; the table reloads with the updated filter/page-size combination; rows-per-page control inherits the shared `DataTable` UX unchanged | Unit (`npm test`) or Manual / browser |
| TC-10 | Frontend | Edge | Open `search-history` with scope=`self` after requester filters were previously set | Requester UI is hidden, requester local state is cleared, and requester filter params are omitted from the API call | Unit (`npm test`) or Manual / browser |
| TC-11 | Integration | Normal | Filter by requester criteria and navigate multiple pages | Filtered rows, `totalCount`, `totalPages`, and visible page navigation remain consistent across API and UI | Integration (browser or documented API flow) |
| TC-12 | Integration | Regression | Use the filtered list and try requester-only actions on rows that belong to other users | Existing requester-only action visibility and backend enforcement remain unchanged after search/paging changes | Integration (browser or documented API flow) |

### Test scenarios

#### Scenario 1: Requester filter and paging work together

1. Open `search-history` with scope not `self`.
2. Enter requester filters and click Search.
3. Confirm the current page resets to `1`, the list reloads, and `totalCount` matches the filtered rows.
4. Change page size and confirm the list reloads with the same filters and the updated paging state while keeping the shared `DataTable` paging UX unchanged.

#### Scenario 2: Scope-specific requester filter handling

1. Log in with `search-history` scope `self`.
2. Open `search-history` and confirm the requester block is hidden.
3. Confirm requester local state is cleared when `scope=self` applies.
4. Call or trigger the list API with requester filters and confirm the backend still returns only the current requester's rows and omits requester params from the request.
5. Repeat with scope `team` and confirm requester filters narrow the already scope-limited set without widening it.

#### Scenario 3: Cross-screen sizing and requester-only regression

1. Open `search-history`, `activity-log`, and `statistics` with scope not `self`.
2. Compare the requester/user blocks for field order, visible width/size, and the same fixed panel-width family.
3. On `search-history`, confirm row actions remain requester-only after the filtered list reloads.

### Test data

- At least two departments and at least three users:
  - one current requester
  - one same-department requester
  - one different-department requester
- Search-history rows owned by multiple requesters so `self`, `team`, and `all` scope behavior can be compared.
- Search-history rows whose requester usernames allow a partial-match test for `username`.

### Test environment

- Frontend: `http://localhost:3001`
- Backend: `http://localhost:9200`
- Database: PostgreSQL (per contract)

### 3.5 Browser automation verification (optional — for frontend-heavy requirements)

- **Applicable TCs**: TC-07, TC-08, TC-09, TC-10, TC-11, TC-12
- **Procedure per TC**:
  - Navigate to the app and log in.
  - Open `search-history`, capture a snapshot of the toolbar and table area, and verify the single compact-row structure, requester-block structure, and pagination placement.
  - Run filter and rows-per-page interactions, then capture updated snapshots to verify page reset and pagination consistency while the shared paging UX remains unchanged.
  - Open `activity-log` and `statistics` and compare requester/user block width and panel-width family.

## 4. Checklist

### Frontend verification

- [x] Requester search/filter toolbar added to `search-history`
- [x] Requester block order is `department -> username -> user ID`
- [x] Group title is placed above requester fields
- [x] Toolbar structure is a single compact row with requester block + Search / Reset only
- [x] Shared field width/panel-width family matches aligned screens
- [x] Rows-per-page and pagination behavior inherit the shared `DataTable` UX and align with `docs/design/grid-and-table.md`
- [x] `scope=self` hides requester filters, clears requester local state, and omits requester params

### Backend verification

- [x] `GET /api/search-history` accepts requester filters
- [x] Scope and requester filters are applied in the authored order
- [x] Count/list filtering is consistent for pagination
- [x] Default sort and page-size validation remain stable
- [x] Existing requester-only row-action behavior remains unchanged

### Integration

- [x] UI and API remain consistent across filtering + paging flows
- [x] Cross-screen requester/user block sizing comparison is verified
- [x] Regression check for requester-only actions completed

### Documentation

- [x] Requirement doc completed
- [x] `docs/api-definition.md` planned for sync
- [x] `docs/design/search-fields-by-screen.md` planned for sync
- [x] Cursor skill update targets identified

## 5. Test results

### Test run date

- Initial QA: 2026-03-13 16:06:02 KST
- Final re-verification: 2026-03-13 16:17:30 KST - 2026-03-13 16:24:29 KST
- Scope: frontend + backend verification
- Health check:
  - Frontend `http://localhost:3001` -> 200
  - Backend `http://localhost:9200/api/health` -> 200 (`status=OK`)

### Test results

#### Summary

- **Result**: Pass
- **Reason**: the initial live failure was caused by a stale packaged backend jar. After rebuild/restart and QA rerun, both live API narrowing and browser table refresh behaved as authored.

#### Automated tests reported by implementing agents

- **Backend handoff**: Pass
  - `cd backend && mvn -Dtest=SearchHistoryServiceTest,SearchHistoryControllerTest test`
  - `cd backend && mvn package -DskipTests`
- **Frontend handoff**: Pass
  - `cd frontend && CI=true npm test -- --watchAll=false --runInBand src/components/SearchHistory/SearchHistoryList.test.js src/services/searchHistoryService.test.js`
  - `cd frontend && npm run build`

#### QA verification details

- **Browser automation tool**: `project-0-dev-browser` (`puppeteer_*`)
- **Base URL**: `http://localhost:3001`
- **API verification tool**: `curl` with authenticated session cookies and in-browser `fetch(..., { credentials: 'include' })`

| ID | Result | Note |
|----|--------|------|
| TC-01 | Pass | `admin/all` session: `GET /api/search-history?page=1&pageSize=20&userId=user2` returned `totalCount=4`; all returned rows were `user2`. |
| TC-02 | Pass | `admin/all` session: `GET /api/search-history?page=1&pageSize=20&username=user` returned `totalCount=4`; all returned rows were `user2`. |
| TC-03 | Not run | Live environment still did not provide a usable department-list seed for a browser-driven department narrowing check. This remains covered by backend automated tests. |
| TC-04 | Not run | No seeded `search-history=self` account was available for live browser/API verification. Covered by backend/controller and frontend unit tests in implementer handoff. |
| TC-05 | Pass | `user1/team` session: `GET /api/search-history?page=1&pageSize=20&userId=admin` returned `data=[]`, `totalCount=0`, so team scope did not widen. |
| TC-06 | Pass | Re-run live API responses kept filtered row sets and pagination metadata aligned (`totalCount=4/0`, `totalPages=1/0`) for the narrowed cases. |
| TC-07 | Pass | Browser check on `search-history` confirmed a single compact toolbar row with `Requester` block legend `요청자`, field order `department -> username -> user ID`, and only `검색 / 초기화` actions. |
| TC-08 | Pass | Browser DOM measurement showed the requester/user block width family matched aligned screens: `search-history=440px`, `activity-log=440px`, `statistics=440px`. |
| TC-09 | Pass | Browser recheck showed visible rows now follow the filter: `admin/all` + `username=user` refreshed the table from 11 rows to 4 rows. The filtered seed stayed below the default page size, so rows-per-page visibility remains covered by frontend unit tests. |
| TC-10 | Not run | No live `scope=self` account for `search-history`; behavior remains covered by frontend unit test handoff (`scope=self` hide + clear + omit). |
| TC-11 | Pass | Browser verification with `user1/team` session and selector `#search-history-requester-username=admin` refreshed to the empty-state table; in-browser authenticated API fetch returned `totalCount=0`, `data=[]`. |
| TC-12 | Pass | `admin/all` browser session filtered to requester `user2` showed `4` visible rows and `0` row-action buttons on all rows, so requester-only actions were not exposed for non-requester rows after filtering. |

#### Bugfix child resolution

- **Resolution doc**: `docs/requirements/20260313-search-history-search-fields-and-paging-bugfix-1.md`
- **Root cause**: The running backend process was still serving a stale packaged jar, while the current backend source already contained the requester-filter propagation (`controller -> DTO -> service -> shared count/list query builder`) that QA expected.
- **Resolution summary**:
  - Backend regression tests rerun: `mvn -Dtest=SearchHistoryServiceTest,SearchHistoryControllerTest test` -> pass
  - Packaged jar rebuilt: `mvn package -DskipTests` -> pass
  - Backend restarted and health-checked -> pass
  - Live API rerun:
    - `admin/all` + `userId=user2` -> `totalCount=4`, only `user2` rows returned
    - `admin/all` + `username=user` -> `totalCount=4`, only `user2` rows returned
    - `user1/team` + `userId=admin` -> `data=[]`, `totalCount=0`
  - QA browser rerun:
    - `admin/all` + `username=user` -> visible table narrowed to 4 rows and no stale unfiltered rows remained
    - `user1/team` + `username=admin` -> visible table changed to the empty state and matched authenticated API result `totalCount=0`

---

**Author**: Requirements subagent
**Date**: 2026-03-13
**Status**: Completed
