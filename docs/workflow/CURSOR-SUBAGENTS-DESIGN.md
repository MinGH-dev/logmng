# Cursor Subagents Design

This project uses **only Cursor Settings Subagents**. Custom sub-agents (mcp_task, run-*-agent, .cursor/subagents/) are **not used**.

---

## 1. Core 9 Subagents (development)

Create the following 9 subagents under Cursor **Settings → Subagents** and paste the corresponding prompt from `docs/cursor-subagents/` into each.

| Subagent name (Settings) | Purpose | Prompt file |
|--------------------------|---------|-------------|
| **Frontend** | Frontend development, requirement docs, tests | `frontend.md` |
| **Backend** | Backend development, requirement docs, tests | `backend.md` |
| **DB** | DB schema, migrations, requirement docs, tests | `db.md` |
| **Requirements** | Requirement and spec document authoring (no code) | `requirements.md` |
| **QA** | Test scenarios, verification checklist, test result documentation | `qa-test.md` |
| **Contract** | API and contract (contract.md, specs) definition and consistency | `contract-api.md` |
| **Security** | Security review: PII, access control, decryption scope (no code) | `security.md` |
| **DBA** | Schema and design review (DBA perspective). JSON/index/performance (no code) | `dba.md` |
| **Architecture** | Architecture review. Performance, scalability, query load (no code) | `architecture.md` |

### 1.1 Backend module-specific subagents (optional, moai-adk style)

When the backend grows, splitting by **module/feature** keeps scope clear and reduces cross-module edits. Add these **optional** subagents in Cursor Settings and paste the corresponding prompt.

| Subagent name | Module/feature | Prompt file | When to use |
|---------------|----------------|-------------|-------------|
| **Backend** | General / unclear scope | `backend.md` | General backend work, health/config, cross-module changes |
| **Backend-Auth** | Login, auth, interceptors | `backend-auth.md` | AuthController, AuthService, AuthInterceptor, login/session |
| **Backend-ActivityLog** | Activity log, statistics, user activity | `backend-activity-log.md` | ActivityStatistics*, UserActivityLog*, ActivityLogAspect |
| **Backend-Log** | Log DB, search, decrypt, log type | `backend-log.md` | LogDb*, SearchSuggest*, Decrypt*, LogType* |

- Using **Backend** alone is fine. For work limited to one module, **Backend-Auth / Backend-ActivityLog / Backend-Log** give clearer scope.
- Reference: [moai-adk .claude/agents](https://github.com/modu-ai/moai-adk/tree/main/.claude) — expert-backend split by domain/platform skills.

### 1.2 Frontend module-specific subagents (optional, moai-adk style)

Splitting the frontend by **screen/feature** keeps scope clear. Add these **optional** subagents and paste the corresponding prompt.

| Subagent name | Screen/feature | Prompt file | When to use |
|---------------|----------------|-------------|-------------|
| **Frontend** | General / unclear scope | `frontend.md` | App, routing, api, shared components, cross-screen changes |
| **Frontend-Auth** | Login, auth UI | `frontend-auth.md` | LoginForm, login flow, auth state |
| **Frontend-ActivityLog** | Activity statistics, user activity log UI | `frontend-activity-log.md` | ActivityStatistics, UserActivityLog/*, Statistics* |
| **Frontend-Log** | Log search, tables, image log, log type UI | `frontend-log.md` | LogGrid, LogTable, ImageLog*, SearchForm, AdvancedSearchForm, LogTypeSelector |

- Using **Frontend** alone is fine. For a single screen/feature, **Frontend-Auth / Frontend-ActivityLog / Frontend-Log** give clearer scope.
- Reference: [moai-adk expert-frontend](https://github.com/modu-ai/moai-adk/blob/main/.claude/agents/moai/expert-frontend.md). Improvement notes: `docs/cursor-subagents/FRONTEND-IMPROVEMENT-POINTS.md`.

### 1.3 Additional subagents (review, docs, release, consistency, UX)

These 5 support collaboration and consistent deliverables. **Role boundaries** are in §2.6.

| Subagent name | Purpose | Prompt file |
|---------------|---------|-------------|
| **Review** | Code/change review (contract, workflow, quality, standards). No code edits. | `review.md` |
| **Documentation** | User/ops docs (README, QUICK_START, deployment, runbooks). No requirement docs, API spec, or code. | `documentation.md` |
| **Release** | CHANGELOG, version, release checklist. No user guides or code. | `release.md` |
| **Consistency** | Standards doc (CONSISTENCY-STANDARDS.md). No review execution or code. Review applies standards. | `consistency.md` |
| **UX** | Design/UX review (a11y, UI consistency, design system). No implementation; Frontend implements. | `ux-design.md` |

---

## 2. Subagent scope

### Common (Frontend / Backend / DB)

- **Requirement docs**: Create or update requirement docs in `docs/requirements/yyyyMMdd-name.md` for the agent’s area.
- **Test automation**: Propose and run unit/integration tests (Frontend: Jest/React Testing Library; Backend: JUnit/Mockito; DB: schema/data validation scripts).
- **Contract compliance**: Follow `docs/contract.md` and `specs/*.spec.yaml`. Update spec before API/schema changes.
- **After code changes: build, restart, QA verification (required)**  
  When Frontend/Backend (and module-specific Frontend-*/Backend-*) **modify code or config**, the plan must **always** include and execute:  
  - **Frontend**: `cd frontend && npm run build` (or `CI=false npm run build` if only ESLint fails) → `./scripts/dev-services.sh frontend restart` → confirm behavior → **instruct QA subagent to perform verification**.  
  - **Backend**: `cd backend && mvn test` (or `mvn package`) → `./scripts/dev-services.sh backend restart` → wait 5–10s, confirm `curl -s http://localhost:9200/api/health` → **instruct QA subagent to perform verification**.  
  - **QA**: After build and restart, **perform verification** — run checklist in `.cursor/commands/verify.md`, update §5 (and §6 for error fixes).  
  - If only docs or review were produced (no code change), skip build, restart, and QA handoff.  
  - Details: `docs/cursor-subagents/frontend.md` § After code changes, `docs/cursor-subagents/backend.md` § After code changes, `docs/cursor-subagents/qa-test.md` § After build and restart.

