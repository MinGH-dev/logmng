# Document Language Policy

This document defines language rules for **all documents that tools (agents, commands, workflow) use**, and for **requirement documents** and **commit messages**. It extends `.cursor/rules/language-policy.mdc`.

---

## 1. Tool-facing documents (English only)

All documents that agents, commands, and workflow reference **must be in English** so that handoffs and instructions are unambiguous and token-efficient.

| Scope | Paths | Rule |
|-------|--------|------|
| Rules, commands, skills, agents | `.cursor/rules/**`, `.cursor/commands/**`, `.cursor/skills/**`, `.cursor/agents/**` | English only (already in language-policy.mdc). |
| Workflow and collaboration | `docs/workflow/*.md` | English. Section titles, procedure text, handoff rules, and policy text in English. |
| Templates | `docs/template/*.md` | English. Placeholders and section headers in English. |
| Subagent prompts | `docs/cursor-subagents/*.md` | English. |
| Design standards (referenced by UX/Frontend) | `docs/design/*.md` | Prefer English; if existing content is in another language, add an English summary or migrate to English when updating. |

**Rationale**: Instruction-following and delegation work best when the instruction layer is in one language (English). User-facing output (e.g. replies to the user) still follows the user's requested language per language-policy.mdc.

---

## 2. Requirement documents: English first, Korean final after verification

### 2.1 Authoring (Step 1)

- **Requirements** subagent (and anyone creating or updating a requirement doc) **authors the requirement in English first**.
- File name: `docs/requirements/yyyyMMdd-short-name.md` (lowercase English, hyphens).
- Sections §1 (user requirement, scenario, expected outcome), §2 (design, change list), and §3 (test plan) are written in **English**.
- Use `docs/template/REQUIREMENT_TEMPLATE.md` (English version).

### 2.2 During implementation and verification

- All steps (Security, Contract, Frontend, Backend, DB, QA, etc.) use the **English** requirement doc. §5 (test results) and §6 (error remedy) may be filled in English.

### 2.3 Final Korean version (after all verification is complete)

- **After** QA has completed verification (all checks pass, §5/§6 updated) and **before or with the final commit**, a **final Korean version** of the requirement document is produced so that stakeholders can read the requirement in Korean.
- **Options** (project may choose one):
  - **Option A**: Add a section **"§ Final version (Korean)"** (or **"7. 최종 요약 (한글)"**) at the end of the same document, containing a concise Korean summary of §1 (user requirement and expected outcome) and, if useful, §2 (design summary) and §3 (test approach). The body of the doc remains in English; only this section is in Korean.
  - **Option B**: Create a companion file `docs/requirements/yyyyMMdd-short-name-ko.md` with the full content in Korean, written after verification is complete. The English doc remains the source of truth; the Korean doc is the final deliverable for Korean readers.
- The agent that performs the **final commit** (typically QA) or **Documentation** is responsible for ensuring the final Korean version exists (per the chosen option) when the requirement is closed.

---

## 3. Commit message: reference requirement doc for traceability

- Every commit that completes a requirement or bugfix **must reference the requirement document** so that **per commit version** it is clear what work was done.
- **Format**: Include the requirement doc path or short ID in the commit message. Examples:
  - `feat: frontend - UX standards compliance audit, §5 updated (req 20260225-ux-standards-compliance-audit)`
  - `fix: frontend - aria-invalid/aria-describedby for date validation, §5·§6 updated (req 20260225-ux-standards-compliance-audit-bugfix-1)`
- **Rule**: The commit message must contain either:
  - `docs/requirements/yyyyMMdd-name.md`, or
  - `req yyyyMMdd-name` / `(요건 yyyyMMdd-name)` (short form).
- This allows anyone to map a commit to the requirement (and thus to the scope, test plan, and results) by reading the requirement doc. See `.cursor/commands/commit-on-complete.md`.

---

## 4. References

- **Language policy (rules/commands/agents)**: `.cursor/rules/language-policy.mdc`
- **Commit when complete**: `.cursor/commands/commit-on-complete.md`
- **Requirement template**: `docs/template/REQUIREMENT_TEMPLATE.md`
- **Workflow**: `docs/workflow/AGENT-COLLABORATION-ON-REQUIREMENT.md`, `docs/workflow/SUBAGENT-DELEGATION.md`
