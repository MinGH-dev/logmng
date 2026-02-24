# Commit When Requirement Is Complete

When **all** of the following are done for the current requirement or bugfix, **commit** the changes. Do not push unless the user asks.

## When to run

- **After** verification passes (restart + health check per `verify.md`).
- **After** requirement doc is updated: §5 Test results, checklist; for error/bug fixes also §6 Error remedy result.
- **Only** when the user's requirement for this task is fully resolved (no open bugfix loop, no failing tests).

## Steps

1. **Confirm** current branch is not `main`/`master` (project policy: work on `feat/<feature-key>` or similar). If on main, create/checkout a branch first and tell the user.
2. **Stage** changed files (code, docs, config that belong to this requirement). Prefer explicit paths; if appropriate, `git add` the relevant dirs/files. Do not add secrets or `.env` with secrets.
3. **Commit** with a clear message:
   - Format: `feat: <short-scope> - <one-line summary>` for features, or `fix: <short-scope> - <one-line summary>` for bugfixes/error remedies.
   - Example: `feat: activity-log - add today filter and §5 results`
   - Example: `fix: auth - 401 on statistics API, add §6`
4. **Do not** run `git push`, `git push --force`, or `git push -f` unless the user explicitly requests it.

## References

- Workflow completion order: `docs/workflow/WORKFLOW_CHECKLIST.md` (step 7)
- Verification must pass first: `.cursor/commands/verify.md`
- Security (no force push, no secrets in commits): `docs/security-guide.md`, `.cursor/rules/security-permissions.mdc`