### Response language

- **All subagents**: Respond to the user in the **user’s requested language** (e.g. Korean when the user writes in Korean). Code, file paths, and identifiers stay as-is. See `.cursor/rules/language-policy.mdc`.

### Frontend

- **Development**: Modify only code and config under `frontend/`. API calls per contract and specs. Security and logging: `docs/security-guide.md`.
- **Requirements**: Create or update requirement docs for UI/UX, screens, and frontend issues.
- **Testing**: Frontend unit/component tests; E2E scenario suggestions.

### Backend

- **Development**: Modify only code and config under `backend/`. API per spec and contract. DB access consistent with schema.sql and contract.
- **Requirements**: Create or update requirement docs for API, business logic, and backend issues.
- **Testing**: API and service unit tests, integration tests, curl/script automation.

### DB

- **Development**: Modify only `backend/src/main/resources/db/` (schema.sql, setup.sh, migrations, etc.) and DB setup docs.
- **Requirements**: Create or update requirement docs for schema, migrations, and data policy.
- **Testing**: Schema validation, initial data validation, setup/check script automation.

### Requirements

- **Docs only**: Create or update `docs/requirements/`, `specs/`, and requirement/spec templates. **Do not modify code.**
- **Requirement doc**: User requirement (What/Why), scenario, expected outcome, checklist, test result sections.
- **Spec doc**: API, data, and UI design for complex features, aligned with the requirement.

### QA

- **Test design**: Test cases (happy path, exception, edge), E2E and regression scenarios.
- **Verification checklist**: Workflow-based checklist; requirement doc checklist and test result sections.
- **Automation**: How to use `/check-*`, `/verify`; CI and test automation. (Test code is written by Frontend/Backend/DB.)

### Contract

- **Contract and spec**: Maintain `docs/contract.md` and `specs/*.spec.yaml`. Single source of truth for API, env, ports. **Spec first** for API add/change.
- **Consistency**: Propose checks that contract/spec match implementation. **No code edits** — Frontend/Backend implement to spec.

### Security

- **Security review**: Review requirement docs (§1·§2) and design for PII, access control, decryption scope, audit logging. Propose or add **§2.1 Security review** (or appendix) with risks, acceptance criteria, and design recommendations.
- **Design recommendations**: So that design and development follow the review (e.g. decryption scope policy). **No code edits** — Requirements/Contract/Backend/Frontend implement.
- **Guide**: Propose updates to `docs/security-guide.md`. Use when: PII/decryption/access-control requirements; before implementation.

### DBA

