# 20250227 - Department hierarchy sample data depth 5

## 1. User requirement

### Requirement description

1. **Sample data with deep hierarchy**: Extend the department sample data in `init-data.sql` so that the department hierarchy has a **depth of approximately 5 levels** (parent → child chain). The existing hierarchy (HQ → DEPT01, DEPT02) is only 2 levels; the user wants to verify that the hierarchy UI renders correctly with a deeper structure.

2. **Hierarchy UI verification**: After applying the extended sample data, verify that the **user permission hierarchy** view (and any department tree views) correctly displays all levels of the hierarchy. Expand/collapse and indentation should work as expected for a 5-level chain.

3. **Completion and push**: When verification is complete, perform **git push** (user explicitly requested push after completion). Include this in the handoff to QA so QA runs `git push` after commit per `commit-on-complete.md`.

### User scenario

1. A developer or administrator runs the DB init/seed (schema + init-data.sql) from a clean state. The department table contains a chain of departments with depth ~5 (e.g. HQ → DEPT01 → DEPT01A → DEPT01A1 → DEPT01A1X).

2. The administrator opens the **"사용자 권한 계층"** (user permission hierarchy) view. The department tree shows all 5 levels; expanding each node reveals child departments and users (where assigned). Indentation and expand/collapse work correctly for the deep structure.

3. The administrator confirms that no regression occurred: existing features (permission group management, department approver, etc.) still work. After all tests pass, the changes are committed and **pushed** to the remote repository.

4. **Problem**: Current sample data has only 2 levels (HQ → DEPT01/DEPT02). This is insufficient to validate that the hierarchy UI handles deeper trees (indentation, expand/collapse, layout) correctly.

### Expected outcome

- **Sample data**: `init-data.sql` (or equivalent) contains department rows forming a chain of **depth ~5** (e.g. root → level 1 → level 2 → level 3 → level 4 → level 5). At least one such chain; optionally additional branches at various depths for variety.
- **Hierarchy UI**: The user permission hierarchy view (and department tree views) correctly render the 5-level structure with proper indentation, expand/collapse, and no layout overflow or truncation.
- **No regression**: Existing APIs and UI (permission groups, hierarchy API, department approver) continue to work.
- **Push on complete**: QA performs commit per `commit-on-complete.md` and runs `git push` when the user requested push.

---

## 2. Design

### Technical design

#### Problem analysis

1. **Shallow sample data**: Current `init-data.sql` inserts only HQ (root), DEPT01, and DEPT02 — a 2-level hierarchy. The user permission hierarchy and department tree components are designed for arbitrary depth, but there is no sample data to verify behavior at depth 5.

2. **Verification gap**: Without deep sample data, it is unclear whether the hierarchy UI (UserPermissionHierarchy, DepartmentApproverManagement tree) handles long chains correctly — e.g. indentation, scroll, or layout issues at depth 5.

3. **User assignment**: To make the hierarchy view meaningful, at least one user should be assigned to a leaf department in the deep chain so that expanding the full path shows users at the deepest level.

#### Solution approach

**Database (init-data.sql)**

- Extend the `department` INSERT to add a **5-level chain** under the existing structure. Example:
  - Level 0 (root): HQ
  - Level 1: DEPT01 (parent HQ), DEPT02 (parent HQ)
  - Level 2: DEPT01A (parent DEPT01)
  - Level 3: DEPT01A1 (parent DEPT01A)
  - Level 4: DEPT01A1X (parent DEPT01A1)
- Keep existing DEPT01, DEPT02; add new codes (DEPT01A, DEPT01A1, DEPT01A1X) to form the chain.
- Optionally add one sample user (e.g. user3) with `department_code = 'DEPT01A1X'` so the hierarchy view shows a user at the deepest level. If adding user3, also add `app_user_permission_group` and `permission_group_screen` assignments as needed for consistency.
- Use `ON CONFLICT (code) DO NOTHING` so re-running init-data does not fail.

**Backend**

- No API changes required. The existing `GET /api/departments/user-permission-hierarchy` and `GET /api/departments?format=tree` already return the full tree; they will automatically include the new departments.

**Frontend**

- No code changes required. UserPermissionHierarchy and DepartmentApproverManagement already render the tree recursively. Verification is manual/browser: confirm that the 5-level chain displays correctly.

**Verification**

- Run init-data; restart backend; open hierarchy view; expand each level and confirm all 5 levels render with correct indentation and no layout issues.
- Run existing §3 test cases from `20250227-user-permission-hierarchy-group.md` (TC-01, TC-08) to ensure no regression.

### Change file list

**(Confirmed by DB subagent after implementation.)**

#### Database

- `backend/src/main/resources/db/init-data.sql` — **modified**
  - Added department rows: DEPT01A (parent DEPT01), DEPT01A1 (parent DEPT01A), DEPT01A1X (parent DEPT01A1) to form a 5-level chain (HQ → DEPT01 → DEPT01A → DEPT01A1 → DEPT01A1X). ON CONFLICT (code) DO NOTHING.
  - Added user3 with department_code = DEPT01A1X, password_hash = 'user123', role = USER. ON CONFLICT (username) DO NOTHING.
  - Added app_user_permission_group: user3 → GENERAL_USER. ON CONFLICT (user_id, permission_group_id) DO NOTHING.

#### Backend

- None (no API or service changes).

#### Frontend

- None (no component changes).

### Database changes

- **department table**: New rows only (INSERT). No schema change.
- **app_user** (optional): If adding user3 for leaf-department verification, one new row.
- **app_user_permission_group** (optional): If adding user3, rows linking user3 to GENERAL_USER (and optionally one other group).

---

## 3. Test approach

### Test case list (required)

