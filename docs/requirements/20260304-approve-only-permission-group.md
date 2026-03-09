# 20260304 - Approve-only permission group (승인 전용 권한 그룹)

**Type**: Bug fix + feature  
**Scope**: Frontend routing/access-check bugs in `App.js` that block approve-only users, plus init-data for the APPROVE_USER permission group pattern.  
**Related**: `.cursor/skills/auth-permission-domain/SKILL.md`, `.cursor/skills/search-history-decrypt-domain/SKILL.md`, `.cursor/skills/department-approver-domain/SKILL.md`, `docs/requirements/20250304-permission-group-function-verification.md`

> **Note (generalization)**: This requirement applies to **all "approval-only permission groups"** — any group where `allowedScreenIds` has no `main` + has `pending-approvals` — not just the group named "APPROVE_USER". APPROVE_USER is the initial example group, but the same routing, menu filtering, and API rules apply to any group matching this condition (e.g. TEAM_APPROVER, REGIONAL_APPROVER). See `specs/permission-group-hierarchy.spec.yaml` §5 and `docs/requirements/20260305-approval-only-group-generalization.md`.

---

## 1. User requirement

### Requirement description

Team leaders (팀장) act as **decrypt approvers only** — they must NOT view or search logs. To enforce this, an approval-only permission group (e.g. **APPROVE_USER**) is created with access to **only** the `pending-approvals` screen and `approve=true`. The team leader is registered in the `decrypt_approver` table so they can approve/reject decryption requests.

However, the current frontend (`App.js`) has multiple bugs that prevent this pattern from working correctly. A user whose only allowed screen is `pending-approvals` still sees the log search screen (`LogTypeSelector` / `LogGrid`) because:

1. `currentView` is always initialized to `'main'` regardless of the user's allowed screens.
2. The access-check `useEffect` skips verification entirely when `currentView === 'main'`.
3. The denied-access fallback always redirects to `'main'` instead of the first allowed screen.
4. The `main` view renders `LogTypeSelector` / `LogGrid` without checking whether the user has `main` in their `allowedScreenIds`.

Additionally, `init-data.sql` does not include an APPROVE_USER permission group, so there is no sample data to exercise or test this pattern.

### User scenario

1. An administrator creates an APPROVE_USER permission group with only `pending-approvals` screen (approve=true).
2. A team leader (e.g. `user1`) is assigned APPROVE_USER as their sole permission group and is registered as a `decrypt_approver`.
3. The team leader logs in. They expect to see only the "승인 대기" (Pending Approvals) screen.
4. **Problem**: Instead, they see `LogTypeSelector` (log search) because `currentView` defaults to `'main'` and the access check does not verify `main` access. The team leader can see the log search UI even though they have no `main` screen permission. The backend correctly rejects log API calls with 403, but the frontend should never show the UI in the first place.

### Expected outcome

- After login, an APPROVE_USER user is **immediately** redirected to `pending-approvals` (not `main`).
- The sidebar shows **only** "이력·승인 > 승인 대기". No "로그 검색", "통계", or "관리" menus appear.
- `LogTypeSelector` and `LogGrid` are **never** rendered for users without `main` in their `allowedScreenIds`.
- If a user somehow navigates to an unauthorized view, they are redirected to their **first allowed screen** (not hardcoded `'main'`).
- A user with an empty `allowedScreenIds` sees an appropriate fallback (empty state or redirect to login).
- APPROVE_USER permission group exists in `init-data.sql` for testing.
- **No regression**: Users with `main` access (e.g. GENERAL_USER) continue to see `LogTypeSelector` as their initial view.

---

## 2. Design

### 2.1 Security review (optional; when PII / decryption / access control)

Access control is directly in scope. The APPROVE_USER pattern restricts a user to approve-only — they must not see logs.

- [ ] Security review performed (check if applicable)
- Risks: If the frontend renders log search UI without access checks, an approve-only user may perceive they have log access even though backend blocks actual data. This is a UI information disclosure concern.
- Acceptance / recommendations: Frontend must gate rendering of `LogTypeSelector` / `LogGrid` behind `canAccessView('main')`. Backend already correctly returns 403 for log APIs when user lacks `main` screen, so no backend changes are needed.

### Technical design

#### Problem analysis

1. **Hardcoded initial view** (App.js line 25): `useState('main')` — the initial view is always `'main'` regardless of the user's allowed screens. An APPROVE_USER user with only `pending-approvals` sees the log search screen on login.

2. **Access check skips `main`** (App.js lines 161–176): The `useEffect` that checks view access has an early return `if (currentView === 'main') return;` — this completely bypasses the access check for the `main` view. Users without `main` permission are never redirected away.

