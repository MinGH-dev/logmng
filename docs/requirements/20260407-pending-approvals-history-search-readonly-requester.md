# 20260407 - Pending approvals: history search, read-only requesters, log-search UI parity

## 1. User requirement

### Requirement description

Improve the **복호화 승인 관리** (`pending-approvals`) screen so that users can **search and browse approval history**, not only a live pending queue. **Requesters** (users who may submit decryption-approval requests but are **not** approvers on this flow) must have **read-only** access on this screen: they can view and filter history but **must not** see or use approve/reject actions. **Approvers** retain current capabilities (approve/reject where business rules allow). After approve or reject, **rows must remain visible** in the list (subject to filters), because the list is no longer **pending-only**.

The **search/filter UI** on this screen must match the **same design system as log search** (검색하기 / `LogGrid` family): layout, panel width, compact spacing, user/requester block behavior, and shared CSS — per `docs/design/forms-and-filters.md`, `docs/design/search-field-definition-items.md`, `docs/design/search-fields-by-screen.md` (including cross-screen user-block rules), `docs/design/css-standard-and-exceptions.md`, and `frontend/src/styles/search-filter-standard.css`.

**Design doc references (field schema and alignment)**  
This requirement explicitly references:

- `docs/design/forms-and-filters.md` — panel width, compact variant, single-row rules for non-date fields, width by role.
- `docs/design/search-fields-by-screen.md` — per-screen field tables; for requester/date/approval filters, align with **검색 이력** (§4.1–4.2) and **화면 간 공통 규칙** (§5), and ensure **로그 검색(검색하기)** user block matches the same **min/max width** family as this screen.
- `docs/design/search-field-definition-items.md` — definition items, §4 cross-field rules, §4.5 user block sizing.

The **action/status** column (동작 구분) must present statuses using the following **user-facing labels**: **승인대기** (PENDING), **승인** (APPROVED), **반려** (REJECTED). **EXPIRED** must be handled per policy in §2 (recommended: distinct label **만료**, consistent with 검색 이력 필터 vocabulary in design docs, unless product decides otherwise).

### User scenario

1. A **requester** opens **복호화 승인 관리** from the menu. They have `pending-approvals` **read** but not **approve**. They see a **검색하기-style** filter panel (date range, requester block, action/status filter, 검색 / 초기화). They do **not** see approve/reject controls on rows.
2. They set a **date range** and optional **requester** filters and run search. The grid lists matching rows including **승인대기**, **승인**, **반려** (and **만료** if applicable).
3. An **approver** opens the same screen. They see the same search UX plus **approve/reject** where applicable (existing business rules). After they approve or reject a row, **the row still appears** when filters include that status (e.g. 승인/반려), instead of disappearing because the backend list was pending-only.
4. **Problem**: Today the screen is driven by **pending-only** data (`getPendingList` / `GET /api/search-history/pending`), so completed rows vanish; **non-approvers** may hit **403** and see an error; filters and layout do not match **로그 검색** standards.

### Expected outcome

- **Requesters**: Can open the screen without spurious **403** for **read-only** list/search; **no** approve/reject UI or APIs invoked for their role.
- **Approvers**: Same approval capabilities as today; additionally benefit from **history-capable** list + filters; rows **do not disappear** solely because status is no longer PENDING.
- **Filters**: **요청일시** range, **요청자** (department / user name / user ID per contract scope rules), and **동작 구분** (approval status / action type) are available and behave consistently with **`GET /api/search-history`** semantics where that API is used.
- **UI parity with log search**: Search/filter **panel width**, **compact** spacing, **row structure** (e.g. row 1 = date range, row 2 = requester block + status + actions), and **user block** field sizes match **로그 검색** and **검색 이력** standards — specifically **department, user name (표시명), user ID** use the **same min/max width** on this screen as on **로그 검색** (and 검색 이력), without layout squeezing the user block (e.g. do not share one `1fr` cell with unrelated controls).
- **Status labels** in the grid: **승인대기 / 승인 / 반려** (and **만료** for EXPIRED per §2).
- **Contract and access rules** remain single source of truth; any API or interceptor change is documented in `docs/contract.md` and `docs/api-definition.md` and kept in sync with `specs/` as needed.

