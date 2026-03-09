# Cursor and tools / docs integration map

Rules, commands, skills, agents, docs, and scripts — **how they connect per workflow step**. Use when integrating with other tools (CLI, IDE, CI). **Language**: All tool-facing docs in English per `docs/workflow/DOCUMENT-LANGUAGE-POLICY.md`.

**Terminology (명칭·구분)**: Definitions and naming conventions for Rule vs Command vs Skill vs Agent are in **`.cursor/TERMINOLOGY.md`**. Use that doc to avoid confusion when adding or referring to .cursor tools.

---

## 1. Workflow phase mapping

| Phase | Rules (.cursor/rules) | Commands (.cursor/commands) | Skills (.cursor/skills) | Docs (docs/) | Scripts / run |
|-------|------------------------|-----------------------------|-------------------------|--------------|---------------|
| **Requirement / plan** | docs-reference, error-first-workflow, workflow-todos, agent-collaboration | plan.md, new-requirement.md, follow-workflow.md | dev-workflow, requirement-doc | WORKFLOW_CHECKLIST.md, DEVELOPMENT_WORKFLOW.md, AGENT-COLLABORATION-ON-REQUIREMENT.md, REQUIREMENT_TEMPLATE.md, DOCUMENT-LANGUAGE-POLICY.md, requirements/ | — |
| **Development (code)** | contract-first, backend-agent, frontend-agent, db-agent, (module *-Auth, *-ActivityLog, *-Log), agent-collaboration | agent-*.md | dev-workflow | contract.md, specs/, CURSOR-SUBAGENTS-DESIGN.md, SUBAGENT-MODEL-SELECTION.md | — |
| **Test** | post-change-test-verify, docs-reference | run-tests.md | dev-workflow | DEVELOPMENT_WORKFLOW.md, requirement doc §3·§5 | `cd backend && mvn test`, `cd frontend && npm test -- --watchAll=false` |
| **Verification (restart / health)** | post-change-test-verify | verify.md, check-backend.md, check-frontend.md, check-frontend-backend.md, check-db.md, restart-*.md | — | verify.md procedure, BUGFIX_CHILD_TEMPLATE.md | `./scripts/dev-services.sh {frontend\|backend\|db\|all} restart` |
| **Service start/stop** | — | start-*.md, stop-*.md, restart-*.md | — | QUICK_START.md | `./scripts/dev-services.sh <target> <start\|stop\|restart>` |
| **PR / review** | core-principles | review.md | — | WORKFLOW_CHECKLIST.md, DEVELOPMENT_WORKFLOW.md | — |
| **Error fix recording** | error-first-workflow, docs-reference | fix.md, record-error-fix.md | requirement-doc | ERROR_FIX_RESULT_TEMPLATE.md, requirement doc §6 | — |

---

## 2. 도구별 진입점