3. **Fallback always goes to `main`** (App.js lines 178–184): `handleNavigate` and the access-check `useEffect` both fall back to `setCurrentView('main')` when access is denied. For a user without `main` permission, this creates an infinite loop of invalid state.

4. **No rendering guard for `main`** (App.js lines 264–272): `LogTypeSelector` and `LogGrid` render unconditionally when `currentView === 'main'`, without checking `canAccessView('main')`.

5. **No APPROVE_USER in init-data** (init-data.sql): No sample data exists for the approve-only pattern, making it impossible to test without manual DB setup.

#### Solution approach

**Frontend (App.js):**

1. **Smart initial view**: After authentication is confirmed and `user` is set, determine the initial `currentView` based on `allowedScreenIds`. If `main` is in the list (or user is system admin), keep `'main'`. Otherwise, set to the first item in `allowedScreenIds`. If empty, set to a safe fallback (e.g. stay on `'main'` but rendering guard will show empty state).

2. **Remove `main` skip in access check**: Delete the `if (currentView === 'main') return;` line in the access-check `useEffect`. Add `main` to the same access-check logic as other views — if user lacks `main` access, redirect to the first allowed screen.

3. **Fix fallback to first allowed screen**: In both `handleNavigate` and the access-check `useEffect`, replace `setCurrentView('main')` with `setCurrentView(firstAllowedScreen)` where `firstAllowedScreen` is derived from `getAllowedScreenIds(user)`.

4. **Add rendering guard**: Wrap the `main` view rendering block (`LogTypeSelector` / `LogGrid`) with `canAccessView('main')` check. If the user doesn't have `main` access and `currentView` is somehow still `'main'`, show nothing or an empty state.

**Backend (init-data.sql):**

5. **Add APPROVE_USER permission group**: Insert a new permission group with code `'APPROVE_USER'`, name `'승인 전용 (팀장)'`, with only `pending-approvals` screen and `approve=true`.

6. **Document the pattern**: Add a comment in init-data.sql explaining that APPROVE_USER is for team leaders who act as decrypt approvers only, with no log search access.

**Frontend (SearchHistoryList.js):**

5. **Hide non-approver actions for users without `main` screen**: In the search-history screen's actions column, hide "재조회" (re-search) and "재요청" (re-request) buttons when the user does not have `main` in their `allowedScreenIds`. These actions depend on `main` screen access (재조회 navigates to main, 재요청 is for original searchers). "자세히 보기" (view detail) remains visible for all users as it provides approval context.

**No backend API changes needed:**

- The backend interceptor already correctly gates log search APIs behind the `main` screen permission.
- Approve/reject APIs are correctly gated by `pending-approvals` screen with approve function check.
- `AuthService` does not force `main` into `allowedScreenIds` — it returns only what the permission group grants.

### Change file list

**(Tentative. Implementing agent (Step 4) confirms or updates with actual files changed.)**

#### Frontend
- `frontend/src/App.js`
  - Fix initial `currentView` to use first allowed screen when `main` is not in `allowedScreenIds`
  - Remove `if (currentView === 'main') return;` early exit in access-check `useEffect`
  - Change fallback from `setCurrentView('main')` to `setCurrentView(firstAllowedScreen)` in both `handleNavigate` and `useEffect`
  - Add `canAccessView('main')` rendering guard before `LogTypeSelector` / `LogGrid`
- `frontend/src/components/SearchHistory/SearchHistoryList.js`
  - Hide "재조회" button when user does not have `main` in `allowedScreenIds`
  - Hide "재요청" button when user does not have `main` in `allowedScreenIds`
  - "자세히 보기" remains visible for all users

#### Backend
- `backend/src/main/resources/db/init-data.sql`
  - Add APPROVE_USER permission group (`pending-approvals` only, `approve=true`)
  - Optionally assign to a test user or add explanatory comment

#### Domain knowledge (already updated)
- `.cursor/skills/auth-permission-domain/SKILL.md`
- `.cursor/skills/search-history-decrypt-domain/SKILL.md`
- `.cursor/skills/department-approver-domain/SKILL.md`

### Database changes

No schema changes. Only `init-data.sql` data insertion:
- New row in `permission_group`: code=`APPROVE_USER`
- New row in `permission_group_screen`: screen_id=`pending-approvals`, approve=`true`

---

## 3. Test approach

### Test case list (required)

