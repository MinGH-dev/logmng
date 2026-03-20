# 20260317 - Search History screen: labels, layout, and decrypt-approval dropdown

## 1. User requirement

### Requirement description

Improve the Search History screen by (1) renaming the date-related label from "요청일시" to "검색일시" in both the search form and the result grid, (2) correcting the search form layout into two explicit rows with date/time on row 1 and requester, approval status, and request reason on row 2, and (3) changing the decrypt approval status control from inline checkboxes to a dropdown that opens to show checkboxes for multi-select (behavior unchanged).

Field definitions and layout must align with `docs/design/search-fields-by-screen.md` and `docs/design/search-field-definition-items.md`; numeric and structural values are sourced from those design docs.

### User scenario

1. User opens the Search History (검색 이력) screen.
2. User sees the search/filter form and the result grid.
3. **Problem**: The form label "요청일시" is misleading (the field represents search-request time, better expressed as "검색일시"); the grid column "일시" is ambiguous; the form layout mixes date and other blocks in one row; the approval status is shown as inline checkboxes and "모두선택", which uses horizontal space and is less compact.
4. User expects: (a) labels "검색일시" in the form and "검색일시" in the grid; (b) row 1 = date/time only, row 2 = requester | approval status | request reason; (c) approval status as a dropdown with checkboxes inside for the same multi-select behavior.

### Expected outcome

- Search form: Label for the date range is **"검색일시 (시작)"** and **"검색일시 (종료)"** (replacing "요청일시 (시작)" / "요청일시 (종료)").
- Result grid: Column header for the timestamp column is **"검색일시"** (replacing "일시").
- Search form layout:
  - **Row 1**: Only the two date/time fields (검색일시 시작, 검색일시 종료). Validation message "시작일시는 종료일시 이전이어야 합니다." remains.
  - **Row 2**: Three blocks in one row — (1) 요청자 block (부서, 사용자명, 사용자 ID), (2) 복호화 승인 여부 control, (3) 요청 사유 — plus Search and Reset buttons.
- 복호화 승인 여부: **Dropdown-style** control. The trigger shows a label (e.g. "복호화 승인 여부") and a summary of selected values (e.g. "전체", "대기, 승인"). Opening the dropdown shows **checkboxes** for 대기, 승인, 반려, 만료; multi-select and "모두선택" (or equivalent) behavior is preserved. API semantics unchanged: `approvalStatuses` as repeated query param when one or more selected.
- Panel width and compact spacing remain per `docs/design/search-fields-by-screen.md` §4 and `docs/design/forms-and-filters.md` § Compact variant.

## 2. Design

### 2.1 Security review (optional)

Not applicable. No change to PII, decryption scope, or access control.

### Technical design

#### Problem analysis

1. **Labels**: "요청일시" on the Search History form suggests "request date/time" but the field filters by when the search was requested (검색 요청 일시); "검색일시" is clearer. The grid column "일시" is generic; "검색일시" aligns with the form and clarifies meaning.
2. **Layout**: The current form has one row containing requester block + (요청일시 from/to, 복호화 승인 여부 checkboxes, 요청 사유). Aligning with `docs/design/forms-and-filters.md` § Single row for non-date: date/period block should sit on its own row; requester, approval, and request reason on a second row.
3. **Approval control**: Inline checkboxes plus "모두선택" consume horizontal space. A dropdown that opens to show the same checkboxes preserves multi-select behavior while reducing footprint and matching a common filter pattern.

#### Solution approach

**Frontend:**

