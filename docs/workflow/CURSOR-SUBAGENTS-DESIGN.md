# Cursor subagents design

This document summarizes the role boundaries and collaboration model for project subagents.

## 1. Core idea

Use dedicated subagents for requirement authoring, expert review, implementation, QA, documentation, and release so each scope has a clear owner.

## 2. Role groups

- **Authoring / review**: Requirements, RequirementsPastSearch, Security, Contract, DBA, Architecture, Consistency, UX, Review
- **Implementation**: Backend, Frontend, DB and their module-scoped delegates
- **Verification / finish**: QA, Documentation, Release

## 3. Ownership rule

- One scope owner per implementation file area.
- Main chat orchestrates and delegates.
- Subagents do not expand into another owned scope without an explicit handoff.

## 4. Requirement-driven sequence

1. Requirements
2. Optional expert review (Security, Contract, DBA, Architecture, Consistency, UX)
3. Implementation (Backend, Frontend, DB)
4. Optional Review
5. QA
6. Documentation / Release

## 5. Language rule

- Tool-facing prompts and workflow docs must be English.
- User-facing assistant replies follow the user's requested language.

## 6. Scope note

This document is a high-level summary. The detailed operational rules live in:

- `docs/workflow/SUBAGENT-DELEGATION.md`
- `docs/workflow/AGENT-COLLABORATION-ON-REQUIREMENT.md`
- `docs/workflow/HANDOFF-CHECKLIST.md`
- `docs/workflow/DOCUMENT-LANGUAGE-POLICY.md`