- **Schema design review**: Review proposed or existing tables for PK/index, data types, constraints, and growth.
- **JSON vs relational**: For JSONB (e.g. row_key_json), review query patterns, indexability, uniqueness, storage; when to prefer composite columns.
- **Performance and operations**: Backup/restore, query performance. **No code edits** — review and recommendations only. DB subagent applies schema changes.

### Architecture

- **Performance and scalability**: For heavy or frequent data access (e.g. snapshot lookups), review query patterns, load, indexing, cache, batching.
- **Trade-offs**: Compare options (e.g. DB-only vs cache, per-request vs batch) and recommend by condition.
- **Operational impact**: Latency, throughput, resources. **No code edits** — Backend/DB apply recommendations.

### Review

- **Review only**: Review changes (patch/file list) against contract, workflow, quality, `docs/workflow/CONSISTENCY-STANDARDS.md`. Output pass/fail and suggestions. **No code edits, test writing, or §5** (→ Backend/Frontend/DB, QA).
- **Standards**: Consistency owns the standards doc; Review **uses** it when reviewing.

### Documentation

- **Scope**: README, QUICK_START, deployment/ops guides, runbooks, troubleshooting. **No requirement docs (`docs/requirements/`), API spec (contract, specs), or code** (→ Requirements, Contract, implementing agents).

### Release

- **Scope**: CHANGELOG, version guidance, release checklist. **No user guides (README, runbooks)** (→ Documentation). **No code edits**.

### Consistency

- **Standards doc owner**: Define and update `docs/workflow/CONSISTENCY-STANDARDS.md` (naming, error codes, logging, file structure). **No review execution or code edits** — Review **applies** this doc when reviewing.

### UX

- **Review only**: a11y, UI consistency, design system, interaction recommendations. **No implementation** (→ Frontend implements).

---

## 2.5 Agent collaboration on requirements (sequence and handoff)

When a **requirement** or **error-fix request** is made, subagents follow the **collaboration sequence**. Roles, inputs, outputs, and handoffs are in a **single reference doc**.

- **Doc**: `docs/workflow/AGENT-COLLABORATION-ON-REQUIREMENT.md`
- **Delegation**: Main agent instructs the user to switch to the matching subagent for each step; it does not perform that step in the main chat. Full table: `docs/workflow/SUBAGENT-DELEGATION.md`.
- **Rule**: `.cursor/rules/agent-collaboration.mdc` (follow this sequence for requirements/error fixes; §5 delegation for all steps)
- **Summary**: Requirements → Security/Contract/DBA/Architecture/Consistency/UX (as needed) → Backend/Frontend/DB → Review (optional) → QA → Documentation/Release (as needed). Role boundaries: §2.6. Each agent’s `.cursor/agents/*.mdc` has a "Collaboration" section with its step and handoff target.

---

## 2.6 Role boundaries (single owner per area)

Each area below has **one owning agent**. Use this table when collaborating to avoid duplication.

| Area | Owner | Not owner (reference) |
|------|--------|------------------------|
| Requirement docs (§1·§2·§3), feature spec | **Requirements** | Documentation: user/ops docs only |
| API, env, spec (contract, specs) | **Contract** | Consistency: coding conventions only |
| Security review (§2.1, security recommendations) | **Security** | — |
| Schema design review | **DBA** | DB: schema file edits |
| Performance/scalability design review | **Architecture** | — |
| **Standards doc** (CONSISTENCY-STANDARDS) | **Consistency** | Review: applies only |
| **Change review** (contract, workflow, quality, standards) | **Review** | QA: test design, §5; Consistency: standards definition only |
| Test design, §3·§5·§6, verification checklist | **QA** | Review: code review only |
| User/ops docs (README, QUICK_START, runbooks) | **Documentation** | Requirements: requirement docs; Release: CHANGELOG only |
| CHANGELOG, version, release checklist | **Release** | Documentation: user/ops docs only |
| Design/UX review (a11y, UI consistency) | **UX** | Frontend: implementation |
| Implementation (backend/, frontend/, db/) | **Backend / Frontend / DB** | Review, UX, Documentation, Release, Consistency: no code edits |

---

## 3. Work flow (when to use which agent)

1. **Requirements and spec**  
   - **Requirements** subagent: Create or update requirement doc (`docs/requirements/yyyyMMdd-name.md`) for the new requirement, feature, or error fix. For complex features, draft a spec.  
   - Or have Requirements produce the requirement doc before asking Frontend/Backend/DB to implement.

