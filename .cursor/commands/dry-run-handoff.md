# Dry-run handoff flow verification

Verify that the current workflow rules produce correct handoff prompts. **No code changes.** Generate prompts only.

## Procedure

1. **Pick a virtual requirement** that exercises the project's domain (e.g. log search, permission, decrypt approval, CSV export). Choose one that involves Backend + at least one expert (Security or Contract) so the handoff checklist is non-trivially exercised.

2. **Simulate Requirements → Backend → QA** handoff chain:

   a. **Requirements handoff** (main → Requirements):
      - Show the Task invocation the main agent would send.
      - Confirm: main does NOT author §1·§2·§3 (per `agent-collaboration.mdc` Step 1).
      - Confirm: prompt instructs Requirements to follow `AGENT-COLLABORATION-ON-REQUIREMENT.md` §1.1 (hybrid consultation: skills-first → codebase investigation → selective experts).

   b. **Backend handoff** (main → Backend):
      - Show the full Task prompt the main agent would build.
      - **Check each item** in `docs/workflow/HANDOFF-CHECKLIST.md` (Backend):
        - [ ] §1 summary
        - [ ] §2 Backend subsection
        - [ ] §2.1 Security (if applicable)
        - [ ] Contract/spec reference
        - [ ] §3 Backend-scope TCs (with Scope tag)
        - [ ] Cross-scope note
      - Confirm: prompt uses scope-specific excerpts, not the full doc.

   c. **QA handoff** (main → QA):
      - Show the full Task prompt.
      - **Check each item** in `docs/workflow/HANDOFF-CHECKLIST.md` (QA):
        - [ ] §1 summary + §3 full TC list
        - [ ] Build/restart confirmation
        - [ ] Requirement doc path for §5/§6 update

3. **Verification table**: Summarize which rules were exercised and whether each passed:

   | Rule / Document | Check | Pass? |
   |-----------------|-------|-------|
   | `agent-collaboration.mdc` Step 1 gate | Main does not author §1·§2·§3 | |
   | `agent-collaboration.mdc` §3 gate | §3 exists before Step 4 | |
   | `AGENT-COLLABORATION-ON-REQUIREMENT.md` §1.1 | Hybrid consultation (skills + tools + selective experts) | |
   | `HANDOFF-CHECKLIST.md` Backend | All 6 items present | |
   | `HANDOFF-CHECKLIST.md` QA | All 3 items present | |
   | `REQUIREMENT_TEMPLATE.md` §3 Scope tag | TCs have Scope column | |
   | `CONTEXT-QUALITY-AND-ORCHESTRATION-MITIGATION.md` §4.1 | Scope-specific excerpts (not full doc) | |

4. **Report**: State pass/fail for each check. If any fail, identify which rule was violated and suggest a fix.
