# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### 2026-03-17 (검색 이력·복호화·활동 통계 배치 동기화)

- **feat (search-history)**: 검색 이력 그리드 컬럼 순서·부서/사용자명·요청자 컬럼 및 모달, 요청 사유 검색 필드, 라벨/레이아웃·복호화 드롭다운 개선; 서버 오류·auth 500 버그 수정; 사용자 ID 쿼리 및 네이밍(user_id 일원화). (req 20260316-*, 20260317-search-history-*)
- **fix (decrypt)**: 복호화 승인·실행 전반 user_id 사용 통일; 타 사용자 승인 시 서버 오류 수정; 실행 시 user_id 버그 수정. (req 20260316-decrypt-approval-use-user-id-everywhere, 20260316-decrypt-approve-cross-user-server-error, 20260317-decrypt-execution-user-id-fix*)
- **fix (activity-statistics)**: 통계 조회 시 결재자/부서(scope=team) 조건에서 발생하던 오류 수정; user_id vs user_name 정렬. (req 20260317-activity-statistics-department-approver-error)
- **fix (self-scope)**: scope=self 사용자 블록 가시성 및 검색 조건 정합성. (req 20260316-self-scope-user-block-visible-and-search-conditions)

### 2026-03-13 (사용자/권한그룹 관리 서버 오류 복구)

- **fix (management/db)**: 런타임 PostgreSQL 스키마 드리프트로 `permission_group_screen.decrypt` 컬럼이 누락되어 `GET /api/permission-groups`가 `500`으로 실패하던 원인을 요구사항 문서에 정리하고, 기존 마이그레이션 `backend/src/main/resources/db/migrate-permission-group-screen-decrypt.sql` 적용 후 **User Management**와 **Permission Group Management** 화면이 다시 정상 로드됨을 검증 결과와 함께 반영. (req `20260313-user-management-permission-group-server-error`)

### 2026-03-13 (tooling workflow 영문화 일관성)

- **chore (workflow)**: `.cursor/rules/`, `.cursor/commands/`, `.cursor/skills/`, `.cursor/agents/`, `docs/workflow/`, `docs/template/`, `docs/cursor-subagents/`의 활성 도구 지향 문서를 English-only로 정리하고, 사용자 응답은 한국어로 유지하는 정책을 일관되게 맞춤. (req `20260313-english-only-tooling-workflow-consistency`)
- **docs**: `docs/requirements/20260313-english-only-tooling-workflow-consistency.md` 기준으로 언어 정책, 위임/핸드오프, Release 기반 최종 push 경로, 예외 분류 규칙을 문서 전반에서 정렬.
- **chore (workflow)**: 레거시 한글 오류 개선 프롬프트 예시를 `docs/workflow/error-fix-prompting-examples.md`로 교체하고, `node scripts/generate-treemap.js` 결과인 `docs/cursor-tools-treemap.html` 갱신을 포함해 추적 가능하도록 반영.

### 2026-03-10 (트리맵 Subagents 카테고리 항상 표시)

- **chore (treemap)**: 에이전트 상세 패널에서 "Subagents" (agents) 카테고리를 항상 표시 — 항목이 없을 때(items.length === 0)에도 "Subagents (0)" 및 힌트 ".cursor/agents/*.mdc 참조만 표시" 노출. `scripts/treemap-template.html` 수정, `docs/cursor-tools-treemap.html` 재생성 반영.

### 2026-03-09 (트리맵 cursor-subagents → Other Docs 복원)

- **chore (treemap)**: `docs/cursor-subagents/*.md` 분류 복원 — Agents/Subagents가 아닌 **Other Docs**에 표시. `scripts/generate-treemap.js`에서 cursor-subagents 경로·파일명을 agents 분류에서 제거하고 'other'로 복귀; refDisplayName용 cursor-subagents 분기 제거.
- **chore (treemap)**: `docs/cursor-tools-treemap.html` 재생성 — Subagents 카테고리는 `.cursor/agents/*.mdc` 참조만 표시, docs/cursor-subagents 참조는 Other Docs에 표시.

