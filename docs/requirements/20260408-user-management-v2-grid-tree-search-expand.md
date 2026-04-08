# 20260408 - User management v2 grid layout, tree width, search, and expand controls

### Amendment log

| Date | Summary |
|------|---------|
| 2026-04-08 | Initial: grid/tree width, single-row search, expand-on-search, expand/collapse as secondary controls (originally in a row **below** the filter toolbar). |
| 2026-04-08 (follow-up) | (A) **Permission group combobox** in user rows must not be **visually clipped** at the cell edge (typical desktop width). (B) **모두 펼치기 / 모두 접기** move to the **inside top** of the **tree panel** (not the search toolbar row); **reduce vertical height** slightly vs prior tree-action buttons while staying within `docs/design/buttons.md` (secondary tier, minimum touch/focus targets). Where this conflicts with earlier text in §1 step 6, §2 “Tree utilities” placement, or TC-16, **this amendment takes precedence**. |
| 2026-04-08 (narrow viewport) | User tables must **not** aggressively collapse into **`text-overflow: ellipsis`** / single-line clamps when the browser window is narrower than a **sensible table minimum width**. Prefer **horizontal scrolling within the table region** (`overflow-x: auto` on the scroll owner) so **data-bearing** header and cell values remain readable; ellipsis allowed only where §2 explicitly lists **optional cosmetic** columns. Align with `docs/design/grid-and-table.md` (`%` + `min-width`, scroll wrapper owns horizontal overflow). |

## 1. User requirement

### Requirement description

User Management v2 (`frontend/src/components/UserManagement/UserManagement.js`) combines a **department tree** with **per-node user tables**. Operators report excessive **horizontal scrolling** on user grids, a **tree panel that starts too narrow** (then grows when nodes expand), and the lack of **search** and **bulk tree expand/collapse** controls.

This requirement limits scope to **frontend** User Management v2 unless an API gap is discovered (e.g. search cannot be satisfied without server-side filtering). Any needed backend or contract work must be recorded in §2 and handed off; do not assume backend changes.

### User scenario

1. An authorized operator opens **User Management v2** on a typical desktop width.
2. The operator sees the tree area with **adequate initial width** (not a small centered strip that only widens after interaction).
3. The operator views user rows under departments with **tighter column layout** so horizontal scrolling is **minimized** where possible without hiding required columns.
4. The operator enters filter criteria for **department name**, **user name**, and **employee number (사번)** on the **same horizontal row** as **Search** and **검색 초기화 (Reset/Clear)** at typical desktop width (single filter toolbar row; wrapping only below the agreed breakpoint if documented).
5. After search is applied, the tree **automatically expands** all ancestor nodes needed so **every matching department and user row is visible** (user expectation: 검색 결과를 모두 펼치기).
6. Inside the **tree panel**, at its **upper edge** (still **not** on the same row as 부서명·사용자명·사번 / Search / Reset), the operator uses **모두 펼치기** and **모두 접기**—styled as **보조(기능) 버튼** (secondary / outline or ghost per `docs/design/buttons.md`), with **slightly reduced vertical height** compared to the filter-row buttons, so they are not mistaken for **Search** or **Reset** but remain usable and focus-visible.
7. In the **user table**, the **권한그룹** control (`UserGroupAssignment` combobox / select) shows its **trigger label and chevron** without being **cut off** by the cell or row overflow at typical desktop width; opening the list is usable (no unusable clip solely due to cell CSS).
8. On a **narrow viewport** (width below the table’s enforced minimum), the **per-department user table** scrolls **horizontally inside its table region** so operators can read full values instead of losing information to **`...`** truncation.
9. **Problem** (baseline): layout could force frequent horizontal scroll; tree could start narrow; search and expand/collapse helpers were missing; **follow-up**: combobox may clip at cell boundary; tree bulk actions were not co-located with the tree; **narrow viewport**: reduced columns could still apply ellipsis/clamp too aggressively and hide **identifier and decision-critical** cell text.

### Expected outcome

