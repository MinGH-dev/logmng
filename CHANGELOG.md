# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

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
