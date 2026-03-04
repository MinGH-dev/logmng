---
name: department-approver-domain
description: >
  Department and decrypt approver: department hierarchy, decrypt_approver table,
  user-permission-hierarchy, canApproveForRequester. Use when user asks about
  department, approver designation, decrypt_approver, department tree, or
  user-permission-hierarchy. 부서, 결재자 지정, department, decrypt_approver 관련 질문 시 사용.
---

# Department & Approver Domain

**Skill usage visibility**: When you use this skill to answer, state at the start of your response: `[Skill used: department-approver-domain]`

Use for **department hierarchy and decrypt approver** in this repo. Scope: department tree, user-permission-hierarchy, decrypt_approver, canApproveForRequester.

## Quick reference

- **Department API**: `GET /api/departments` (tree/flat) — admin-only. 부서별 결재자·멤버·팀장 지정 API는 제거됨.
- **User-permission hierarchy**: `GET /api/departments/user-permission-hierarchy` — admin-only. 부서별 사용자·권한 그룹 계층.
- **decrypt_approver**: `user_id`, `department_code` (null=전역 결재자). 결재자 지정 API(POST/DELETE /api/users/approvers)는 410 Gone.
- **canApproveForRequester**: 전역 결재자(department_code null) → 전체; 부서별 결재자(팀장) → 요청자 소속 부서만 승인 가능. 승인 대기 목록은 이에 따라 팀장에게는 해당 팀원 요청만 노출 (req 20250304-team-scope-default).

## When to use

- Department tree, hierarchy, 부서 계층
- decrypt_approver, 결재자 지정, approver designation
- user-permission-hierarchy, 부서별 사용자·권한 그룹
- canApproveForRequester, 결재 범위

## Document references

| Question type | Document | Section |
|---------------|----------|---------|
| Department API | Path: `docs/api-definition.md` | `## 12. 부서 계층`, `## 14.9 사용자 권한 계층 조회` |
| decrypt_approver design | Path: `docs/requirements/20260224-decryption-approver-designation.md` | §1, §2 |
| Department approver hierarchy | Path: `docs/requirements/20260225-department-approver-hierarchy.md` | §1, §2 |
| Full list (전체 처리 이력) | Path: `docs/requirements/TOPIC-INDEX.md` | §department \| 부서 \| 결재자 |

## Code references

| Concern | Location |
|---------|----------|
| Department tree, user-permission-hierarchy | **backend/src/main/java/com/logmng/controller/DepartmentController.java** |
| canApproveForRequester, isApprover | **backend/src/main/java/com/logmng/service/DecryptApproverService.java** |
| Department service | backend/src/main/java/com/logmng/service/DepartmentService.java |
| User-permission hierarchy | backend/src/main/java/com/logmng/service/UserPermissionHierarchyService.java |
| decrypt_approver schema | backend/src/main/resources/db/schema.sql |
| Frontend hierarchy | frontend/src/components/UserManagement/UserManagement.js, UserPermissionHierarchy/UserPermissionHierarchy.js |

## Before answering

1. Department APIs: GET /api/departments, GET /api/departments/user-permission-hierarchy — admin-only. 부서별 결재자 CRUD API는 제거됨.
2. decrypt_approver: user_id + department_code (null=전역). 결재자 추가/해제 API는 410 Gone.
3. **Requirement traceability**: When explaining design, cite requirement doc (path + §section). Use core refs; do not load full doc.

## Related skills

- `search-history-decrypt-domain`: **Depends on this skill** — canApproveForRequester determines which pending requests an approver can approve/reject.
- `auth-permission-domain`: Permission model; decrypt_approver is one condition for approve gate.
- `api-permission-map`: API-level permission checks for approve-gated endpoints.
- `db-domain`: decrypt_approver table schema (`backend/src/main/resources/db/schema.sql`).

## References

- API: docs/api-definition.md §12, §14.9
- Improvement design: docs/SKILL-DOCUMENT-IMPROVEMENT-DESIGN.md
