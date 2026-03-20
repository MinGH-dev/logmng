# 20260317 - Search History screen improvements

## 1. User requirement

### Requirement description

Improve the Search History (검색 이력) screen with: (1) default search date range and quick presets (7d / 15d / 30d), (2) consistent styling of the decryption-approval filter so it does not look read-only, (3) shorter label "복호화" instead of "복호화 승인 여부", (4) smaller column widths for sequence number and decryption-approval status in the result grid, and (5) confirmation that result paging follows the same rules as other list screens (user activity log, log search).

### User scenario

1. User opens the Search History screen (복호화 승인).
2. **Problem**: Search date range is empty by default; user must set start/end every time. No quick way to select "last 7 / 15 / 30 days".
3. **Problem**: The "복호화 승인 여부" filter control has a background that looks like a read-only or disabled field (e.g. dark), unlike other editable filter fields.
4. **Problem**: The label "복호화 승인 여부" is long; user expects a shorter label "복호화" in both the search filter and the result grid.
5. **Problem**: In the result grid, the sequence number (순번) and decryption-approval status columns are wider than needed for their values.
6. User expects search result paging (total count, rows per page, prev/next) to behave the same as on User Activity Log and Log Search screens.

### Expected outcome

- **Default search date**: On load, "검색일시 (시작)" is set to **d−7** (start of day, 7 days ago) and "검색일시 (종료)" to **d+0** (end of today). Values use the same semantics and format as the existing datetime-local fields (API format `yyyy-MM-dd HH:mm:ss`). Reset restores these defaults (and other filter defaults).
- **Quick presets**: A control (e.g. select or button group) **7d | 15d | 30d** is placed to the right of the search-date fields. Selecting 7d sets start to d−7, 15d to d−15, 30d to d−30; end remains d+0. After selection, the search is refreshed automatically (same effect as clicking Search) while **keeping all other applied filters** (requester, approval statuses, request reason). Existing search conditions are preserved.
- **Decryption-approval filter styling**: The decryption-approval filter control (dropdown trigger and panel) uses the **same background (and overall look) as other editable filter fields** on the screen (e.g. request reason input, date inputs) so it is clearly editable, not read-only.
- **Label change**: The label "복호화 승인 여부" is changed to **"복호화"** in: (a) the search filter (dropdown label and any aria-label), (b) the result grid column header. No change to option labels inside the dropdown (대기, 승인, 반려, 만료).
- **Grid column size**: The **순번** (sequence) and **복호화** (decryption-approval status) columns are **reduced in width** so they fit their content (e.g. narrow min-width or fixed narrow width); other columns unchanged unless needed for layout.
- **Paging**: Search result list continues to use the shared DataTable pagination contract: total count ("총 n건"), rows-per-page control (default 20, min/max per grid-and-table.md), prev/next (or simple pagination). Behavior matches User Activity Log and Log Search (same rules); no functional change if already aligned.

**Note**: Numeric and structural values (e.g. default date range, column min-width) must be sourced from or aligned with design docs. This requirement references `docs/design/search-fields-by-screen.md` §4 (search-history) and `docs/design/grid-and-table.md` for table/pagination. Field defaults and labels will be updated in the design doc to stay in sync.

## 2. Design

### 2.1 Security review (optional)

Not applicable (no PII, decryption scope, or access-control change).

### Technical design

#### Problem analysis

1. **Default date**: Search History currently initializes `requestedAtFrom` and `requestedAtTo` to empty strings; the design doc §4.2 lists `defaultValue: ''` for both. Users expect a default range (start d−7, end d+0) to reduce repeated input.
2. **Presets**: There is no 7d/15d/30d preset; adding a selector that sets start date and triggers search (keeping other filters) improves usability.
3. **Decryption-approval field appearance**: The approval dropdown may be perceived as read-only due to background or inherited styles; it must match other editable filter fields (e.g. same background as `form-control` / search-filter standard).
4. **Label length**: "복호화 승인 여부" is verbose; shortening to "복호화" in the filter and grid is a product request.
5. **Grid columns**: `SEARCH_HISTORY_COLUMNS` defines no column widths; the table uses default layout. Sequence and approval-status values are short; columns can be narrowed.
6. **Paging**: Search History already uses DataTable with `pagination`, `pageSize`, `onPageSizeChange`, default 20, and backend supports `page`/`pageSize`. Verification is to ensure the same rules as activity log and log search (shared footer, default 20, same UX).

#### Solution approach

**Frontend:**