**Note**: Numeric and structural values (max-width, gaps, field heights) are **sourced from** design docs above; this requirement **references** them. If a value appears only here and not in design docs, add it to the design doc per `docs/template/REQUIREMENT_TEMPLATE.md`.

## 2. Design

### 2.1 Security review (optional; when PII / decryption / access control)

- [ ] Security review performed (check if applicable)

**Risks**

- **Permission mismatch** between a pending-only endpoint and **`GET /api/search-history`**: if list/detail/filter normalization diverges (e.g. `scope=self` handling), **another user’s approval-request metadata** could be exposed.
- **Over-sharing**: list rows vs detail (`GET /api/search-history/{id}`) must obey the **same row access** rules to avoid **ID-based leakage**.
- **Client-only gating**: approve/reject must be denied **server-side** with contract error codes, not hidden buttons only.

**Acceptance / recommendations**

- **Single authority**: Row visibility and scope rules must match **`docs/contract.md`** for **search-history** and **pending-approvals** (self / team / all); **never** rely on client filters alone for authorization.
- **Approve=false**: `POST .../approve` and `POST .../reject` must remain **403** / contract codes for users without effective approve (per 「복호화 승인 자격」).
- **Detail API**: `GET /api/search-history/{id}` must allow access only when the same principal could see that row in list (existing contract intent).
- **Endpoint choice**: Reusing **`GET /api/search-history`** for filtered history is acceptable if **server-side** permission and scope checks are identical to the contract; otherwise introduce a clearly scoped backend path with **one** service-layer implementation for scope to avoid drift.

*(Consolidated from Security advisory; formal Security step may add §2.1 checkbox and edits after review.)*

### Technical design

#### Codebase summary

- **Frontend** (`frontend/src/components/PendingApprovals/PendingApprovals.js`): Loads data via **`getPendingList`** → **`GET /api/search-history/pending`**; shows 403 message for `FORBIDDEN_NOT_APPROVER` / `NOT_APPROVER`. Columns include 요청자, 검색 조건 요약, 요청일시, 동작; **pending-only** behavior so rows disappear after approve/reject.
- **Frontend** (`frontend/src/services/searchHistoryService.js`): **`getSearchHistoryList`** already supports `requestedAtFrom`, `requestedAtTo`, `approvalStatuses`, requester fields, paging — aligns with **`docs/api-definition.md` §6.1.2**.
- **Frontend** (`frontend/src/components/SearchHistory/SearchHistoryList.js`): Rich filters and status labels (e.g. 대기/승인/반려/만료); **reference for behavior** but **복호화 승인 관리** must match **로그 검색** / shared search-filter standards, not copy Search History styling if it diverges from LogGrid.
- **Backend** (`ScreenAccessInterceptor`): **`GET /api/search-history`…** requires **`search-history`** screen; **`/api/search-history/pending`** and approve/reject require **`pending-approvals`**. Users with **only** `pending-approvals` (e.g. approval-only groups per `specs/permission-group-hierarchy.spec.yaml` §5) **cannot** call **`GET /api/search-history`** today — **gap** for unified list unless interceptor/contract changes.
- **Backend** (`SearchHistoryController` **GET** list): Resolves scope using **`ScreenConstants.SEARCH_HISTORY`**, not `PENDING_APPROVALS`. **`GET /pending`** uses **pending-approvals** scope. **Risk**: switching approvers to **`GET /api/search-history`** without aligning scope may **change** which rows appear vs current pending list.

#### Problem analysis

1. **Pending-only API** removes rows after decision; product needs **history-capable** list with filters.
2. **Requesters** need **read-only** access; current **pending** endpoint is **approver-only** (`§6.1.5`); **`GET /api/search-history`** requires **`search-history`** screen access at interceptor level — **requesters and approval-only approvers** may be blocked or scoped incorrectly.
3. **UI** does not follow **로그 검색 / search-filter-standard** patterns.
4. **Status column wording** must match **승인대기 / 승인 / 반려** (+ **만료** for EXPIRED).

#### Diagnostic phase (mandatory for error/bug fix only)

*Not applicable (feature requirement).*

#### Solution approach

**Contract / product (must confirm in Step 3)**

