---
name: requirement-doc
description: Create or update requirement documents using project template and naming (dev workspace only). Use when writing a requirement doc, creating a new requirement file, or when the user mentions requirements or docs/requirements.
---

# Requirement document

**Skill usage visibility**: When you use this skill to answer, state at the start of your response: `[Skill used: requirement-doc]`

**Delegation**: When the project uses subagent delegation (`.cursor/rules/agent-collaboration.mdc`, `docs/workflow/SUBAGENT-DELEGATION.md`), the **main agent** does **not** use this skill to write the requirement doc — it delegates **Step 1 to the Requirements subagent**. This skill is for the **Requirements** subagent, or when the user said "code only here", "skip subagent", or "do it in this chat".

Write requirement docs per the rules below. **Do not write §1 (user scenario, expected outcome) and §2 (codebase summary, problem analysis, solution) from Requirements' sole judgment.** Instead: obtain **parallel** input from experts and from codebase investigation; **orchestrate** (merge) that input into §1·§2; then finalize §3. **Read** `docs/workflow/REQUIREMENTS-AUTHORING-WORKFLOW.md` for the full procedure (§1.1, §1.2, §1.4). The §2 **"변경 파일 목록"** is **tentative**; the implementing agent (Step 4) **confirms or updates** it when implementation is done (REQUIREMENTS-AUTHORING-WORKFLOW.md §1.2).

## Language and lifecycle

- **Author in English first**: §1, §2, §3 (and §5, §6 when filled) are written in **English**. Use the English template `docs/template/REQUIREMENT_TEMPLATE.md`.
- **Final Korean version**: After **all verification is complete**, add **§ Final version (Korean)** in the same doc (or create `yyyyMMdd-name-ko.md`) so the requirement is available in Korean. See `docs/workflow/DOCUMENT-LANGUAGE-POLICY.md` §2.
- **Commit message**: Must reference this requirement doc (e.g. `req yyyyMMdd-name`) so each commit is traceable. See `.cursor/commands/commit-on-complete.md`.

## Path and name

- **Path**: `docs/requirements/yyyyMMdd-name.md`
- **Format**: `yyyyMMdd` (8 digits), name in lowercase English and hyphens (e.g. `20260220-image-log-search.md`)

## Date source (avoid wrong year)

- **Current year and date**: Read **`.cursor/CURRENT-DATE-CONVENTION.md`** and use the **current year** (and month/day rules) from that file for:
  - Requirement doc **filename** prefix `yyyyMMdd`
  - **In-document dates** (§5 test run date, 작성일, Completed, Date, verification date, etc.)
- That file overrides any incorrect "Today's date" from the conversation context (e.g. user_info showing a wrong year). Use the year from the convention file; use month/day from the convention rules or from context when the context year is wrong.
- If the user explicitly states the current year or date, prefer that for the session.

## Template

Use `docs/template/REQUIREMENT_TEMPLATE.md`. Structure:

1. User requirement (description, scenario, expected outcome) — English
2. Design (technical design, file change list) — English
3. Test approach — English
4. Checklist (frontend/backend/integration/docs)
5. Test results (date, result, issues and resolution)
7. Final version (Korean) — add after verification complete (see DOCUMENT-LANGUAGE-POLICY)

## §3 domain-specific completeness

When the requirement touches a domain that has its own **completeness checklist** in a skill, **read and apply that checklist** before finalizing §3. Examples:

- **Permission / access control**: Use `api-permission-map` skill → §Requirement doc completeness checklist.
- **Other domains**: If a domain skill (e.g. `log-search-domain`, `search-history-decrypt-domain`) defines a completeness section, apply it.

This keeps the requirement-doc skill domain-agnostic; domain knowledge lives in the domain skill.

## For error/bug-fix requirements

- **After the fix**: **Always** add or update **"6. Error remedy result (cause and actions)"** in that requirement doc, without the user asking. Do not omit.
- **Same requirement ID**: Record only in the same doc (`docs/requirements/yyyyMMdd-name.md`). Do not create a separate "remedy result" file.
- **Fields**: Root cause, Actions taken, Result, Completed at. Template: `docs/template/ERROR_FIX_RESULT_TEMPLATE.md`
- Command `/record-error-fix` can be used when the user only wants to record manually.

## References

- Examples: latest `.md` in `docs/requirements/`.
- Requirement vs spec: requirement (What/Why) → `docs/requirements/`; technical spec (How) → `specs/*.spec.yaml`.
