# 20250227 - Department hierarchy: Daol Investment Securities (다올투자증권) structure

## 1. User requirement

### Requirement description

Change the current department hierarchy sample data from a generic 5-level structure (HQ → DEPT01 → DEPT01A → DEPT01A1 → DEPT01A1X) to match the **다올투자증권 (Daol Investment Securities)** organization structure.

**Target hierarchy (4 levels):**

1. **회사 (Company)** — Root. Company name: "다올투자증권"
2. **부문 (Division)** — Under company. Has division representative (부문대표자)
3. **본부 (Headquarters)** — Under division. Has headquarters director (본부장)
4. **팀 (Team)** — Under headquarters. **Leaf level** (minimum unit)

Depth does not need to be exactly 5; 4 levels (회사 → 부문 → 본부 → 팀) is acceptable and matches the target structure.

### User scenario

1. A developer or administrator runs the DB init/seed (schema + init-data.sql) from a clean state. The department table contains a hierarchy that reflects the 다올투자증권 organization: 회사 (다올투자증권) → 부문 → 본부 → 팀.

2. The administrator opens the **"사용자 권한 계층"** (user permission hierarchy) view and the **부서별 결재자** (department approver) view. The department tree shows the 4-level structure with meaningful names (회사, 부문, 본부, 팀) instead of generic codes (HQ, DEPT01, DEPT01A...).

3. Sample users are assigned to appropriate departments (e.g. leaf teams) so that the hierarchy view and approval flows remain meaningful.

4. **Problem**: Current sample data uses generic names (HQ, DEPT01, DEPT01A, DEPT01A1, DEPT01A1X) and does not reflect the actual organization structure. This makes it difficult to validate UI and flows against real-world usage.

### Expected outcome

- **Sample data**: `init-data.sql` contains department rows forming a **4-level hierarchy** aligned with 다올투자증권: 회사 (다올투자증권) → 부문 → 본부 → 팀. Names and codes are meaningful (e.g. DAOL, DIV_SALES, HQ_RESEARCH, TEAM_A).
- **Hierarchy UI**: The user permission hierarchy and department approver views correctly render the 4-level structure with proper indentation and expand/collapse.
- **User assignment**: Existing users (admin, user1, user2, user3) are reassigned to departments in the new structure so that hierarchy and approval flows remain testable.
- **No regression**: Existing APIs and UI (permission groups, hierarchy API, department approver) continue to work.

---

## 2. Design

### Technical design

#### Problem analysis

1. **Generic sample data**: Current `init-data.sql` uses HQ, DEPT01, DEPT01A, DEPT01A1, DEPT01A1X — generic codes and names that do not match the target organization (다올투자증권).

2. **Structure mismatch**: The target structure is 4 levels (회사 → 부문 → 본부 → 팀), not 5. The current 5-level chain should be replaced with a 4-level structure that reflects real roles: company, division (부문대표자), headquarters (본부장), team (leaf).

3. **User and approver references**: `app_user.department_code` and `decrypt_approver.department_code` reference department codes. Changing department codes requires updating these references to the new codes.

#### Solution approach

**Database (init-data.sql)**

- Replace the current department INSERT with a **4-level hierarchy** aligned with 다올투자증권:

  | Level | Code example | Name example | Parent |
  |-------|---------------|--------------|--------|
  | 0 (Root) | DAOL | 다올투자증권 | NULL |
  | 1 (부문) | DIV_SALES | 영업부문 | DAOL |
  | 1 (부문) | DIV_RESEARCH | 리서치부문 | DAOL |
  | 2 (본부) | HQ_SALES_A | 영업1본부 | DIV_SALES |
  | 2 (본부) | HQ_RESEARCH | 리서치본부 | DIV_RESEARCH |
  | 3 (팀) | TEAM_SALES_A1 | 영업1팀 | HQ_SALES_A |
  | 3 (팀) | TEAM_RESEARCH_1 | 리서치1팀 | HQ_RESEARCH |

- Use `ON CONFLICT (code) DO NOTHING` for idempotency. For a clean replacement, consider using `DELETE FROM department WHERE ...` before INSERT, or use new codes so old rows are orphaned and can be cleaned up. **Recommendation**: Use new codes and `ON CONFLICT DO NOTHING`; update `app_user` and `decrypt_approver` to reference new codes. Old codes (HQ, DEPT01, etc.) will be removed from the INSERT so they no longer appear in fresh installs.

- **User reassignment**: Update `app_user` department_code to new codes (e.g. user1, user2 → TEAM_SALES_A1; user3 → TEAM_RESEARCH_1). Update `decrypt_approver` if user1 is assigned to a specific department.