- Document which **GET** endpoint(s) the **복호화 승인 관리** screen uses for **list+filter**: primary candidate **`GET /api/search-history`** (`§6.1.2`) with full filters including **`approvalStatus`** multi-values.
- **`screenFunctions.pending-approvals.read`**: Explicitly means **screen access + read-only list/search**; **does not** imply approve (per Contract advisory).
- **Interceptor**: If requesters or approval-only users must call **`GET /api/search-history`** without **`search-history`** in `allowedScreenIds`, **`docs/contract.md`** / **`ScreenAccessInterceptor`** must allow **`GET`** list for **`pending-approvals`** (read) **or** a dedicated documented path — **verify** with Contract and Backend (no silent assumption).
- **Scope resolution**: When the unified list is shown **on pending-approvals**, backend **must** apply the **correct** scope key (**`pending-approvals`** vs **`search-history`**) per product decision. **Mandatory**: avoid approvers seeing a **different** row set than today solely because **`SEARCH_HISTORY` scope** replaced **`PENDING_APPROVALS` scope**. Options for Step 4: (a) query param / internal flag to resolve scope from **`pending-approvals`** for this UI; (b) extend **`/pending`** with **`§6.1.2`-style** filters and non-pending statuses; (c) single service method used by both paths. **Contract** must describe chosen behavior.

**Frontend**

- Replace or supplement **pending-only** load with **history search** using shared **search-filter** layout (LogGrid parity): **`UserContextFilterBlock`** or equivalent, date row, **동작 구분** multi-select aligned with **`approvalStatuses`**, **검색** / **초기화**, **`search-filter-standard.css`**.
- **Requester**: **`canApprove === false`** → hide approve/reject; show read-only actions (e.g. detail if contract allows).
- **Approver**: Keep approve/reject; after action, **refresh** list so row remains when status filter includes new state.
- **Grid**: **동작** column uses labels **승인대기 / 승인 / 반려** (and **만료** per §2.1 policy). Map from API codes `PENDING` / `APPROVED` / `REJECTED` / `EXPIRED`.
- **Tests**: Jest tests for gating, labels, and filter wiring per §3.

**Backend**

- Implement **authorization + scope** consistent with Contract for: (1) **read-only** list for requesters; (2) **approver** list including non-PENDING rows with **same business rules** as today for who may see which requesters’ rows.
- If **`GET /api/search-history`** is extended for **pending-approvals** screen access, update **`ScreenAccessInterceptor`** tests and rules; ensure **no** broadening beyond contract.
- **Approve/reject** endpoints: unchanged permission model; add/adjust tests for **403** when read-only.

**DB**

- None unless new indices are required for performance (TBD by Backend/DBA); **no schema change** implied by §1 alone.

### Affected scopes and change targets (verification)

Per `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md` (including **§3.4 Search/filter UI consistency** and **§2.4** verification when pattern applies):

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|------------|-----------------------------------------------|
| Backend | Yes | Yes |
| Frontend (config UI + view screen) | View screen yes; config UI only if permission copy in admin must mention read vs approve | Partial — verify permission-group UI copy per product |
| DB | Optional / TBD | — |
| Contract / Spec | Yes | Yes |
| Cursor tools (skills, specs) | Yes | Yes |

**§2.4 verification (mandatory — search/filter UI consistency)**

| Check | Done |
|-------|------|
| §1 Expected outcome explicitly requires **user block** (department, user name, user ID) **same width/size** as **로그 검색** (and aligned screens) | Yes (see Expected outcome) |
| §2 and change list reference **user-block** width and **no squeezed layout** | Yes |
| §3 includes TC comparing user-block sizing **로그 검색 vs 복호화 승인 관리** | Yes (TC-FE-04) |

**Implementation note for Frontend** (full text per `docs/workflow/HANDOFF-CHECKLIST.md` / `REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md` pattern §2.4):

