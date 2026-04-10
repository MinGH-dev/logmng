# 20260330 - PB FEP log search: expand/collapse all across pagination

**Bugfix note (2026-03-30):** Open defect — after manual collapse on page 2+, re-expanding so the full result set is expanded again does not restore the **전체 접기** toolbar state (see §1 step 6, §2 problem 4, TC-08).

## 1. User requirement

### Requirement description

On the **PB FEP v2.0.0** log search screen (`viewId` / screen id `pb-fep-log-search`), the grid toolbar provides a global control for expanding and collapsing row detail (stream) rows. Today, “expand all” only affects the **current result page**. The product must treat expansion as a **session-scoped** choice across **all pages** of the current search result set: when the user chooses to expand everything, that intent must apply to every page they visit until they explicitly collapse all or change the expansion state in a way that invalidates a global “all expanded” reading. The toolbar control must switch between **expand all** and **collapse all** with **opposite** chevron directions, and manual per-row toggles must keep the global control’s visual and semantic state aligned with reality (full expand, full collapse, or mixed).

### User scenario

1. User runs a search on `pb-fep-log-search` and receives multiple pages of rows.
2. User clicks **전체 펼치기** (expand all). User navigates to page 2, then page 3. **Expected:** rows on each visited page render expanded without requiring expand-all again; expansion state is not limited to the page where the button was first clicked.
3. While in “all expanded” mode (or equivalent), the toolbar shows **전체 접기** with a chevron **opposite** to the expand control. User clicks it. **Expected:** all rows are collapsed **across the result set**, including rows on pages not currently visible; when the user moves to another page, rows there are also collapsed.
4. User clicks **전체 펼치기** again, then manually collapses **one** row on the current page. **Expected:** the UI no longer indicates a full “all expanded” active state; the global control reflects **partial / mixed** expansion (or an equivalent consistent pattern per project UX standards).
5. User expands rows individually until every row on the current page is expanded, but some rows on other pages remain collapsed. **Expected:** the global control must **not** show the same “full expand all active” state as when the user explicitly chose expand-all for the entire result set (unless the product defines equivalence—default is: distinguish explicit global expand-all from accidental parity on one page).
6. **Bugfix — manual collapse then re-expand on page 2+:** User has already chosen **전체 펼치기** (full result set expanded). On **page 2**, the user manually collapses **one** row. **Expected:** the toolbar correctly switches to **전체 펼치기** (mixed state — OK). The user then expands **that same** row again so **all rows in the result set** are expanded again. **Expected:** the toolbar must switch back to **전체 접기** (global collapse-all), because the effective state is “all rows in the result set expanded.” **Incorrect:** the toolbar stays on **전체 펼치기** when every row is expanded.

### Expected outcome

- **Cross-page expand-all:** After the user invokes expand-all for the current search result, navigating to any other page shows **all rows on that page expanded** without re-clicking expand-all, for the same search result and pagination parameters until the user changes that mode or runs a new search.
- **Collapse-all:** The control toggles to **전체 접기** with the **opposite** arrow direction to expand-all; invoking it collapses **all** rows for the **entire** result set (all pages), not only the current page.
- **Manual row toggle:** Expanding or collapsing a single row updates the global control state so it **matches** the actual expansion situation: full expand-all active only when the product definition of “all rows expanded” is met; otherwise partial/mixed or collapsed, per UX consistency rules.
- **Re-expand after manual collapse (bugfix):** If the user collapses one or more rows and then expands them again until **every** row in the current result set is expanded (including after navigating to page 2+), the toolbar must show **전체 접기**, not remain on **전체 펼치기**.
- **New search / reset:** A new search or other result-reset actions (as already defined for PB FEP log search) must clear expand/collapse global mode and per-row state so stale keys do not apply to a different result set.

**Note:** Numeric and structural layout for the toolbar and table follow the authoritative wireframe for `pb-fep-log-search` (see `docs/requirements/20260326-pb-fep-log-search-screen-wireframe.md` and linked SVG). This requirement does not redefine search field layout or panel width.

---

## 2. Design

### 2.1 Security review (optional; when PII / decryption / access control)

- [ ] Security review performed (check if applicable)

**Assessment:** This change is **UI state and presentation** for row expansion only. It does not widen decryption scope or API access. No additional §2.1 security items unless Security requests a formal pass for accessibility or logging of row keys.

- Risks: None identified beyond normal UI state handling.
- Acceptance / recommendations: N/A for PII; follow existing patterns for not logging row payloads in debug messages.

### Technical design

#### Codebase summary (Frontend)

