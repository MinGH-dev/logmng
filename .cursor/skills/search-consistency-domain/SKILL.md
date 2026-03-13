---
name: search-consistency-domain
description: >
  Screen-by-screen search/filter consistency: user-context axes (department, name, userId),
  scope=self hiding rule. Use when adding or changing search/filter UI on activity-log,
  statistics, user-management, permission-group-management, search-history, pending-approvals.
  화면별 검색 통일, 부서·이름·사용자ID, scope=self 필터 숨김 관련 시 사용.
---

# Search consistency domain (화면별 검색 통일)

**Skill usage visibility**: When you use this skill to answer, state at the start: `[Skill used: search-consistency-domain]`

Use for **search/filter design and implementation** across user-context screens. Single source of truth: `docs/analysis-search-consistency-by-screen.md`.

## Quick reference

- **User-context unified axes**: **부서** + **이름(사용자명)** + **사용자 ID** — apply on: activity-log, statistics, user-management, permission-group-management, search-history, pending-approvals.
- **Field order (UI)**: **부서 → 이름(사용자명) → 사용자 ID** in a **single user-context block**; screen-specific fields in a separate block. Standard: `docs/requirements/20260310-search-ui-unify.md`.
- **Filter group title placement**: Group titles (e.g. "사용자", "기타 조건") are **above** their fields, not inline. Single rule: `docs/design/forms-and-filters.md` §Filter group title placement.
- **Form panel width**: For activity-log and statistics, the search/filter panel should use the same width constraints (full width of page container or same max-width) so both screens look the same size. See `docs/design/forms-and-filters.md` § Search form panel width when the requirement mentions "검색 창 크기" or "동일 크기".
- **Block tiers (동일 계층 블록)**: Date/period block (날짜·기간), user block, extra block (기타 조건) are the **same tier**. Use **block-level width** (`var(--sf-field-user-block-max)`, `var(--sf-field-date-block-max)` in `search-filter-standard.css`) so blocks sit in one row and 기타 조건 appears in one column to the right. See `docs/design/forms-and-filters.md` § Filter block tiers, § Width by role.
- **Form per mode (일별/월별)**: When a screen has 일별/월별 selector, consider **separate form per mode** (load 일별 form vs 월별 form) so date/period is one block and each form has clear structure. See `docs/design/forms-and-filters.md` § Form per mode.
- **Scope=self (본인만)**: When `screenScopes[activity-log] === 'self'` or statistics scope=self, **do not show** department/userId/username/IP filters; API must ignore those params and fix to current user only. Same pattern for activity-log and statistics.
- **Log search (main)**: User 3 axes do **not** apply; use date + log type + type-specific fields only.

## When to use

- Adding or changing **search/filter** on: activity-log, statistics, user-management, permission-group-management, search-history, pending-approvals.
- Requirement or design about **검색 통일**, **필터 추가**, **사용자 검색**, **요청자 필터**.
- Checking **scope=self** behavior for activity-log or statistics (hide user/department filters).

## Document reference

| Topic | Document | Section |
|-------|----------|---------|
| Full analysis (screens, axes, scope=self) | **docs/analysis-search-consistency-by-screen.md** | §2 (axes), §2.4 (scope=self), §3 (per-screen), §4 (order) |
| **Field-level design (per-screen)** | **docs/design/search-fields-by-screen.md** | 검색하기·활동 이력 필드별 정의표; 동일 이름·다른 성격 필드 시 피드백 요청 |
| **Definition items schema** | **docs/design/search-field-definition-items.md** | 필드 정의 항목(사이즈, 종류, 제한값, 데이터 소스 등) |
| Form layout, filter groups, panel width | **docs/design/forms-and-filters.md** | § Filter group title placement, § Search form panel width, § Compact variant, § Filter block tiers, § Form per mode (일별/월별) |

## Rules to apply

1. **User-context screens**: Provide **부서, 이름, 사용자ID** as the common base; add screen-specific axes (IP, action type, log type) as needed.
2. **scope=self**: Hide the whole user/department filter block; do not send userId/username/department/ip to API. Backend must override and return only current user.
3. **main (log search)**: No user 3 axes; keep date + log type + type-specific fields.
4. **Field-level design**: When adding or changing search/filter fields, follow **docs/design/search-fields-by-screen.md** and **docs/design/search-field-definition-items.md** for size, controlType, constraints, and data source. For **same field name on different screens** (e.g. startDate/endDate on main vs activity-log), do not unify or change without user direction — see search-fields-by-screen.md § "동일 이름·다른 성격 필드 — 피드백 요청".

## Related skills

- `activity-statistics-domain`: scope enforcement and filter visibility for statistics.
- `auth-permission-domain`: screenScopes, is_system_admin.
- `ui-ux-domain`: screen IDs, canAccessView.

## References

- Analysis doc: docs/analysis-search-consistency-by-screen.md
- Scope (self/team/all): specs/permission-group-hierarchy.spec.yaml §4.3
