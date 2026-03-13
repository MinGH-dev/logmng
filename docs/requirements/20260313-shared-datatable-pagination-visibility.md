# 20260313 - Shared DataTable footer visibility and tooling alignment

## 1. User requirement

### Requirement description

Extend the previous shared `DataTable` pagination visibility bugfix so that shared data-table screens always render the **footer region** when paging/footer metadata exists, even when the dataset fits on a single page. The shared footer must keep the **total count** (`총 n건`), **rows-per-page control** (`표시 건수`), and the **pagination/footer area itself** visible as a stable part of the shared table contract.

This is a **frontend shared-component and standards-alignment follow-up**. It must preserve existing API behavior, paging semantics, sort behavior, and page-size behavior. No backend, DB, or contract change is planned.

### User scenario

1. A user opens **search history**, **pending approvals**, **activity log**, or another shared `DataTable` screen and the result fits on a single page.
2. The current shared implementation still hides the entire footer region because the shared component renders it only when `pagination && pagination.totalPages > 1`, and several consumer screens also pass `pagination` only for multi-page results.
3. As a result, the user cannot see the total result count, cannot access the rows-per-page control, and cannot confirm a stable footer region even though the screen still uses shared table paging/footer UX.
4. **Problem**: The shared footer contract is still treated as a multi-page-only UI fragment instead of a stable shared table footer, so one-page result sets lose shared information and controls across consumer screens.

### Expected outcome

- **Always-visible shared footer**: On shared data-table screens that use the shared paging/footer UX, the footer region must render even when `totalPages === 1`.
- **One-page stability**: For one-page datasets, the footer region remains visible and height-stable. Page navigation buttons may be disabled or absent, but the footer region itself must not disappear.
- **Total count and rows-per-page visibility**: The footer must keep the total count (`총 n건`) and the rows-per-page control (`표시 건수`) visible for one-page and multi-page results alike.
- **Shared contract first**: The fix must be made in the shared `DataTable` component/CSS and in the shared consumer contract for pagination/footer metadata, not as a single-screen workaround.
- **Consumer alignment**: Consumer screens that currently pass `pagination` only when `totalPages > 1` must align to the shared footer contract so one-page results still provide the shared footer data.
- **Future-proof guidance**: Related design/workflow documents must explicitly describe that the shared table footer region is always visible, while one-page navigation buttons may be disabled or omitted.
- **Behavior preserved**: Existing multi-page paging semantics, sorting, and page-size behavior remain unchanged apart from the one-page footer visibility improvement.

## 2. Design

### 2.1 Security review (optional; when PII / decryption / access control)

Not applicable. This requirement changes shared frontend footer/layout behavior and requirement/tooling guidance only. No PII, decryption scope, or access-control behavior changes.

### 2.2 Codebase summary

- `frontend/src/components/DataTable.js`
  - The shared component currently renders the entire footer region only when `pagination && pagination.totalPages > 1`.
  - `renderPaginationButtons()` already returns `null` for one-page data, so the current implementation couples **footer visibility** and **page-navigation visibility** too tightly.
  - `showRowsPerPage` is already derived from `pagination != null && typeof onPageSizeChange === 'function'`, so one-page footer visibility depends on the shared pagination contract continuing to exist.
- `frontend/src/components/DataTable.css`
  - Owns the shared footer layout (`.pagination`, `.pagination-info`, `.rows-per-page`, `.pagination-buttons`) and the table-container contract (`.log-table-container` / `.table-wrapper`).
  - Was reviewed for the follow-up, but did not require a change because the one-page footer fix was completed by shared rendering logic and consumer metadata alignment.
- `frontend/src/components/LogGrid.js`
  - Participates in the shared log-table flow and was aligned so one-page datasets preserve shared footer metadata on the grid path.
- `frontend/src/components/SearchHistory/SearchHistoryList.js`
  - Was aligned so one-page results still pass shared footer metadata instead of suppressing the footer at the consumer layer.
- `frontend/src/components/PendingApprovals/PendingApprovals.js`
  - Was aligned so one-page results keep total count and rows-per-page visible through the shared footer contract.
- `frontend/src/components/LogTable.js`
  - Was aligned for one-page shared footer metadata so log-table consumers no longer suppress the footer region for single-page datasets.
