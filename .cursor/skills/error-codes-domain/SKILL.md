---
name: error-codes-domain
description: >
  Error codes: FORBIDDEN, DECRYPTION_NOT_APPROVED, ROW_NOT_IN_APPROVED_SNAPSHOT,
  PERMISSION_GROUP_NOT_FOUND, SYSTEM_ADMIN_IMMUTABLE, INVALID_INPUT. Use when user asks about
  API error codes, 403/400/404 codes, or error code meaning. 에러 코드, 오류 코드 관련 질문 시 사용.
---

# Error Codes Domain

**Skill usage visibility**: When you use this skill to answer, state at the start of your response: `[Skill used: error-codes-domain]`

Use for **API error codes** in this repo. Single source of truth: `docs/api-definition.md` §11.

## Quick reference

- **Error code location**: `docs/api-definition.md` §11 (에러 코드 요약). 새 코드 추가 시 CONSISTENCY-STANDARDS: api-definition §11에 등록.
- **Common 403**: FORBIDDEN (권한 없음), DECRYPTION_NOT_APPROVED (복호화 미승인), ROW_NOT_IN_APPROVED_SNAPSHOT (스냅샷 미포함), FORBIDDEN_NOT_APPROVER (결재자 아님).
- **Common 400**: INVALID_INPUT, PERMISSION_GROUP_HAS_USERS, SYSTEM_ADMIN_IMMUTABLE, LAST_SYSTEM_ADMIN_BLOCKED, INVALID_SCREEN_ID.

## When to use

- API error code meaning
- 403, 400, 404 code lookup
- 에러 코드, 오류 코드

## Document references

| Question type | Document | Section |
|---------------|----------|---------|
| Error code list (authoritative) | Path: `docs/api-definition.md` | `## 11. 에러 코드 요약` |
| Decrypt-specific codes | Path: `docs/api-definition.md` | `## 10. 복호화` (DECRYPTION_NOT_APPROVED, ROW_NOT_IN_APPROVED_SNAPSHOT) |
| New code registration | Path: `docs/workflow/CONSISTENCY-STANDARDS.md` | 에러 코드 등록 규칙 |

## Code references

| Concern | Location |
|---------|----------|
| CustomException, error code usage | **backend/src/main/java/com/logmng/exception/CustomException.java** |
| Frontend error code handling | **frontend/src/utils/security.js** (DECRYPTION_NOT_APPROVED_MESSAGE 등) |

## Before answering

1. Always refer to api-definition.md §11 for the authoritative list.
2. Decrypt codes: DECRYPTION_NOT_APPROVED, ROW_NOT_IN_APPROVED_SNAPSHOT — see api-definition §10.
3. Permission codes: FORBIDDEN, PERMISSION_GROUP_*, SYSTEM_ADMIN_* — see api-definition §11, §14.

## References

- API definition: docs/api-definition.md §10, §11
- Consistency: docs/workflow/CONSISTENCY-STANDARDS.md
- Improvement design: docs/SKILL-DOCUMENT-IMPROVEMENT-DESIGN.md
