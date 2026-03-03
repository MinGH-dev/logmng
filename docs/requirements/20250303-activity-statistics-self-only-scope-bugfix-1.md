# 20250303-activity-statistics-self-only-scope-bugfix-1 — Backend: scope enforcement not applied for non-admin (session/scope resolution)

**Parent requirement ID**: 20250303-activity-statistics-self-only-scope  
**Bugfix sequence**: 1

---

## §1. Failure description / user impact

### Discovery

- **When**: During QA verification (integration curl tests per parent §3)
- **What failed**:
  - **TC-02**: Non-admin user1 (scope=self) calls GET /api/statistics/activity/daily with userId=user2 → **Expected**: only user1's statistics; **Actual**: user2's statistics returned (userId param not overridden)
  - **TC-06**: Non-admin user1 (scope=self) calls POST /api/activity-log/search with userId=user2 → **Expected**: only user1's logs; **Actual**: user2's logs returned
  - **TC-08**: Non-admin user1 (scope=self) calls GET /api/activity-log/{id} where id belongs to user2 → **Expected**: 403 or 404; **Actual**: 200 with user2's log detail

### User impact

- **Security**: Non-admin users with scope=self can access other users' activity logs, search history, and statistics by tampering with `userId` or record ID in API requests. Backend scope enforcement is bypassed.
- **Affected APIs**: GET/POST statistics (daily/monthly/users/export), POST activity-log/search, GET activity-log/{id}, GET search-history (list/detail).

---

## §2. Scope, cause, fix design, change list

### Error scope

- **Failure scope**: **backend**
- **Layer**: backend (controllers, services, session handling)
- **Symptom**: Scope enforcement (override userId when scope=self, ownership check for getActivityLogDetail) is not applied for non-admin users.
- **Impact**: ActivityStatisticsController, UserActivityLogController, SearchHistoryController. Non-admin with scope=self can access other users' data via parameter tampering.

### Cause (estimated)

- **Root cause**: `getCurrentUser()` or `getScreenScopes()` may return null when the controller runs, so scope override is skipped (or ownership check receives null).
- **Possible causes**:
  1. Session attributes (`username`, `screenScopes`) not persisted correctly between login and subsequent API requests (cookie/session handling).
  2. `getScreenScopes()` returns null/empty; `ScopeHelper.resolveScope()` may default to 'self' — but `getCurrentUser()` returning null still causes the bug (no override when currentUser is null).
  3. Session not found (`request.getSession(false)` returns null) — e.g. JSESSIONID not sent or invalid.

### Fix design

1. **When scope=self and non-admin**:
   - **Statistics / activity-log search / search-history list**: Override `userId` with current user; ignore client-supplied userId. If `getCurrentUser()` is null, return **401 Unauthorized** (session invalid) or resolve username from another source (e.g. re-fetch from auth).
   - **getActivityLogDetail(id) / search-history getById**: Verify ownership (`user_id == currentUser`). If not owner → return **403 Forbidden**. If `getCurrentUser()` is null → return **401 Unauthorized**.

2. **Session / auth**:
   - Verify `session.setAttribute("username", ...)` and `session.setAttribute("screenScopes", ...)` are set at login and session is correctly associated with the response cookie.
   - Ensure controllers receive valid `getCurrentUser()` and `getScreenScopes()` for authenticated requests.

3. **Defensive handling**:
   - When scope=self and `currentUser == null`: treat as unauthenticated → 401.
   - When scope=self and `screenScopes == null` for the screen: treat as scope=self (default per parent requirement).

### Tentative change list

- `backend/.../controller/ActivityStatisticsController.java`: Ensure `applyScopeForStatistics` receives valid `getCurrentUser()` and `getScreenScopes()`; when scope=self and currentUser null → 401.
- `backend/.../controller/UserActivityLogController.java`: Same for search (override userId) and getActivityLogDetail (ownership check); when scope=self and currentUser null → 401.
- `backend/.../controller/SearchHistoryController.java`: Same scope enforcement; when scope=self and currentUser null → 401.
- `backend/.../service/AuthService.java`, `AuthController.java`: Verify session attributes set at login; session correctly associated with response cookie.
- **Actual changes (Backend subagent)**:
  - `ActivityStatisticsController`: getCurrentUser fallback (username→userId); applyScopeForStatistics/getUsers/getIps throw 401 when scope=self and currentUser null.
  - `UserActivityLogController`: getCurrentUser fallback; searchActivityLogs and getActivityLogDetail throw 401 when scope=self and currentUser null; ownership check now always receives valid currentUser when scope=self.
  - `SearchHistoryController`: getUserId fallback (userId→username).
  - AuthController/AuthService: session attributes (username, userId, screenScopes) already set at login; no change.

---

## §3. Test plan

### Re-verification TCs

| TC | Scenario | Expected |
|----|----------|----------|
| TC-02 | Non-admin user1 (scope=self) GET /api/statistics/activity/daily with userId=user2 | Returns only user1's statistics |
| TC-06 | Non-admin user1 (scope=self) POST /api/activity-log/search with userId=user2 | Returns only user1's logs |
| TC-08 | Non-admin user1 (scope=self) GET /api/activity-log/{id} where id belongs to user2 | 403 or 404 |

### Procedure

- Re-run TC-02, TC-06, TC-08 with curl (user1 scope=self, userId=user2 or access user2's log).
- Record results in parent §5 until all pass.
- QA updates parent §5 and commits when verification passes.

---

## Handoff

- **To Backend**: Fix scope enforcement per §2. After fix + build + restart, hand off to **QA** for re-verification.
- **To QA**: After Backend closes the issue, re-run verification (TC-02, TC-06, TC-08). When all pass, update parent §5, **commit**, and **push** (user requested "push까지 해줘").