- User grid columns use **layout and width rules** from `docs/design/grid-and-table.md` (e.g. prefer `%` + `min-width`, avoid unnecessary fixed widths; reduce padding only within standard bounds) so horizontal scroll is reduced on common viewports.
- When the **viewport is narrower** than the table’s **minimum intrinsic width** (column mins + padding), the **table’s scroll container** (not the whole page alone) provides **horizontal scroll** (`overflow-x: auto` on the scroll owner per `grid-and-table.md`: scroll wrapper contains the `<table>`). **Do not** rely on **`text-overflow: ellipsis`**, **`white-space: nowrap`** with hidden overflow, or **single-line line-clamp** on **data-bearing** columns (identifiers, names, 권한그룹 label, actions) merely to fit the viewport—operators must be able to scroll sideways to see full values. **`...`** is acceptable **only** for columns explicitly treated as **optional / cosmetic** in §2 (if any); otherwise truncation must not suggest missing data when content is still present off-screen.
- The tree container has a **sufficient default minimum width** and uses **available horizontal space** so the tree does not appear as a small centered column before expansion (align with `docs/design/layout-and-navigation.md` work-area usage where applicable).
- A **search/filter row** appears above the tree (toolbar area per `grid-and-table.md` page structure: header → toolbar → content). On typical desktop width, **부서명·사용자명·사번** fields and **Search** plus **검색 초기화 (Reset/Clear)** sit on **one row** (align with `docs/design/forms-and-filters.md` **Single row for non-date** intent: this screen has no date block, so the entire filter toolbar is that single row unless a narrow viewport forces wrap, in which case document breakpoint behavior in §5). Filters cover:
  - **Department name** (matches department node display name; case/whitespace policy consistent within implementation),
  - **User name** (display name as shown in the user table),
  - **Employee number (사번)** (same semantic as table “사용자 ID” column source: `employeeNumber` / `employee_number` when present).
- Field **order and grouping** follow `docs/design/forms-and-filters.md` **User-context filter order**: **부서 → 이름(사용자명) →** third identifier; for this screen the third control is **사번** (employee number), not login username, unless product later unifies labeling in `docs/design/search-fields-by-screen.md`.
- **Explicit (§2.4 user block)**: Department, user name, and 사번 filter controls use the **same width/size rules** as the user-context block on other aligned screens per `docs/design/search-fields-by-screen.md` and `docs/design/search-field-definition-items.md` (same role → same min/max width).
- When a non-empty search is **applied**, the implementation **expands** every tree node on the path from root to each **matching** department or user so all matches are visible; user rows **not matching** the filter are hidden (or equivalent UX that does not hide matches—product default: hide non-matching rows under expanded departments). Clearing the search restores showing all users under expanded nodes unless specified otherwise in implementation notes.
- **Expand all** expands every expandable node in the tree for current data. **Collapse all** collapses all nodes (or collapses to a defined default, e.g. roots only)—implementer must pick one consistent behavior and document in §5 if QA flags ambiguity. These two controls are **not** primary actions: they use the **Secondary** (outlined / subtle) pattern from `docs/design/buttons.md`, with **reduced visual weight** versus **Search** (primary) and **Reset** (secondary form action)—e.g. `size="small"` (or equivalent) and **slightly shorter vertical padding / line box** for this pair vs the filter toolbar buttons, aligned with **compact** guidance in `buttons.md` / `forms-and-filters.md`, while keeping **keyboard focus** and **minimum usable target** per `buttons.md`.
- **Tree action placement**: **모두 펼치기** / **모두 접기** sit in a **dedicated strip at the top of the tree column** (inside the same visual panel as the hierarchy list), **above** the scrollable node list, **not** in the global search/filter toolbar row. Spacing must separate them from the department nodes so they are not confused with tree content.
- **Permission group combobox**: Table cell / wrapper styles for the 권한그룹 column and `UserGroupAssignment` must avoid `overflow: hidden` clipping of the **control chrome** (label, icon, focus ring) at typical widths; if the column is narrow, prefer **ellipsis inside the select** or **`MenuList`/`Popper` portaling** (MUI `Select`) per component patterns—scope **frontend** unless investigation shows an API payload issue (unlikely).
- Controls use shared search/filter styling where required: `frontend/src/styles/search-filter-standard.css`, `docs/design/forms-and-filters.md` (compact variant where appropriate), `docs/design/css-standard-and-exceptions.md`.
- **Accessibility**: Filter inputs have labels; **Expand all** / **Collapse all** / **Search** / **Reset** (if present) are keyboard-operable with visible focus; icon-only controls include `aria-label` per `docs/design/buttons.md`.
- `specs/user-management-v2.spec.yaml` describes APIs for tree and user CRUD but **does not define client-side search**; behavior in this doc is **frontend-only**. If product requires documented UX in spec, Contract owner may add a non-normative § or link to this requirement.

