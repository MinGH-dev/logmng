---
name: requirement-doc
description: Create or update requirement documents using project template and naming (dev workspace only). Use when writing a requirement doc, creating a new requirement file, or when the user mentions requirements or docs/requirements.
---

# Requirement document

**Skill usage visibility**: When you use this skill to answer, state at the start of your response: `[Skill used: requirement-doc]`

**Delegation**: When the project uses subagent delegation (`.cursor/rules/agent-collaboration.mdc`, `docs/workflow/SUBAGENT-DELEGATION.md`), the **main agent** does **not** use this skill to write the requirement doc — it delegates **Step 1 to the Requirements subagent**. This skill is for the **Requirements** subagent, or when the user said "code only here", "skip subagent", or "do it in this chat".

Write requirement docs per the rules below. **Do not write §1 (user scenario, expected outcome) and §2 (codebase summary, problem analysis, solution) from Requirements' sole judgment.** Instead: obtain **parallel** input from experts (Security, Contract, DBA, Architecture, Consistency, UX) and from **Backend/Frontend/DB/QA** (scenario, codebase summary, problem analysis, solution); **orchestrate** (merge) that input into §1·§2; then finalize §3. See `docs/workflow/AGENT-COLLABORATION-ON-REQUIREMENT.md` §1.1. The §2 **"변경 파일 목록"** is **tentative**; the implementing agent (Step 4) **confirms or updates** it when implementation is done (§1.2).

## Language and lifecycle

- **Author in English first**: §1, §2, §3 (and §5, §6 when filled) are written in **English**. Use the English template `docs/template/REQUIREMENT_TEMPLATE.md`.
- **Final Korean version**: After **all verification is complete**, add **§ Final version (Korean)** in the same doc (or create `yyyyMMdd-name-ko.md`) so the requirement is available in Korean. See `docs/workflow/DOCUMENT-LANGUAGE-POLICY.md` §2.
- **Commit message**: Must reference this requirement doc (e.g. `req yyyyMMdd-name`) so each commit is traceable. See `.cursor/commands/commit-on-complete.md`.

## Path and name

- **Path**: `docs/requirements/yyyyMMdd-name.md`
- **Format**: `yyyyMMdd` (8 digits), name in lowercase English and hyphens (e.g. `20260220-image-log-search.md`)

## Template

Use `docs/template/REQUIREMENT_TEMPLATE.md`. Structure:

1. User requirement (description, scenario, expected outcome) — English
2. Design (technical design, file change list) — English
3. Test approach — English
4. Checklist (frontend/backend/integration/docs)
5. Test results (date, result, issues and resolution)
7. Final version (Korean) — add after verification complete (see DOCUMENT-LANGUAGE-POLICY)

## For error/bug-fix requirements

- **After the fix**: **Always** add or update **"6. Error remedy result (cause and actions)"** in that requirement doc, without the user asking. Do not omit.
- **Same requirement ID**: Record only in the same doc (`docs/requirements/yyyyMMdd-name.md`). Do not create a separate "remedy result" file.
- **Fields**: Root cause, Actions taken, Result, Completed at. Template: `docs/template/ERROR_FIX_RESULT_TEMPLATE.md`
- Command `/record-error-fix` can be used when the user only wants to record manually.

## References

- Examples: latest `.md` in `docs/requirements/`.
- Requirement vs spec: requirement (What/Why) → `docs/requirements/`; technical spec (How) → `specs/*.spec.yaml`.
