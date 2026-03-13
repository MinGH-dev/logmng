# Error-fix workflow flowchart

Use this flow for error-driven work unless the user explicitly asks for a code-only shortcut.

## Flow

1. Capture the error message, stack trace, and reproduction context.
2. Create or update the requirement document.
3. Define §3 test cases before implementation.
4. Implement the fix in the correct scope.
5. Run tests.
6. Restart and verify behavior.
7. If verification fails, continue the bugfix loop until it passes.
8. Record §6 error-fix result in the same requirement doc.
9. Commit after verification passes.

## Bugfix loop

If verification fails:

1. identify the failure scope
2. update the requirement/bugfix path
3. apply the next fix
4. run tests again
5. restart and verify again

## Rules

- Do not skip the requirement doc and §3 test plan unless the user explicitly asks for a code-only exception.
- Keep the same requirement ID for the remediation result.
- Do not push unless the user explicitly asks for it or the final release flow requires it.

## References

- `.cursor/rules/error-first-workflow.mdc`
- `docs/workflow/WORKFLOW_CHECKLIST.md`
- `docs/template/ERROR_FIX_RESULT_TEMPLATE.md`