- `frontend/src/components/ImageLogTable.js`
  - Was aligned for one-page shared footer metadata on the image-log table path.
- `frontend/src/components/UserActivityLog/UserActivityLogTable.js`
  - Was aligned for one-page shared footer metadata; this consumer already carried `infoText` and now keeps the shared footer visible for one-page results as well.
- `frontend/src/components/DataTable.test.js`
  - Regression coverage was added or updated to guard the shared footer contract directly for both one-page and multi-page cases.
- `frontend/src/components/SearchHistory/SearchHistoryList.test.js` and `frontend/src/components/PendingApprovals/PendingApprovals.test.js`
  - Regression coverage was added or updated so one-page shared footer visibility is verified on concrete consumer screens.
- `docs/design/grid-and-table.md` and `docs/workflow/CONSISTENCY-STANDARDS.md`
  - Define the shared table/footer standard and must explicitly describe that the footer region stays visible on one-page datasets.
- `docs/workflow/REQUIREMENTS-AUTHORING-WORKFLOW.md`, `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md`, and `docs/workflow/HANDOFF-CHECKLIST.md`
  - Must guide future requirement authors and frontend handoffs to treat hidden total count / page-size / footer issues as shared footer-contract work first, including one-page datasets.

### Technical design

#### Problem analysis

1. The previous fix corrected the shared container/overflow contract for multi-page pagination visibility, but the shared component still suppresses the entire footer region when `totalPages <= 1`.
2. The footer region currently bundles together three concerns that should not share the same visibility gate: total-count display, rows-per-page control, and page-navigation buttons.
3. Several consumer screens still treat `pagination` as a multi-page-only prop (`totalPages > 1 ? ... : null`), so one-page datasets lose footer data before the shared component can render it.
4. Because this is a shared `DataTable` contract issue, a screen-specific workaround would leave the same one-page footer regression available on other consumers.
5. Current design/workflow guidance describes shared pagination placement, but it does not state clearly enough that one-page datasets must keep the shared footer region visible and stable.
6. Existing regression coverage is centered on multi-page visibility; it does not yet assert that one-page results keep total count and rows-per-page visible.

#### Solution approach

**Frontend:**

- Update the shared `DataTable` footer contract so the footer region can render whenever shared footer data exists, even when `totalPages <= 1`.
- Separate **footer-region visibility** from **page-navigation-button visibility**. One-page datasets may hide or disable the navigation buttons, but the footer region itself must remain visible.
- Keep the fix at the shared component/shared consumer-contract layer first; do not apply screen-specific footer workarounds.
- Align `SearchHistoryList`, `PendingApprovals`, `LogTable`, `ImageLogTable`, `UserActivityLogTable`, and `LogGrid` so one-page datasets still provide the shared footer metadata needed for total count and rows-per-page display.
- Extend frontend regression coverage in `DataTable.test.js`, `SearchHistoryList.test.js`, and `PendingApprovals.test.js` so both one-page and multi-page data assert shared footer visibility, total count visibility, and rows-per-page visibility.
- Preserve current page-size control behavior (`20` default, immediate apply on `+/-`, Enter apply), sorting behavior, and API call flow.

**Cursor tools / docs:**

- Update `docs/design/grid-and-table.md` so the shared table standard explicitly states that the footer region remains visible for one-page and multi-page datasets; only the navigation buttons may be omitted or disabled when there is one page.
- Update `docs/workflow/CONSISTENCY-STANDARDS.md` §6 to mirror the always-visible shared footer rule as part of the unified data-table standard.
- Update `docs/workflow/REQUIREMENTS-AUTHORING-WORKFLOW.md` so requirement authors explicitly inspect one-page footer visibility when a shared table footer issue is reported.
- Update `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md` so shared UI primitive defects cover both the shared footer target and the consumer screens that currently suppress footer metadata on one-page datasets.
- Update `docs/workflow/HANDOFF-CHECKLIST.md` so Frontend handoffs for grid/table/pagination/footer work mention the always-visible footer rule and the one-page navigation expectation.

**Backend:**

- No change planned.

**DB:**

- No change planned.

**Contract / Spec:**