**References (mandatory for implementers):** `docs/design/README.md`, `docs/design/grid-and-table.md`, `docs/design/forms-and-filters.md`, `docs/design/search-fields-by-screen.md`, `docs/design/search-field-definition-items.md`, `docs/design/layout-and-navigation.md`, `docs/design/buttons.md`, `docs/design/text-input.md`, `docs/design/css-standard-and-exceptions.md`, `docs/design/ux-frontend-standard-principles.md`.

## 2. Design

### 2.1 Security review (optional; when PII / decryption / access control)

- [ ] Security review performed (recommended before implementation completion)
- Risks: Search fields process **user names** and **employee numbers** (identifiers/PII). Screen is **admin-only** with existing hierarchy/list APIs; client-side filtering does not inherently widen who can see data but may change **what is visible at once** in the UI.
- Acceptance / recommendations: No new public API for search unless approved; keep access control unchanged (same `getUserPermissionHierarchy` + `getUsers` gating). Avoid logging full filter strings at INFO in production.

### Technical design

#### Codebase summary

- `UserManagement.js` renders `HierarchyTree` with `expandedCodes` state, `handleToggle`, and embedded `hierarchy-users-table` (`log-table`) per department node.
- `UserManagement.css` sets `.user-management` **max-width: 1400px** and **margin: 0 auto**, which can **center** content and limit usable width; tree section uses `user-permission-hierarchy-tree-section` from `UserPermissionHierarchy.css` with **min-width: 280px**—likely contributing to a **narrow initial tree** when the flex child shrinks.
- `UserPermissionHierarchy.css` defines `.hierarchy-node-users` with `overflow: auto` and `.hierarchy-users-table` cell padding—horizontal scroll may come from **many columns** (`UserGroupAssignment`, long text) and **table auto layout**.
- No search or expand-all state exists today; filtering would operate on **in-memory tree + user list** after `loadHierarchy()` unless backend paging/filter is introduced later.

#### Problem analysis

1. **Grid width**: Seven columns including interactive `UserGroupAssignment` widens the table; combined with container max-width and padding, users scroll horizontally often.
2. **Tree width**: Shared `.user-permission-hierarchy-tree-section` **min-width 280px** and centered page max-width make the tree column feel **small** before expansion.
3. **Discoverability**: Without search and expand/collapse, operators cannot quickly find users in deep trees.
4. **Combobox clip (follow-up)**: The 권한그룹 control may sit in a **tight table cell** with `overflow`, fixed column width, or flex rules that **truncate or hide** the end of the select trigger (chevron/label), hurting recognition and click targets.
5. **Tree actions placement (follow-up)**: Bulk **모두 펼치기 / 모두 접기** under the global filter row is **spatially disconnected** from the tree; operators expect these utilities **next to the tree**. Button **height** can still read too heavy vs compact tree chrome.
6. **Narrow viewport / ellipsis (amendment)**: After column reduction, CSS may still force **header or body cells** into **ellipsis** when flex/grid parents shrink the table below a **reasonable floor**. That hides **사용자 ID, 이름, 부서 맥락, 권한그룹** (and similar) behind **`...`**, which is **misleading** when the value exists but is clipped—operators expect **horizontal scroll** in the table area instead of silent truncation.

#### Solution approach

**Frontend:**

