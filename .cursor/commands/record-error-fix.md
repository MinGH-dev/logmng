# Record error remedy result (same requirement ID)

**Principle**: When an error/bug fix is done, **always** add "6. Error remedy result (cause and actions)" to that requirement doc, without the user asking. This command is for **manual** recording or when automatic recording was missed.

Record **cause** and **remedy result** in the same requirement doc for the completed error-fix. Use the **same file** so the same requirement ID tracks requirement → design → tests → cause and remedy.

## Steps

1. **Identify the requirement doc**  
   Use the requirement ID (or filename) the user gave. If none: check the latest error-fix doc in `docs/requirements/yyyyMMdd-*.md` or ask the user for the ID.

2. **Add or update section in the same file**  
   File: `docs/requirements/{requirementID}.md` (e.g. `20260220-activity-statistics-api-fix.md`).  
   Use the "paste block" structure from `docs/template/ERROR_FIX_RESULT_TEMPLATE.md`. At the **end** of the doc, add (or update) **6. Error remedy result (cause and actions)** with:
   - **Requirement ID**: same as this doc
   - **Root cause**: why the error occurred
   - **Actions taken**: what was changed or configured
   - **Result**: how it was verified and recurrence prevention
   - **Completed at**: date/time

3. **Fill content**  
   From the conversation and the fix just done, write concrete cause, actions, and result. If the doc already has cause/solution in §2, summarize the **post-fix verification** in "Result".

## References

- Template: `docs/template/ERROR_FIX_RESULT_TEMPLATE.md`
- **Do not create a separate file**; keep requirement → design → tests → cause and remedy in one doc.