### 2026-03-09 (트리맵 Agents 카테고리·팀장 라벨)

- **chore (treemap)**: 에이전트 상세 패널에서 `.cursor/agents/*.mdc`, `docs/cursor-subagents/*.md` 참조를 "Other Docs" 대신 "Agents / Subagents" 카테고리로 표시 (generate-treemap.js: classifyRef, refDisplayName, scanAgents, buildAgentData, buildDocPaths, computeHubs; treemap-template.html: CATEGORY_CONFIG.agents, resolveFilepath, .agent-ref-item.agents).
- **chore (treemap)**: Step 4 플로우 칩에 팀장 라벨 표시 — "Backend (팀장)", "Frontend (팀장)" (generate-treemap.js: TEAM_LEAD_LABEL, buildFlowSteps displayName; treemap-template.html: agent chip displayName).
- **chore (treemap)**: `docs/cursor-tools-treemap.html` 재생성 반영.

### 2026-03-09 (Backend 팀 리드·핸드오프·트리맵)

- **chore (workflow)**: Backend 팀 리드 모델 — Main은 Backend만 호출; Backend가 Backend-Auth/ActivityLog/Log로 위임; Backend가 §2 집약 및 빌드/재시작 1회 수행; 위임 대상은 빌드/재시작 미실행.
- **docs**: `SUBAGENT-DELEGATION.md` — Backend team-lead 위임 규칙 반영. `docs/cursor-subagents/backend.md` — 팀 리드 역할, HANDOFF-CHECKLIST·CONSISTENCY-STANDARDS 참조, 단일 빌드/재시작.
- **docs**: `HANDOFF-CHECKLIST.md` — Backend 핸드오프에 CONSISTENCY-STANDARDS 항목 추가.
- **docs**: `backend-auth.md`, `backend-activity-log.md`, `backend-log.md` — CONSISTENCY-STANDARDS 참조; Backend에 위임 시 빌드/재시작 금지, 변경 파일 목록 반환.
- **docs**: `CURSOR-SUBAGENTS-DESIGN.md` §5.1 — 현재 모델(팀 리드가 공통 소유); 공통 계층 확대 시 Common 에이전트 추가 안내.
- **chore (dry-run)**: `docs/workflow/DRYRUN-backend-team-lead-handoff.md` — Backend 팀 리드 핸드오프 검증용 드라이런 보고서 추가.
- **chore (treemap)**: `scripts/treemap-i18n.json` — SUBAGENT-DELEGATION, HANDOFF-CHECKLIST, hubs 갱신; DRYRUN-backend-team-lead-handoff.md 추가. `docs/cursor-tools-treemap.html` 재생성 반영.
- **chore (agent)**: `.cursor/agents/Backend.mdc` — Backend 팀 리드 설명; SUBAGENT-DELEGATION, HANDOFF-CHECKLIST, CONSISTENCY-STANDARDS 참조.

### 2025-03-09 (문서–코드 동기화 정책, 핸드오프·트리맵)

- **docs**: 문서–코드 동기화 정책 (`docs/workflow/DOC-CODE-SYNC.md`) — 원칙 "documentation follows the code"; 일회성 정렬 단계·지속 동기화(Same-PR 규칙, 체크리스트, 단일 참조); 선택 검사.
- **chore (rules)**: `contract-first.mdc` — "Documentation follows the code" 반영; API/상수/에러 변경 시 동일 변경에서 문서 갱신; DOC-CODE-SYNC.md 참조.
- **chore (handoff)**: `docs/workflow/HANDOFF-CHECKLIST.md` — Backend·Frontend·Review 핸드오프에 "Doc–code sync" 항목 추가; Reference에 DOC-CODE-SYNC.md 명시.
- **chore (integration)**: `docs/workflow/CURSOR-AND-TOOLS-INTEGRATION.md` — 도구별 진입점 테이블에 "문서–코드 동기화" 행 추가; §3 contract-first에 DOC-CODE-SYNC 언급.
- **chore (dry-run)**: `docs/workflow/DRYRUN-handoff-verification-report.md` — 가상 요건(INVALID_DATE_RANGE)으로 Requirements→Backend→QA 핸드오프 시뮬레이션; 검증 테이블 전체 통과.
- **chore (treemap)**: `scripts/treemap-i18n.json` — DOC-CODE-SYNC.md, HANDOFF-CHECKLIST.md, CURSOR-AND-TOOLS-INTEGRATION.md, contract-first.mdc, DRYRUN-handoff-verification-report.md 워크플로·룰 항목 추가/갱신; `docs/cursor-tools-treemap.html` 재생성 반영.

