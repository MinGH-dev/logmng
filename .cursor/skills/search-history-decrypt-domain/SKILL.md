---
name: search-history-decrypt-domain
description: >
  Search history and decryption approval: PENDING, APPROVED, REJECTED, DECRYPTION_NOT_APPROVED,
  ROW_NOT_IN_APPROVED_SNAPSHOT, decrypt_approver, 결재자, 승인, 반려. Use when user asks about
  search history, decryption approval flow, pending approvals, DECRYPTION_NOT_APPROVED,
  ROW_NOT_IN_APPROVED_SNAPSHOT, or approver vs admin (decrypt_approver vs is_system_admin).
  검색 이력, 복호화, 승인, 반려, 결재자 관련 질문 시 사용.
---

# Search History & Decryption Domain

**Skill usage visibility**: When you use this skill to answer, state at the start of your response: `[Skill used: search-history-decrypt-domain]`

Use for **search history, decryption approval, and approver** in this repo. Scope: PENDING/APPROVED/REJECTED flow, snapshot check, decrypt_approver vs is_system_admin.

## Quick reference

- **Search history flow**: POST search-history → PENDING → 결재자/관리자 승인 → APPROVED → 복호화 가능. 만료 시 재요청.
- **DECRYPTION_NOT_APPROVED**: searchHistoryId 없거나, 해당 검색 이력이 본인 소유·APPROVED·미만료가 아님. 복호화 전 '복호화 승인 요청' 필요.
- **ROW_NOT_IN_APPROVED_SNAPSHOT**: 승인 시점 스냅샷에 포함된 row만 복호화 가능. 스냅샷에 없는 guid로 요청 시 403.
- **결재자 vs 관리자**: 결재자 = decrypt_approver 테이블 등록; 관리자 = is_system_admin. 둘 다 승인/반려 가능. 관리자는 전체 PENDING 접근; 결재자(팀장, 부서별 결재자)는 canApproveForRequester(요청자 소속 부서)인 건만 — 승인 대기창에는 해당 팀원의 승인요청만 노출 (req 20250304-team-scope-default).
- **승인 전용 권한 그룹 (approval-only)**: `allowedScreenIds`에 `main` 없음 + `pending-approvals` 있음인 **모든** 권한 그룹(예: APPROVE_USER, TEAM_APPROVER 등 — 이름 무관)에 동일 규칙 적용. 팀장은 로그 조회 불가 규칙 → 승인 전용 그룹 + `decrypt_approver` 등록. 로그 검색(`main`) 없이 승인/반려만 가능. `searchParamsSummary`(시작일, 종료일, logType)로 승인 시 검색 조건 파악 가능. 상세: `auth-permission-domain` SKILL §Approval-only permission group.
- **검색이력 화면 동작(액션) 제한 — 요청자 전용 (예외 없음)**: `search-history` 화면의 **동작** 컬럼(재조회, 재요청, 자세히 보기)은 **해당 검색 이력을 요청한 사용자(requester)만** 사용 가능. **admin·시스템 관리자도 예외 없이** 적용 — 다른 사용자의 이력에 대해서는 동작 버튼을 사용할 수 없음. 목록/scope는 기존대로(self/team/all) 유지하되, 동작 수행은 `row.user_id === currentUserId`인 경우에만 허용. Backend: GET `/api/search-history/{id}`, POST `/api/search-history/{id}/re-request` 는 **requester만** 허용(scopeAll/team bypass 제거). Frontend: 목록에서 `row.userId === user.username` 인 row에만 재조회/재요청/자세히 보기 노출. (이전: 재조회/재요청은 `main` 접근 권한자에게만 노출되었으나, 개선 후에는 **요청자 본인**만 사용.)

## When to use

- Search history, decryption approval, PENDING, APPROVED, REJECTED
- DECRYPTION_NOT_APPROVED, ROW_NOT_IN_APPROVED_SNAPSHOT
- 결재자, decrypt_approver, 승인/반려 권한
- 검색 이력, 복호화 승인 흐름

