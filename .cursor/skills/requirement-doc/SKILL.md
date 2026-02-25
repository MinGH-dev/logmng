---
name: requirement-doc
description: Create or update requirement documents using project template and naming (dev workspace only). Use when writing a requirement doc, creating a new requirement file, or when the user mentions requirements or docs/requirements.
---

# Requirement document

**Delegation**: When the project uses subagent delegation (`.cursor/rules/agent-collaboration.mdc`, `docs/workflow/SUBAGENT-DELEGATION.md`), the **main agent** does **not** use this skill to write the requirement doc — it delegates **Step 1 to the Requirements subagent**. This skill is for the **Requirements** subagent, or when the user said "code only here", "skip subagent", or "do it in this chat".

Write requirement docs per the rules below. When the requirement involves Security, Contract, DBA, Architecture, Consistency, or UX, **solicit feedback from that expert subagent** during authoring and incorporate into §1·§2 before finalizing §3 — see `docs/workflow/AGENT-COLLABORATION-ON-REQUIREMENT.md` §1.1.

## Path and name

- **Path**: `docs/requirements/yyyyMMdd-name.md`
- **Format**: `yyyyMMdd` (8 digits), name in lowercase English and hyphens (e.g. `20260220-image-log-search.md`)

## Template

Use `docs/template/REQUIREMENT_TEMPLATE.md`. Structure:

1. User requirement (description, scenario, expected outcome)
2. Design (technical design, file change list)
3. Test approach
4. Checklist (frontend/backend/integration/docs)
5. Test results (date, result, issues and resolution)

## For error/bug-fix requirements

- **After the fix**: **Always** add or update **"6. Error remedy result (cause and actions)"** in that requirement doc, without the user asking. Do not omit.
- **Same requirement ID**: Record only in the same doc (`docs/requirements/yyyyMMdd-name.md`). Do not create a separate "remedy result" file.
- **Fields**: Root cause, Actions taken, Result, Completed at. Template: `docs/template/ERROR_FIX_RESULT_TEMPLATE.md`
- Command `/record-error-fix` can be used when the user only wants to record manually.

## References

- Examples: latest `.md` in `docs/requirements/`.
- Requirement vs spec: requirement (What/Why) → `docs/requirements/`; technical spec (How) → `specs/*.spec.yaml`.