Before changing form/filter CSS or component layout, read `docs/design/search-field-definition-items.md` (§1 definition items, §4 cross-field rules), `docs/design/search-fields-by-screen.md` (per-screen tables for the affected screen; align pending-approvals with 검색 이력 §4.1–4.2 and **로그 검색** user block with §5 cross-screen rules), and `docs/design/forms-and-filters.md` (§ Single row for non-date, § Form per mode, § Width by role, § Compact variant). Apply width, height, padding, gap, and layout/structural rules from those docs; verify requirement §2 numeric excerpts against the docs. Also apply `docs/design/css-standard-and-exceptions.md` and `frontend/src/styles/search-filter-standard.css`; for exceptions use component CSS only, comment, and Exception index per design process.

If any required standard is **undefined or ambiguous** in those design docs, the implementer **must not** infer or hardcode: inform the user, explain why a value is needed, propose a **recommended standard draft**, and request feedback before implementation (see `docs/design/ux-frontend-standard-principles.md` §2 if present).

### Planned change file list (expected change targets)

**(Planned at authoring. Implementing agent (Step 4) confirms or amends when implementation is complete.)**

#### Frontend

- `frontend/src/components/PendingApprovals/PendingApprovals.js` — **Done (2026-04-07):** `getSearchHistoryList` + `listContext=pending-approvals`; 상세는 `getSearchHistoryDetail(id, { listContext })`; 요청일시·요청자(`UserContextFilterBlock`)·동작 구분 필터; `search-history-toolbar` + `sf-compact-panel` + `SearchHistory.css` 패턴; 동작 컬럼 라벨 승인대기/승인/반려/만료; `approve===false`면 승인/반려 미표시; PENDING만 승인/반려.
- `frontend/src/components/PendingApprovals/PendingApprovals.css` — **Done:** 패널 폭·상세 pre·반려 모달; 컨트롤 크기는 공통 CSS에 위임.
- `frontend/src/services/searchHistoryService.js` — **Done:** `listContext`·상세 쿼리; 오류 시 `status`/`code`.
- `frontend/src/services/filterOptionsService.js` — **Done:** `PENDING_APPROVALS` → `screen=pending-approvals`.
- `frontend/src/components/PendingApprovals/PendingApprovals.test.js`, `frontend/src/services/searchHistoryService.test.js` — **Done:** TC-FE-01–03, listContext, 필터 wiring.
- `frontend/src/components/LogGrid.js` / `frontend/src/components/LogGrid.css` — **Reference only** (변경 없음).
- `frontend/src/components/common/UserContextFilterBlock.js` — **Reuse only** (변경 없음).

#### Backend

- `backend/src/main/java/com/logmng/config/ScreenAccessInterceptor.java` — **`GET /api/search-history`** and **`GET /api/search-history/{id}`** allow `search-history` **or** (`pending-approvals` + effective read); approve/reject paths also allow **`search-history`** (contract §6.1.6–6.1.7).
- `backend/src/main/java/com/logmng/controller/SearchHistoryController.java` — `listContext` query param; scope via `ScopeHelper.resolveScope(effectiveScreenId, …)`; detail uses same visibility as list.
- `backend/src/main/java/com/logmng/service/SearchHistoryService.java` — `getDetail(..., scopeScreenId, …)` list-consistent visibility (`assertSearchHistoryRowVisible`).
- `backend/src/main/java/com/logmng/service/AuthService.java` — `hasEffectiveReadForPendingApprovals` (used by `ScreenAccessInterceptor`).
- `backend/src/main/java/com/logmng/controller/FilterOptionsController.java` — `screen=pending-approvals` for shared department filter options.
- `backend/src/main/java/com/logmng/util/SearchHistoryListContextHelper.java` — `listContext` → effective screen id (checked exception → 403).
- Tests: `ScreenAccessInterceptorTest`, `SearchHistoryListContextHelperTest`, updates to `SearchHistoryControllerTest`, `FilterOptionsControllerTest`, `SearchHistoryServiceTest`.

**(Step 4 confirmed 2026-04-07.)**

#### Contract / documentation

- `docs/contract.md` — Screen access matrix for **GET** list; **read vs approve** for `pending-approvals`.
- `docs/api-definition.md` — §6.1.2 / §6.1.5 relationship; optional query/header for scope context; error codes.
- `specs/permission-group-hierarchy.spec.yaml` — If screen access or screenFunctions text changes.

#### Cursor tool update targets

