---
name: activity-statistics-domain
description: >
  Activity statistics: scope (self/all), daily/monthly stats, user/department/IP
  filters, CSV export. Use when user asks about activity statistics, scope,
  self vs all, or statistics API. 활동 이력, 통계, scope, self, all 관련 질문 시 사용.
---

# Activity Statistics Domain

**Skill usage visibility**: When you use this skill to answer, state at the start of your response: `[Skill used: activity-statistics-domain]`

Use for **activity statistics and scope enforcement** in this repo. Scope: /api/statistics/*, scope=self|all, filter override.

## Quick reference

- **Scope**: is_system_admin=false일 때 권한 그룹의 statistics scope 적용. scope='self' → userId/department/ip 무시, 현재 사용자 데이터만; scope='all' → 파라미터 그대로.
- **APIs**: GET /api/statistics/activity/daily, /monthly, /users, /activity/export. GET /api/statistics/users, /departments, /ips (필터 옵션).
- **applyScopeForStatistics**: scope='self' 시 userId→currentUser, department/ip→null로 override.

## When to use

- Activity statistics, 활동 로그 통계
- scope=self, scope=all
- Statistics API, CSV export
- User/department/IP filter visibility

## Document references

| Question type | Document | Section |
|---------------|----------|---------|
| Statistics API | Path: `docs/api-definition.md` | (activity-log §8, statistics는 contract/spec 참조) |
| Scope (self/all) | Path: `specs/permission-group-hierarchy.spec.yaml` | `# 4. Screen IDs`, §4.2, §4.3 |
| Scope requirement | Path: `docs/requirements/20250303-activity-statistics-self-only-scope.md` | §1, §2 |
| Full list (전체 처리 이력) | Path: `docs/requirements/TOPIC-INDEX.md` | §activity-log \| statistics |

## Code references

| Concern | Location |
|---------|----------|
| Statistics API, applyScopeForStatistics | **backend/src/main/java/com/logmng/controller/ActivityStatisticsController.java** |
| Scope resolution | backend/src/main/java/com/logmng/util/ScopeHelper.java |
| Activity statistics service | backend/src/main/java/com/logmng/service/ActivityStatisticsService.java |
| Frontend statistics | frontend/src/components/ActivityStatistics/ActivityStatistics.js |

## Before answering

1. scope='self': userId, department, ip 파라미터 무시; 현재 사용자 데이터만. 필터 UI 숨김.
2. scope='all': 파라미터 그대로 전달. 필터 UI 표시.
3. is_system_admin=true: 항상 전체; scope 무시.
4. **Requirement traceability**: When explaining design, cite requirement doc (path + §section).

## Related skills

- `auth-permission-domain`: **Dependency** — is_system_admin bypass and scope resolution depend on permission model.
- `api-permission-map`: Screen-access-only and scope-enforced API classification for statistics endpoints.

## References

- Spec: specs/permission-group-hierarchy.spec.yaml §4
- Improvement design: docs/SKILL-DOCUMENT-IMPROVEMENT-DESIGN.md