- **`frontend/src/components/LogGrid.js`**
  - For `logType.id === 'pb_feplog'`, the component keeps `expandedRowKeys` (`Set`) and `expandAllActive` (`boolean`).
  - The **전체 펼치기** button (shown when `isPbFepLogType && logs.length > 0`) sets `expandedRowKeys` to keys derived **only from the current `logs` array** via `getPbFeplogRowKey`, and sets `expandAllActive` to `true`. It does not merge with keys from other pages.
  - Pagination (`handlePageChange`) refetches `logs` for the selected page; it does **not** re-apply expand-all to the newly loaded rows.
  - `handleRowExpandChange` updates `expandedRowKeys` from `LogTable` and clears `expandAllActive` only when `meta.manualCollapse` is true (user collapsed a row). Manual **expand** of a row does not reconcile `expandAllActive` against “all rows on all pages,” and does **not** restore `expandAllActive` when the expanded key set again matches the **full result set** (see problem 4).
  - New search (`handleSearch`) and logType/screen reset effects clear `expandedRowKeys` and `expandAllActive` for `pb_feplog`.
- **`frontend/src/components/LogTable.js`**
  - Controlled mode: `expandedRowKeys` + `onRowExpandChange`. `toggleRowExpanded` calls `onRowExpandChange(next, { manualCollapse: wasExpanded })` when the user toggles a row (`wasExpanded` true means user collapsed).
  - Row identity: `getPbFeplogRowKey(log)` combines `log_type` and `id` (or fallback from timestamp/codes/user fields).

#### Problem analysis

1. **Expand-all is page-local:** Keys are only added for the current page; after page change, new rows are not expanded even if `expandAllActive` remains `true`, so the toolbar state lies about “all expanded.”
2. **No collapse-all:** The toolbar always shows **전체 펼치기**; there is no **전체 접기** action or opposite chevron for the global mode.
3. **Global flag vs reality:** `expandAllActive` can stay `true` after paging while most rows on the new page are collapsed, or manual operations can leave the button’s pressed state inconsistent with cross-page truth.
4. **Toolbar label after manual re-expand (bugfix):** After `expandAllActive` is cleared by a **manual collapse** (e.g. on page 2), the user can manually re-expand until **every** row in the result set is expanded again. The toolbar should show **전체 접기** in that state, but the implementation only clears `expandAllActive` on `manualCollapse` and never **sets** it back when `expandedRowKeys` again reflects a full result-set expansion—so the control can remain stuck on **전체 펼치기**.

#### Diagnostic phase (mandatory for error/bug fix only)

Applies to the **toolbar regression** in problem 4: before changing reconciliation logic, the implementer must confirm behavior with **DEBUG-level or dev-only** diagnostic traces (e.g. `expandedRowKeys.size`, `totalCount`, `manualCollapse` / expand paths, page index) per project error-fix workflow; remove or gate diagnostics before production. The broader cross-page feature (problems 1–3) remains design-led as in the solution below.

#### Solution approach

Structure by scope. **Backend** and **DB** are not required for this behavior if row keys remain client-derived from API rows (same as today). **Contract** change is not required unless product later documents toolbar behavior in a spec.

**Frontend:**

- Introduce a **clear state model** for PB FEP log search (wireframe `pb-fep-log-search`) that supports:
  - **Global mode** (conceptual): e.g. `none` | `all_pages_expanded` | `mixed` — or an equivalent derived model using a boolean “expand-all intent” plus `expandedRowKeys`, provided the UI rules below hold.
  - **Per-row keys:** Continue using `Set` of `getPbFeplogRowKey` strings for rows that are expanded. When **global expand-all** is active, **on each page load** (whenever `logs` updates for the current search), the implementation **must** add every current row’s key to the set (union), so paging shows all rows expanded. When the user invokes **collapse-all**, clear the set and set global mode to **none**; when loading any page, no row is expanded.
  - **Collapse-all:** Toolbar shows **전체 접기** with chevron opposite to **전체 펼치기** while `all_pages_expanded` (or equivalent) is active; click clears expansion everywhere and resets global mode.
  - **Manual toggle:** When the user collapses any row while in global expand-all mode, transition to **mixed** (or `none` if that matches UX). When the user expands/collapses rows individually, recompute or update global mode so `aria-pressed` / visual “active” state does not imply full expand-all unless all rows in the result set are expanded per the chosen definition.
  - **`handleRowExpandChange` (bugfix, doc-only — do not implement here):** In addition to clearing `expandAllActive` on `manualCollapse`, the handler must **restore** global “all expanded” / **전체 접기** toolbar (collapse-all) mode when expansion state matches the **full result set**. A practical check for `pb_feplog` is: `totalCount > 0` and `expandedRowKeys.size === totalCount`, with **one unique row key per result row** (same key function as expand-all). **Edge cases to document in code / QA:** `totalCount === 0`; possible duplicate or unstable keys (must not treat as “full expand”); relationship between **loaded-page keys** and **server `totalCount`** until the cross-page union model is fully applied—reconciliation must stay consistent with the chosen definition of “all rows expanded” from this requirement.
  - **Definition of “all expanded”:** The requirement prefers: **all rows on all pages** of the current search result. Because the client may not load off-page rows without API calls, the implementation may use an **intent flag** (“user chose expand-all for this result”) combined with union-of-keys on each fetched page, and must clear that intent on collapse-all, new search, or manual collapse that invalidates full expansion—document the exact rule in code comments for QA.
  - **Scope of code paths:** `LogGrid` currently shows the expand-all control for **all** `pb_feplog` searches, including legacy `viewId` `pb-feplog`. **Primary acceptance** is **`pb-fep-log-search`**. Implementers must verify **legacy** `pb-feplog` still works; if the same state model applies, treat regressions as defects unless product excludes legacy.