### 2025-03-05 (approve-only permission group, requester-only actions)

- **feat (approve-only-permission-group)**: APPROVE_USER 패턴 — 팀장 전용 승인 권한 그룹 (로그 검색 불가, 복호화 승인만 가능). DB init-data에 APPROVE_USER 그룹 추가 (pending-approvals 화면만, approve=true); 프론트엔드 main 화면 미허용 사용자 → 첫 번째 허용 화면으로 리다이렉트 (`getFirstAllowedScreen()`, `canAccessView('main')` 가드); auth-permission-domain·department-approver-domain·search-history-decrypt-domain 스킬 반영. (req 20260304-approve-only-permission-group)
- **feat (search-history)**: 요청자 전용 동작 제한 — 재조회/재요청/자세히보기는 기록 소유자만 가능 (관리자·scope 우회 불가). api-permission-map 스킬에 Requester-only APIs 섹션 추가.
- **docs**: Interactive Cursor tools treemap (`docs/cursor-tools-treemap.html`) — Rules, Skills, Commands, Workflow Docs, Templates, Subagents의 시각적 맵 및 협업 플로우(Step 1-6)·문서 참조 관계 표시. README 링크 추가.
- **docs**: T7 SSD 마이그레이션 가이드 (`docs/setup/T7-MIGRATION-GUIDE.md`).

### 2025-03-04 (워크플로·서브에이전트 문서 정리)

- **feat (permission-group)**: 사용자당 단일 권한 그룹 (req 20250304-single-permission-group-per-user). 사용자–권한그룹 관계를 1:1로 변경; DB `app_user_permission_group.user_id` UNIQUE 제약, 백엔드 assign 시 기존 그룹 자동 교체, 프론트 UserGroupAssignment 배지 UI → 단일 드롭다운으로 전환. 스펙·auth-permission-domain·api-permission-map 스킬 반영.
- **chore (docs)**: 서브에이전트 위임·모델 선택·역할 경계 문서 및 용어 통일.
  - **SUBAGENT-DELEGATION.md**: §2.2 Task tool 매핑 테이블 추가; 문서 전반 "mcp_task" → "Task tool"로 용어 통일.
  - **SUBAGENT-MODEL-SELECTION.md**: 모델 파라미터 제약 정리 — Light/Default 티어 도입, Task tool 실제 제약(model enum `fast`만 지원) 반영; 사용자 보고는 자연어로 명시.
  - **CURSOR-SUBAGENTS-DESIGN.md** §2.6: `.cursor/skills/`, `.cursor/rules/`, `.cursor/agents/`, `specs/`, `docs/workflow/` 역할 경계 추가.
  - **AGENT-COLLABORATION-ON-REQUIREMENT.md**: §1.4 "Cursor infrastructure update" 추가(도메인 모델 변경 시 skills/specs 갱신); §1.1·§1.3 "mcp_task" → "Task tool".
  - **.cursor/rules/agent-collaboration.mdc**: 위임 게이트에서 "Task tool" 명시; 모델 보고 규칙 및 Cursor 인프라(스킬/스펙) 갱신 규칙 반영.
  - **.cursor/TERMINOLOGY.md**: "mcp_task" → "Task tool" 및 매핑 참조 반영.

### 2025-03-03 (Agent Skill Phase 3)

