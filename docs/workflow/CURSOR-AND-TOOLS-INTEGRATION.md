# Cursor and tools integration

This document summarizes how rules, commands, skills, workflow docs, and subagents fit together.

## 1. Rules

Rules define always-on guardrails such as language policy, delegation gates, security boundaries, and workflow order.

## 2. Commands

Commands provide repeatable task entry points such as test execution, verification, release handoff, and commit completion.

## 3. Skills

Skills provide focused domain knowledge or workflow knowledge. They should stay concise and point to the canonical workflow or design docs for detail.

## 4. Workflow docs

Workflow docs define the order of work, handoff requirements, language rules, and release behavior.

## 5. Subagents

Subagents own scoped work such as Requirements, Security, Contract, Backend, Frontend, QA, Documentation, and Release.

## 6. Design principle

- Keep the execution-path docs in English.
- Keep user-facing assistant replies in the user's language.
- Keep ownership clear so the main agent does not duplicate subagent work.

## References

- `docs/workflow/WORKFLOW_CHECKLIST.md`
- `docs/workflow/SUBAGENT-DELEGATION.md`
- `docs/workflow/AGENT-COLLABORATION-ON-REQUIREMENT.md`
- `docs/workflow/DOCUMENT-LANGUAGE-POLICY.md`
