# Document Language Policy

This document defines the language rules for documents that agents, commands, workflow guides, and reusable prompt sources read directly. It extends `.cursor/rules/language-policy.mdc`.

## 1. Tool-facing documents: English only

All documents that tools use directly must be written in **English** so that delegation, handoffs, and copied prompt blocks stay predictable and unambiguous.

| Scope | Paths | Rule |
|-------|-------|------|
| Rules, commands, skills, agents | `.cursor/rules/**`, `.cursor/commands/**`, `.cursor/skills/**`, `.cursor/agents/**` | English only |
| Core workflow guides | `docs/workflow/*.md` that define policy, steps, handoffs, or checklists | English only |
| Templates | `docs/template/*.md` | English only |
| Cursor subagent prompt sources | `docs/cursor-subagents/*.md` | English only |

The same rule applies to:

- section titles
- policy text
- copied handoff blocks
- example Task prompts
- placeholder text used to build prompts

## 2. User-facing output

User-facing assistant replies still follow the user's requested language. In this repository, that usually means Korean for assistant responses to the user.

This distinction is intentional:

- **Tool-facing** documents and prompt payloads: English
- **User-facing** assistant summaries and explanations: user-requested language

## 3. Requirement documents

- Requirement docs are authored in English first.
- The source-of-truth requirement content (§1, §2, §3, §5, §6) should remain English unless a documented stakeholder-facing final section is intentionally added after verification.
- If a Korean stakeholder-facing final summary is needed, it must be clearly marked as a final output section and must not be reused as tool-facing prompt source text.

## 4. Release artifacts vs release instructions

Release **instructions** and release **prompt source documents** must be in English because tools read them directly.

Stakeholder-facing **release artifacts** may remain Korean when appropriate, for example:

- `CHANGELOG.md` entries
- `docs/RELEASE_NOTES-*.md`

This means:

- `docs/workflow/RELEASE_CHECKLIST.md`, `.cursor/commands/release.md`, `.cursor/agents/Release.mdc`, and `docs/cursor-subagents/release.md` must be English.
- The generated release notes or changelog content may still be Korean when the release workflow says they are stakeholder-facing outputs.

## 5. Handoff prompt language

Whenever an agent invokes a subagent via `Task`, the **prompt payload itself** must be in English, even if:

- the user asked in Korean
- the main agent reports progress to the user in Korean
- the resulting stakeholder-facing artifact will later be Korean

The user-facing summary and the tool-facing prompt are separate outputs and may use different languages for this reason.

## 6. Audit rule

When a workflow or prompt-source change is complete, run an audit such as:

```bash
rg "[\\uAC00-\\uD7A3]" .cursor/rules .cursor/commands .cursor/skills .cursor/agents docs/workflow docs/template docs/cursor-subagents
```

Classify each hit as one of the following:

1. **Must translate now**: active tool-facing document
2. **Allowed stakeholder-facing output**: generated output or final summary not used as prompt source
3. **Archive / historical reference**: not part of the execution path

No active tool-facing file may remain mixed-language without an explicit exception classification.

## 7. Allowed exceptions outside the execution path

The following may remain outside the English-only execution path when they are clearly archival, analytical, or stakeholder-facing and are not used as tool prompts:

- `docs/workflow/archive/**`
- `docs/workflow/ANALYSIS-*.md`
- `docs/workflow/*-ANALYSIS*.md`
- `docs/workflow/DRYRUN-*.md`
- `docs/workflow/PLAN-*.md`
- `docs/workflow/QA-FEEDBACK-*.md`
- `docs/workflow/REVIEW-*.md`
- `docs/workflow/WHY-*.md`
- `docs/workflow/HANDOFF-CONTEXT-QUALITY-SESSION.md`
- `docs/workflow/CONTEXT-QUALITY-AND-ORCHESTRATION-MITIGATION.md`
- `docs/workflow/AGENT-INSTRUCTION-AND-PERFORMANCE.md`
- `docs/workflow/REQUIREMENT-DATE-YEAR-ANALYSIS.md`
- `docs/cursor-subagents/FRONTEND-IMPROVEMENT-POINTS.md`

If any of those files becomes a live prompt source later, translate it to English at that time.

## 8. References

- Rule: `.cursor/rules/language-policy.mdc`
- Requirement template: `docs/template/REQUIREMENT_TEMPLATE.md`
- Workflow: `docs/workflow/AGENT-COLLABORATION-ON-REQUIREMENT.md`, `docs/workflow/SUBAGENT-DELEGATION.md`
- Release flow: `docs/workflow/RELEASE_CHECKLIST.md`, `.cursor/agents/Release.mdc`
