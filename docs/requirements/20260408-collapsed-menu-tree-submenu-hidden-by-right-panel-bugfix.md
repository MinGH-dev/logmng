# 20260408 - Collapsed sidebar submenu visibility and left-gap style improvement

## 1. User requirement

### Requirement description
After the recent collapsed-sidebar submenu overlap fix, users requested an additional UI improvement: collapsed mode must use a dedicated style so submenu/menu content has no unnecessary left blank space.

This requirement defines a frontend-only follow-up improvement for the sidebar menu in collapsed state:
- separate collapsed-only styling from shared sidebar styling, and
- remove left-side spacing (padding/margin/indent gap) in collapsed rendering.

### User scenario
1. A user logs in and opens a page with the sidebar menu tree.
2. The user clicks the sidebar toggle and enters collapsed mode.
3. The user opens a menu group (and its submenu) in collapsed mode.
4. **Problem**: The collapsed menu uses shared style rules, and left-side blank spacing remains visible, reducing visual density and alignment quality.

### Expected outcome
- Collapsed-sidebar mode must apply a dedicated style path (not only shared expanded/collapsed common style).
- In collapsed mode, menu/submenu content must render without unnecessary left blank spacing.
- Expanded-sidebar mode must keep existing spacing and visual behavior without regression.
- Submenu visibility/click behavior from the previous fix must remain intact after this style separation.

## 2. Design

### 2.1 Security review (optional; when PII / decryption / access control)
- [ ] Security review performed (not required for this frontend layout-layer bugfix)
- Risks: N/A (no authentication/authorization/decryption scope change)
- Acceptance / recommendations: N/A

### Technical design

#### Problem analysis
1. Sidebar style logic is currently coupled between expanded and collapsed states, making collapsed-specific spacing control incomplete.
2. Collapsed submenu/menu wrappers still inherit left padding/margin/indent values intended for expanded layout readability.
3. Without a dedicated collapsed style contract, small spacing regressions can reappear when submenu behavior or shell CSS changes.

#### Solution approach

**Frontend:**
- Introduce explicit collapsed-only style branch in sidebar rendering so collapsed behavior can be controlled independently.
- Remove left blank spacing in collapsed menu/submenu by aligning `padding-left`, `margin-left`, and indent-related style values to collapsed UX intent.
- Preserve expanded-mode spacing and interaction by scoping the style change to collapsed state selectors/classes only.
- Verify submenu visibility and clickability are not regressed while applying the collapsed spacing changes.
- Add/update frontend tests to assert collapsed style separation and no-left-gap behavior.

**Backend:**
- No backend behavior change is expected for this requirement.

**DB:**
- No database change is expected for this requirement.

### Affected scopes and change targets (verification)

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|------------|-----------------------------------------------|
| Backend | [ ] Yes / [x] No | [x] |
| Frontend (config UI + view screen) | [x] Yes / [ ] No | [x] |
| DB | [ ] Yes / [x] No | [x] |
| Contract / Spec | [ ] Yes / [x] No | [x] |
| Cursor tools (skills, specs) | [ ] Yes / [x] No | [x] |

### Planned change file list (expected change targets)

**(Implementation complete; list below is confirmed with actual change targets.)**

#### Frontend
- `frontend/src/components/AppSidebar.js`
  - Added explicit collapsed-only menu item style branch and removed collapsed submenu left indent (`button` and `subMenuContent`).
- `frontend/src/components/AppSidebar.test.js`
  - Added collapsed/expanded padding assertions and retained interaction/layering regression check.

#### Backend
- No planned change.

#### DB
- No planned change.

## 3. Test approach

