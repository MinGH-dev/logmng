# 20260313 - English-only tooling workflow consistency

## 0. Completion and release handoff

The user requested that, after the workflow/tooling changes are complete, the process must use the **Release** subagent and **push the current branch**.

- Verification and requirement updates remain part of the normal workflow.
- After the implementation is verified, the final handoff must explicitly invoke **Release** to update release artifacts, perform any release-scoped commit work that still remains, and run `git push origin <current-branch>` without force.
- User-facing assistant responses remain in Korean, but the **tool-facing handoff text and referenced workflow documents must be English**.

---

## 1. User requirement

### Requirement description

Unify the repository's **tool-facing workflow language** so that delegation, handoff prompts, commands, skills, rules, templates, and subagent prompt source documents operate in **English only**. The current repository still contains Korean or mixed-language text in files that agents and tools read directly, which causes inconsistent prompt generation and uneven delegation behavior.

This requirement is about **workflow/delegation/prompt-language consistency**, not product features. It must preserve the existing rule that **user-facing assistant replies remain Korean**, while making the **tooling instruction layer** consistently English.

The requirement must also define a repeatable way to **audit and validate remaining mixed-language files** in tool-facing directories, and it must make the **Release** subagent handoff explicit for final release/push behavior.

### User scenario

1. The main agent prepares a handoff to a subagent by reading workflow documents, commands, rules, skills, and subagent prompt source files.
2. Some of those source documents are fully English, while others still contain Korean or mixed-language sections, examples, headings, or prompt text.
3. Because the source documents are inconsistent, the generated subagent prompt and the delegation behavior are sometimes English and sometimes Korean.
4. The user expects the system to keep **assistant-to-user communication in Korean**, but to keep **tool-consumed workflow and prompt documents in English only** so delegation is predictable.
5. After the workflow changes are implemented and verified, the user expects the final completion path to use the **Release** subagent and push the current branch.

### Expected outcome

- All active **tool-facing documents** under the scoped directories use **English-only instruction text**: `.cursor/rules/`, `.cursor/commands/`, `.cursor/skills/`, `.cursor/agents/`, `docs/workflow/`, `docs/template/`, and `docs/cursor-subagents/`.
- Delegation guidance explicitly states that **Task/subagent handoff prompts** and copied prompt blocks must be authored in **English**, even when the user-facing summary sent by the assistant remains Korean.
- The language policy clearly separates **tool-facing English** from **user-facing Korean**, and it also distinguishes **tool-consumed docs** from **stakeholder-facing outputs** such as final Korean summaries or release notes.
- The workflow defines a repeatable **mixed-language audit** for the scoped directories and requires either:
  - zero Korean text in active tool-facing files, or
  - an explicit classification of any remaining hit as an allowed exception outside the tool-consumed execution path.
- The release flow explicitly states that, when the user requests release/push after completion, the handoff must go to **Release** and the branch must be pushed after release updates are complete.

## 2. Design

### 2.1 Security review (optional; when PII / decryption / access control)

Not applicable. This requirement changes workflow, prompt language, and release handoff behavior only; it does not change access control, PII handling, or decryption scope.

### Technical design

#### Codebase summary

- The repository already contains an English-first policy in `docs/workflow/DOCUMENT-LANGUAGE-POLICY.md`, but multiple active tool-facing files still contain Korean or mixed-language content.
- Representative active files with mixed-language tooling text include:
  - `docs/workflow/WORKFLOW_CHECKLIST.md`
  - `docs/workflow/RELEASE_CHECKLIST.md`
  - `docs/workflow/SUBAGENT-DELEGATION.md`
  - `docs/workflow/AGENT-COLLABORATION-ON-REQUIREMENT.md`
  - `.cursor/commands/release.md`
  - `docs/cursor-subagents/release.md`
- Repository audit results also show remaining Korean text in other active tool-facing locations such as:
  - `.cursor/rules/*.mdc`
  - `.cursor/commands/*.md`
  - `.cursor/skills/**/SKILL.md`
  - `docs/template/*.md`
  - `docs/cursor-subagents/*.md`
  - multiple top-level files in `docs/workflow/`
- Current release ownership is ambiguous:
  - `docs/workflow/SUBAGENT-DELEGATION.md` says QA performs commit/push after verification in the normal delegated flow.
  - `.cursor/agents/Release.mdc`, `.cursor/commands/release.md`, and `docs/workflow/RELEASE_CHECKLIST.md` also describe release-driven commit/push behavior.
