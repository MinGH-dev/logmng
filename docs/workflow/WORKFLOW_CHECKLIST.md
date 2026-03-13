# Workflow Checklist (order only)

Read this document first for any **new requirement** or **bug fix** in this project. It contains the **required order and gates only**. For detail, examples, commands, and rationale, see `DEVELOPMENT_WORKFLOW.md`.

## Gate (before development)

- Do **not** start code changes before steps 1, 2, and 3 are complete.
- Do **not** record the requirement or test plan only after implementation. The requirement doc and §3 test cases must exist first.

## Order

1. Collect and analyze the requirement.
2. Author the requirement document (§1, §2).
3. Define the test plan (§3 test case list).
4. If the requirement involves PII, decryption, or access control: run **Security** review and add/apply §2.1.
5. If needed for a complex change: write/update specs, then work on a feature branch.
6. Implement the change.
7. Implement and run automated tests for the modified behavior (unit and/or integration) and record results in §5.
8. Verify on a restarted application and repeat the bugfix loop if verification fails.
9. Update documentation (§5, and §6 for error fixes).
10. Commit only after verification passes and documentation is updated. Push only when the user explicitly requests it or when the workflow explicitly hands off the final release-and-push step.

## Language rule for tooling

- User-facing assistant replies follow the user's requested language.
- Tool-facing workflow documents, copied handoff blocks, and Task/subagent prompts must be written in **English**.

## References

- Template: `docs/template/REQUIREMENT_TEMPLATE.md`
- Detail: `docs/workflow/DEVELOPMENT_WORKFLOW.md`
- Security review timing: `docs/workflow/CURSOR-SUBAGENTS-DESIGN.md`
- Cursor/doc/script integration map: `docs/workflow/CURSOR-AND-TOOLS-INTEGRATION.md`
- Commit when complete: `.cursor/commands/commit-on-complete.md`
