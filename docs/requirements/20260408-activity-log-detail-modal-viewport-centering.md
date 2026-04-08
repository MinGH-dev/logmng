# 20260408 - Activity log detail modal viewport centering and scroll

## 1. User requirement

### Requirement description
On **Activity History** (User Activity Log), opening the **detail view** shows a modal dialog. On **small browser viewports** (short height and/or narrow width), the modal is **partially hidden**, **clipped**, or **difficult to reach** (content appears lost off-screen). The product must ensure the detail modal remains **usable**: it should be **centered in the main application content region** (the area beside the sidebar), remain **fully reachable**, and allow **scrolling** so users can see **all** modal content including header, body, and footer actions.

This is a **frontend-only** UX and layout bugfix. No API or backend changes.

### User scenario
1. A signed-in user navigates to **User Activity Log** and runs a query so at least one row is listed.
2. The user opens **detail** for a row (opens the detail modal).
3. The user resizes the browser to a **small height** (e.g. laptop split-screen) and/or **narrow width**.
4. **Problem**: The modal is not fully visible; top or bottom (or sides) clip or sit off-screen; scrolling does not reliably expose the full dialog or primary actions.

### Expected outcome
- The detail modal is **positioned and centered relative to the main content area** (the primary column where the activity log grid lives—not merely the geometric center of the full browser window if that misaligns with user perception when the sidebar is visible).
- No part of the modal that the user must interact with (title, close control, primary content, footer buttons) is **permanently clipped** or unreachable on small viewports.
- When content exceeds available space, **scrolling** (vertical and, if needed, horizontal for layouts like wide tables or JSON blocks) allows viewing **all** content without losing the modal context.
- Existing behaviors (click-outside to close, escape/focus expectations if already implemented, z-order above the main UI) remain intact unless a conflict with centering is unavoidable—in which case the requirement prefers **reachability and scroll** over marginal layout polish.
- **Note**: Numeric layout values (e.g. max-height percentages, padding) must follow existing app/modal patterns where defined; otherwise the implementing agent should align with nearby modals and document any new constants in component CSS with traceability to this requirement.

## 2. Design

### 2.1 Security review (optional; when PII / decryption / access control)
- [ ] Security review performed (not required for this viewport/layout-only frontend bugfix)
- Risks: Activity log detail can display operational metadata; layout changes must not unintentionally expose hidden content in new surfaces (no requirement to change data visibility—layout only).
- Acceptance / recommendations: N/A for security beyond maintaining existing disclosure rules.

### Technical design

#### Problem analysis
1. **Current implementation** (baseline for implementer verification): `UserActivityLogDetail.js` renders `.activity-log-detail-overlay` and `.activity-log-detail-modal`. In `UserActivityLog.css`, the overlay is `position: fixed` covering the full viewport with flex `align-items: center` / `justify-content: center`; the modal uses `max-height: 90vh`, `overflow-y: auto`, `width: 90%`, `max-width: 900px`, `z-index: 1300`.
2. **Tall content + short viewport**: Vertical flex centering (`align-items: center`) combined with a tall modal can cause **top/bottom clipping**—the centered box may extend beyond the viewport with **no scroll on the overlay**, while inner `overflow-y: auto` on the modal may not recover lost header/footer regions depending on height distribution and nested overflow.
3. **“Main content area” vs viewport**: A full-viewport `fixed` overlay centers relative to the **window**, which may not match product language “main content area” when the **sidebar** occupies horizontal space; users may perceive the dialog as shifted or clipped relative to the **content column**.
4. **Ancestor overflow**: The app shell (`App.js` / main column wrappers) may apply `overflow: hidden` or transform/contain patterns that interact with `position: fixed` or stacking contexts; implementer must confirm whether clipping originates in the modal CSS alone or in **ancestor overflow**.

#### Diagnostic phase (mandatory for error/bug fix only)

This bugfix is **layout/CSS-first**. Confirm root cause from **reproducible visual and computed layout evidence** before changing production behavior. **Application code diagnostic logging is not required** unless, after inspection, the cause depends on runtime state that cannot be inferred from the DOM/CSS chain.

- **Phase 0 (diagnostic / repro):**
  1. **Reproduce** on Activity History with a detail row that has **tall** `action_detail` (e.g. JSON / permission-group audit) at viewport heights **≤ ~600px** and widths **≤ ~400px**, and intermediate sizes.
  2. In DevTools, inspect **computed** `overflow`, `max-height`, `align-items`, `position`, and **containing blocks** for `.activity-log-detail-overlay`, `.activity-log-detail-modal`, and **ancestors** up to `body` (note any `overflow: hidden`, `transform`, `filter`, or `contain` that create new containing blocks).
  3. Verify whether clipping is due to: (a) flex vertical centering without overlay scroll, (b) `max-height` / margin on the modal vs actual available space (including **safe-area** / mobile notches if applicable), (c) **main column** vs full-viewport centering expectation, or (d) conflicting z-index/stacking with app chrome.
  4. Record the **minimal** finding (which layer fails) in §6 after fix validation.