- Current language guidance is also incomplete for delegation prompts:
  - user-facing response language is defined,
  - but the workflow does not consistently require that **subagent handoff prompt text itself** be English.

#### Problem analysis

1. **Prompt-language inconsistency**: When tool-facing source documents mix English and Korean, the generated Task prompts and copied handoff blocks become inconsistent across agents and steps.
2. **Policy-to-document mismatch**: The repository already states that workflow/tool documents should be English, but active files in those directories still contain Korean or mixed-language sections.
3. **Release ownership ambiguity**: The final push path is not described consistently between QA-driven completion and Release-driven completion, which makes it unclear when Release must be invoked.
4. **No enforced audit gate**: There is no single documented validation step that scans active tool-facing directories for remaining Korean text and classifies or removes the hits.
5. **Template drift**: Templates and subagent prompt source docs still carry mixed-language placeholders or explanations, which allows the inconsistency to re-enter future prompts and requirement docs.

#### Solution approach

Structure by scope.

**Workflow / policy:**

- Update `docs/workflow/DOCUMENT-LANGUAGE-POLICY.md` so it becomes the explicit single source of truth for:
  - English-only tool-facing documents,
  - Korean user-facing assistant replies,
  - which generated artifacts may remain Korean because they are stakeholder-facing rather than tool-consumed,
  - English-only Task/subagent handoff prompt text.
- Update `docs/workflow/SUBAGENT-DELEGATION.md`, `docs/workflow/AGENT-COLLABORATION-ON-REQUIREMENT.md`, `docs/workflow/HANDOFF-CHECKLIST.md`, and `docs/workflow/WORKFLOW_CHECKLIST.md` so every delegation example, copied prompt block, and handoff rule is written in English only and explicitly requires English prompt payloads.
- Resolve the completion-path rule:
  - QA remains responsible for verification and requirement test-result updates in the normal delegated workflow.
  - When the user explicitly requests **release including push**, the post-verification handoff must invoke **Release** for release-scoped updates and branch push.
  - The wording across delegation, collaboration, release checklist, and release command docs must align on that rule.

**Commands / subagent prompts:**

- Update `.cursor/commands/release.md` so the handoff instructions, examples, and commit/push guidance are English-only while still allowing Korean stakeholder-facing outputs where policy says so.
- Update `.cursor/agents/Release.mdc` and `docs/cursor-subagents/release.md` so the Release agent prompt source is English-only and matches the final workflow rule for release-and-push requests.
- Update any active `docs/cursor-subagents/*.md`, `.cursor/commands/*.md`, `.cursor/rules/*.mdc`, and `.cursor/skills/**/SKILL.md` files returned by the language audit when they contain Korean or mixed-language instruction text that tools may read directly.

**Templates / future authoring:**

- Update `docs/template/REQUIREMENT_TEMPLATE.md`, `docs/template/ERROR_FIX_RESULT_TEMPLATE.md`, and other active templates in `docs/template/` so headings, placeholders, and instructions stay English-only.
- Remove mixed-language prompt fragments from reusable templates or replace them with English-only wording that still references Korean user-facing outputs when necessary.

**Validation / audit:**

- Add or update a documented validation step in the workflow so implementation must run a repository audit similar to:
  - `rg "[가-힣]" .cursor/rules .cursor/commands .cursor/skills .cursor/agents docs/workflow docs/template docs/cursor-subagents`
- The validation rule must classify hits into:
  - **Must translate now**: active tool-facing files,
  - **Allowed exception**: user-facing output files or generated artifacts outside the tool-consumed path,
  - **Archive / historical**: explicitly out of execution scope when the workflow does not load them.
- The implementation is complete only when the scoped active files either contain no Korean text or any remaining hit is documented as an allowed non-tool exception.

**Cursor tool update targets:**

- Because this requirement changes the workflow/delegation model, the implementation must verify and update the tool-facing knowledge sources that can reintroduce mixed-language prompts:
  - `.cursor/rules/language-policy.mdc`
  - `.cursor/rules/core-principles.mdc`
  - `.cursor/commands/release.md`
  - `.cursor/skills/requirement-doc/SKILL.md`
  - `.cursor/skills/dev-workflow/SKILL.md`
  - `.cursor/agents/Release.mdc`
  - `docs/cursor-subagents/release.md`
  - any additional active skill/rule/command/prompt files returned by the audit