- **chore (docs)**: Phase 3 도메인 skill 4개 추가 및 설계 문서 갱신.
  - **department-approver-domain skill**: `.cursor/skills/department-approver-domain/SKILL.md` 신규. 부서·결재자 지정·decrypt_approver·user-permission-hierarchy 관련 질문 시 사용.
  - **log-search-domain skill**: `.cursor/skills/log-search-domain/SKILL.md` 신규. 로그 검색·logType·pb_feplog·imagelog 관련 질문 시 사용.
  - **activity-statistics-domain skill**: `.cursor/skills/activity-statistics-domain/SKILL.md` 신규. 활동 이력·통계·scope(self/all) 관련 질문 시 사용.
  - **ui-ux-domain skill**: `.cursor/skills/ui-ux-domain/SKILL.md` 신규. 메뉴·화면·view·adminOnly·canAccessView 관련 질문 시 사용.
  - **SKILL-DOCUMENT-IMPROVEMENT-DESIGN.md**: Phase 3 완료 반영. 3.2 문서 분리 보류, 3.3 trigger overlap 검토 완료, §7.6 Phase 3 테스트 템플릿 추가.
  - **docs/README.md**: Skills 섹션에 Phase 3 skill 4개 설명 추가.

### 2025-03-03 (auth-permission-domain 스킬 수정)

- **chore (docs)**: auth-permission-domain 스킬을 계약·스펙과 일치하도록 수정.
  - **접근 규칙 통일**: "Admin-only" vs "Screen-based" 이원화 제거. 모든 화면 API(user-management 포함)는 `is_system_admin` OR `allowedScreenIds`로 판단.
  - **단일 출처**: specs/permission-group-hierarchy.spec.yaml §4.3, docs/contract.md §화면 기반 접근 제어 기준.
  - **Before answering**: 권한 그룹으로 user-management 등 접근 가능함 명시; 403 시 allowedScreenIds·구현 버그 가능성 안내.
  - **docs/README.md**: auth-permission-domain 스킬 설명 추가.

### 2025-03-03 (Agent Skill Phase 2)

- **chore (docs)**: Phase 2 도메인 skill 추가 및 검증 완료.
  - **search-history-decrypt-domain skill**: `.cursor/skills/search-history-decrypt-domain/SKILL.md` 신규. 검색 이력·복호화·승인·반려·결재자, DECRYPTION_NOT_APPROVED, ROW_NOT_IN_APPROVED_SNAPSHOT 관련 질문 시 사용.
  - **error-codes-domain skill**: `.cursor/skills/error-codes-domain/SKILL.md` 신규. API 에러 코드(FORBIDDEN, DECRYPTION_NOT_APPROVED 등) 관련 질문 시 사용. api-definition §11 단일 소스 참조.
  - **Phase 2 검증**: §7.5 테스트 질문 5개 baseline/post-skill 검증 완료. 5/5 ✓.

### 2025-03-03 (Agent Skill & Document Improvement)

- **chore (docs)**: Agent Skill·문서 개선 설계 및 auth-permission-domain skill 도입.
  - **SKILL-DOCUMENT-IMPROVEMENT-DESIGN.md**: 도메인 skill 도입·문서 분할 점진적 로드맵 설계 문서 추가. Skill as router, 문서 단일 소스, progressive disclosure 원칙.
  - **auth-permission-domain skill**: `.cursor/skills/auth-permission-domain/SKILL.md` 신규. 권한·접근 제어·is_system_admin·permission group·화면 접근 관련 질문 시 사용. Quick reference, Document/Code references, Phase 1 테스트 질문 세트 포함.
  - **Skill usage visibility**: db-domain, dev-workflow, requirement-doc, test-workflow skill에 `[Skill used: <name>]` 응답 선언 규칙 추가.
  - **Phase 1 Validation Guide**: §7.4 추가 — baseline/post-skill 검증 절차(1.6–1.8) 단계별 안내.
  - **docs/README.md**: SKILL-DOCUMENT-IMPROVEMENT-DESIGN.md 링크 추가.

### 2026-03-03

