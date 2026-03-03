# 릴리즈 노트 — Agent Skill Phase 3 (2025-03-03)

## 개요

Phase 3 도메인 skill 4개(department-approver-domain, log-search-domain, activity-statistics-domain, ui-ux-domain)를 추가하고 설계 문서를 갱신했습니다.

## 변경 사항

### 1. department-approver-domain Skill

- **파일**: `.cursor/skills/department-approver-domain/SKILL.md` (신규)
- **용도**: 부서·결재자 지정·decrypt_approver·user-permission-hierarchy 관련 질문 시 사용
- **포함 내용**:
  - Quick reference: 부서 API, user-permission-hierarchy, decrypt_approver, canApproveForRequester
  - Document references: api-definition §12, §14.9, 20260224-decryption-approver-designation
  - Code references: DepartmentController, DecryptApproverService, DepartmentService

### 2. log-search-domain Skill

- **파일**: `.cursor/skills/log-search-domain/SKILL.md` (신규)
- **용도**: 로그 검색·logType·pb_feplog·imagelog 관련 질문 시 사용
- **포함 내용**:
  - Quick reference: pb_feplog, java_fw_imglog, search/advanced-search API
  - Document references: api-definition §4, §5, §6
  - Code references: LogTypeController, LogDbController, SearchSuggestController

### 3. activity-statistics-domain Skill

- **파일**: `.cursor/skills/activity-statistics-domain/SKILL.md` (신규)
- **용도**: 활동 이력·통계·scope(self/all) 관련 질문 시 사용
- **포함 내용**:
  - Quick reference: scope=self|all, applyScopeForStatistics, 통계 API
  - Document references: specs §4, 20250303-activity-statistics-self-only-scope
  - Code references: ActivityStatisticsController, ScopeHelper

### 4. ui-ux-domain Skill

- **파일**: `.cursor/skills/ui-ux-domain/SKILL.md` (신규)
- **용도**: 메뉴·화면·view·adminOnly·canAccessView 관련 질문 시 사용
- **포함 내용**:
  - Quick reference: Screen IDs, canAccessView, adminOnly, MENU_TREE
  - Document references: specs §4.1, contract.md
  - Code references: menuTree.js, App.js, AppSidebar

### 5. 설계 문서 갱신

- **SKILL-DOCUMENT-IMPROVEMENT-DESIGN.md**:
  - §3.2 Skill Inventory: 4개 skill Done
  - §5 Phase 3: 3.2 Deferred(문서 분리 보류), 3.3 Done(trigger overlap 검토), 3.4 Pending
  - §7.6 Phase 3 테스트 질문 템플릿 추가
  - §10 Appendix: 4개 skill 경로 추가

### 6. docs/README.md

- Skills 섹션에 Phase 3 skill 4개 설명 추가

## 참고

- 설계 문서: `docs/SKILL-DOCUMENT-IMPROVEMENT-DESIGN.md`
- CHANGELOG: `CHANGELOG.md` 2025-03-03 (Agent Skill Phase 3) 항목