**Backend:**

- None for default approach (no API change).

**DB:**

- None.

### Affected scopes and change targets (verification)

Per `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md`:

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|------------|-----------------------------------------------|
| Backend | No | N/A |
| Frontend (config UI + view screen) | Yes — **view screen** only; no separate config UI | Yes |
| DB | No | N/A |
| Contract / Spec | No — optional note in PB FEP UI spec if one exists | N/A |
| Cursor tools (skills, specs) | Optional — update `log-search-domain` skill only if behavior is normative for agents | Optional |

**Pattern §2.4 (search/filter UI consistency):** Does **not** apply — this requirement does not change search form fields, panel width, or user-block alignment.

### Planned change file list (expected change targets)

**(Planned at authoring. Implementing agent (Step 4) confirms or amends this list when implementation is complete.)**

#### Frontend

- `frontend/src/components/LogGrid.js`
  - **Must** implement cross-page expand/collapse-all state for PB FEP log grid, toggle **전체 펼치기** / **전체 접기** with opposite chevrons, and reconcile state on pagination and manual row toggles; **must** reset state on new search / logType screen reset consistent with existing `pb_feplog` clears.
- `frontend/src/components/LogTable.js`
  - **Must** support controlled expansion callbacks sufficient for parent to maintain global mode (e.g. distinguish manual expand vs collapse if needed, or parent derives from `next` Set); adjust only if current `onRowExpandChange` contract is insufficient.
- `frontend/src/components/LogGrid.test.js`
  - **Must** add unit tests for expand-all across page changes, collapse-all, and manual row toggle effects on toolbar state (mock fetch / props as needed).
- `frontend/src/components/LogTable.test.js`
  - **Must** add or extend tests for controlled `expandedRowKeys` + `onRowExpandChange` behavior used by PB FEP row expansion.
- `frontend/src/components/LogTable.css` or `frontend/src/components/LogGrid.css`
  - **Verify** only if **전체 접기** styling or chevron direction needs class changes; avoid unrelated visual churn.

#### Backend

- None planned.

#### DB

- None planned.

### Cursor tool update targets

- **Optional:** If PB FEP expand behavior becomes canonical for documentation, add a short bullet to `.cursor/skills/log-search-domain/SKILL.md` under PB FEP / grid behavior. Not blocking for implementation.

---

## 3. Test approach

### Test case list (required)

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|----|-------|------|------------------------------|-----------------|--------------------------------------------|
| TC-01 | Frontend | Normal | `pb-fep-log-search`, search returns ≥2 pages; user clicks **전체 펼치기**; navigates to page 2 | All rows on page 2 are expanded (detail/stream visible) without clicking expand-all again | Unit (npm test) |
| TC-02 | Frontend | Normal | Same as TC-01; user clicks **전체 접기** (or equivalent label); navigates to page 2 | All rows on page 2 are collapsed | Unit (npm test) |
| TC-03 | Frontend | Normal | Expand-all active; user manually collapses one row on current page | Toolbar does not show full “all expanded” active state (`aria-pressed` / visual state per implementation) | Unit (npm test) |
| TC-04 | Frontend | Edge | Expand-all active on page 1; change to page 2 without manual row toggles | Page 2 rows all expanded; no stale-only-page-1 keys preventing correct rendering | Unit (npm test) |
| TC-05 | Frontend | Normal | User runs a **new search** after expand-all | Expansion state reset; no rows expanded by default unless spec says otherwise | Unit (npm test) |
| TC-06 | Frontend | Regression | Legacy `viewId` `pb-feplog` (if still routed): search + expand-all + page change | No crash; expansion behavior matches chosen product rule (inherit or unchanged) | Manual / browser |
| TC-07 | Integration | Normal | End-to-end on `pb-fep-log-search`: multi-page result, expand-all, paginate, collapse-all | Matches §1 expected outcome | Manual / browser |
| TC-08 | Frontend | Bugfix | `pb-fep-log-search`, ≥2 pages; **전체 펼치기**; navigate to **page 2**; manually collapse one row (toolbar shows **전체 펼치기**); manually expand that same row until all rows in the result set are expanded again | Toolbar shows **전체 접기** (not stuck on **전체 펼치기**) | Unit (npm test) |