- **Layout / CSS**: Revisit `.user-management` width constraints (e.g. allow **full width** of main work area or raise max-width) so tree and tables share space; increase **tree section min-width** for v2 (prefer overrides scoped under `.user-management` to avoid breaking `UserPermissionHierarchy` legacy screen if still used).
- **Table**: Apply `docs/design/grid-and-table.md` column guidance—**`%` + `min-width`** per column; align the hierarchy user grid with the standard pattern **scroll wrapper** owns **`overflow-x: auto`** and contains the `<table>`. Enforce a **sensible minimum table width** (e.g. `min-width` on `.hierarchy-users-table`, the scroll wrapper, or an inner wrapper—implementer chooses the smallest change that guarantees the floor) so the layout **prefers horizontal scroll** over **shrinking below the floor**. **Relax or remove** `text-overflow: ellipsis` / **`white-space: nowrap`** + hidden overflow on **data-bearing** columns for narrow viewports so full text remains reachable via scroll; reserve ellipsis (if used at all) for **optional cosmetic** columns **listed in §2.5**—otherwise do not use ellipsis to hide required cell values.
- **Filter row (single row)**: Add one toolbar row inside the compact search panel (`sf-compact-panel` / `search-filter-standard.css`) with three fields (부서명, 사용자명, 사번) and **Search** (primary) + **검색 초기화** (secondary) per `forms-and-filters.md` **Buttons in forms** and **Single row for non-date** (this screen has no date row; fields + both buttons share that row at desktop width). Use flex/grid with shared gaps; field widths still follow `search-field-definition-items.md` / `--sf-field-user-block-*`. If viewport is too narrow, allow controlled wrap but prefer keeping **Search + Reset on the same row as the three fields** until a documented min-width is hit.
- **Tree panel header strip (separate from 검색)**: Render **모두 펼치기** / **모두 접기** inside the **tree column’s visual bounds**, in a **horizontal strip aligned to the top** of that panel (above the scrollable hierarchy). Use flex row with gap; **do not** place this strip on the filter toolbar row. Style per `docs/design/buttons.md`: **Secondary** (outline/ghost), **`size="small"`** (or equivalent), and **tighter block-axis padding** than `.sf-compact-panel` primary actions so vertical **extent is slightly smaller** than Search/검색 초기화—verify against `buttons.md` minimum targets and document in §5 if an exception row is added in `docs/design/css-standard-and-exceptions.md`.
- **UserGroupAssignment / table cell**: Inspect `UserGroupAssignment` root, `UserGroupAssignment.css`, and parent `.hierarchy-users-table` **td** rules: remove or narrow `overflow: hidden` on the combo cell where it clips the control; set **min-width** on the 권한그룹 column if design allows; ensure MUI `Select` uses **menu popper** so the list is not clipped by the cell; align overflow behavior with `docs/design/grid-and-table.md` for interactive cells.
- **Filter logic**: Client-side match on loaded tree: department name substring; user name and 사번 against row fields; normalization (trim, case-insensitive) must be consistent.
- **Expand on search**: Compute set of `department` codes to expand = all **ancestors** of departments that match **or** contain a matching user; merge into `expandedCodes` when search is applied.
- **Expand all / Collapse all**: `Expand all` adds all node codes that have children or users; `Collapse all` clears `expandedCodes` (or minimal set—implementer confirms).
- **Tests**: Extend `UserManagement.test.js` for filter matching, expand-on-search, expand/collapse; mock hierarchy data.

**Backend:**

- **None assumed.** If in-memory filtering is insufficient at scale (very large trees), document gap: need paginated or query-parameter-based API—hand off to Contract + Backend; do not implement here without requirement amendment.

**DB:**

- None.

**Contract / Spec:**

- Optional: add reference in `specs/user-management-v2.spec.yaml` metadata or Related docs to this requirement for client-side search UX; no API change required for default approach.

### §2.4 Search/filter pattern — Implementation note for Frontend

