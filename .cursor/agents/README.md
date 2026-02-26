# Local subagent definitions (dev only)

The `.mdc` files in this folder are **local subagent definitions** that Cursor may recognize for this project.

## Agents

| File | Role |
|------|------|
| Frontend.mdc | Frontend development (general). Modify `frontend/` only. |
| Frontend-Auth.mdc | Frontend **auth UI only** (LoginForm, login flow). Optional. |
| Frontend-ActivityLog.mdc | Frontend **activity log / statistics UI only** (ActivityStatistics, UserActivityLog/*, Statistics*). Optional. |
| Frontend-Log.mdc | Frontend **log/search/log-type UI only** (LogGrid, LogTable, ImageLog*, SearchForm, LogTypeSelector). Optional. |
| Backend.mdc | Backend development (general). Modify `backend/` only. |
| Backend-Auth.mdc | Backend **auth only** (AuthController, AuthService, AuthInterceptor). Optional; use when task is auth-only. |
| Backend-ActivityLog.mdc | Backend **activity log / statistics only** (ActivityStatistics*, UserActivityLog*, ActivityLogAspect). Optional. |
| Backend-Log.mdc | Backend **log DB / search / decrypt / log type** (LogDb*, SearchSuggest*, Decrypt*, LogType*). Optional. |
| DB.mdc | **DB (Schema)**: schema, migrations, config. Modify `backend/.../db/` only. |
| Requirements.mdc | Requirement and spec docs. No code changes. |
| QA.mdc | Test scenarios, checklists, test result docs. |
| Contract.mdc | API and contract (`docs/contract.md`, `specs/`) definition and updates. |
| Security.mdc | Security review (PII, access, decryption scope). No code changes. |
| DBA.mdc | **DBA (Review)**: schema/design review (DBA perspective). No code changes. |
| Architecture.mdc | Performance/scalability review. No code changes. |
| Review.mdc | Code/change review (contract, workflow, quality, standards). Read-only; no code edit. |
| Documentation.mdc | User/ops docs (README, QUICK_START, runbooks). No requirement docs, no code. |
| Release.mdc | CHANGELOG, version, release checklist. No user guides, no code. |
| Consistency.mdc | Standards doc (`CONSISTENCY-STANDARDS.md`) definition. No review execution, no code. |
| UX.mdc | Design/UX review (a11y, UI consistency). No code; Frontend implements. |

**Role boundaries (no duplication)**: `docs/workflow/CURSOR-SUBAGENTS-DESIGN.md` §2.6.

## Usage

- If Cursor supports **local agents** (`.cursor/agents/*.mdc`), these subagents may appear when you open this project.
- If not, create the 14 agents under **Cursor Settings → Subagents** and paste the prompt blocks from `docs/cursor-subagents/*.md` (see CURSOR-SUBAGENTS-DESIGN.md §4.2).

Design: `docs/workflow/CURSOR-SUBAGENTS-DESIGN.md`
