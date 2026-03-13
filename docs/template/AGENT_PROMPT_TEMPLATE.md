# Agent Prompt Template

Use this template when you start a new agent session and want to request work in a way that matches this repository's workflow.

## Template A: use `@` references

Reference the workflow docs directly:

```text
Please follow the workflow in @docs/workflow/DEVELOPMENT_WORKFLOW.md.

Important requirements:
1. Create or update the requirement document first.
2. Use the project templates in @docs/template/.
3. Work on a feature branch.
4. Implement, test, verify, and update the requirement document before finishing.
5. Keep user-facing replies in Korean, but keep tool-facing prompts and workflow text in English.

Current requirement:
[Describe the requirement here]
```

## Template B: direct file-path instruction

Use this when you do not want to rely on `@` references:

```text
Please follow this process:

1. Read `docs/workflow/DEVELOPMENT_WORKFLOW.md`.
2. Create or update the requirement document at `docs/requirements/yyyyMMdd-name.md`.
3. If the task is complex, create or update the spec in `specs/`.
4. Work on a feature branch such as `feat/name`.
5. Implement the change.
6. Run tests and verification.
7. Update the requirement document with test results and completion notes.

Current requirement:
[Describe the requirement here]
```

## Tips

- Reference both the workflow and the requirement template together when you want consistent output.
- Call out any section that must be followed strictly.
- If the task is requirement-driven, make the requirement doc path explicit in the prompt.
- Keep copied handoff prompts in English when they are intended for tools or subagents.

## Suggested checklist to include in prompts

```text
Please work in this order:

1. Read `docs/workflow/DEVELOPMENT_WORKFLOW.md`
2. Analyze the related files
3. Create or update the requirement document
4. Confirm the branch
5. Implement the change
6. Run tests
7. Verify the result
8. Update the requirement document with results
```

## Common mistakes to avoid

1. Skipping the requirement document or §3 test plan
2. Implementing before reading the workflow
3. Forgetting to update the requirement doc after testing
4. Writing mixed-language tool-facing prompt text
