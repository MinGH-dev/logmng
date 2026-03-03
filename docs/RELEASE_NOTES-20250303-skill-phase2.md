# 릴리즈 노트 — Agent Skill Phase 2 (2025-03-03)

## 개요

Phase 2 도메인 skill(search-history-decrypt-domain, error-codes-domain)을 추가하고 검증을 완료했습니다.

## 변경 사항

### 1. search-history-decrypt-domain Skill

- **파일**: `.cursor/skills/search-history-decrypt-domain/SKILL.md` (신규)
- **용도**: 검색 이력·복호화·승인·반려·결재자 관련 질문 시 사용
- **포함 내용**:
  - Quick reference: PENDING→APPROVED 흐름, DECRYPTION_NOT_APPROVED, ROW_NOT_IN_APPROVED_SNAPSHOT, 결재자 vs 관리자
  - Document references: api-definition §6.1, §10, §11, 20260224-decryption-snapshot-final-design-en
  - Code references: SearchHistoryService, SearchHistoryController, DecryptController, DecryptApproverService

### 2. error-codes-domain Skill

- **파일**: `.cursor/skills/error-codes-domain/SKILL.md` (신규)
- **용도**: API 에러 코드(FORBIDDEN, DECRYPTION_NOT_APPROVED 등) 관련 질문 시 사용
- **포함 내용**:
  - Quick reference: api-definition §11 단일 소스, 403/400 코드 목록
  - Document references: api-definition §10, §11, CONSISTENCY-STANDARDS

### 3. Phase 2 검증 결과

- **테스트 질문**: 5개 (DECRYPTION_NOT_APPROVED, ROW_NOT_IN_APPROVED_SNAPSHOT, 결재자 vs 관리자, FORBIDDEN, api-definition 에러 코드 위치)
- **결과**: baseline 5/5 ✓, post-skill 5/5 ✓
- **기록**: docs/SKILL-DOCUMENT-IMPROVEMENT-DESIGN.md §7.5

## 참고

- 설계 문서: `docs/SKILL-DOCUMENT-IMPROVEMENT-DESIGN.md`
- CHANGELOG: `CHANGELOG.md` 2025-03-03 (Agent Skill Phase 2) 항목
