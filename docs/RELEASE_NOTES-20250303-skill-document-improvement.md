# 릴리즈 노트 — Agent Skill·문서 개선 (2025-03-03)

## 개요

Agent 응답 품질과 토큰 효율 개선을 위한 도메인 skill 도입 및 문서 구조화 로드맵을 추가했습니다.

## 변경 사항

### 1. Agent Skill·문서 개선 설계 문서

- **파일**: `docs/SKILL-DOCUMENT-IMPROVEMENT-DESIGN.md` (신규)
- **내용**:
  - Skill as router, 문서 단일 소스, progressive disclosure 원칙
  - Phase 1~4 구현 로드맵
  - auth-permission-domain skill 인벤토리 및 테스트 질문 세트
  - Phase 1 검증 가이드 (§7.4): baseline/post-skill 검증 절차(1.6–1.8) 단계별 안내

### 2. auth-permission-domain Skill

- **파일**: `.cursor/skills/auth-permission-domain/SKILL.md` (신규)
- **용도**: 권한·접근 제어·is_system_admin·permission group·화면 접근 관련 질문 시 사용
- **포함 내용**:
  - Quick reference (Admin-only vs Screen-based 접근 규칙)
  - Document references (contract.md, specs)
  - Code references (ScreenAccessInterceptor, UserController, AuthService 등)
  - Skill 사용 시 `[Skill used: auth-permission-domain]` 응답 선언 규칙

### 3. Skill 사용 가시성 규칙 적용

- **대상**: db-domain, dev-workflow, requirement-doc, test-workflow
- **변경**: 각 skill에 `[Skill used: <skill-name>]` 응답 선언 규칙 추가

### 4. 문서 참조 업데이트

- **docs/README.md**: Agent Skill·문서 개선 설계 문서 링크 추가

## 다음 단계 (Phase 1 검증)

설계 문서 §7.4에 따라 다음을 수행할 수 있습니다:

1. **1.6 Baseline**: skill 비활성화 상태에서 테스트 질문 5개 실행 후 §7.3 표에 기록
2. **1.7 Post-skill**: skill 활성화 후 동일 질문 실행 후 기록
3. **1.8 Token 비교**: Cursor Usage에서 토큰 사용량 비교 (선택)

## 참고

- 설계 문서: `docs/SKILL-DOCUMENT-IMPROVEMENT-DESIGN.md`
- CHANGELOG: `CHANGELOG.md` 2025-03-03 (Agent Skill & Document Improvement) 항목
