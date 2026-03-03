# 20250303-activity-statistics-self-only-scope-bugfix-2 — Re-verification: TC-02, TC-06, TC-08 still fail after bugfix-1

**Parent requirement ID**: 20250303-activity-statistics-self-only-scope  
**Bugfix sequence**: 2

---

## §1. Failure description / user impact

### Discovery

- **When**: During QA re-verification (curl integration tests) after bugfix-1 completion
- **What failed**:
  - **TC-02**: Non-admin user1 (scope=self) GET /api/statistics/activity/daily with userId=user2 → **Expected**: user1's statistics only; **Actual**: user2's statistics returned (totalSearches=30, totalLogins=32)
  - **TC-06**: Non-admin user1 (scope=self) POST /api/activity-log/search with userId=user2 → **Expected**: user1's logs only; **Actual**: user2's logs returned (user_id: user2)
  - **TC-08**: Non-admin user1 (scope=self) GET /api/activity-log/322 where id belongs to user2 → **Expected**: 403 or 404; **Actual**: 200 with user2's log detail

### User impact

- **Security**: Non-admin users with scope=self can still access other users' activity logs and statistics by tampering with `userId` or record ID. Bugfix-1 did not resolve the scope enforcement bypass.

### Test procedure

```bash
# Login as user1 (scope=self) — session cookies saved
curl -s -c /tmp/user1_cookies.txt -b /tmp/user1_cookies.txt -X POST http://localhost:9200/api/auth/login \
  -H "Content-Type: application/json" -d '{"username":"user1","password":"user123"}'

# TC-02: user1 with userId=user2 → got user2's stats
curl -s -b /tmp/user1_cookies.txt "http://localhost:9200/api/statistics/activity/daily?userId=user2&startDate=2025-01-01&endDate=2026-12-31"

# TC-06: user1 with userId=user2 → got user2's logs
curl -s -b /tmp/user1_cookies.txt -X POST "http://localhost:9200/api/activity-log/search" \
  -H "Content-Type: application/json" -d '{"userId":"user2","startDate":"2025-01-01","endDate":"2026-12-31","page":1,"pageSize":5}'

# TC-08: user1 GET activity-log/322 (user2's) → 200 with user2's detail
curl -s -b /tmp/user1_cookies.txt "http://localhost:9200/api/activity-log/322"
```

### Environment

- Backend: 9200, health OK
- Build and restart done per user handoff
- DB: init-data applied; user1 has GENERAL_USER (scope NULL = self per permission_group_screen)

---

## §2. Error scope, cause, fix design, change list

### Error scope

- **Failure scope**: **backend**
- **Layer**: backend (controllers, services, session/scope resolution)
- **Symptom**: Scope enforcement (override userId when scope=self, ownership check for getActivityLogDetail) is still not applied when non-admin user1 sends userId=user2 or accesses user2's log by ID.
- **Impact**: ActivityStatisticsController, UserActivityLogController. Non-admin with scope=self can access other users' data via parameter tampering.

### Cause (estimated)

- **Root cause**: Scope override logic may not be invoked for user1. Possible causes:
  1. **screenScopes in session**: `session.getAttribute("screenScopes")` may return null or empty. When null, `ScopeHelper.resolveScope()` defaults to 'self' per design — but if session attributes differ between login and subsequent requests, scope could be wrong.
  2. **Session cookie**: curl sends JSESSIONID; auth is verified (no cookies → 401). So session is used. But `screenScopes` or `username` might not be present in the session for the statistics/activity-log request.
  3. **Scope resolution**: `getScreenScopesForUser("user1")` returns map from DB. `permission_group_screen` has scope NULL for GENERAL_USER → effective = 'self'. If login response omits `screenScopes` in JSON (Jackson omits null), session may store null. `getScreenScopes(request)` returns null → `resolveScope(..., null)` returns 'self'. So scope should be 'self'. Unless `isSystemAdmin` is incorrectly true or session has wrong user.
  4. **applyScopeForStatistics / search override**: When scope='self', logic overrides userId with currentUser. If `getCurrentUser()` returns null, we throw 401. We got 200, so we didn't throw. Either scope is 'all' (then we pass userId as-is) or currentUser is being used but service ignores it — unlikely since service receives applied params.
  5. **Most likely**: `getScreenScopes()` returns null/empty; `ScopeHelper.resolveScope(STATISTICS, false, null)` returns 'self'. But `getScreenScopes()` might return a map that has "statistics" → "all" if DB has scope='all' for some group user1 has. Check: user1 has GENERAL_USER, AUDIT, REPORT. If any has scope='all' for statistics, we'd get 'all'. Init-data inserts permission_group_screen without scope, so scope is NULL. NULL → 'self'. So no group has 'all'. **Conclusion**: Need to verify session attributes at request time and trace why scope override is not applied.

### Fix design

1. **Trace scope resolution and session attributes** for user1 at request time (statistics/activity-log endpoints).
2. **Ensure session persistence**: `AuthController.login` sets `session.setAttribute("screenScopes", ...)` with non-null map. If session loses attributes between requests, fix session/cookie handling.
3. **Fallback when screenScopes is null**: When `getScreenScopes(request)` returns null for non-admin, treat as scope='self' (per parent requirement). Ensure controllers do not pass client userId when scope='self'.
4. **Ownership check**: `getActivityLogDetail(id)` must verify `user_id == currentUser` when scope='self'; return 403 if not owner.

### Tentative change list

- `backend/.../controller/ActivityStatisticsController.java`: Trace and fix scope resolution; ensure `applyScopeForStatistics` overrides userId when scope='self' regardless of session state. Consider fallback: when `getScreenScopes()` is null and non-admin, use scope='self'.
- `backend/.../controller/UserActivityLogController.java`: Same for search (override request.userId) and getActivityLogDetail (ownership check). Ensure service receives overridden userId.
- `backend/.../controller/AuthController.java`, `AuthService.java`: Verify `screenScopes` is set at login and persists. If `getScreenScopesForUser` returns empty map for screens user has access to, ensure map includes activity-log/statistics/search-history with 'self' when scope is NULL in DB.
- `backend/.../service/UserActivityLogService.java`: Verify `getActivityLogDetail(id, currentUserIdForOwnership)` enforces 403 when scope=self and record belongs to another user.
- **Actual changes**:
  - `ActivityStatisticsController.java`: Injected AuthService; replaced session-based getCurrentUser/getScreenScopes/isSystemAdmin with AuthService.getCurrentUserInfo() for DB-backed scope resolution. applyScopeForStatistics, getUsers, getIps now use fresh screenScopes from DB.
  - `UserActivityLogController.java`: Injected AuthService; search and getActivityLogDetail use AuthService.getCurrentUserInfo() for scope resolution. When scope='self', userId overridden and ownership enforced.

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

## §5. Verification

- **Re-verification (2026-03-03)**: TC-02, TC-06, TC-08 **Pass**.
- Parent §5 updated. Commit per commit-on-complete.md.

---

## Handoff

- **To Backend**: Fix scope enforcement per §2–§3. Trace session attributes and scope resolution; ensure userId override and ownership check are applied for non-admin with scope=self. After fix + build + restart, hand off to **QA** for re-verification.
- **To QA**: After Backend closes the issue, re-run verification (TC-02, TC-06, TC-08). When all pass, update parent §5, **commit** (per commit-on-complete.md).
