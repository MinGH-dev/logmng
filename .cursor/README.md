# .cursor — dev workspace only

- **Scope**: Applied only when Cursor has **this folder (dev)** as the workspace root.
- **rules**, **commands**, **skills**, **agents** are **dev-project only**; not used globally or in other projects.

## Do not apply globally

- Do not copy or symlink this into **`~/.cursor/`**. For other workspaces, copy only what you need into that project's `.cursor/`.

## Layout

| Dir | Purpose |
|-----|--------|
| **rules/** | Always or conditional rules: docs reference, contract-first, error-first, test/verify, core principles, file-reading optimization, security, language policy, **agent-collaboration** (요구사항 시 에이전트 협업 순서) |
| **commands/** | Slash commands: verify, run-tests, check-*, plan, fix, review, new-requirement, record-error-fix, start/stop/restart, agent-* |
| **skills/** | dev-workflow, requirement-doc |
| **agents/** | Subagent role definitions. **Core**: Backend, Frontend, DB, Contract, QA, Requirements, Security, DBA, Architecture. **추가**: Review, Documentation, Release, Consistency, UX. **Optional by module**: Backend-Auth, Backend-ActivityLog, Backend-Log; Frontend-Auth, Frontend-ActivityLog, Frontend-Log. Prompts: `docs/cursor-subagents/*.md` |

## Principles (summary)

- **Response**: User-facing replies in the **user's requested language** (e.g. Korean). Instructions (rules, commands, skills, agents) are in **English** for performance and consistency. See `language-policy.mdc`.
- **Workflow**: Requirement doc + §3 test plan → development → tests and verification. Same for error fixes (requirement + §3 first). **Agent collaboration**: When a requirement or error-fix is requested, follow `docs/workflow/AGENT-COLLABORATION-ON-REQUIREMENT.md` so Requirements, Security, Contract, Backend/Frontend/DB, QA (and DBA/Architecture when needed) collaborate in order.
- **Contract**: API and DB follow `docs/contract.md` and specs.
- **Tools**: Prefer Read, Grep, StrReplace, Glob; use parallel calls when independent.
- **Security**: No secrets in repo; caution with .env and dangerous Bash. See `security-permissions.mdc`, `core-principles.mdc`.

**Instruction and performance**: `docs/workflow/AGENT-INSTRUCTION-AND-PERFORMANCE.md` (language policy, alwaysApply, length, duplication, etc.)

## Rules

- **Always**: `docs-reference.mdc`, `contract-first.mdc`, `post-change-test-verify.mdc`, `error-first-workflow.mdc`, `core-principles.mdc`, `security-permissions.mdc`, `language-policy.mdc`, `workflow-todos.mdc`, `agent-collaboration.mdc`
- **Conditional / reference**: `frontend-agent.mdc`, `backend-agent.mdc`, `db-agent.mdc`, `subagent-invoke.mdc`, `file-reading-optimization.mdc`

## Commands

- **Verify & test**: verify.md, run-tests.md, check-backend.md, check-frontend.md, check-frontend-backend.md, check-db.md
- **Workflow**: plan.md, fix.md, review.md, follow-workflow.md, new-requirement.md, record-error-fix.md
- **Services**: start-all.md, start-frontend.md, start-backend.md, start-db.md, stop-*.md, restart-*.md
- **Subagent**: agent-frontend/backend/db; **run-*-agent commands are unused** — use Cursor Settings → Subagents.

## Subagents

- Create **Frontend / Backend / DB** (and optionally **Requirements, QA, Contract, Security, DBA, Architecture, Review, Documentation, Release, Consistency, UX**) under **Cursor Settings → Subagents** and paste prompts from **docs/cursor-subagents/** (frontend.md, backend.md, db.md, review.md, documentation.md, release.md, consistency.md, ux-design.md, etc.).
- **역할 중복 방지**: 동일 영역은 한 에이전트만 담당. 표: **docs/workflow/CURSOR-SUBAGENTS-DESIGN.md** §2.6.
- **Optional module subagents**: Backend-Auth, Backend-ActivityLog, Backend-Log; Frontend-Auth, Frontend-ActivityLog, Frontend-Log. See **docs/workflow/CURSOR-SUBAGENTS-DESIGN.md** §1.1, §1.2.
- Design: **docs/workflow/CURSOR-SUBAGENTS-DESIGN.md**
- `.cursor/subagents/`, the subagent-invoke rule, and run-*-agent commands are **not used** (project uses Cursor built-in Subagents).

## Integration with other tools

- **Single map** of how rules, commands, skills, agents, docs, and scripts connect: **docs/workflow/CURSOR-AND-TOOLS-INTEGRATION.md**
- **Scripts**: Service control uses **`./scripts/dev-services.sh`** (frontend | backend | db | all) (start | stop | restart). Ports: 3001 (frontend), 9200 (backend), 5432 (DB) — same as `docs/contract.md` and `verify.md` / `check-*.md`.
- **Tests**: `run-tests.md` and `post-change-test-verify.mdc` align with `mvn test` / `npm test -- --watchAll=false` and requirement doc §3·§5.
- **Workflow**: `docs/workflow/WORKFLOW_CHECKLIST.md` and `DEVELOPMENT_WORKFLOW.md` are the single source for order and gates; `.cursor` rules and skills reference them.

All of the above assumes the **dev** layout: `docs/`, `frontend/`, `backend/`, `scripts/`.