- No change planned. The follow-up must preserve the existing pagination API contract and response shape.

### Affected scopes and change targets (verification)

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|-----------|-----------------------------------------------|
| Backend | [ ] No | [x] N/A |
| Frontend (config UI + view screen) | [x] Yes | [x] Yes |
| DB | [ ] No | [x] N/A |
| Contract / Spec | [ ] No | [x] N/A |
| Cursor tools (skills, specs) | [x] Yes | [x] Yes |

**Change target verification result**:

- The follow-up remained a **shared footer-contract** defect, not a single-screen bug.
- Shared change targets were implemented first in `DataTable.js`, then consumer screens were aligned so one-page footer metadata is not dropped before shared rendering.
- `DataTable.css` was reviewed but did not require a change in this follow-up; the actual implementation centered on shared rendering logic and consumer metadata passing.
- Cursor-tool scope stayed limited to the minimal design/workflow documents needed to keep the shared-footer rule and one-page behavior explicit in future requirement authoring and frontend handoff.

### Change file list (confirmed follow-up implementation outputs)

**(Confirmed after follow-up implementation. This section records the actual change set for the one-page shared footer visibility follow-up.)**

#### Frontend

- `frontend/src/components/DataTable.js`
  - Updated so the shared footer region renders independently from the one-page/multi-page navigation-button rule.
- `frontend/src/components/LogGrid.js`
  - Updated so the shared log-table flow preserves one-page footer metadata on the grid path.
- `frontend/src/components/LogTable.js`
  - Updated so one-page datasets still provide shared footer metadata instead of suppressing the footer region.
- `frontend/src/components/ImageLogTable.js`
  - Updated so one-page datasets still provide shared footer metadata on the image-log table path.
- `frontend/src/components/SearchHistory/SearchHistoryList.js`
  - Updated so one-page footer metadata is still passed to the shared `DataTable` contract.
- `frontend/src/components/PendingApprovals/PendingApprovals.js`
  - Updated so one-page footer metadata is still passed to the shared `DataTable` contract.
- `frontend/src/components/UserActivityLog/UserActivityLogTable.js`
  - Updated so one-page datasets keep the shared footer visible while preserving existing info-text usage.
- `frontend/src/components/DataTable.test.js`
  - Added or updated regression coverage for one-page and multi-page shared footer behavior.
- `frontend/src/components/SearchHistory/SearchHistoryList.test.js`
  - Added or updated regression coverage for one-page shared footer visibility on search history.
- `frontend/src/components/PendingApprovals/PendingApprovals.test.js`
  - Added or updated regression coverage for one-page shared footer visibility on pending approvals.

**Confirmed non-change items:**

- `frontend/src/components/DataTable.css`
  - Reviewed during the follow-up, but no CSS change was required because always-visible footer behavior was implemented through shared rendering logic and consumer metadata alignment.

#### Cursor tools / docs

- `docs/design/grid-and-table.md`
  - Updated to define the always-visible shared footer rule for one-page and multi-page datasets.
- `docs/workflow/CONSISTENCY-STANDARDS.md`
  - Updated to mirror the shared-footer visibility rule in the unified data-table standard.
- `docs/workflow/REQUIREMENTS-AUTHORING-WORKFLOW.md`
  - Updated so requirement authors inspect one-page footer visibility and consumer pagination gating when a shared footer issue is reported.
- `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md`
  - Updated to require shared footer targets plus consumer-screen verification/alignment targets when the footer is suppressed on one-page datasets.
- `docs/workflow/HANDOFF-CHECKLIST.md`
  - Updated so Frontend handoffs for shared table work include the always-visible footer rule and the one-page navigation expectation.

#### Backend

- None.

#### DB

- None.

#### Contract / Spec

- None.

## 3. Test approach

