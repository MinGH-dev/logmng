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

## Before answering

1. Screen IDs: specs §4.1 목록. user-permission-hierarchy는 user-management로 리다이렉트(Option B).
2. canAccessView: App.js. user-management 특수: user-management OR user-permission-hierarchy.
3. adminOnly: menuTree.js의 admin 그룹. 관리자만 해당 메뉴 표시.
4. **Requirement traceability**: When explaining design, cite requirement doc (path + §section).

## References

- Spec: specs/permission-group-hierarchy.spec.yaml §4
- Contract: docs/contract.md
- Improvement design: docs/SKILL-DOCUMENT-IMPROVEMENT-DESIGN.md