- **chore (docs)**: RequirementsPastSearch 토큰 최적화 — 과거 요구사항 검색 시 토큰 사용량 70~90% 절감을 위한 구조 개선.
  - **TOPIC-INDEX.md**: `docs/requirements/TOPIC-INDEX.md` 신규 추가. 62개 요건을 11개 주제(permission, activity-log, sidebar, department 등)로 분류한 인덱스. RequirementsPastSearch가 전체 문서 검색 대신 인덱스 먼저 읽어 관련 doc ID만 추출.
  - **읽기 범위 제한**: past-requirements-search에 §1만 읽도록 규칙 추가 (offset=1, limit=90). §2·§3·§5는 요청 시에만.
  - **출력 제한**: 요약 300단어 이내, `DocID | one-line` 형식, 불릿만.
  - **2단계 호출**: "ids only" 시 doc ID 목록만 반환하는 모드 지원. Requirements가 필요 시 상세 요약만 추가 요청 가능.
  - **Requirements·AGENT-COLLABORATION 갱신**: RequirementsPastSearch 호출 시 topic 전달 및 TOPIC-INDEX 사용 안내.
  - **RequirementsPastSearch.mdc**: Token optimization 섹션, TOPIC-INDEX 참조 추가.
  - **generate-requirements-index.sh**: `scripts/generate-requirements-index.sh` 신규. TOPIC-INDEX에 없는 요건 doc 목록 출력. 새 요건 추가 후 인덱스 누락 확인용.
  - **REQUIREMENT_TEMPLATE**: 검증 완료 후 TOPIC-INDEX에 한 줄 추가하는 유지보수 안내.

### 2025-03-03

- **feat**: Permission group delete constraint and system administrator protection (req `docs/requirements/20250303-permission-group-delete-system-admin-protection.md`): (1) Permission group deletion allowed only when users=0; 400 `PERMISSION_GROUP_HAS_USERS` when users assigned. (2) System administrator (`is_system_admin`) immutable — role change and delete blocked; minimum one system admin enforced (`SYSTEM_ADMIN_IMMUTABLE`, `LAST_SYSTEM_ADMIN_BLOCKED`). (3) User management UI: system admin badge, disabled role dropdown, error message mappings. APIs: `GET /api/users` and hierarchy include `isSystemAdmin`; `PUT /api/users/{userId}` rejects system admin modification.

### 2026-02-27

- **feat**: User permission hierarchy and permission group management (req `20250227-user-permission-hierarchy-group`): single-screen hierarchy by department (code/parent_code), users with role and permission groups per node; permission group CRUD and user assign/remove; new admin menu items; sample data (departments, permission groups, user–group assignments). APIs: `GET /api/departments/user-permission-hierarchy`, `GET/POST/PUT/DELETE /api/permission-groups*`, user–group assign/remove; admin-only (403 for non-admin).
- **fix**: DB schema and init-data not applied (req `20250227-user-permission-hierarchy-group-bugfix-1`): `setup.sh` updated to use `DB_SUPERUSER` (default `postgres`); schema.sql and init-data.sql applied to target DB; TC-01–TC-09 pass after backend restart. Delivered on `feat/cursor-commit-on-complete`.
- **docs**: New workflow docs — `DB-AGENT-REVIEW.md` (DB agent role review, apply steps, schema handoff); `QA-BROWSER-TEST-TROUBLESHOOTING.md` (browser automation failures, snapshot/refs, mitigations); `SUBAGENT-MODEL-SELECTION.md` (model per subagent for token optimization, fast vs default).
- **chore**: `.cursor` and docs updates — agent-collaboration, docs-reference, verify.md, QA.mdc, db-prompt; CURSOR-SUBAGENTS-DESIGN, SUBAGENT-DELEGATION, AGENT-COLLABORATION-ON-REQUIREMENT, CURSOR-AND-TOOLS-INTEGRATION; cursor-subagents db.md, qa-test.md.
- **chore**: `SUBAGENT-MODEL-SELECTION.md` — use concrete model names (`claude-haiku-4.5`, `sonnet4.6`) instead of presets (fast/default); agent-collaboration and SUBAGENT-DELEGATION model parameter wording aligned.
- **chore**: Release — CHANGELOG and release checklist update; commit and push for current workflow/agent doc changes (agent-collaboration, SUBAGENT-MODEL-SELECTION).
- **chore**: mcp_task model parameter constraint — mcp_task accepts only `fast`; omit `model` when invoking; §2.1 model names (`claude-haiku-4.5`, `sonnet4.6`) are for user-facing report only. Updated: agent-collaboration, docs-reference, CURSOR-SUBAGENTS-DESIGN, SUBAGENT-DELEGATION, SUBAGENT-MODEL-SELECTION.