- **Default date**: On initial mount, set `requestedAtFrom` and `requestedAtTo` to computed values: start = (today − 7 days) 00:00:00, end = today 23:59:59 (or equivalent in local time; format compatible with `datetime-local` and `toApiDatetime`). Apply the same defaults on Reset (replace empty with d−7 / d+0).
- **7d / 15d / 30d presets**: Add a control (e.g. select or button group) next to the search-date row (to the right of the end-date field). On change: set start date to d−7, d−15, or d−30 (end = d+0), then call the same logic as Search submit (set applied date range, set page to 1, trigger `loadList`) without changing other applied filters (requester, approval statuses, request reason).
- **Decryption-approval styling**: Ensure the approval dropdown trigger (and panel if needed) use the same background as other editable controls (e.g. `#fff` or `var(--sf-panel-bg)` / standard input background from `search-filter-standard.css`). Remove or override any style that makes it look disabled (e.g. dark background). Align with `docs/design/css-standard-and-exceptions.md` and `frontend/src/styles/search-filter-standard.css`.
- **Label "복호화"**: In `SearchHistoryList.js`, change the dropdown label text and any `aria-labelledby` / `aria-label` from "복호화 승인 여부" to "복호화". In `SEARCH_HISTORY_COLUMNS`, change the column with `key: 'approvalStatus'` label from "복호화 승인 여부" to "복호화".
- **Grid column size**: Add column width constraints for the sequence (seq) and approval status (approvalStatus) columns so they are narrower (e.g. via `columns` config with `width`/`minWidth` if DataTable supports it, or via CSS class on table/col/td for those columns). Use values that fit content (e.g. seq: narrow fixed or min-width; approval status: fit "대기"/"승인"/"반려"/"만료").
- **Paging**: Confirm Search History uses the shared DataTable footer (total count, rows-per-page 1–100, default 20, prev/next when multi-page). If already correct, no code change; otherwise align with `docs/design/grid-and-table.md` and User Activity Log / Log Search behavior.

**Backend:**

- No change. Date range and presets are client-side; list API already supports `requestedAtFrom`, `requestedAtTo`, `page`, `pageSize`. Label and column width are UI-only.

**DB:**

- No change.

**Design doc update:**

- Update `docs/design/search-fields-by-screen.md` §4.2 (search-history): set `defaultValue` for `requestedAtFrom` to "d−7 00:00:00" and for `requestedAtTo` to "d+0 23:59:59" (or equivalent wording); update label for approval field from "복호화 승인 여부" to "복호화". Optionally document the 7d/15d/30d preset in the toolbar row 1 description.

**Implementation note for Frontend (search/filter and grid):**

Implementer must read and apply field-level and layout values from `docs/design/search-field-definition-items.md`, `docs/design/search-fields-by-screen.md` §4 (search-history), and `docs/design/forms-and-filters.md` when changing the search form or toolbar. For table and paging, apply `docs/design/grid-and-table.md`. For control styling (including the decryption-approval dropdown), use `frontend/src/styles/search-filter-standard.css` and `docs/design/css-standard-and-exceptions.md`; for any screen-specific exception, use component CSS only with a comment and document in the Exception index (§5). If any required standard for layout, field sizing, or control semantics is not defined or is ambiguous in these design docs, the implementer must not infer or hardcode a solution; inform the user of the undefined items, explain why each is needed, propose a recommended standard draft, and request feedback before implementation.

### Affected scopes and change targets (verification)

**Before finalizing §2**, the Requirements author must verify that every affected scope is covered. See `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md`.

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|-----------|-----------------------------------------------|
| Backend | No | N/A |
| Frontend (config UI + view screen) | Yes | Yes |
| DB | No | N/A |
| Contract / Spec | No (optional: doc default in api-definition) | Optional |
| Cursor tools (skills, specs) | No | N/A |

This requirement does not add or change API or DB schema. It aligns one screen’s UI with product expectations and design standards. Pattern 3.4 (search/filter UI consistency) partially applies (field defaults, label, styling); design doc references are included.

### Planned change file list (expected change targets)

**(Planned at authoring. Implementing agent (Step 4) confirms or amends this list when implementation is complete.)**

#### Frontend

- `frontend/src/components/SearchHistory/SearchHistoryList.js`
  - Set default `requestedAtFrom` / `requestedAtTo` to d−7 and d+0 on init and on Reset.
  - Add 7d / 15d / 30d preset control to the right of search-date fields; on select, set start date (d−7, d−15, d−30), end d+0, then apply and refresh list (keep other filters, set page to 1).
  - Change approval dropdown label and grid column label from "복호화 승인 여부" to "복호화".
  - Add or pass column width constraints for seq and approvalStatus columns (narrower width).
- `frontend/src/components/SearchHistory/SearchHistory.css`
  - Ensure decryption-approval dropdown trigger (and panel if needed) use the same background as other editable filter fields; remove or override any dark/read-only appearance. Align with `search-filter-standard.css` and `docs/design/css-standard-and-exceptions.md`.
  - If column widths for seq/approvalStatus are implemented via component CSS, add scoped rules (and document exception in design if applicable).