Implementer must **read and apply** field-level and layout values from `docs/design/search-field-definition-items.md`, `docs/design/search-fields-by-screen.md`, `docs/design/forms-and-filters.md`, and `frontend/src/styles/search-filter-standard.css` when adding filter controls; use `docs/design/css-standard-and-exceptions.md` for any screen-specific exception (comment + exception index). **If any required standard is missing or ambiguous** for this screen (e.g. exact label for the third field vs “사용자 ID” on other screens), do **not** hardcode arbitrarily—inform the user, cite the gap, propose a draft, request confirmation per `docs/design/ux-frontend-standard-principles.md` §2 and §10.

### §2.5 Table ellipsis policy (narrow viewport)

- **Design reference**: `docs/design/grid-and-table.md` — column widths prefer **`%` + `min-width`**; horizontal scroll appears **when necessary**; scroll wrapper owns **`overflow: auto`** around the table.
- **Default for User Management v2 user rows**: Treat **all columns currently shown** in `.hierarchy-users-table` as **data-bearing** for this amendment: **no** `text-overflow: ellipsis` (or equivalent single-line clamp) may hide **meaningful** values on narrow viewports—**horizontal scroll** must expose full text. If product later adds a **purely decorative** column (e.g. redundant icon with `aria-hidden`), it may be listed here as **cosmetic-only** and may use ellipsis; until then the list is **empty** and §1 applies to **all** visible columns.
- **Document in §5** the chosen **minimum table width** (or rule) and which element owns **`overflow-x: auto`** after implementation.

### Affected scopes and change targets (verification)

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|------------|-----------------------------------------------|
| Backend | No (unless gap) | N/A |
| Frontend (config UI + view screen) | Yes | Yes |
| DB | No | N/A |
| Contract / Spec | Optional note only | Optional |
| Cursor tools | No | N/A |

### Planned change file list (expected change targets)

**(Planned at authoring. Implementing agent (Step 4) confirms or amends this list when implementation is complete.)**

#### Frontend — **confirmed as implemented** (implementing agent updates for amendment)

- `frontend/src/components/UserManagement/UserManagement.js`
  - Filter state, apply/reset handlers, expand-all/collapse-all, derive expanded codes on search, filter visible user rows (and prune or dim non-matching branches as per §1). **Amendment**: move **모두 펼치기 / 모두 접기** DOM into the **tree panel** top region (selector e.g. a wrapper such as `.user-management-v2-tree-panel` / `.user-management-v2-tree-toolbar`—implementer names and documents); ensure search row JSX is **unchanged** in structure except as needed for layout siblings.
- `frontend/src/components/UserManagement/UserManagement.css`
  - Container width, tree panel min-width for v2, table column tightening per design refs. **Amendment**: styles for **inner tree toolbar** (top-aligned, compact buttons), and any **table cell** overflow rules affecting the 권한그룹 column. **Narrow-viewport amendment**: **`min-width`** on `.hierarchy-users-table` and/or its **scroll wrapper** (per `grid-and-table.md`); ensure **`overflow-x: auto`** on the scroll owner; **relax** ellipsis / nowrap rules on data columns so scroll—not `...`—is the overflow strategy.
- `frontend/src/components/UserGroupAssignment/UserGroupAssignment.js`
  - **Amendment (if needed)**: `Select` `MenuProps`, `slotProps`, or display width so the trigger is not clipped; avoid assumptions that break a11y labels.
- `frontend/src/components/UserGroupAssignment/UserGroupAssignment.css`
  - **Amendment**: full-width select where appropriate, `min-width` / `max-width`, text overflow on **input** only—not on the whole control box if that hides the chevron.
- `frontend/src/components/UserPermissionHierarchy/UserPermissionHierarchy.css`
  - **Unchanged** for global rules — prefer overrides under `.user-management` / v2 tree panel only. **Exception**: if `.hierarchy-node-users` / `.hierarchy-users-table` rules are the only place enforcing ellipsis or overflow, v2-scoped overrides may amend **narrow-viewport** scroll + **min-width** here instead of duplicating—implementer confirms least blast radius.