### Affected scopes and change targets (verification)

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|-----------|-----------------------------------------------|
| Backend | No | N/A |
| Frontend (config UI + view screen) | No | N/A |
| DB | No | N/A |
| Contract / Spec | No | N/A |
| Cursor tools (skills, specs) | Yes | Yes |

**Change target verification:** Completed for the scoped documentation/workflow task. The requirement covers policy, delegation, release handoff, templates, and audit/validation rules, and it explicitly excludes product-feature code changes.

### Planned change file list (expected change targets)

**(Planned at authoring. Implementing agent (Step 4) confirms or amends this list when implementation is complete.)**

Use requirement tone in this list: the implementation must align, verify, and confirm the English-only tooling rule; it must not treat mixed-language active tooling docs as acceptable drift.

#### Workflow / policy

- `docs/workflow/DOCUMENT-LANGUAGE-POLICY.md`
  - Must clarify English-only rules for tool-consumed docs, English-only subagent handoff prompts, allowed stakeholder-facing Korean outputs, and the Release handoff boundary.
- `docs/workflow/SUBAGENT-DELEGATION.md`
  - Must align delegation examples and final push ownership with the English-only prompt rule and Release-after-completion behavior.
- `docs/workflow/AGENT-COLLABORATION-ON-REQUIREMENT.md`
  - Must align Step 5 and Step 6 handoff wording with the Release invocation rule when the user requested release/push.
- `docs/workflow/HANDOFF-CHECKLIST.md`
  - Must require English-only handoff payload text and keep user-facing language as a separate concern.
- `docs/workflow/WORKFLOW_CHECKLIST.md`
  - Must be translated to English and keep the same gate/order semantics.
- `docs/workflow/RELEASE_CHECKLIST.md`
  - Must be translated to English and must define the Release-owned release/push path consistently with the delegation docs.

#### Commands / rules / skills / agent prompts

- `.cursor/commands/release.md`
  - Must provide an English-only handoff block for Release and must align release/push instructions with the workflow policy.
- `.cursor/agents/Release.mdc`
  - Must be English-only and must match the release ownership defined in workflow docs.
- `.cursor/rules/language-policy.mdc`
  - Must align with the documented English-only tooling rule and Korean user-facing output rule.
- `.cursor/rules/core-principles.mdc`
  - Must keep the English-only tooling/document expectation consistent with the workflow policy.
- `.cursor/skills/requirement-doc/SKILL.md`
  - Must reference the English-only tooling rule when requirements touch workflow/prompt documents.
- `.cursor/skills/dev-workflow/SKILL.md`
  - Must reference the updated workflow checklist and validation gate if needed.

#### Templates / subagent prompt source docs

- `docs/template/REQUIREMENT_TEMPLATE.md`
  - Must remain English-only in headings, placeholders, and instructions.
- `docs/template/ERROR_FIX_RESULT_TEMPLATE.md`
  - Must remain English-only in headings, placeholders, and instructions.
- `docs/cursor-subagents/release.md`
  - Must be translated to English and aligned with `.cursor/agents/Release.mdc`.
- `docs/cursor-subagents/*.md`
  - Any active prompt source file returned by the audit must be translated when it still contains Korean instruction text.

#### Audit sweep

- `.cursor/rules/*.mdc`
  - Audit all active rule files for remaining Korean instruction text; translate any active mixed-language hits.
- `.cursor/commands/*.md`
  - Audit all active command files for remaining Korean instruction text; translate any active mixed-language hits.
- `.cursor/skills/**/SKILL.md`
  - Audit all active skill files for remaining Korean instruction text; translate any active mixed-language hits.
- `docs/workflow/*.md`
  - Audit all active workflow files for remaining Korean instruction text; translate any active mixed-language hits or classify archival exceptions explicitly.
- `docs/template/*.md`
  - Audit all active template files for remaining Korean instruction text; translate any active mixed-language hits.
- `docs/cursor-subagents/*.md`
  - Audit all active prompt source docs for remaining Korean instruction text; translate any active mixed-language hits.

## 3. Test approach

