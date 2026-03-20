# Error-fix workflow flowchart

Use this flow for error-driven work unless the user explicitly asks for a code-only shortcut.

## Flow

1. Capture the error message, stack trace, and reproduction context.
2. Create or update the requirement document.
3. Define §3 test cases before implementation.
4. **Diagnostic phase (mandatory before code fix):**
   - Add **diagnostic (debug) logs** in the suspected areas (e.g. key variables, branch outcomes, per-item results) so the root cause can be verified from logs.
   - Reproduce the error and capture the logs.
   - **Analyze the logs** to confirm the actual root cause.
   - Only **after** the cause is confirmed from logs, proceed to implement the fix.
5. Implement the fix in the correct scope.
6. Run tests.
7. Restart and verify behavior.
8. If verification fails, continue the bugfix loop until it passes.
9. Record §6 error-fix result in the same requirement doc.
10. Commit after verification passes.

## Bugfix loop

If verification fails:

1. identify the failure scope
2. update the requirement/bugfix path
3. (If cause is unclear) add or refine diagnostic logs, reproduce, and analyze again to confirm cause.
4. apply the next fix
5. run tests again
6. restart and verify again

## Rules

- **Do not fix based on hypothesis.** When the user request is mainly an error message, stack trace, or "fix this error", implementers (Backend, Frontend, DB) must **not** change logic immediately based on a suspected cause. They must **verify the cause from evidence** first (via the diagnostic phase).
- Do not skip the requirement doc and §3 test plan unless the user explicitly asks for a code-only exception.
- Keep the same requirement ID for the remediation result.
- Do not push unless the user explicitly asks for it or the final release flow requires it.

## Diagnostic logs in production

Diagnostic logs added for verification must **not** run in production. They must be either: (a) at **DEBUG** (or equivalent) level so they are disabled in production, or (b) behind a **feature flag / dev-only path**, or (c) **removed or reduced** after the fix is verified. Do not leave verbose or sensitive diagnostic output enabled in production.

## References

- `.cursor/rules/error-first-workflow.mdc`
- `docs/workflow/WORKFLOW_CHECKLIST.md`
- `docs/template/ERROR_FIX_RESULT_TEMPLATE.md`