- `frontend/src/components/UserManagement/UserManagement.test.js`
  - Unit tests for filter, expand-on-search, expand/collapse, and key a11y attributes where testable. **Amendment**: assert tree-toolbar placement (e.g. within tree panel container query) and avoid regressions on filter toolbar row (TC-15, TC-18). **Narrow-viewport amendment**: TC-20/TC-21 are **manual / browser** unless a stable RTL assertion for scroll container + `min-width` is agreed (optional unit smoke).
- `frontend/src/styles/search-filter-standard.css`
  - **Unchanged** (imported from `UserManagement.js` for `.sf-compact-panel`).
- `docs/design/css-standard-and-exceptions.md`
  - §5 Exception index row for User Management v2 layout/table overrides.

#### Backend

- None (document in this doc and branch requirement if API becomes necessary).

#### Contract / Spec

- `specs/user-management-v2.spec.yaml` (optional cross-reference only).

## 3. Test approach

### Test case list (required)

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|----|-------|------|--------------------------------|-----------------|--------------------------------------------|
| TC-01 | Frontend | Normal | Default load with multi-level tree and users | Tree section width ≥ planned minimum; layout does not present as overly narrow centered strip on 1280px-class viewport | Manual / browser |
| TC-02 | Frontend | Normal | Same data, compare horizontal scroll extent before/after | Horizontal scrollbar thumb range reduced or eliminated on reference viewport (document viewport width in §5) | Manual / browser |
| TC-03 | Frontend | Normal | Enter department partial name and apply | Departments whose **display name** matches show expanded path; matching departments visible | Unit + Manual / browser |
| TC-04 | Frontend | Normal | Enter user name substring and apply | All ancestors expanded; non-matching user rows hidden under visible departments; matching rows visible | Unit + Manual / browser |
| TC-05 | Frontend | Normal | Enter 사번 substring and apply | Same as TC-04 for employee number field | Unit + Manual / browser |
| TC-06 | Frontend | Edge | Search with multiple branches matching | All branches expanded so **no** match is hidden by collapsed parent | Unit |
| TC-07 | Frontend | Edge | Clear / Reset filters | Full user list visibility restored under expanded nodes; filter fields empty | Unit + Manual / browser |
| TC-08 | Frontend | Normal | Click **Expand all** | Every node with children or users is expanded | Unit + Manual / browser |
| TC-09 | Frontend | Normal | Click **Collapse all** | Expanded state cleared per §2 definition; no inconsistent partial expand | Unit + Manual / browser |
| TC-10 | Frontend | Edge | Search then **Collapse all** then **Expand all** | Deterministic state; no runtime error | Unit |
| TC-11 | Frontend | Normal | Tab through filter and buttons | Focus order logical; focus visible; buttons operable with Enter/Space | Manual / browser |
| TC-12 | Frontend | Regression | `UserGroupAssignment` and delete still work on visible row | No regression from layout/CSS changes | Manual / browser |
| TC-13 | Frontend | Normal | User-block filter field widths vs reference screen | Department / name / 사번 controls use same min/max width behavior as activity log (or documented aligned screen) per design | Manual / browser |
| TC-14 | Frontend | Exception | Empty tree / no users | No crash; controls disabled or empty state clear | Unit |
| TC-15 | Frontend | Normal | Desktop width (e.g. 1280px-class viewport from TC-01); filter panel visible | **부서명·사용자명·사번**, **Search**, and **검색 초기화** appear on **one toolbar row** without placing expand/collapse in that row | Manual / browser |
| TC-16 | Frontend | Normal | Same view as TC-15 | **모두 펼치기** / **모두 접기** are **secondary** (outline/ghost, not primary filled), **visibly smaller** than Search/Reset (including **slightly reduced vertical height** vs filter-row buttons per amendment), and sit **inside the tree panel** at its **top** (not on the filter toolbar row) per §1 amendment | Manual / browser |
| TC-17 | Frontend | Normal | Row with `UserGroupAssignment`; typical desktop width (e.g. 1280px-class); medium-length permission group label | Combobox **trigger** (text + chevron) is **not clipped** by table cell edges; control remains clickable; opened menu usable (portal/popper not trapped in a hidden overflow) | Manual / browser |
| TC-18 | Frontend | Regression | After amendment: compare filter toolbar DOM/layout | **부서명·사용자명·사번**, **Search**, **검색 초기화** remain on the **same single toolbar row** as TC-15; expand/collapse **not** injected into that row | Manual / browser + unit (structure query where reliable) |
| TC-19 | Frontend | Normal | Focus tree-area **모두 펼치기** / **모두 접기** | Focus ring visible; **Enter/Space** activates; vertical size **smaller** than adjacent filter primary/secondary pair (visual check or computed style snapshot in §5) | Manual / browser |
| TC-20 | Frontend | Normal | Narrow viewport: browser width **below** the table minimum (e.g. **~768px** width or narrower—document exact width used in §5); expanded department with user rows visible | The **user table region** shows a **horizontal scrollbar** when table intrinsic width exceeds the viewport; user can scroll horizontally to bring **all** header labels and **data-bearing** cell text (see §2.5) **fully into view** without depending on ellipsis | Manual / browser |
| TC-21 | Frontend | Normal | Same as TC-20; rows with medium/long **이름**, **사용자 ID**, **권한그룹** labels | **No** misleading **`...`** truncation on those columns at rest: either full text fits in the scrolled layout or horizontal scroll reveals full text; if any column retains ellipsis, it must be **§2.5 cosmetic-only** and documented—otherwise **fail** | Manual / browser |

