# Error-fix workflow

When the user reports an error or asks for a bugfix, follow this order.

## Order

1. **Gather and analyze** — Summarize error, repro steps, impact.
2. **Write requirement doc** — `docs/requirements/yyyyMMdd-name.md` with §1 (error content), §2 (design: cause and approach). Template: `docs/template/REQUIREMENT_TEMPLATE.md`.
3. **§3 Test approach** — Add test cases in §3 (repro and fix verification).
4. **Development (fix)** — Change backend/frontend code or config per root cause.
5. **Tests and verification** — Run unit tests → record in §5. Then run verification per `verify.md` (restart and health check). On failure create bugfix child and repeat.
6. **Document** — §5 test results; for error fixes add §6 Error remedy result.

## References

- **Error-first**: `docs-reference.mdc`, `error-first-workflow.mdc`
- **Verification**: `.cursor/commands/verify.md`
- **Bugfix child**: `docs/template/BUGFIX_CHILD_TEMPLATE.md`
- **Tests**: `.cursor/commands/run-tests.md`

Do not jump to "implement API" or "change api.js" from the error alone; do 1→2→3 first, then 4.

**Example prompts for requesting error fixes**: `docs/workflow/error-fix-prompting-examples.md`
