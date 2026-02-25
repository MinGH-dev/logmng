# Commit When Requirement Is Complete

When **all** of the following are done for the current requirement or bugfix, **commit** the changes. Do not push unless the user asks.

## When to run

- **After** verification passes (restart + health check per `verify.md`).
- **After** requirement doc is updated: §5 Test results, checklist; for error/bug fixes also §6 Error remedy result.
- **Only** when the user's requirement for this task is fully resolved (no open bugfix loop, no failing tests).

## Steps

1. **Confirm** current branch is not `main`/`master` (project policy: work on `feat/<feature-key>` or similar). If on main, create/checkout a branch first and tell the user.
2. **Stage** changed files (code, docs, config that belong to this requirement). Prefer explicit paths; if appropriate, `git add` the relevant dirs/files. Do not add secrets or `.env` with secrets.
   - **.cursor, docs, specs**: If you added or changed files under `.cursor/`, `docs/`, or `specs/`, include them in this commit or a follow-up commit so they are not left untracked. Optional: run `./scripts/check-untracked-docs.sh` before push to see untracked files there. See `docs/workflow/COMMIT-SCOPE-AND-UNTRACKED.md`.
3. **Commit** with a clear message that **includes the requirement**:
   - Include **requirement doc** (e.g. `docs/requirements/yyyyMMdd-name.md`) or its short ID (`yyyyMMdd-name`), and **requirement content** (one-line summary from §1 사용자 요건 or 기대 결과).
   - Format: `feat: <scope> - <requirement summary> (요건 yyyyMMdd-name)` or `fix: <scope> - <requirement summary> (요건 yyyyMMdd-name)`.
   - Examples:
     - `feat: activity-log - 오늘 필터 및 §5 결과 반영 (요건 20260220-activity-log-today-empty-fix)`
     - `fix: auth - statistics API 401 조치, §6 반영 (요건 20260220-activity-statistics-api-error-fix)`
     - `feat: search-history - 재조회 시 검색 조건 동일 표시, 자세히 보기 (요건 20260224-search-history-reload-and-detail-view)`
4. **Do not** run `git push`, `git push --force`, or `git push -f` unless the user explicitly requests it.

## References

- Workflow completion order: `docs/workflow/WORKFLOW_CHECKLIST.md` (step 7)
- Verification must pass first: `.cursor/commands/verify.md`
- Security (no force push, no secrets in commits): `docs/security-guide.md`, `.cursor/rules/security-permissions.mdc`