- **Label changes**: In the search form, replace "요청일시 (시작)" / "요청일시 (종료)" with "검색일시 (시작)" / "검색일시 (종료)". Update `aria-label` / `role="group"` text for the date block (e.g. "검색일시·승인 여부·요청사유" or "검색일시"). In the grid column definition, change the column with `key: 'requested_at'` label from "일시" to "검색일시".
- **Layout**: Restructure the toolbar into two rows. Row 1: single block containing only 검색일시 (시작) and 검색일시 (종료) with existing validation. Row 2: UserContextFilterBlock (요청자) | 복호화 승인 여부 (dropdown with checkboxes) | 요청 사유 | Search + Reset. Use CSS (e.g. two distinct rows with `search-history-toolbar__row-1`, `search-history-toolbar__row-2`) so that row 2 aligns with `docs/design/forms-and-filters.md` § Single row for non-date and § Filter block tiers; block-level width and spacing per compact variant (row/field gap 8–12px, block 12–16px).
- **복호화 승인 여부 dropdown**: Replace the current inline checkboxes + "모두선택" button with a single dropdown control. Trigger: visible label "복호화 승인 여부" and selection summary (e.g. "전체", "대기, 승인"). Dropdown content: same four options (대기, 승인, 반려, 만료) as checkboxes; multi-select and "select all" behavior preserved. Keyboard: Enter/Space to open/close, arrow keys to move, Space to toggle option, Escape to close; focus moves into list when open and back to trigger when closed. ARIA: trigger `aria-expanded`, `aria-haspopup="listbox"`, `aria-controls`; dropdown `role="listbox"`, options `role="option"` with `aria-selected`; avoid duplicate announcement of state (e.g. checkboxes `aria-hidden="true"` if selection is exposed via option). Control sizing and panel width remain per `docs/design/search-field-definition-items.md` and `search-fields-by-screen.md` §4.
- **Design doc and CSS**: Implementer must read and apply `docs/design/search-fields-by-screen.md` (§4), `docs/design/search-field-definition-items.md`, and `docs/design/forms-and-filters.md` for layout, block tiers, and compact variant. Use `frontend/src/styles/search-filter-standard.css` for shared variables (e.g. `--sf-field-date-min/max`, `--sf-field-extra-min/max`, `--sf-row-gap`, `--sf-block-gap`). For any standard that is undefined or ambiguous (e.g. dropdown trigger min-width), do not infer or hardcode; list the undefined item, explain why it is needed, propose a recommended draft if possible, and request product/UX feedback before implementation. See `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md` §2.4 and `docs/design/css-standard-and-exceptions.md` for exceptions.

**Backend:**

- No change. API request shape (e.g. `requestedAtFrom`, `requestedAtTo`, `approvalStatuses`, `requestReason`) and semantics remain as defined in contract and `searchHistoryService`.

**DB:**

- No change.

### Affected scopes and change targets (verification)

Per `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md`:

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|-----------|-----------------------------------------------|
| Backend | No | N/A |
| Frontend (view screen) | Yes | Yes |
| DB | No | N/A |
| Contract / Spec | No | N/A |
| Cursor tools (skills, design docs) | Yes (design doc only) | Yes |

This requirement does not add or change API or scope-supporting behavior; it only changes labels, layout, and control type on the Search History screen. Design doc is updated so that §4 reflects the new labels and two-row layout.

### Planned change file list (expected change targets)

**(Planned at authoring. Implementing agent (Step 4) confirms or amends this list when implementation is complete.)**

**Confirmed (implementation complete):** All listed files changed as above; no additional files. Design doc §4 and §4.2 updated.

#### Frontend

- `frontend/src/components/SearchHistory/SearchHistoryList.js`
  - Change search form labels "요청일시 (시작)" / "요청일시 (종료)" to "검색일시 (시작)" / "검색일시 (종료)"; update group `aria-label` for date block.
  - Change grid column label for `requested_at` from "일시" to "검색일시".
  - Restructure toolbar into row 1 (date/time only) and row 2 (requester block | 복호화 승인 여부 | 요청 사유 | actions).
  - Replace inline approval checkboxes + "모두선택" with a dropdown that opens to show checkboxes; preserve multi-select and API param `approvalStatuses`.
- `frontend/src/components/SearchHistory/SearchHistory.css`
  - Add/update classes for two-row layout (e.g. `search-history-toolbar__row-1`, `search-history-toolbar__row-2`); style dropdown trigger and dropdown panel; retain compact variant spacing; do not re-declare control height/padding (use search-filter-standard.css). Remove or repurpose `.search-history-approval-checkboxes` / `.search-history-btn-select-all` as needed for the new dropdown implementation.
- `frontend/src/components/SearchHistory/SearchHistoryList.test.js`
  - Update tests that rely on "요청일시 (시작)" / "요청일시 (종료)" labels to use "검색일시 (시작)" / "검색일시 (종료)" (e.g. `getByLabelText`).
  - Update grid header expectation from "일시" to "검색일시".
  - Update tests for approval status control: assert dropdown trigger and opening dropdown to select options (multi-select and search params unchanged).

#### Design doc

- `docs/design/search-fields-by-screen.md`
  - §4 intro: Update toolbar structure description to two-row layout (row 1: 검색일시 only; row 2: 요청자 | 복호화 승인 여부 | 요청 사유 + 검색/초기화).
  - §4.2: Change labels "요청일시 (시작)" / "요청일시 (종료)" to "검색일시 (시작)" / "검색일시 (종료)"; change approval control from "checkboxes (multi)" to "dropdown with checkboxes (multi)" and note trigger label + summary, keyboard, ARIA per UX recommendation. Add grid column label "검색일시" for the timestamp column.

