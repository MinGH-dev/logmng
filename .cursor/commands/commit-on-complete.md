# Commit When Requirement Is Complete

When **all** of the following are done for the current requirement or bugfix, **commit** the changes. Do not push unless the user asks.

**When subagent delegation is in effect**: The **QA subagent** performs this commit after verification and §5/§6 update (main agent does not commit). See `docs/workflow/SUBAGENT-DELEGATION.md` §2 and §5.

## When to run

- **After** verification passes (restart + health check per `verify.md`).
- **After** requirement doc is updated: §5 Test results, checklist; for error/bug fixes also §6 Error remedy result.
- **Only** when the user's requirement for this task is fully resolved (no open bugfix loop, no failing tests).

## Steps

1. **Confirm** current branch is not `main`/`master` (project policy: work on `feat/<feature-key>` or similar). If on main, create/checkout a branch first and tell the user.
2. **Stage** changed files (code, docs, config that belong to this requirement). Prefer explicit paths; if appropriate, `git add` the relevant dirs/files. Do not add secrets or `.env` with secrets.
   - **.cursor, docs, specs**: If you added or changed files under `.cursor/`, `docs/`, or `specs/`, include them in this commit or a follow-up commit so they are not left untracked. Optional: run `./scripts/check-untracked-docs.sh` before push to see untracked files there. See `docs/workflow/COMMIT-SCOPE-AND-UNTRACKED.md`.
3. **Commit** with a clear message that **references the requirement document** so **each commit version** is traceable to the work done:
   - **Required**: Include the requirement doc path (e.g. `docs/requirements/yyyyMMdd-name.md`) or short ID (`req yyyyMMdd-name`), and a one-line summary from §1 (user requirement or expected outcome).
   - **Language**: Keep the guidance and examples in English because this is a tool-facing document. Actual commit wording may follow repository conventions as long as it is clear and traceable. See `docs/workflow/DOCUMENT-LANGUAGE-POLICY.md`.
   - **Format**: `feat: <scope> - <one-line summary> (req yyyyMMdd-name)` or `fix: <scope> - <one-line summary> (req yyyyMMdd-name)`.
   - **Traceability**: Anyone can see what work a commit represents by reading the referenced requirement doc.
   - Examples:
     - `feat: activity-log - apply today filter and record §5 results (req 20260220-activity-log-today-empty-fix)`
     - `fix: auth - resolve statistics API 401 and update §6 (req 20260220-activity-statistics-api-error-fix)`
     - `feat: search-history - support rerun and detail view (req 20260224-search-history-reload-and-detail-view)`
4. **Push**: Do **not** run `git push` (or force push) unless the user explicitly requests it.  
   **When the user does request push** (for example "push this" or "push to remote"), run `git push` (or `git push origin <current-branch>`) **after** the commit. The agent that performed the commit should execute the push when the user asked for it, or the workflow should hand off the final release-and-push step to **Release** when that path is explicitly requested.

## References

- Workflow completion order: `docs/workflow/WORKFLOW_CHECKLIST.md` (step 7)
- Verification must pass first: `.cursor/commands/verify.md`
- Security (no force push, no secrets in commits): `docs/security-guide.md`, `.cursor/rules/security-permissions.mdc`