### Test case list (required)

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|----|-------|------|------------------------------|-----------------|--------------------------------------------|
| TC-01 | Frontend | Normal | Collapse sidebar and open a menu group with submenu | Collapsed-specific style path is applied | Unit (npm test) |
| TC-02 | Frontend | Normal | In collapsed mode, inspect menu/submenu horizontal alignment | No unnecessary left blank spacing is visible in collapsed menu/submenu | Manual / browser |
| TC-03 | Frontend | Regression | Keep sidebar expanded and open same menu groups | Expanded-mode spacing/visual style remains unchanged | Manual / browser |
| TC-04 | Frontend | Regression | Collapse sidebar, open submenu, then click a child menu item | Submenu remains visible/clickable and navigation works | Manual / browser |
| TC-05 | Frontend | Edge | Toggle expanded/collapsed repeatedly and reopen submenu | No spacing regression or state-leak between expanded and collapsed styles | Manual / browser |
| TC-06 | Frontend | Regression | Run frontend unit tests after style separation update | Sidebar-related test suites pass without new failures | Unit (npm test) |

### Test scenarios

#### Scenario 1: Collapsed dedicated style and left-gap removal
1. Log in and collapse the sidebar.
2. Open a parent menu group that has child menus.
3. Verify collapsed-specific style is applied and left blank spacing is not present.

#### Scenario 2: Regression check for behavior and interaction
1. In collapsed mode, open submenu and click a child menu item.
2. Confirm navigation occurs to the intended screen.
3. Expand the sidebar again and verify expanded spacing remains unchanged.

### Test data
- Existing account with permissions to at least one child menu under each major menu group.
- Existing test navigation targets in `pb-feplog`, `search-history`, `statistics`, and `user-management`.

### Test environment
- Frontend: `http://localhost:3001`
- Backend: `http://localhost:9200`
- Browser: Chrome latest (desktop)

### 3.5 Browser automation verification (optional — for frontend-heavy requirements)
- Applicable TCs: TC-01, TC-02, TC-03, TC-04, TC-05
- Procedure per TC:
  - Navigate to app and log in.
  - Collapse sidebar and open target submenu.
  - Use `browser_snapshot` and screenshot comparison to verify no left blank spacing in collapsed mode.
  - Execute click/navigation verification and confirm no behavior regression.

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
- 2026-04-08

### Test results
#### Frontend
- `npm test -- --watchAll=false AppSidebar.test.js`: PASS (3/3)
- `npm test -- --watchAll=false`: FAIL (1 test failure in existing `UserActivityLogList.test.js`, unrelated to this sidebar change)
- `npm run build`: PASS
- `./scripts/dev-services.sh frontend restart`: PASS
- `curl -s -o /dev/null -w "%{http_code}" http://localhost:3001`: `200`

#### Backend
- Not applicable (frontend-only requirement)

### Issues found and resolution
- Existing unrelated frontend test failure: `frontend/src/components/UserActivityLog/UserActivityLogList.test.js` (assertion mismatch in existing test suite). Sidebar scope tests and build/restart checks passed.

### Next steps
1. QA verification for manual scenarios TC-02/TC-03/TC-04/TC-05 (visual no-left-gap + expanded regression + navigation behavior).
2. If needed, address unrelated `UserActivityLogList.test.js` failure in separate requirement/child bugfix.

## 6. Error remedy result (cause and action) — for error/bug fix requirements only
- **Requirement ID**: `20260408-collapsed-menu-tree-submenu-hidden-by-right-panel-bugfix`
- **Root cause**: Follow-up improvement request after prior overlap fix; collapsed mode still needs dedicated style separation to control left spacing precisely.
- **Actions taken**: Implemented collapsed-only style branch in `AppSidebar` and set collapsed submenu child/button and `subMenuContent` left indent to 0 while preserving expanded indent and layering settings; added regression tests.
- **Result**: Collapsed submenu left-gap style issue fixed in frontend unit scope; expanded mode indent preserved; no regression observed in updated sidebar tests and build/restart checks.
- **Completed**: 2026-04-08

---

**Author**: Requirements subagent
**Date**: 2026-04-08
**Status**: In progress