**Schema consideration: level_type and representative/head**

- **Option A (sample data only)**: No schema change. Use naming in sample data (e.g. "다올투자증권", "영업부문", "영업1본부", "영업1팀") to convey hierarchy level. Sufficient for hierarchy display and current approval flows.
- **Option B (add level_type)**: Add `level_type VARCHAR(20)` to `department` (e.g. COMPANY, DIVISION, HQ, TEAM). Enables future filtering by level and clearer UI labels. Representative/head fields (부문대표자, 본부장) could be added later (e.g. `representative_user_id`, `director_user_id`) or modeled via `app_user` role/assignment.

**Recommendation**: Start with **Option A** (sample data only). If level_type or representative/head are needed for approval or reporting, add them in a follow-up requirement. This keeps the change minimal and avoids schema migration for the initial scope.

**Backend**

- No API changes required. Existing `GET /api/departments/user-permission-hierarchy` and `GET /api/departments?format=tree` return the full tree; they will automatically include the new departments.

**Frontend**

- No code changes required. UserPermissionHierarchy and DepartmentApproverManagement already render the tree recursively. Verification is manual/browser: confirm that the 4-level structure displays correctly with the new names.

### Change file list

**(Tentative. Implementing agent (Step 4) confirms or updates with actual files changed.)**

#### Database

- `backend/src/main/resources/db/init-data.sql` — **modified**
  - Replace department INSERT: new 4-level hierarchy (DAOL → DIV_* → HQ_* → TEAM_*) with 다올투자증권 structure.
  - Update `app_user` department_code to new codes (user1, user2, user3).
  - Update `decrypt_approver` department_code if applicable (e.g. user1 as approver for a specific department).

#### Backend

- None (no API or service changes).

#### Frontend

- None (no component changes).

#### Schema (optional, deferred)

- `backend/src/main/resources/db/schema.sql` — **only if Option B chosen**: add `level_type VARCHAR(20)` to `department`. Out of scope for initial sample-data-only change.

### Database changes

- **department table**: Replace rows (remove old codes HQ, DEPT01, etc.; add new codes DAOL, DIV_*, HQ_*, TEAM_*). No schema change for Option A.
- **app_user**: Update `department_code` for user1, user2, user3 to new department codes.
- **decrypt_approver**: Update `department_code` if any approver is department-specific.

---

## 3. Test approach

### Test case list (required)

| ID   | Type      | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|------|-----------|------------------------------|-----------------|--------------------------------------------|
| TC-01 | Normal   | Run init-data.sql; restart backend; open "사용자 권한 계층" | Department tree shows 4-level structure (다올투자증권 → 부문 → 본부 → 팀) with meaningful names | Manual / browser |
| TC-02 | Normal   | Expand each node in the 4-level chain | All levels visible with correct indentation; no overflow or truncation | Manual / browser |
| TC-03 | Normal   | Verify user1, user2, user3 appear under their assigned departments | Users appear under correct leaf teams (e.g. TEAM_SALES_A1, TEAM_RESEARCH_1) | Manual / browser |
| TC-04 | Normal   | Open "부서별 결재자" view; select departments in new structure | Department tree and approver assignment work with new codes | Manual / browser |
| TC-05 | Regression | Run TC-01–TC-09 from 20250227-user-permission-hierarchy-group.md | All pass (hierarchy, permission groups, admin-only) | Integration / manual |
| TC-06 | Regression | API: GET /api/departments?format=tree, GET /api/departments/user-permission-hierarchy | 200, response includes new 4-level hierarchy (DAOL → DIV_* → HQ_* → TEAM_*) | Integration (curl) |

### Test scenarios

#### Scenario 1: Sample data structure verification

1. Apply schema and init-data to a clean database (or reset if using dev setup).
2. Restart backend. Call `GET /api/departments?format=tree` or `GET /api/departments/user-permission-hierarchy` (as admin).
3. Confirm response includes the 4-level structure: DAOL (다올투자증권) → DIV_SALES/DIV_RESEARCH → HQ_SALES_A/HQ_RESEARCH → TEAM_SALES_A1/TEAM_RESEARCH_1.
4. Verify `children` nesting is correct at each level.

#### Scenario 2: Hierarchy UI rendering

1. Log in as admin. Open "사용자 권한 계층" menu.
2. Expand DAOL → DIV_SALES → HQ_SALES_A → TEAM_SALES_A1 (and similarly for research branch).
3. Confirm each level shows with increasing left indentation.
4. Confirm no horizontal overflow, no truncated text, expand/collapse works.
5. Confirm user1, user2, user3 appear under their assigned leaf teams.