- `frontend/src/components/SearchHistory/SearchHistoryList.test.js`
  - Update tests: default date range present on load; Reset restores d−7/d+0; preset selection updates dates and triggers search; label "복호화" in filter and grid; paging still present and consistent.

#### Design doc

- `docs/design/search-fields-by-screen.md`
  - §4.2 search-history: update `requestedAtFrom` / `requestedAtTo` default values to d−7 and d+0; change approval field label to "복호화"; optionally describe 7d/15d/30d preset in row 1.

#### Backend

- None.

#### DB

- None.

## 3. Test approach

### Test case list (required)

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|----|-------|------|------------------------------|-----------------|--------------------------------------------|
| TC-01 | Frontend | Normal | Open Search History screen; no prior interaction. | "검색일시 (시작)" shows d−7 00:00, "검색일시 (종료)" shows today 23:59 (or equivalent). | Unit (npm test) or Manual |
| TC-02 | Frontend | Normal | Click Reset. | Date range resets to d−7 and d+0; other filters reset per spec. | Unit or Manual |
| TC-03 | Frontend | Normal | Select 7d preset (with other filters set). | Start = d−7, end = d+0; list refreshes; other applied filters unchanged. | Unit or Manual |
| TC-04 | Frontend | Normal | Select 15d then 30d. | Start becomes d−15 then d−30; list refreshes each time; other filters unchanged. | Unit or Manual |
| TC-05 | Frontend | Normal | Compare decryption-approval filter control with request reason input. | Same background (and editable appearance); no dark/read-only look. | Manual |
| TC-06 | Frontend | Normal | Check filter label and grid column header. | Both show "복호화" (not "복호화 승인 여부"). | Unit or Manual |
| TC-07 | Frontend | Normal | View result grid with multiple rows. | 순번 and 복호화 columns are narrower than before; content not truncated. | Manual |
| TC-08 | Frontend | Normal | Search returns more than one page. | Footer shows total count, rows-per-page (default 20), prev/next; behavior matches activity log. | Manual |
| TC-09 | Frontend | Regression | Change rows-per-page and navigate pages. | Page resets to 1 when filters or page size change; list loads correctly. | Unit or Manual |

### Test scenarios

#### Scenario 1: Default date and presets

1. Open Search History.
2. Confirm default start d−7 and end d+0.
3. Select 15d preset; confirm start = d−15 and list reloads.
4. Set requester/approval/request reason, click Search; then select 30d; confirm list refreshes with d−30/d+0 and other filters unchanged.

#### Scenario 2: Styling and labels

1. Compare decryption-approval dropdown with request reason input: same background, editable look.
2. Confirm filter label and grid header show "복호화".

#### Scenario 3: Grid and paging

1. Run a search that returns many rows.
2. Confirm seq and 복호화 columns are narrow; paging shows total count and rows-per-page; prev/next work.

### Test data

- No special test data required. Use existing search-history records or create via normal flow (search with decryption approval request).

### Test environment

- Frontend: `http://localhost:3001` (or per contract)
- Backend: `http://localhost:9200`
- Database: per project setup

### 3.5 Browser automation verification (optional)

Applicable TCs: TC-01–TC-08 (manual checks). QA may use Browser MCP to open Search History, snapshot, and verify default dates, preset control, labels, grid column widths, and footer. Reference: `docs/workflow/BROWSER-AUTOMATION-VERIFICATION-POLICY.md`.

## 4. Checklist

### Frontend verification

- [ ] Default date range and Reset behavior verified
- [ ] 7d/15d/30d preset updates dates and refreshes list; other filters preserved
- [ ] Decryption-approval control styling matches editable fields
- [ ] Label "복호화" in filter and grid
- [ ] Seq and 복호화 column widths reduced; paging unchanged/aligned

### Backend verification

- [ ] No backend change; list API unchanged

### Integration

- [ ] Search and paging work end-to-end after changes

### Documentation

- [ ] Requirement doc completed
- [ ] Design doc (§4.2) updated when implementation is done

## 5. Test results

### Test run date

- [To be filled by QA]

### Test results

#### Frontend

[To be filled]

#### Backend

[To be filled]

**Commands:**

[One executable command per TC when §5 is completed.]

**Outcome:**

- [To be filled]

### Issues found and resolution

[To be filled if any.]

### Next steps

[To be filled]

## 6. Error remedy result (cause and action) — for error/bug fix requirements only

N/A (feature/improvement requirement).

---

**Author**: Requirements subagent  
**Date**: 2026-03-17  
**Status**: In progress