### 2026-02-26

- **UX (공통 사항)**: UX standards compliance audit and common verification — (1) UX standards compliance audit (req `20260225-ux-standards-compliance-audit`): alignment with `docs/design` (layout, grid-and-table, forms-and-filters, date-search, text-input, buttons). (2) Browser automation verification for frontend (TC-01~TC-08, §3.5): mandatory for frontend changes; policy in `docs/workflow/BROWSER-AUTOMATION-VERIFICATION-POLICY.md`. (3) Bugfix-1: date range validation `aria-invalid`/`aria-describedby` (req `20260225-ux-standards-compliance-audit-bugfix-1`). Delivered on `feat/cursor-commit-on-complete`.
- **docs**: Document language policy — tool-facing docs (docs/workflow, docs/template, docs/cursor-subagents) in English; requirements authored in English first, final Korean version after verification; commit message must reference requirement doc for traceability. New `docs/workflow/DOCUMENT-LANGUAGE-POLICY.md`; `.cursor/rules/language-policy.mdc`, `docs/template/REQUIREMENT_TEMPLATE.md`, `docs/template/BUGFIX_CHILD_TEMPLATE.md`, `.cursor/commands/commit-on-complete.md`, `.cursor/skills/requirement-doc/SKILL.md`, and workflow docs updated.
- **docs**: Browser Automation verification policy — frontend changes require browser verification; detailed report in §5; on failure create bugfix child and hand off to Frontend.
- **docs**: New policy doc `docs/workflow/BROWSER-AUTOMATION-VERIFICATION-POLICY.md` — analysis of why browser automation wasn’t used initially; policy: mandatory browser verification for frontend, §3.5 when frontend-heavy, detailed report, handoff on failure.
- **chore**: `.cursor/commands/verify.md` — step 3.5 required for frontend scope; detailed report in §5; on failure create bugfix child and hand off.
- **chore**: `.cursor/agents/QA.mdc` — browser verification required for frontend; detailed report; on failure bugfix child + hand off to Frontend.
- **chore**: `docs/workflow/SUBAGENT-DELEGATION.md` — Step 5 QA row: mandatory browser automation for frontend, detailed report, handoff on failure.
- **chore**: `docs/workflow/BROWSER-AUTOMATION-MCP.md` — policy link; QA row "must" + report format; §4 verification report format; §2.4 handoff on failure.
- **chore**: `docs/cursor-subagents/qa-test.md` — same policy; reference to BROWSER-AUTOMATION-VERIFICATION-POLICY.
- **chore**: `docs/template/REQUIREMENT_TEMPLATE.md` — added §3.5 브라우저 자동화 검증 (optional for frontend-heavy requirements).
- **chore**: `docs/workflow/CURSOR-AND-TOOLS-INTEGRATION.md` — added BROWSER-AUTOMATION-VERIFICATION-POLICY to browser row.

### 2026-02-25

- **docs**: design standards in `docs/design/` — grid-and-table, layout-and-navigation, buttons, text-input, date-search, forms-and-filters, README; approval flow for design system.
- **chore**: UX agent (`.cursor/agents/UX.mdc`) — design system owner; approval when changes are outside or conflict with standards.
- **chore**: `docs/cursor-subagents/ux-design.md` — index to design docs; no screen-specific reference.

### 2025-02-24

- **feat**: search-history — re-query form sync and detail view modal
- **feat**: search-history — 검색 이력 및 복호화 승인 재요청 부가 기능 (요건 20260224)
- **feat**: cursor — add commit-on-complete workflow step (verify 후 커밋)
- **chore**: release checklist with remote push step (`docs/workflow/RELEASE_CHECKLIST.md`)