| ID   | Type     | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|------|----------|------------------------------|-----------------|--------------------------------------------|
| TC-01 | Normal  | Run init-data.sql; restart backend; open "사용자 권한 계층" | Department tree includes 5-level chain (HQ → DEPT01 → DEPT01A → DEPT01A1 → DEPT01A1X) | Manual / browser |
| TC-02 | Normal  | Expand each node in the 5-level chain | All levels visible with correct indentation; no overflow or truncation | Manual / browser |
| TC-03 | Normal  | If user3 at DEPT01A1X: expand full path and select leaf | User3 appears under DEPT01A1X with role and permission groups | Manual / browser |
| TC-04 | Regression | Run TC-01–TC-09 from 20250227-user-permission-hierarchy-group.md | All pass (hierarchy, permission groups, admin-only) | Integration / manual |
| TC-05 | Normal  | After verification: commit and push | Commit references this requirement; `git push` succeeds | Manual |

### Test scenarios

#### Scenario 1: Sample data depth verification

1. Apply schema and init-data to a clean database (or reset if using dev setup).
2. Restart backend. Call `GET /api/departments?format=tree` or `GET /api/departments/user-permission-hierarchy` (as admin).
3. Confirm response includes the 5-level chain: HQ → DEPT01 → DEPT01A → DEPT01A1 → DEPT01A1X.
4. Verify `children` nesting is correct at each level.

#### Scenario 2: Hierarchy UI rendering

1. Log in as admin. Open "사용자 권한 계층" menu.
2. Expand HQ → DEPT01 → DEPT01A → DEPT01A1 → DEPT01A1X.
3. Confirm each level shows with increasing left indentation (level * padding).
4. Confirm no horizontal overflow, no truncated text, expand/collapse works.
5. If user3 exists at DEPT01A1X, confirm user3 and permission groups appear under the leaf node.

#### Scenario 3: Regression and push

1. Re-run TC-01–TC-09 from `20250227-user-permission-hierarchy-group.md` (or a subset: TC-01, TC-07, TC-08).
2. All pass. Update §5 in this requirement doc.
3. Commit per `commit-on-complete.md` (message references `req 20250227-dept-hierarchy-sample-depth5`).
4. Run `git push` (user requested push).

### Test data

- Rely on extended `init-data.sql`: departments HQ, DEPT01, DEPT02, DEPT01A, DEPT01A1, DEPT01A1X (and optionally user3 at DEPT01A1X).

### Test environment

- Frontend: `http://localhost:3001` (or per contract)
- Backend: `http://localhost:9200`
- Database: PostgreSQL (per contract)

### 3.5 Browser automation verification (optional)

For TC-01, TC-02, TC-03: QA may use Browser MCP to navigate to hierarchy view, expand nodes, and take snapshots to confirm 5-level rendering. Reference: `docs/workflow/BROWSER-AUTOMATION-VERIFICATION-POLICY.md`.

---

## 4. Checklist

### Frontend verification

- [x] Hierarchy UI shows 5-level chain correctly (indentation, expand/collapse)
- [x] No layout overflow or regression

### Backend verification

- [x] init-data.sql applies without error
- [x] GET /api/departments/user-permission-hierarchy returns 5-level tree

### Integration

- [x] End-to-end: init-data → restart → hierarchy view → expand all levels
- [x] Regression: 20250227-user-permission-hierarchy-group TCs pass

### Documentation

- [x] Requirement doc completed
- [x] §5 test results recorded

---

## 5. Test results

### Test run date

- 2026-02-27 (QA verification after backend restart)

### Scope

- DB (init-data) + Backend (no code change) + Frontend (verification only)

### Health check

| Target | Result | Note |
|--------|--------|------|
| Backend 9200 | Pass | `curl http://localhost:9200/api/health` → 200, JSON OK |
| Frontend 3001 | Pass | `curl http://localhost:3001` → 200 |
| DB connection | Pass | `curl http://localhost:9200/api/db/test` → `data.connected === true` |

### §3 test cases (TC-01–TC-05)

| ID | Result | Note |
|----|--------|------|
| TC-01 | Pass | Hierarchy view shows 5-level chain (HQ → DEPT01 → DEPT01A → DEPT01A1 → DEPT01A1X). API `GET /api/departments/user-permission-hierarchy` returns full tree; Browser MCP (project-0-dev-browser puppeteer) confirmed UI. |
| TC-02 | Pass | Expanded each node; correct indentation (level * 1.25rem); no overflow or truncation. |
| TC-03 | Pass | user3 appears under DEPT01A1X with role USER and permission group GENERAL_USER. |
| TC-04 | Pass | Regression: TC-07 (non-admin 403 on hierarchy API) verified; TC-01, TC-08 from 20250227-user-permission-hierarchy-group pass. |
| TC-05 | Pass | Commit and push performed per commit-on-complete.md. |

### Browser automation

- **Tool used**: project-0-dev-browser (puppeteer_navigate, puppeteer_click, puppeteer_fill, puppeteer_screenshot, puppeteer_evaluate)
- **Base URL**: http://localhost:3001
- **Steps**: Login as admin → Navigate to "사용자 권한 계층" → Expand HQ → DEPT01 → DEPT01A → DEPT01A1 → DEPT01A1X → Verified 5-level chain and user3 with role/permission groups.

### Issues found and resolution

None.

---

## 6. Error remedy result (cause and action) — for error/bug fix requirements only

(Not applicable.)

---

## 7. Final version (Korean) — add after all verification is complete

(To be added after QA verification. See `docs/workflow/DOCUMENT-LANGUAGE-POLICY.md` §2.)

---

**Author**: Requirements subagent  
**Date**: 2025-02-27  
**Status**: Complete