- **Production safety:** If temporary `console` diagnostics are used during investigation, they must be **removed** or **dev-only** before merge; do not rely on persistent client logs in production for this issue.

#### Solution approach

**Frontend:**
- **Centering definition (“main content area”)**: Implement positioning so the modal is **visually centered within the main content column** (right of the sidebar). Acceptable patterns include—but are not limited to—(a) constraining overlay/flex centering to a wrapper that matches the main column’s box, or (b) using **inset** values derived from the main content region so `fixed` positioning aligns with that region; the implementer must verify the result matches user expectation on **sidebar expanded/collapsed** if both exist.
- **Scroll ownership**: Prefer a **single primary scroll container** to avoid nested scroll confusion:
  - **Option A**: Overlay is `overflow-y: auto` with **padding** (vertical and horizontal) so the modal can scroll into view when taller than the viewport; modal may drop outer `max-height` or coordinate with overlay.
  - **Option B**: Modal keeps bounded `max-height` with **internal** scroll on a **content** region only, while **header** (and optionally **footer**) remain **visible** (`position: sticky` or flex column with non-scrolling header/footer)—only if this reliably passes TCs on short viewports.
  - Implementer must pick one coherent model; document briefly in code comment if non-obvious.
- **Horizontal overflow**: Wide inner content (tables, `.json-content`) already uses or can use `overflow-x: auto`; ensure the **modal** does not clip horizontal access on narrow widths when the product expects sideways scroll inside the dialog.
- **Z-index**: Maintain stacking **above** sidebar and main chrome; current `z-index: 1300` is a baseline—**reconcile** with other modals (e.g. MUI theme, global overlays) so the detail dialog is never obscured by the app bar or sidebar.
- **Accessibility**: Preserve **focus trap** / **Escape** behavior if present; ensure scrollable regions are keyboard-reachable where applicable.

**Backend:**
- No change.

**DB:**
- No change.

### Affected scopes and change targets (verification)

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|------------|-----------------------------------------------|
| Backend | [ ] Yes / [x] No | [x] |
| Frontend (config UI + view screen) | [x] Yes / [ ] No | [x] |
| DB | [ ] Yes / [x] No | [x] |
| Contract / Spec | [ ] Yes / [x] No | [x] |
| Cursor tools (skills, specs) | [ ] Yes / [x] No | [x] |

### Planned change file list (expected change targets)

**(Planned at authoring. Implementing agent (Step 4) confirms or amends this list when implementation is complete.)**

#### Frontend
- `frontend/src/App.js`
  - Sets `--app-main-inset-left` (drawer open/collapsed) and `--app-main-inset-top` (matches main `mt: 7`) so the detail overlay’s fixed inset aligns with the main content column below the app bar.
- `frontend/src/components/UserActivityLog/UserActivityLog.css`
  - Adjust `.activity-log-detail-overlay` / `.activity-log-detail-modal` (and related rules, including any existing media queries) for main-content-relative centering, safe padding, overflow/scroll, and modal max-height behavior consistent with §2.
- `frontend/src/components/UserActivityLog/UserActivityLogDetail.js`
  - JSX: `role="dialog"`, `aria-labelledby`, scroll wrapper `.activity-log-detail-body`, close button `aria-label`.
- `frontend/src/components/UserActivityLog/UserActivityLogDetail.test.js`
  - TC-06 layout assertions: dialog, body wrapper, overlay class, accessible close.

#### Backend
- No planned change.

#### DB
- No planned change.

## 3. Test approach