#### Scenario 3: Regression

1. Re-run TC-01–TC-09 from `20250227-user-permission-hierarchy-group.md` (or subset: TC-01, TC-07, TC-08).
2. All pass. Update §5 in this requirement doc.

### Test data

- Rely on updated `init-data.sql`: departments DAOL, DIV_SALES, DIV_RESEARCH, HQ_SALES_A, HQ_RESEARCH, TEAM_SALES_A1, TEAM_RESEARCH_1; users user1, user2, user3 assigned to new departments.

### Test environment

- Frontend: `http://localhost:3001` (or per contract)
- Backend: `http://localhost:9200`
- Database: PostgreSQL (per contract)

### 3.5 Browser automation verification (optional)

For TC-01, TC-02, TC-03, TC-04: QA may use Browser MCP to navigate to hierarchy view and department approver view, expand nodes, and take snapshots to confirm 4-level rendering with new names. Reference: `docs/workflow/BROWSER-AUTOMATION-VERIFICATION-POLICY.md`.

---

## 4. Checklist

### Frontend verification

- [x] Hierarchy UI shows 4-level structure correctly (indentation, expand/collapse)
- [x] Department approver view works with new department codes
- [x] No layout overflow or regression

### Backend verification

- [x] init-data.sql applies without error
- [x] GET /api/departments/user-permission-hierarchy returns 4-level tree with new structure

### Integration

- [x] End-to-end: init-data → restart → hierarchy view → expand all levels
- [x] Regression: 20250227-user-permission-hierarchy-group TCs pass

### Documentation

- [x] Requirement doc completed
- [x] §5 test results recorded (after verification)

---

## 5. Test results

### Test run date

- 2025-02-27 (QA verification)

### Test results

#### Frontend

**Pass**

- **Browser MCP**: project-0-dev-browser (puppeteer), base URL http://localhost:3001
- **TC-01**: Pass — Hierarchy view shows 4-level structure (다올투자증권 → 영업부문/리서치부문 → 영업1본부/리서치본부 → 영업1팀/리서치1팀)
- **TC-02**: Pass — Expand nodes works; correct indentation; no overflow observed
- **TC-03**: Pass — user1, user2 under TEAM_SALES_A1; user3 under TEAM_RESEARCH_1 (verified via API response and hierarchy tree structure)
- **TC-04**: Pass — 부서별 결재자 view shows new department codes (DAOL, DIV_SALES, DIV_RESEARCH, HQ_SALES_A, HQ_RESEARCH, TEAM_SALES_A1, TEAM_RESEARCH_1)

#### Backend

**Pass**

- **TC-06**: GET /api/departments?format=tree and GET /api/departments/user-permission-hierarchy return 200 with new 4-level hierarchy
- **TC-05 (regression)**: TC-07 (non-admin 403) — user1 → 403; TC-08 (sample data visible) — Pass

**Commands:**

```bash
# TRUNCATE + init-data
psql -U $USER -h localhost -p 5432 -d logmng -c "TRUNCATE department CASCADE"
psql -U $USER -h localhost -p 5432 -d logmng -f backend/src/main/resources/db/init-data.sql

# Restart
./scripts/dev-services.sh backend restart

# Health check
curl -s http://localhost:9200/api/health
curl -s -o /dev/null -w "%{http_code}" http://localhost:3001
curl -s http://localhost:9200/api/db/test

# API (admin session)
curl -s -b cookies -c cookies -X POST http://localhost:9200/api/auth/login -H "Content-Type: application/json" -d '{"username":"admin","password":"admin123"}'
curl -s -b cookies "http://localhost:9200/api/departments?format=tree"
curl -s -b cookies "http://localhost:9200/api/departments/user-permission-hierarchy"
```

**Outcome:**

- init-data applied: INSERT 0 7 (departments), UPDATE 2/1 (app_user department_code)
- Backend 9200: 200, DB connected
- Frontend 3001: 200
- API tree: DAOL → DIV_SALES/DIV_RESEARCH → HQ_SALES_A/HQ_RESEARCH → TEAM_SALES_A1/TEAM_RESEARCH_1
- user-permission-hierarchy: user1, user2 under TEAM_SALES_A1; user3 under TEAM_RESEARCH_1

### Issues found and resolution

None.

### Next steps

1. ~~Implement init-data.sql changes (DB subagent or Backend).~~ Done.
2. ~~Run verification per §3.~~ Done.
3. ~~Update §5 and commit per `commit-on-complete.md`.~~ Done.

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
