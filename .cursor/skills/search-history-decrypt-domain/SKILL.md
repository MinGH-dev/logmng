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
- **결재자 vs 관리자**: 결재자 = decrypt_approver 테이블 등록; 관리자 = is_system_admin. 둘 다 승인/반려 가능. 관리자는 전체 PENDING 접근; 결재자는 canApproveForRequester(요청자 소속 부서)인 건만.

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

## Before answering

1. DECRYPTION_NOT_APPROVED: searchHistoryId·본인 소유·APPROVED·미만료 검사 실패. '복호화 승인 요청' 후 결재자/관리자 승인 필요.
2. ROW_NOT_IN_APPROVED_SNAPSHOT: 승인 시점 검색 결과(스냅샷)에 포함된 row만 복호화 가능. 스냅샷에 없는 guid → 403.
3. 결재자 vs 관리자: decrypt_approver(부서별/전역) 또는 is_system_admin. 둘 다 승인/반려 가능.

## References

- API: docs/api-definition.md §6.1, §10, §11
- Snapshot: docs/requirements/20260224-decryption-snapshot-final-design-en.md
- Improvement design: docs/SKILL-DOCUMENT-IMPROVEMENT-DESIGN.md