| 사용 목적 | 진입점 (Cursor) | 진입점 (문서) | 진입점 (스크립트) |
|-----------|-----------------|---------------|-------------------|
| 워크플로우 순서 확인 | rules: docs-reference.mdc, skills: dev-workflow | docs/workflow/WORKFLOW_CHECKLIST.md | — |
| 요건 문서 작성 | commands: plan.md, new-requirement.md / skills: requirement-doc | docs/template/REQUIREMENT_TEMPLATE.md, docs/requirements/ | — |
| API·DB 계약 확인 | rules: contract-first.mdc | docs/contract.md, docs/api-definition.md, specs/ | — |
| 문서–코드 동기화 (doc follows code) | rules: contract-first.mdc | docs/workflow/DOC-CODE-SYNC.md | — |
| 어떤 Subagent 쓸지 | agents/*.mdc, commands: agent-*.md | docs/workflow/CURSOR-SUBAGENTS-DESIGN.md (§1, §1.1, §1.2, §1.3) | — |
| Subagent별 모델 지정 | rules: agent-collaboration.mdc | docs/workflow/SUBAGENT-MODEL-SELECTION.md (§2.1 override, §5 visibility) | — |
| 요구사항 시 에이전트 협업 순서·인계·역할 중복 방지 | rules: agent-collaboration.mdc | new-requirement.md | docs/workflow/AGENT-COLLABORATION-ON-REQUIREMENT.md, CURSOR-SUBAGENTS-DESIGN.md §2.6 | — |
| 표준(일관성) 문서 | Consistency 에이전트 | — | docs/workflow/CONSISTENCY-STANDARDS.md |
| 코드/변경 검토(체크리스트 적용) | Review 에이전트, commands: review.md | — | .cursor/commands/review.md, CONSISTENCY-STANDARDS.md |
| 단위 테스트 실행 | commands: run-tests.md, rules: post-change-test-verify.mdc | 요건 문서 §3·§5 | `mvn test`, `npm test -- --watchAll=false` |
| 재시작·헬스 확인 | commands: verify.md, check-*.md | .cursor/commands/verify.md | scripts/dev-services.sh |
| 브라우저 자동화 (QA/UX/Frontend) | agents: QA.mdc, Frontend.mdc, UX.mdc | docs/workflow/BROWSER-AUTOMATION-MCP.md, docs/workflow/BROWSER-AUTOMATION-VERIFICATION-POLICY.md, .cursor/commands/verify.md §3.5 | — (MCP: .cursor/mcp.json) |
| 서비스 시작/중지 | commands: start-*.md, stop-*.md, restart-*.md | docs/QUICK_START.md | scripts/dev-services.sh |

---

## 3. 규칙 ↔ 커맨드 ↔ 문서 상호 참조

- **규칙에서 참조하는 문서·커맨드**  
  - docs-reference.mdc → WORKFLOW_CHECKLIST, DEVELOPMENT_WORKFLOW, contract.md, template, requirements, verify.md, run-tests, post-change-test-verify.  
  - **workflow-todos.mdc** → TodoWrite 시 todo 순서를 WORKFLOW_CHECKLIST와 동일하게 강제(요건+§3 → 개발 → 테스트 → 검증 → 문서화).  
  - post-change-test-verify.mdc → verify.md, run-tests.md, restart-*.md, check-*.md, BUGFIX_CHILD_TEMPLATE.  
  - contract-first.mdc → contract.md, api-definition.md, specs/, schema.sql; doc–code sync: **DOC-CODE-SYNC.md**.  
  - error-first-workflow.mdc → WORKFLOW_CHECKLIST, REQUIREMENT_TEMPLATE.  
  - core-principles.mdc → post-change-test-verify, verify.md, error-first-workflow, contract-first, security-permissions.

- **커맨드에서 참조하는 규칙·문서**  
  - verify.md → run-tests(선행), WORKFLOW, BUGFIX_CHILD_TEMPLATE, dev-services.sh.  
  - run-tests.md → docs-reference, DEVELOPMENT_WORKFLOW, 요건 §3·§5.  
  - plan.md, fix.md, review.md → WORKFLOW_CHECKLIST, DEVELOPMENT_WORKFLOW, verify.md.

- **에이전트에서 참조하는 문서**  
  - 모든 Backend/Frontend/DB/Contract/QA/Requirements/Security → contract.md, DEVELOPMENT_WORKFLOW.md, REQUIREMENT_TEMPLATE.md.  
  - 모듈별(Auth, ActivityLog, Log) → 각 프롬프트 파일: docs/cursor-subagents/*.md, CURSOR-SUBAGENTS-DESIGN.md §1.1·§1.2.  
  - Subagent별 모델 지정·토큰 최적화: SUBAGENT-MODEL-SELECTION.md (§2.1, §5).

---

## 4. 스크립트와의 일치

- **재시작/시작/중지**: `./scripts/dev-services.sh <frontend|backend|db|all> <start|stop|restart>`  
  - 커맨드 verify.md, start-*.md, stop-*.md, restart-*.md에서 동일한 사용법으로 안내.  
- **포트**: frontend 3001, backend 9200, DB 5432 — `docs/contract.md` 및 dev-services.sh 기본값과 동일.  
- **헬스 확인**: check-backend.md, check-frontend.md, check-db.md, verify.md의 curl 명령과 실제 서비스 포트 일치.

---

## 5. 다른 도구와 융합 시 체크

- **CI**: 테스트 단계는 `run-tests.md` 절차 및 `mvn test` / `npm test -- --watchAll=false`와 동일하게 구성.  
- **IDE/에디터**: 규칙·커맨드 경로는 `.cursor/rules/`, `.cursor/commands/` 기준. 문서 경로는 `docs/` 기준.  
- **외부 스크립트**: 서비스 제어는 `scripts/dev-services.sh`만 사용하면 Cursor 커맨드와 동일 동작.  
- **Subagent 목록**: Cursor Settings에 등록할 이름·프롬프트 파일은 `docs/workflow/CURSOR-SUBAGENTS-DESIGN.md` §1·§1.1·§1.2와 `.cursor/agents/README.md`에 정리됨.

이 문서는 **단일 참조점**으로, .cursor와 docs·scripts가 서로와 다른 도구와 어떻게 맞물리는지 파악할 때 사용한다.
