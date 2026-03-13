# Error-fix prompting examples

Use these examples when you want to request an error fix while still following the project workflow.

## 1. Basic pattern

```text
I am seeing the following error. Please fix it.

[Paste the error log or stack trace here]
```

## 2. Include reproduction steps

```text
The activity statistics API returns a 500 error. It happens after login when I open the statistics screen.
Please fix it.

[Paste the error log or response body here]
```

## 3. Emphasize workflow order

```text
Please fix the error below. Create the requirement document and the §3 test plan first, then proceed with the fix.
Create todos in workflow order.

[Paste the error message here]
```

## 4. Request the full fix workflow

```text
Please follow the fix workflow for this error:
requirement document -> §3 test plan -> implementation -> tests and verification -> §6 error-fix result.

[Paste the error details here]
```

## 5. Exception: code-only shortcut

```text
Please apply the smallest code-only fix for this error. You may skip the requirement document and test-plan workflow for this one.

[Paste the error details here]
```

## References

- `.cursor/rules/error-first-workflow.mdc`
- `.cursor/commands/fix.md`
- `.cursor/rules/workflow-todos.mdc`
- `docs/template/REQUIREMENT_TEMPLATE.md`
- `docs/template/ERROR_FIX_RESULT_TEMPLATE.md`