## Document references

| Question type | Document | Section |
|---------------|----------|---------|
| Search history API, approval flow | Path: `docs/api-definition.md` | `## 6.1 검색 이력 (Search History)` (§6.1.1–§6.1.7) |
| Decrypt API, error codes | Path: `docs/api-definition.md` | `## 10. 복호화 (단일 로우)`, `## 11. 에러 코드 요약` |
| Snapshot design, ROW_NOT_IN_APPROVED_SNAPSHOT | Path: `docs/requirements/20260224-decryption-snapshot-final-design-en.md` | §6.1 Decrypt flow, §6.2 DB |
| Approval snapshot guide | Path: `docs/requirements/20260224-decryption-approval-snapshot-guide.md` | §4.2 복호화 시 스냅샷 검사 |
| DECRYPTION_NOT_APPROVED | Path: `docs/requirements/20260224-decryption-require-approval.md` | §1, §2 |

## Code references

| Concern | Location |
|---------|----------|
| Search history CRUD, approve, reject | **backend/src/main/java/com/logmng/service/SearchHistoryService.java** |
| Pending list, approve/reject API | **backend/src/main/java/com/logmng/controller/SearchHistoryController.java** |
| Decrypt API, snapshot check | **backend/src/main/java/com/logmng/controller/DecryptController.java** |
| isApprover, canApproveForRequester | **backend/src/main/java/com/logmng/service/DecryptApproverService.java** |
| Frontend decrypt error handling | **frontend/src/components/ImageLogTable.js**, **frontend/src/utils/security.js** |

## Requirement doc completeness checklist (decryption/approval)

When writing a **requirement document** that involves decryption approval, search history, or snapshot verification, apply this checklist before finalizing §3:

- [ ] **DECRYPTION_NOT_APPROVED TC**: TC where user requests decryption without approved search history → 403 DECRYPTION_NOT_APPROVED.
- [ ] **ROW_NOT_IN_APPROVED_SNAPSHOT TC**: TC where user requests decryption for a row not in the approved snapshot → 403 ROW_NOT_IN_APPROVED_SNAPSHOT.
- [ ] **Approver vs admin TC**: TC distinguishing decrypt_approver (department-scoped) from is_system_admin (global). canApproveForRequester for non-global approver.
- [ ] **Approval flow TC**: PENDING → approve → APPROVED → decrypt succeeds; PENDING → reject → REJECTED → decrypt fails.
- [ ] **Snapshot expiry/re-request**: If applicable, TC for expired approval and re-request flow.
- [ ] **§5 curl commands**: Login as requester, approver, admin; per-TC curl for search-history and decrypt APIs.

## Before answering

1. DECRYPTION_NOT_APPROVED: searchHistoryId·본인 소유·APPROVED·미만료 검사 실패. '복호화 승인 요청' 후 결재자/관리자 승인 필요.
2. ROW_NOT_IN_APPROVED_SNAPSHOT: 승인 시점 검색 결과(스냅샷)에 포함된 row만 복호화 가능. 스냅샷에 없는 guid → 403.
3. 결재자 vs 관리자: decrypt_approver(부서별/전역) 또는 is_system_admin. 둘 다 승인/반려 가능.

## Related skills

- `department-approver-domain`: **Dependency** — canApproveForRequester (approver scope: global vs department) determines who can approve/reject.
- `auth-permission-domain`: is_system_admin bypass; screenFunctions.approve derivation.
- `api-permission-map`: Approve-gated API enforcement (requireApproverOrAdmin).
- `error-codes-domain`: DECRYPTION_NOT_APPROVED, ROW_NOT_IN_APPROVED_SNAPSHOT codes.

## References

- API: docs/api-definition.md §6.1, §10, §11
- Snapshot: docs/requirements/20260224-decryption-snapshot-final-design-en.md
- Improvement design: docs/SKILL-DOCUMENT-IMPROVEMENT-DESIGN.md