#### Cursor tools (optional)

- `.cursor/skills/search-history-decrypt-domain/SKILL.md`
  - Optionally add one line that the Search History search form uses the label "검색일시" for the date range (and grid column "검색일시"). Implementer may skip if the design doc is the single source of truth.

## 3. Test approach

### Test case list (required)

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|----|-------|------|------------------------------|-----------------|--------------------------------------------|
| TC-01 | Frontend | Normal | Open Search History; inspect search form labels | "검색일시 (시작)", "검색일시 (종료)" visible; no "요청일시" in form | Unit (npm test) / Manual |
| TC-02 | Frontend | Normal | Open Search History; inspect grid header | Column for timestamp shows "검색일시" | Unit (npm test) / Manual |
| TC-03 | Frontend | Normal | Inspect toolbar layout | Row 1 contains only 검색일시 시작/종료; row 2 contains 요청자 block, 복호화 승인 여부 control, 요청 사유, Search, Reset | Manual |
| TC-04 | Frontend | Normal | Open 복호화 승인 여부 dropdown; select one or more options; click Search | Same API params as before (e.g. `approvalStatuses` array); results filtered correctly | Unit (npm test) + Manual |
| TC-05 | Frontend | Normal | Select all approval options (or "모두선택" equivalent) in dropdown; Search | `approvalStatuses` includes all four or omitted; no regression | Unit (npm test) |
| TC-06 | Frontend | Normal | Reset form | 검색일시 and approval dropdown cleared; requester cleared per existing behavior | Unit (npm test) |
| TC-07 | Frontend | Accessibility | Focus trigger; open with Enter/Space; move with arrows; toggle with Space; close with Escape | Keyboard operable; focus returns to trigger on close | Manual |
| TC-08 | Frontend | Regression | Set 검색일시 range and search | `requestedAtFrom`, `requestedAtTo` sent in API as before | Unit (npm test) |

### Test scenarios

#### Scenario 1: Labels and layout

1. Navigate to Search History.
2. Confirm search form shows "검색일시 (시작)" and "검색일시 (종료)" and grid header shows "검색일시".
3. Confirm row 1 has only the two date fields; row 2 has 요청자, 복호화 승인 여부, 요청 사유, and buttons.

#### Scenario 2: Approval dropdown

1. Click the 복호화 승인 여부 dropdown trigger.
2. Confirm dropdown opens with four options (대기, 승인, 반려, 만료) as checkboxes or equivalent.
3. Select one or more; close dropdown; click Search.
4. Confirm list is filtered by selected status(es) and API receives correct `approvalStatuses`.

### Test data

- Existing search history rows with mixed `approval_status` (PENDING, APPROVED, REJECTED, EXPIRED) for filter tests.

### Test environment

- Frontend: `http://localhost:3001` (or per contract)
- Backend: `http://localhost:9200`

### 3.5 Browser automation verification (optional)

Applicable TCs: TC-01, TC-02, TC-03, TC-04, TC-07. Procedure: navigate to Search History, snapshot to confirm labels and layout, open dropdown and snapshot to confirm options, run search and verify request/response. Reference: `docs/workflow/BROWSER-AUTOMATION-VERIFICATION-POLICY.md`.

## 4. Checklist

### Frontend verification

- [ ] Labels and grid column updated
- [ ] Two-row layout implemented; spacing per design docs
- [ ] Approval dropdown with checkboxes; multi-select and API unchanged
- [ ] Keyboard and ARIA per UX recommendation
- [ ] Unit tests updated and passing

### Backend verification

- [ ] No change; N/A

### Integration

- [ ] Search and reset flow works; API params unchanged

### Documentation

- [ ] Requirement doc completed
- [ ] Design doc §4 updated

## 5. Test results

### Test run date

- [Date and time]

### Test results

#### Frontend

[Pass / Fail]

- [Result description]

#### Backend

N/A

**Commands:**

```bash
cd frontend && npm test -- --watchAll=false --testPathPattern=SearchHistoryList
```

**Outcome:**

- [Item 1]
- [Item 2]

### Issues found and resolution

[To be filled after implementation and QA]

### Next steps

1. Implement per §2 and run TC-01–TC-08.
2. Update §2 change file list with actual files changed.
3. QA verification; then §5 and commit per workflow.

---

**Author**: Requirements subagent  
**Date**: 2026-03-17  
**Status**: In progress