| ID | Type | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|----|------|------------------------------|-----------------|--------------------------------------------|
| TC-01 | Normal | Login as APPROVE_USER (only `pending-approvals`). Check initial view after login. | Redirected to `pending-approvals` (NOT `main`). Sidebar shows only "승인 대기". | Manual / browser |
| TC-02 | Normal | APPROVE_USER user navigates. Check sidebar menu items. | Only "이력·승인 > 승인 대기" visible. No "로그 검색", "통계", "관리" menus. | Manual / browser |
| TC-03 | Security | APPROVE_USER user: programmatically set `currentView` to `'main'`. | View redirected to first allowed screen (`pending-approvals`). `LogGrid` NOT rendered. | Unit (Jest) |
| TC-04 | Normal | APPROVE_USER + `decrypt_approver` sees pending approval list. | Can see pending requests with `searchParamsSummary`. Approve/reject buttons enabled. | Integration (curl) |
| TC-05 | Security | APPROVE_USER user calls `GET /api/logs/db-refactored?logType=pb_feplog`. | 403 FORBIDDEN (no `main` screen permission). | Integration (curl) |
| TC-06 | Normal | GENERAL_USER (has `main`) logs in. Check initial view. | Shows `main` (`LogTypeSelector`) as before. No regression. | Manual / browser |
| TC-07 | Edge | User with `allowedScreenIds = []` (empty). | Frontend shows appropriate fallback (no crash, no log search UI). | Unit (Jest) |
| TC-08 | Normal | User WITHOUT `main` screen views search-history. Check action buttons. | "재조회" and "재요청" buttons are hidden. "자세히 보기" remains visible. | Manual / browser |
| TC-09 | Normal | User WITH `main` screen views search-history. Check action buttons. | "재조회", "자세히 보기", "재요청" all visible as before (no regression). | Manual / browser |

### Test scenarios

#### Scenario 1: APPROVE_USER login and initial view (TC-01, TC-02)

1. Ensure APPROVE_USER permission group exists in DB with only `pending-approvals` screen.
2. Assign APPROVE_USER to a test user (e.g. `user1`) and remove other permission groups.
3. Login as the test user via the frontend.
4. Verify: initial view is `pending-approvals`, sidebar shows only "승인 대기".
5. Verify: no "로그 검색", "통계", "관리" sidebar items.

#### Scenario 2: Access check prevents main view (TC-03)

1. Mount `App` component with a mocked user having `allowedScreenIds: ['pending-approvals']`.
2. Simulate setting `currentView` to `'main'`.
3. Verify: `useEffect` detects lack of access and redirects to `pending-approvals`.
4. Verify: `LogTypeSelector` and `LogGrid` components are NOT in the rendered output.

#### Scenario 3: Decrypt approval flow for APPROVE_USER (TC-04)

1. Login as APPROVE_USER user who is a `decrypt_approver`.
2. Call `GET /api/search-history/pending-approvals` with the user's session.
3. Verify: response contains pending requests.
4. Call `POST /api/search-history/{id}/approve` or `POST /api/search-history/{id}/reject`.
5. Verify: 200 OK with approval/rejection recorded.

#### Scenario 4: Log API rejection for APPROVE_USER (TC-05)

1. Login as APPROVE_USER user.
2. Call `GET /api/logs/db-refactored?logType=pb_feplog` with the user's session.
3. Verify: 403 response with `FORBIDDEN` error code (no `main` screen).

#### Scenario 5: GENERAL_USER regression (TC-06)

1. Login as GENERAL_USER (has `main` screen).
2. Verify: initial view is `main`, `LogTypeSelector` is rendered.
3. Navigate to `pending-approvals` and back to `main`.
4. Verify: everything works as before.

#### Scenario 6: Empty allowedScreenIds (TC-07)

1. Mount `App` with mocked user having `allowedScreenIds: []` (or `null`).
2. Verify: no crash, no `LogTypeSelector`/`LogGrid` rendered.
3. Verify: graceful empty state or fallback.

### Test data

- **APPROVE_USER permission group** (in `init-data.sql`):

```sql
INSERT INTO permission_group (code, name, description, sort_order)
VALUES ('APPROVE_USER', '승인 전용 (팀장)', '팀장 전용 — 로그 검색 불가, 복호화 승인만 가능', 10)
ON CONFLICT (code) DO NOTHING;

INSERT INTO permission_group_screen (permission_group_id, screen_id, approve)
SELECT id, 'pending-approvals', true
FROM permission_group WHERE code = 'APPROVE_USER'
ON CONFLICT (permission_group_id, screen_id) DO NOTHING;
```

- **Test user setup** (for manual testing — assign APPROVE_USER to user1):

```sql
-- Remove existing permission groups for user1 (for test isolation)
DELETE FROM app_user_permission_group WHERE user_id = 'user1';

-- Assign APPROVE_USER only
INSERT INTO app_user_permission_group (user_id, permission_group_id)
SELECT 'user1', id FROM permission_group WHERE code = 'APPROVE_USER'
ON CONFLICT (user_id, permission_group_id) DO NOTHING;
```