### Test case list (required)

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|----|-------|------|------------------------------|-----------------|--------------------------------------------|
| TC-01 | Frontend | Normal | Open detail modal on Activity Log at **desktop** viewport (e.g. 1280×800). | Modal centered in main content perception; full content visible or scrollable; close and footer actions reachable. | Manual / browser |
| TC-02 | Frontend | Edge | Same as TC-01 at **short height** (e.g. 1280×480 or 1024×600) with **tall** detail payload (long JSON / audit section). | No clipping of header/footer; user can scroll to see **top and bottom** of modal; no content permanently off-screen. | Manual / browser |
| TC-03 | Frontend | Edge | Same flow at **narrow width** (e.g. 360×800). | Modal stays within usable width; wide inner blocks scroll horizontally if needed; no loss of critical controls. | Manual / browser |
| TC-04 | Frontend | Edge | Sidebar **expanded** vs **collapsed** (if both exist): open modal at medium viewport. | Centering remains consistent with **main content column** (not drifting under sidebar or lost at canvas edge). | Manual / browser |
| TC-05 | Frontend | Regression | Open modal, then resize viewport **while modal is open**. | Layout recovers without broken scroll; modal remains closable; no duplicate unusable scrollbars unless acceptable per implementation. | Manual / browser |
| TC-06 | Frontend | Normal | `npm test` for `UserActivityLogDetail` after changes. | All tests pass; new assertions cover structural/regression expectations per Step 4. | Unit (`npm test -- --watchAll=false`) |
| TC-07 | Frontend | Optional | RTL or `prefers-reduced-motion` (if project supports): smoke open modal. | No worse than baseline; layout remains usable (document gaps if environment unsupported). | Manual |

### Test scenarios

#### Scenario 1: Short viewport, tall content
1. Navigate to User Activity Log; open detail for a row with large `action_detail`.
2. Reduce window height below typical laptop content area.
3. Scroll until the first line of the header and the last footer control have both been visible at least once.

#### Scenario 2: Narrow viewport
1. Open detail modal; narrow width until horizontal overflow appears in inner content.
2. Confirm horizontal scroll inside modal (or agreed single scroll container) allows reading clipped columns/JSON.

### Test data
- Use existing dev/stage activity log rows; if none with tall JSON, use a permission-group or audit-rich action type per environment seed data.

### Test environment
- Frontend: `http://localhost:3001` (or per project contract)
- Backend: unchanged; standard auth for Activity Log access

### 3.5 Browser automation verification (optional)

- **Applicable TCs**: TC-01–TC-05 (layout and scroll are best verified with Browser MCP or manual DevTools).
- **Procedure**: Navigate to Activity Log → login if needed → open detail → `browser_snapshot` at multiple viewport sizes → confirm header/footer visibility and scroll containers.
- **Reference**: `docs/workflow/BROWSER-AUTOMATION-VERIFICATION-POLICY.md`

## 4. Checklist

### Frontend verification
- [ ] UI behavior confirmed (viewports in §3)
- [ ] Activity Log detail modal centering and scroll verified
- [ ] No regression to overlay close behavior

### Backend verification
- [ ] N/A

### Integration
- [ ] End-to-end open/close detail flow smoke-tested

### Documentation
- [ ] Requirement doc completed
- [ ] Code comments only where layout contract is non-obvious

## 5. Test results

### Test run date
- 2026-04-08 (Frontend-ActivityLog)

### Test results

#### Frontend
- `cd frontend && npm test -- --watchAll=false --testPathPattern=UserActivityLogDetail` — exit **0** (14 tests).
- `cd frontend && npm run build` — exit **0**.
- `./scripts/dev-services.sh frontend restart` — OK; `curl` http://localhost:3001 → **200**.
- TC-01–TC-05: manual / browser verification recommended (see §3); not run in this pass.

#### Backend
- N/A

**Outcome:**
- Unit/build/restart gates passed; QA manual TC-01–TC-05 still requested.

## 6. Error remedy result (cause and action) — for error/bug fix requirements only

- **Requirement ID**: `20260408-activity-log-detail-modal-viewport-centering`
- **Root cause**: (a) `html, body { overflow: hidden }` and app shell `overflow: hidden` — overlay had **no** scrolling while using flex `align-items: center`, so a tall modal could be clipped at top/bottom with no way to reach header/footer. (b) Overlay was **full viewport** (`left: 0`), so horizontal centering did not match the **main column** beside the sidebar. (c) Scroll lived on the whole modal (`overflow-y: auto` on `.activity-log-detail-modal`), mixing header/body/footer in one scroll area.
- **Actions taken**: App root exposes `--app-main-inset-left` / `--app-main-inset-top` from drawer width and app bar offset. Overlay uses those insets, `overflow-y: auto`, padding, and `margin-top/bottom: auto` for safe vertical centering when content fits. **Option B**: modal is a flex column with bounded `max-height`; **`.activity-log-detail-body`** is the primary vertical scroll region; header/footer stay outside that scroller. Wide inner blocks continue using existing `overflow-x` patterns.
- **Result**: Layout matches main-column positioning; double-scroll (overlay + body) only when the dialog exceeds the available region—both remain reachable.
- **Completed**: 2026-04-08 (implementation); full manual §3 TC-01–TC-05 pending QA.

---

**Author**: Requirements (subagent)  
**Date**: 2026-04-08  
**Status**: In progress
