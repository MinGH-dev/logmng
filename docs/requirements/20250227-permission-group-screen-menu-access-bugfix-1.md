# 20250227-permission-group-screen-menu-access-bugfix-1 — Admin section hidden for non-admin with user-management screen

**Parent requirement ID**: `20250227-permission-group-screen-menu-access`  
**Bugfix sequence**: 1

---

## §1 Failure description and user impact

### Discovery

- **When**: During verification (browser automation, TC-05)
- **What failed**: Non-admin user (user2) with group USER_MGT_TEST that allows `user-management` screen does not see "사용자 관리" in the sidebar. The "관리" section is completely hidden.

### User impact

- **Symptom**: Admin section (관리) with 사용자 관리, 부서별 결재자, 사용자 권한 계층 is always hidden for non-admin users, regardless of `allowedScreenIds`.
- **Impact**: TC-05 fails. Non-admin users who have permission groups granting access to user-management (or other admin screens) cannot see or navigate to those screens via the sidebar. The parent requirement states: "A screen is visible if the user is ADMIN or if at least one of their groups allows that screen." This behavior violates that rule for admin-only menu nodes.

---

## §2 Scope, cause, fix design, and change list

### Failure scope

- **Scope**: frontend
- **Layer**: frontend

### Root cause

- **AppSidebar.js** line 28: `if (node.adminOnly && !isAdmin) return false;` — The entire admin node is filtered out for any non-admin user, without checking `allowedScreenIds`. Admin-only items should still be shown to non-admin users when they have the screen in their `allowedScreenIds`.
- **menuTree.js**: The "관리" node has `adminOnly: true`. The intent is that admin sees it by default; non-admin sees it only when they have an allowed screen in that node. Current logic ignores `allowedScreenIds` for `adminOnly` nodes.

### Fix design

For `adminOnly` nodes, instead of unconditionally hiding when `!isAdmin`, show the node when:
- `isAdmin` **OR**
- (non-admin has at least one child screen in `allowedScreenIds`)

Children of the admin node are already filtered by `allowedScreenIds` in the map step (lines 36–38), so only the top-level filter logic needs adjustment.

**Proposed change** (replace line 28):

```javascript
// Before:
if (node.adminOnly && !isAdmin) return false;

// After:
if (node.adminOnly && !isAdmin) {
  const ids = Array.isArray(allowedScreenIds) ? allowedScreenIds : [];
  const hasAnyAllowedChild = node.children.some((c) => c.view && ids.includes(c.view));
  if (!hasAnyAllowedChild) return false;
}
```

### Tentative change list

| File | Change |
|------|--------|
| `frontend/src/components/AppSidebar.js` | Update filter logic for `adminOnly` nodes to check `allowedScreenIds` before hiding |

---

## §3 Test plan

### Re-verification

1. **TC-05 (primary)**: Re-run TC-05 — user2 with USER_MGT_TEST (user-management) → sidebar must show "관리" section with "사용자 관리".
2. **TC-04 (regression)**: TC-04 must still pass — user2 with only GENERAL_USER → no "관리" section.

### Verification method

- Manual or browser automation (cursor-ide-browser)
- Base URL: http://localhost:3001
- Test data: user2 with GENERAL_USER + USER_MGT_TEST for TC-05; user2 with GENERAL_USER only for TC-04

### Handoff

- **Frontend** implements the fix → build and restart → hand off to **QA** for re-verification.
- **QA** re-runs verification; when all pass, QA updates §5 in this doc and **commits** (commit message references this doc). User requested push after commit; QA runs `git push` when verification passes.

---

## 4. Loop (reference)

- If verification fails again, create `20250227-permission-group-screen-menu-access-bugfix-2.md` and repeat.
- See `docs/template/BUGFIX_CHILD_TEMPLATE.md`.

---

## 5. Test results

### Re-verification date

- 2026-02-27

### TC-05 (primary)

| ID | Result | Note |
|----|--------|------|
| TC-05 | **Pass** | user2 with USER_MGT_TEST (user-management) → sidebar shows "관리" section with "사용자 관리". Tool: project-0-dev-browser (puppeteer). Base URL: http://localhost:3001. Logged in as user2; sidebar displayed 관리; expanded to confirm 사용자 관리 visible. |

### TC-04 (regression)

| ID | Result | Note |
|----|--------|------|
| TC-04 | **Pass** | user1 with GENERAL_USER only (no user-management) → sidebar does NOT show "관리" section. Logged in as user1; sidebar showed 로그 검색, 이력·승인, 통계 only; no 관리 section. |

### Summary

- Both TC-04 and TC-05 pass. Bugfix verified. Frontend fix (AppSidebar.js adminOnly + allowedScreenIds) resolves the issue.