### Test case list (required)

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|----|-------|------|------------------------------|-----------------|--------------------------------------------|
| TC-01 | Frontend | Regression | Render shared `DataTable` with `pagination: { currentPage: 1, totalPages: 1, totalCount: 7, infoText: '총 7건' }` and `onPageSizeChange` provided | The shared footer region renders inside `.log-table-container`; `총 7건` and `표시 건수` are visible even for one-page data; navigation buttons are absent or disabled, but the footer region remains visible | Unit (`npm test`, `DataTable.test.js`) |
| TC-02 | Frontend | Regression | Search-history fixture returns `pagination: { currentPage: 1, totalPages: 1, totalCount: 7 }` | Search history keeps the shared footer visible and shows total count plus rows-per-page control for one-page results | Unit (`npm test`, `SearchHistoryList.test.js`) |
| TC-03 | Frontend | Regression | Pending-approvals fixture returns `pagination: { currentPage: 1, totalPages: 1, totalCount: 5 }` | Pending approvals keeps the shared footer visible and shows total count plus rows-per-page control for one-page results | Unit (`npm test`, `PendingApprovals.test.js`) |
| TC-04 | Frontend | Regression | Shared footer consumer screens that currently build `pagination` as `null` for one-page data (`LogTable`, `ImageLogTable`, `UserActivityLogTable`) are reviewed and, when in scope, aligned to the shared contract | One-page datasets do not suppress the shared footer region on in-scope consumers; follow-up scope is explicitly confirmed for each reviewed consumer | Unit or manual |
| TC-05 | Frontend | Regression | Render shared `DataTable` with `totalPages: 3` | Multi-page pagination remains visible, with total count, rows-per-page, and page navigation preserved | Unit (`npm test`, `DataTable.test.js`) |
| TC-06 | Frontend | Regression | Search-history or pending-approvals fixture returns `pagination: { currentPage: 1, totalPages: 3, totalCount: 42 }` | Multi-page behavior remains unchanged while the same shared footer contract is used for one-page data | Unit (`npm test`) |
| TC-07 | Frontend | Layout | Reduced browser height on a shared `DataTable` screen with one-page data and with multi-page data | The footer region remains visible/reachable in both cases; `.table-wrapper` owns only the scrollable table region | Manual / browser |
| TC-08 | Frontend | Behavior | Change rows-per-page with `+`, `-`, and Enter while the result remains one page or becomes multi-page | Page-size behavior remains unchanged, and the shared footer stays visible before and after the page-size change | Unit or manual |

### Test scenarios

#### Scenario 1: Shared footer contract for one-page data

1. Render or open a shared `DataTable` consumer with `totalPages = 1`.
2. Confirm that the shared footer region is still rendered below `.table-wrapper`.
3. Confirm that `총 n건` and `표시 건수` remain visible even though page navigation may be absent or disabled.

#### Scenario 2: Search-history and pending-approvals one-page regression

1. Open **search history** or **pending approvals** with a one-page response.
2. Confirm that rows render in the shared table area and the footer remains visible.
3. Confirm that total count and rows-per-page remain visible without a screen-specific footer workaround.

#### Scenario 3: Shared multi-page regression

1. Re-run the same screens with `totalPages > 1`.
2. Confirm that the footer still renders correctly and page navigation behavior is preserved.
3. Confirm that the one-page fix did not remove existing multi-page paging behavior.

#### Scenario 4: Reduced-height layout

1. Re-check a shared `DataTable` screen with reduced browser height for both one-page and multi-page datasets.
2. Scroll the table body if necessary.
3. Confirm that the shared footer remains visible/reachable and is not pushed out of the container.

### Test data

- A one-page response fixture for shared `DataTable` consumers (`totalPages: 1`, `totalCount <= pageSize`).
- A multi-page response fixture for shared `DataTable` consumers (`totalPages >= 2`, `totalCount > pageSize`).
- Search-history fixture with both one-page and multi-page pagination payloads.
- Pending-approvals fixture with both one-page and multi-page pagination payloads.

### Test environment

- Frontend: `http://localhost:3001`
- Browser verification target: shared `DataTable` consumer screens in the local frontend app

### 3.5 Browser automation verification (optional)

**Applicable TCs**: TC-07 and the manual portion of TC-04 / TC-08

**Procedure**:

- Navigate to the target screen.
- Verify one-page data first, then multi-page data.
- Use page snapshots to confirm that the shared footer region remains visible below the scrollable table area.
- Confirm that total count and rows-per-page are visible for one-page results and that multi-page navigation still works or remains visible.

## 4. Checklist

### Frontend verification

