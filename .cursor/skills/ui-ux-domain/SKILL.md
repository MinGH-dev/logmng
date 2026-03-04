---
name: ui-ux-domain
description: >
  UI/UX: menu tree, view, screen IDs, adminOnly, canAccessView, sidebar.
  Use when user asks about menu, screen, view, adminOnly, canAccessView, or
  sidebar layout. 메뉴, 화면, view, adminOnly, canAccessView 관련 질문 시 사용.
---

# UI/UX Domain

**Skill usage visibility**: When you use this skill to answer, state at the start of your response: `[Skill used: ui-ux-domain]`

Use for **menu, screen, view, and access visibility** in this repo. Scope: MENU_TREE, screen IDs, canAccessView, adminOnly.

## Quick reference

- **Screen IDs**: main, search-history, activity-log, statistics, pending-approvals, user-management, user-permission-hierarchy, permission-group-management. specs §4.1.
- **canAccessView**: is_system_admin=true → 모든 화면; else allowedScreenIds에 view 포함 시 허용. user-management는 user-management OR user-permission-hierarchy 허용 시 접근.
- **adminOnly**: 메뉴 그룹(관리) — 관리자만 메뉴 표시. 하위: user-management, permission-group-management.
- **MENU_TREE**: frontend/src/constants/menuTree.js. 2-depth (그룹 → leaf screens).

## When to use

- Menu structure, sidebar, 메뉴 트리
- Screen IDs, view mapping
- adminOnly, canAccessView
- 화면 접근, 메뉴 표시 조건

## Document references

| Question type | Document | Section |
|---------------|----------|---------|
| Screen IDs, API mapping | Path: `specs/permission-group-hierarchy.spec.yaml` | `# 4. Screen IDs and screen-based access` (§4.1, §4.2, §4.3) |
| allowedScreenIds, screenScopes | Path: `docs/contract.md` | `## 화면 기반 접근 제어` |
| Full list (전체 처리 이력) | Path: `docs/requirements/TOPIC-INDEX.md` | §sidebar, §grid, §UX |

## Code references

| Concern | Location |
|---------|----------|
| Menu tree, ALLOWED_SCREEN_IDS | **frontend/src/constants/menuTree.js** |
| canAccessView, view routing | **frontend/src/App.js** |
| Sidebar, menu render | frontend/src/components/AppSidebar/AppSidebar.js |
| Screen access interceptor (backend) | backend/src/main/java/com/logmng/config/ScreenAccessInterceptor.java |

## screenFunctions → UI application rules

Source: `frontend/src/constants/screenFunctionDescriptions.js`

**Screens with write**: user-management, department-approvers, user-permission-hierarchy, permission-group-management (`SCREENS_WITH_WRITE`).
**Screens with approve**: search-history, pending-approvals (`SCREENS_WITH_APPROVE`).

| screenFunctions value | UI behavior | Tooltip (ACTION_DISABLED_TOOLTIPS) |
|-----------------------|-------------|-------------------------------------|
| `write === false` | Create/Edit/Delete/Assign buttons → **disabled** | '수정 권한이 없습니다' / '생성 권한이 없습니다' / '삭제 권한이 없습니다' |
| `approve === false` | Approve/Reject buttons → **disabled** | '승인 권한이 없습니다' / '반려 권한이 없습니다' |
| `write === true` | Buttons enabled | — |
| `approve === true` (and decrypt_approver) | Buttons enabled | — |

Code: `screenFunctionDescriptions.js` → `ACTION_DISABLED_TOOLTIPS`, `SCREENS_WITH_WRITE`, `SCREENS_WITH_APPROVE`, `getScreenFunctionCapabilities()`.

## Requirement doc completeness checklist (UI/UX)

When writing a **requirement document** that adds a new screen, changes menu structure, or modifies UI based on permissions, apply this checklist before finalizing §3:

- [ ] **menuTree.js**: New menu item added with correct group, path, screen ID.
- [ ] **adminOnly**: If the screen is management-only, verify it is under the admin group.
- [ ] **canAccessView**: Route guard in App.js checks allowedScreenIds (or is_system_admin).
- [ ] **screenFunctions → buttons**: If the screen has write or approve, verify disabled state + tooltip when function is false (see table above).
- [ ] **docs/design/ standards**: UI follows grid-and-table, buttons, forms-and-filters, layout-and-navigation standards.
- [ ] **a11y**: Keyboard navigation, focus ring, aria-label on icon buttons, contrast per docs/design/buttons.md.

## Before answering

1. Screen IDs: specs §4.1 목록. user-permission-hierarchy는 user-management로 리다이렉트(Option B).
2. canAccessView: App.js. user-management 특수: user-management OR user-permission-hierarchy.
3. adminOnly: menuTree.js의 admin 그룹. 관리자만 해당 메뉴 표시.
4. **Requirement traceability**: When explaining design, cite requirement doc (path + §section).

## Related skills

- `auth-permission-domain`: screenFunctions derivation rules (read/write/approve).
- `api-permission-map`: Backend enforcement of write/approve — UI disabled state must match backend denial.

## References

- Spec: specs/permission-group-hierarchy.spec.yaml §4
- Contract: docs/contract.md
- Improvement design: docs/SKILL-DOCUMENT-IMPROVEMENT-DESIGN.md