- `.cursor/skills/search-history-decrypt-domain/SKILL.md` — Pending-approvals screen behavior vs APIs.
- `.cursor/skills/auth-permission-domain/SKILL.md` — `pending-approvals` read vs approve.
- `.cursor/skills/ui-ux-domain/SKILL.md` — If menu/access copy changes.

## 3. Test approach

### Test case list (required)

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification |
|----|-------|------|------------------------------|-----------------|--------------|
| TC-BE-01 | Backend | Normal | User with **`pending-approvals.read` only** (no `search-history` screen if product requires) calls documented **GET list** per §2 | 200 + rows within **scope**; no approve rights | Unit / integration (mvn) |
| TC-BE-02 | Backend | Exception | Same user calls **POST** `/approve` or `/reject` | 403 + contract code | mvn |
| TC-BE-03 | Backend | Normal | Approver loads list with **`approvalStatus`** including APPROVED/REJECTED | Rows returned; visibility matches **pending-approvals** scope rules (not wider than contract) | Integration |
| TC-BE-04 | Backend | Regression | Approver **team/all/self** scope: row set vs pre-change baseline | No unintended broadening; document if intentional | Integration |
| TC-FE-01 | Frontend | Normal | Requester: screen loads, **no** approve/reject buttons | Read-only UI | Jest (npm test) |
| TC-FE-02 | Frontend | Normal | Approver: approve/reject still visible when `approve===true` | Actions present | Jest |
| TC-FE-03 | Frontend | Normal | After approve, list refresh with filter including **승인** shows row | Row visible | Jest / manual |
| TC-FE-04 | Frontend | Normal | **User block** field widths on **복호화 승인 관리** match **로그 검색** (min/max same CSS variables or measured parity) | Visual/CSS parity per design docs | Manual / browser or Jest snapshot (project standard) |
| TC-INT-01 | Integration | Normal | End-to-end: filter by date + requester + 동작 구분 | Grid matches API | Browser MCP optional §3.5 |

**api-permission-map**: Map **GET list**, **GET detail**, **POST approve/reject** to controllers and denial codes for TC-BE-02.

### Test scenarios

#### Scenario 1: Requester read-only

1. Login as requester with **`pending-approvals` read**, **approve** false.  
2. Open **복호화 승인 관리**.  
3. Verify list loads (no 403), filters work, **no** approve/reject.

#### Scenario 2: Approver history

1. Login as approver.  
2. Approve a pending row.  
3. Set **동작 구분** to include **승인**; verify row still listed.

### Test data

- Provide **at least one** PENDING, APPROVED, REJECTED, EXPIRED row per environment (SQL INSERT or seed instructions) so filters are testable.

### Test environment

- Frontend: `http://localhost:3001` (per project)
- Backend: `http://localhost:9200`
- Database: PostgreSQL (per project)

### 3.5 Browser automation verification (optional)

- **Applicable TCs**: TC-FE-04, TC-INT-01  
- **Procedure**: Navigate to **복호화 승인 관리** → set filters → snapshot grid → compare layout to **로그 검색** per QA policy.  
- **Reference**: `docs/workflow/BROWSER-AUTOMATION-VERIFICATION-POLICY.md`

## 4. Checklist

### Frontend verification

- [ ] API parameters validated
- [ ] UI behavior confirmed
- [ ] Error handling verified

### Backend verification

- [ ] API test cases written and run
- [ ] Logs checked
- [ ] Performance checked (if applicable)

### Integration

- [ ] End-to-end flow tested
- [ ] Edge cases tested

### Documentation

- [ ] Requirement doc completed
- [ ] Code comments added (if applicable)

## 5. Test results

### Test run date

- (Pending)

### Test results

*(To be filled by QA after implementation.)*

---

**Author**: Requirements (subagent)  
**Date**: 2026-04-07  
**Status**: In progress  

---

## Contract sync note (implementation handoff)

When API behavior or screen access changes:

1. Update **`docs/contract.md`** and **`docs/api-definition.md`** in the **same change** as code (per `docs/workflow/DOC-CODE-SYNC.md`).  
2. Update **`specs/permission-group-hierarchy.spec.yaml`** if screenFunctions or path rules change.  
3. **Review** agent: cross-check contract ↔ frontend service ↔ interceptor.