- [x] Shared `DataTable` footer visibility contract updated for one-page and multi-page datasets
- [x] Consumer screens that suppress one-page footer metadata aligned to the shared contract
- [x] Automated regression coverage updated for one-page and multi-page shared footer behavior
- [x] Existing page-size and paging behavior preserved by automated regression/build evidence

### Documentation

- [x] Requirement doc updated for the follow-up request
- [x] Shared table design standard updated for one-page footer visibility
- [x] Workflow/handoff guidance updated for shared footer-contract defects

## 5. Test results

**QA note**: This section is updated for the follow-up implementation handoff. The evidence below confirms automated frontend regression, frontend build, frontend restart, and frontend reachability. Browser/manual verification for reduced-height layout and real-screen checks was not performed in this turn and remains open.

### Test run date

- 2026-03-13 (follow-up QA update from verified implementation/build handoff)

### Test results

#### Frontend
Partial pass (automated verification, build, restart, and health check passed; browser/manual verification pending)

- Automated unit regression evidence passed for the follow-up implementation that keeps the shared footer region visible for one-page and multi-page datasets.
- Verified implementation scope included shared table consumers and regression coverage updates in `DataTable.js`, `LogGrid.js`, `LogTable.js`, `ImageLogTable.js`, `PendingApprovals.js`, `SearchHistoryList.js`, `UserActivityLogTable.js`, `DataTable.test.js`, `PendingApprovals.test.js`, and `SearchHistoryList.test.js`.
- Automated evidence covered the shared `DataTable` contract plus the search-history and pending-approvals regression paths that were extended for this follow-up.
- Frontend build completed successfully.
- Frontend restart and reachability check completed successfully (`http://localhost:3001` returned `200`).
- Existing `ReactDOMTestUtils.act` deprecation warning appeared during test execution, but it did not fail the suites.
- Browser/manual verification for TC-07 (reduced-height layout) was not performed in this turn.
- Browser/manual verification for the manual portions of TC-04 and TC-08 was not performed in this turn.
- Real-screen browser verification was not executed for the in-scope shared table screens changed in this follow-up (`LogGrid`, `LogTable`, `ImageLogTable`, `PendingApprovals`, `SearchHistory`, `UserActivityLog`).

**Commands:**

```bash
cd frontend && npm test -- --watchAll=false --runTestsByPath src/components/DataTable.test.js src/components/SearchHistory/SearchHistoryList.test.js src/components/PendingApprovals/PendingApprovals.test.js
cd frontend && npm run build
./scripts/dev-services.sh frontend restart
curl -s -o /dev/null -w "%{http_code}" http://localhost:3001
```

**Outcome:**
- `npm test`: 3 suites passed, 9 tests passed, exit 0
- `npm run build`: success, exit 0
- `./scripts/dev-services.sh frontend restart`: already completed successfully before QA handoff
- Frontend health check: `200`
- Remaining gap: browser/manual verification is still required for reduced-height layout and real-screen footer visibility/interaction checks

## 6. Error remedy result (cause and action) — for error/bug fix requirements only

- **Requirement ID**: `20260313-shared-datatable-pagination-visibility`
- **Root cause**: After the earlier multi-page visibility fix, the shared footer contract still treated the footer region as a multi-page-only UI fragment. `DataTable` and several consumers suppressed shared footer metadata or the footer region itself when `totalPages <= 1`, so one-page datasets lost total count, rows-per-page, and stable footer visibility.
- **Actions taken**: The follow-up frontend implementation updated the shared `DataTable` and relevant shared-table consumers so one-page datasets continue to provide/render shared footer metadata, added/extended regression tests for the shared component plus search-history and pending-approvals, and updated related design/workflow documents so the always-visible shared footer rule is explicit in future design and handoff guidance.
- **Result**: Automated frontend regression passed (`3 suites`, `9 tests`), frontend build passed, frontend restart was already completed successfully before QA handoff, and the frontend health check returned `200`. Browser/manual verification for reduced-height layout and real-screen shared-footer behavior was not performed in this turn and remains open.
- **Completed**: 2026-03-13 (follow-up QA result update)

---

**Author**: Requirements subagent  
**Date**: 2026-03-13  
**Status**: Implemented pending QA verification