### Test case list (required)

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|----|-------|------|------------------------------|-----------------|--------------------------------------------|
| TC-01 | Integration | Normal | Run `rg "[가-힣]"` across `.cursor/rules`, `.cursor/commands`, `.cursor/skills`, `.cursor/agents`, `docs/workflow`, `docs/template`, and `docs/cursor-subagents` after implementation | No Korean text remains in active tool-facing files, or every remaining hit is explicitly classified as an allowed non-tool exception | Manual |
| TC-02 | Integration | Normal | Review `docs/workflow/DOCUMENT-LANGUAGE-POLICY.md`, `docs/workflow/SUBAGENT-DELEGATION.md`, and `docs/workflow/AGENT-COLLABORATION-ON-REQUIREMENT.md` after implementation | Policy and delegation docs consistently say: user-facing replies remain Korean, but subagent Task prompts and tool-facing docs are English-only | Manual |
| TC-03 | Integration | Normal | Review `.cursor/commands/release.md`, `.cursor/agents/Release.mdc`, `docs/cursor-subagents/release.md`, and `docs/workflow/RELEASE_CHECKLIST.md` | Release-related tool docs are English-only and describe one consistent release/push flow | Manual |
| TC-04 | Integration | Regression | Compare pre-change mixed-language examples in active files with post-change versions | All active delegation/prompt/release examples use English-only wording; no Korean prompt fragments remain in copied handoff blocks | Manual |
| TC-05 | Integration | Normal | Simulate a user request that says "release including push" and inspect the documented handoff path | Workflow says: verify first, then invoke Release for release-scoped updates and `git push origin <current-branch>` without force | Manual |
| TC-06 | Integration | Normal | Review `docs/template/*.md` and `docs/cursor-subagents/*.md` included in the implementation scope | Templates and prompt source docs are English-only and do not reintroduce mixed-language prompt text | Manual |
| TC-07 | Integration | Edge | Review any remaining audit hits and their classification | No active tool-facing file is left mixed-language without an explicit rationale; archive or stakeholder-facing exceptions are clearly separated from execution-path docs | Manual |

### Test scenarios

#### Scenario 1: English-only tooling audit

1. Run the documented `rg "[가-힣]"` audit across the scoped tool-facing directories.
2. Inspect every hit and classify it as active tooling, allowed stakeholder-facing output, or archive/historical content.
3. Verify that no active tooling file remains mixed-language after the implementation is complete.

#### Scenario 2: Delegation prompt consistency

1. Open the updated language policy and delegation workflow documents.
2. Inspect the sections that describe Task/subagent invocation, copied prompt blocks, and user-facing response language.
3. Verify that English-only prompt payloads are required while Korean remains limited to user-facing assistant responses.

#### Scenario 3: Release and push handoff

1. Open the updated release command, Release agent prompt, Release subagent prompt source, and release checklist.
2. Trace the completion path for a user request that explicitly asks for release and push after verification.
3. Verify that the documented flow invokes Release and pushes the current branch in a consistent way.

#### Scenario 4: Future authoring guardrail

1. Open the updated templates and any audited skill/rule/command files.
2. Confirm that headings, instructions, placeholders, and reusable prompt snippets are English-only.
3. Verify that future requirement and handoff authoring will not reintroduce Korean prompt text into tool-consumed documents.

### Test data

- Representative audit command:
  - `rg "[가-힣]" .cursor/rules .cursor/commands .cursor/skills .cursor/agents docs/workflow docs/template docs/cursor-subagents`
- Representative files currently known to require verification:
  - `docs/workflow/WORKFLOW_CHECKLIST.md`
  - `docs/workflow/RELEASE_CHECKLIST.md`
  - `docs/workflow/SUBAGENT-DELEGATION.md`
  - `.cursor/commands/release.md`
  - `docs/cursor-subagents/release.md`

### Test environment

- Workspace root: `/Volumes/T7/dev/logmng_frontend/dev`
- Verification method: document review plus repository search
- Git branch expectation: perform implementation on a feature branch, then push the current branch only after the release handoff is complete

## 4. Checklist

### Frontend verification
- [ ] Not applicable

### Backend verification
- [ ] Not applicable

### Integration
- [ ] English-only tooling audit completed
- [ ] Delegation and release handoff wording aligned
- [ ] Remaining mixed-language hits classified

### Documentation
- [x] Requirement doc completed
- [ ] Workflow/prompt/template docs updated in implementation

## 5. Test results

Pending implementation and verification.

---

**Author**: Requirements subagent
**Date**: 2026-03-13
**Status**: Ready for implementation