- user1 is already in `decrypt_approver` table (init-data.sql line 53-54).

### Test environment

- Frontend: `http://localhost:3001`
- Backend: `http://localhost:9200`
- Database: PostgreSQL (local)

### 3.5 Browser automation verification (optional — for frontend-heavy requirements)

This is a frontend-dominant requirement. The following TCs can be verified via Browser MCP:

- **Applicable TCs**: TC-01, TC-02, TC-06
- **Procedure**:
  - TC-01: `browser_navigate` to login page → fill credentials for APPROVE_USER user → submit → `browser_snapshot` → confirm `currentView` shows pending-approvals content, NOT LogTypeSelector.
  - TC-02: After TC-01 login → `browser_snapshot` sidebar → confirm only "승인 대기" menu item is visible. No "로그 검색", "통계", "관리" items.
  - TC-06: `browser_navigate` to login → fill GENERAL_USER credentials → submit → `browser_snapshot` → confirm LogTypeSelector is visible (regression check).

---

## 4. Checklist

### Frontend verification
- [ ] `currentView` initialized to first allowed screen (not hardcoded `'main'`)
- [ ] Access check `useEffect` no longer skips `main` view
- [ ] Fallback redirects to first allowed screen (not `'main'`)
- [ ] `LogTypeSelector` / `LogGrid` rendering guarded by `canAccessView('main')`
- [ ] APPROVE_USER login shows only `pending-approvals`
- [ ] GENERAL_USER login shows `main` (no regression)
- [ ] Empty `allowedScreenIds` handled gracefully

### Backend verification
- [ ] APPROVE_USER permission group inserted in `init-data.sql`
- [ ] `permission_group_screen` row has `approve=true` for `pending-approvals`
- [ ] Existing permission groups unaffected (no data regression)

### Integration
- [ ] APPROVE_USER user can access pending-approvals API
- [ ] APPROVE_USER user blocked from log search API (403)
- [ ] End-to-end: login → see only pending-approvals → approve/reject works

### Documentation
- [ ] Requirement doc completed (this document)
- [ ] Domain skills already updated (auth-permission-domain, search-history-decrypt-domain, department-approver-domain)

---

## 5. Test results

### Test run date
- [Pending]

### Test results

**Commands:**

```bash
# TC-04: APPROVE_USER + decrypt_approver sees pending approval list
# Step 1: Login as APPROVE_USER user (user1)
curl -s -c /tmp/approve_cookie.txt -X POST http://localhost:9200/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"user1","password":"user1"}' | python3 -m json.tool

# Step 2: Get pending approvals list
curl -s -b /tmp/approve_cookie.txt http://localhost:9200/api/search-history/pending-approvals | python3 -m json.tool

# TC-05: APPROVE_USER user calls log search API → 403
curl -s -b /tmp/approve_cookie.txt 'http://localhost:9200/api/logs/db-refactored?logType=pb_feplog&page=0&size=10' | python3 -m json.tool
# Expected: 403 with FORBIDDEN error code

# TC-06 (regression): GENERAL_USER login and auth check
curl -s -c /tmp/general_cookie.txt -X POST http://localhost:9200/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"user2","password":"user2"}' | python3 -m json.tool
# Expected: allowedScreenIds includes 'main'

curl -s -b /tmp/general_cookie.txt 'http://localhost:9200/api/logs/db-refactored?logType=pb_feplog&page=0&size=10' | python3 -m json.tool
# Expected: 200 OK with log data (has 'main' screen)
```

**Outcome:**
- [Pending]

### Issues found and resolution
- [Pending]

### Next steps
1. Frontend subagent: fix App.js (initial view, access check, fallback, rendering guard)
2. Backend/DB subagent: add APPROVE_USER to init-data.sql
3. QA: run TC-01 through TC-07, update §5

---

## 6. Error remedy result (cause and action) — for error/bug fix requirements only

- **Requirement ID**: 20260304-approve-only-permission-group
- **Root cause**: [Pending — to be filled after fix]
- **Actions taken**: [Pending]
- **Result**: [Pending]
- **Completed**: [Pending]

---

## 7. Final version (Korean) — add after all verification is complete

### 요건 요약 (한글)
- **요건 설명**: [검증 완료 후 작성]
- **기대 결과**: [검증 완료 후 작성]
- **검증 결과**: [검증 완료 후 작성]

---

**Author**: Agent (Requirements)  
**Date**: 2026-03-04  
**Status**: In progress