2. **Security review (when PII, decryption, or access control)**  
   - **Security** subagent: Review requirement draft (§1·§2) or design for PII, decryption scope, and access control. Add §2.1 Security review or recommendations to the requirement doc. Design and development should follow the review (e.g. decryption scope policy).  
   - Call before or in parallel with Requirements/Contract.

3. **API and contract (when cross-layer change)**  
   - **Contract** subagent: Update `docs/contract.md` and `specs/*.spec.yaml` first for API add/change.  
   - Then Frontend/Backend implement to the spec.

4. **Development**  
   - **Frontend / Backend / DB** subagent: Modify only the owned directory. Use contract, spec, and **security review result**. Implement per requirement doc when present.

5. **Code/change review (optional)**  
   - **Review** subagent: Review the change against contract, workflow, quality, and `CONSISTENCY-STANDARDS.md`. No code edits; implementing agent applies suggestions.

6. **Test and verification**  
   - **QA** subagent: Propose test scenarios and checklist; update requirement doc §5·§6.  
   - **Frontend/Backend/DB**: Write unit/integration test code.  
   - Use `/check-backend`, `/check-db`, `/check-frontend-backend`, `/verify` for status and verification.

7. **Docs and release (as needed)**  
   - **Documentation**: Update user/ops docs (README, QUICK_START, runbooks). **Release**: Update CHANGELOG and release checklist.

---

## 4. How to register subagents

### 4.1 Local agents (in project)

This project has subagent definitions under **`.cursor/agents/`**.

- **Core 9**: `Frontend.mdc`, `Backend.mdc`, `DB.mdc`, `Requirements.mdc`, `QA.mdc`, `Contract.mdc`, `Security.mdc`, `DBA.mdc`, `Architecture.mdc`
- **Additional 5**: `Review.mdc`, `Documentation.mdc`, `Release.mdc`, `Consistency.mdc`, `UX.mdc`
- If Cursor supports **local agents** (`.cursor/agents/*.mdc`), these subagents may appear in the list when the project is opened.
- To change behavior, edit `.cursor/agents/*.mdc`. (Keep in sync with `docs/cursor-subagents/*.md`.)

### 4.2 Manual registration in Cursor Settings (one-time)

If local agents are not used, register manually:

1. Open **Settings → Subagents**.
2. **Add** 14 subagents with names: `Frontend`, `Backend`, `DB`, `Requirements`, `QA`, `Contract`, `Security`, `DBA`, `Architecture`, `Review`, `Documentation`, `Release`, `Consistency`, `UX`.
3. For each, paste the **full content** of the corresponding file under `docs/cursor-subagents/` into the **Prompt** field:
   - Frontend → `frontend.md`, Backend → `backend.md`, DB → `db.md`
   - Requirements → `requirements.md`, QA → `qa-test.md`, Contract → `contract-api.md`
   - Security → `security.md`, DBA → `dba.md`, Architecture → `architecture.md`
   - Review → `review.md`, Documentation → `documentation.md`, Release → `release.md`, Consistency → `consistency.md`, UX → `ux-design.md`
   - **(Optional)** Backend modules: Backend-Auth → `backend-auth.md`, Backend-ActivityLog → `backend-activity-log.md`, Backend-Log → `backend-log.md`
   - **(Optional)** Frontend modules: Frontend-Auth → `frontend-auth.md`, Frontend-ActivityLog → `frontend-activity-log.md`, Frontend-Log → `frontend-log.md`
4. (Optional) Open the workspace from this `dev` folder so rules (docs-reference, contract-first, agent-collaboration) apply.

---

## 5. References

- **Contract**: `docs/contract.md` (ports, API, DB — single source of truth).
- **Workflow**: `docs/workflow/DEVELOPMENT_WORKFLOW.md` (requirement doc first, verification required).
- **Prompt text**: `docs/cursor-subagents/*.md` — edit these to keep subagent behavior consistent.

**Core 9** + **Additional 5** = **14** subagents cover development, requirements, spec, review, documentation, release, consistency, and UX **without role overlap** (§2.6).

---

## 6. Integration with other tools

- How **rules, commands, docs, and scripts** connect: **docs/workflow/CURSOR-AND-TOOLS-INTEGRATION.md**
- Subagent choice (general vs module vs additional 5): §1·§1.1·§1.2·§1.3; **role boundaries**: §2.6. Actual prompts: `docs/cursor-subagents/*.md`. Restart and health check: same as `.cursor/commands/verify.md` and `scripts/dev-services.sh`.