**Mandatory automated tests:** TC-01–TC-05 and **TC-08** **must** have implementing test code in `LogGrid.test.js` / `LogTable.test.js` (or split per project convention).

### Test scenarios

#### Scenario 1: Cross-page expand-all

1. Open PB FEP v2.0.0 log search (`pb-fep-log-search`), execute a query with multiple pages.
2. Click **전체 펼치기**, go to the next page.
3. **Verify:** All visible rows on the new page are expanded.

#### Scenario 2: Collapse-all and opposite chevron

1. With multi-page results, click **전체 펼치기**.
2. **Verify:** Button shows **전체 접기** and chevron direction is opposite to expand.
3. Click **전체 접기**, navigate to another page.
4. **Verify:** All rows collapsed on every page visited.

#### Scenario 3: Manual row vs global state

1. Expand-all, then collapse one row via row control.
2. **Verify:** Global control reflects non–full-expand state.

#### Scenario 4: Manual collapse on page 2, then re-expand (bugfix)

1. Multi-page result; click **전체 펼치기**; go to **page 2**.
2. Manually collapse one row. **Verify:** Toolbar shows **전체 펼치기** (mixed state).
3. Manually expand that row again until every row in the result set is expanded. **Verify:** Toolbar shows **전체 접기**.

### Test data

- Use existing PB FEP sample data or environment where POST `pb-fep-log-search` returns **at least two pages** for a chosen filter. Document query parameters in QA notes if static.

### Test environment

- Frontend: `http://localhost:3001` (or per project contract)
- Backend: `http://localhost:9200`
- Database: Per project dev setup

### 3.5 Browser automation verification (optional)

- **Applicable TCs:** TC-06, TC-07, TC-08 (or Scenario 4) where automated coverage is insufficient.
- **Procedure:** Log in → navigate to PB FEP v2.0.0 search → run query → execute steps in §3 scenarios; use snapshot to confirm button labels and expanded rows.
- **Reference:** `docs/workflow/BROWSER-AUTOMATION-VERIFICATION-POLICY.md`

---

## 4. Checklist

### Frontend verification

- [ ] API parameters validated (no API change expected)
- [ ] UI behavior confirmed (expand/collapse all, pagination, manual row)
- [ ] Error handling verified (no regression on empty results / single page)

### Backend verification

- [ ] N/A unless implementation adds API use

### Integration

- [ ] End-to-end flow tested (TC-07)
- [ ] Edge cases tested (page size change with multi-page results, if not covered in unit tests)

### Documentation

- [ ] Requirement doc completed (check only when this document is finalized for the workflow; triggers index maintenance per template)
- [ ] Code comments added (if applicable) — state machine / intent flag

---

## 5. Test results

### Test run date

- [Pending]

### Test results

#### Frontend

- [Pending]

#### Backend

- N/A

**Commands:**

```bash
cd frontend && npm test -- --watchAll=false
```

**Outcome:**

- [Pending]

### Issues found and resolution

- [Pending]

### Next steps

- [Pending]

---

## 6. Error remedy result (cause and action) — for error/bug fix requirements only

**Toolbar stuck on “전체 펼치기” after full re-expand (§1 step 6 / TC-08):**

- **Cause (expected):** `handleRowExpandChange` clears `expandAllActive` on `manualCollapse` but does not set it when `expandedRowKeys` again matches the full result set — to be **confirmed** via diagnostic phase in §2.
- **Action:** Reconcile global expand/collapse-all mode in `handleRowExpandChange` (or equivalent) when expansion matches full result set; see §2 solution bullet for `handleRowExpandChange`.
- **Verified:** [Pending]

---

## 7. Final version (Korean) — add after all verification is complete

*To be added after QA verification per `docs/workflow/DOCUMENT-LANGUAGE-POLICY.md`.*

---

**Author:** Requirements (subagent)  
**Date:** 2026-03-30  
**Status:** In progress — feature (cross-page expand/collapse) + **bugfix** (TC-08 toolbar after manual re-expand on page 2+).  