### Test scenarios

#### Scenario 1: Layout and scroll

1. Open User Management v2 with representative data (deep tree, wide permission group names).
2. Note tree panel width and horizontal scroll on user table.
3. After implementation, repeat; confirm improvement per TC-01/TC-02.
4. Narrow the browser below the table minimum; confirm **TC-20** (scrollbar on table region) and **TC-21** (key columns readable, no inappropriate ellipsis).

#### Scenario 2: Search and expand

1. Collapse several inner nodes.
2. Apply search matching a deep user.
3. Confirm path expanded and user row visible (TC-03–TC-06).

#### Scenario 3: Expand / collapse controls

1. Use **Collapse all** then **Expand all**; confirm tree state matches TC-08/TC-09.

#### Scenario 4: Filter toolbar layout and tree action styling

1. At desktop width, confirm TC-15 and TC-18 (single-row filters + Search + Reset; bulk tree buttons **not** on that row).
2. Confirm TC-16 and TC-19 (expand/collapse inside **tree panel top**, secondary, compact height).
3. Confirm TC-17 (권한그룹 combobox not clipped).

### Test data

- Use hierarchy with at least three levels and multiple siblings; include users with distinct names and employee numbers for filter tests.
- Include one user with long permission group label to stress table width.

### Test environment

- Frontend: `http://localhost:3001` (per contract)
- Backend: `http://localhost:9200`
- Database: per project dev DB

### 3.5 Browser automation verification (optional)

- **Applicable TCs**: TC-01, TC-02, TC-11–TC-13, TC-15–TC-21 (and TC-03–TC-09 snapshot checks).
- **Procedure**: Login → navigate to User Management v2 → `browser_snapshot` for layout → exercise filter and expand buttons per TC steps.
- **Reference**: `docs/workflow/BROWSER-AUTOMATION-VERIFICATION-POLICY.md`

## 4. Checklist

### Frontend verification

- [x] Filter behavior and expand/collapse match §1 core behavior (automated TC-03–TC-10, TC-14; manual TC-01, TC-02, TC-11–TC-13) — re-confirm after amendment if tree toolbar DOM moves
- [x] Layout/styling: TC-15, TC-16, TC-18 (filter row unchanged; tree bulk actions **inside tree section top**; not in `.user-management-v2-search-panel`) — automation in `UserManagement.test.js`; TC-16/TC-19 **visual** (compact MUI height vs filter row) still 1280px-class manual
- [ ] Combobox: TC-17 (no clip on 권한그룹 control)—manual / browser after CSS (overflow visible on col 5, min-width, select width)
- [ ] Narrow viewport: TC-20, TC-21 (table region horizontal scroll; no misleading ellipsis on data columns per §2.5) — CSS landed; QA/browser confirmation
- [x] Design docs referenced; no arbitrary widths contradicting `search-filter-standard.css` (user block uses `--sf-field-user-block-*`; exceptions documented §5 css-standard)
- [x] Error/edge cases (empty tree) handled

