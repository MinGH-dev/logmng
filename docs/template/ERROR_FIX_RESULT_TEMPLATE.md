# Error Fix Result Template

Use this template after an error or bug-fix requirement has been resolved and you need to record the root cause and remediation result while keeping the **same requirement ID**.

**Traceability**: The requirement ID comes from the existing requirement file name, for example `docs/requirements/yyyyMMdd-name.md`. Add the result to the **same file**; do not create a separate result-only document.

## Paste-in block

Append the following section to the end of the existing requirement document:

```markdown
---

## 6. Error fix result

**Requirement ID**: `yyyyMMdd-name`

### Root cause
- [Cause 1]
- [Cause 2]
- [Optional technical evidence: log, stack trace, configuration, etc.]

### Actions taken
- [Action 1: changed files and summary]
- [Action 2: config, operational, or deployment change]

### Result
- [Verification method and result]
- [Recurrence prevention or follow-up guardrail]

### Completed at
- yyyy-MM-dd HH:mm
```

## Notes

- Keep the same requirement ID and the same file.
- This makes it easy to trace requirement -> design -> test results -> root cause -> remediation in one document.
- Skip this section for non-error feature work.
