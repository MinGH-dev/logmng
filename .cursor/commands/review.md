# Self-review and pre-PR checklist

Before submitting a PR, confirm the following. **Review** Subagent can perform this checklist on a change (read-only); implementers apply fixes. See `docs/workflow/CURSOR-SUBAGENTS-DESIGN.md` §2 (Review).

## Quality and consistency

- [ ] **Input validation and error codes** — API and forms handle validation and error responses consistently.
- [ ] **Logging** — Appropriate log level; no PII in logs.
- [ ] **Docs and comments** — Updated for the changed behavior.

## Contract and spec

- [ ] **API** — Implementation matches `docs/contract.md` and `specs/*.spec.yaml`. New or changed API: update spec first.
- [ ] **DB** — Schema and migrations match contract and spec.

## Tests and verification

- [ ] **Unit tests** — backend: `mvn test`; frontend: `npm test -- --watchAll=false` pass.
- [ ] **Test results** — Recorded in requirement doc §5.
- [ ] **Verification** — `verify.md` (restart and health check) done and services healthy.

## PR write-up (recommended)

- Purpose and context
- Spec summary (link)
- List of changed files
- Test results (summary or screenshot)
- Risks and rollback (if applicable)

See: `.cursor/commands/verify.md`, `docs/workflow/DEVELOPMENT_WORKFLOW.md`, `docs/workflow/WORKFLOW_CHECKLIST.md`.