### Backend verification

- [ ] N/A unless API gap opened

### Integration

- [ ] End-to-end load + search + mutation smoke (optional)

### Documentation

- [x] Requirement doc §4/§5 updated for amendment implementation (manual TC-17/TC-19 remain)
- [ ] Optional spec cross-reference updated

## 5. Test results

### Test run date

- 2026-04-08 (Frontend Step 4 automated tests)
- **Amendment follow-up 2026-04-08 (Frontend)**: Tree bulk actions moved into `section[aria-label="부서별 사용자 계층"]` top; 권한그룹 column CSS anti-clip; compact tree bulk button height — tests + build re-run below.
- **2026-04-08 (Frontend, narrow-viewport table)**: `UserManagement.css` §2.5 hierarchy table scroll + ellipsis removal — `npm test` + `npm run build` re-run (record counts below).

### Test results

#### Frontend

- **Pass**: `npm test -- --watchAll=false --testPathPattern=UserManagement.test` — **27/27** tests passed (includes TC-03–TC-10, TC-14; **TC-15 / TC-16 / TC-18**: `.user-management-v2-filter-toolbar-row` contains only the three filter fields + 검색 + 검색 초기화; `모두 펼치기` is `MuiButton-outlined` + `MuiButton-sizeSmall`, **not** under `.user-management-v2-search-panel`, **is** inside `.user-permission-hierarchy-tree-section`; tree toggle queries use `/^펼치기$/` to avoid clash with “모두 펼치기”). **Re-pass 2026-04-08** after narrow-viewport table CSS: **27/27**.
- **Pass**: `npm run build` — completed without error (2026-04-08 amendment follow-up). **Re-pass 2026-04-08** after narrow-viewport table CSS.
- **Manual / browser still required** (per §3): TC-01, TC-02, TC-11–TC-13, **TC-17** (combobox trigger/chevron and open list at 1280px, long label), **TC-16 / TC-19** (tree buttons visibly slimmer than filter **검색/검색 초기화**; focus ring + Enter/Space), TC-15 single-row filter at 1280px, plus filter field width parity vs reference screen.
- **Narrow viewport (§2.5, TC-20, TC-21)**: Implemented in `UserManagement.css` — horizontal scroll owner `.user-management .hierarchy-node-users` (`overflow-x: auto`); table `.user-management .hierarchy-users-table` uses `width: max-content`, `min-width: max(100%, 35rem)` (floor **35rem** ≈ sum of column `min-width`s + margin for padding); data `th`/`td` use `overflow: visible` and **`text-overflow: clip`** (no `...`). **TC-20 / TC-21 remain manual** in browser (~768px or narrower width per §3); no new RTL assertion in `UserManagement.test.js`.

#### Backend

- N/A

**Commands:**

```bash
cd /Volumes/T7/dev/logmng_frontend/dev/frontend && npm test -- --watchAll=false --testPathPattern=UserManagement.test
cd /Volumes/T7/dev/logmng_frontend/dev/frontend && npm run build
```

### Issues found and resolution

- **RTL query clash**: Substring match `/펼치기/` matched both tree toggles and “모두 펼치기”. **Resolution**: use `/^펼치기$/` for tree expand buttons in tests.

## 6. Error remedy result (cause and action) — for error/bug fix requirements only

- N/A (feature/enhancement requirement).

---

## 7. Final version (Korean) — add after all verification is complete

Per `docs/workflow/DOCUMENT-LANGUAGE-POLICY.md` §2: QA 검증 완료 후 §1·기대효과·검증 결과 요약을 한국어로 추가한다.

---

**Author**: Requirements (subagent)  
**Date**: 2026-04-08  
**Status**: In progress (amendment 2026-04-08 implemented in frontend; **manual** TC-17 and TC-16/TC-19 visual checks pending QA/browser)
