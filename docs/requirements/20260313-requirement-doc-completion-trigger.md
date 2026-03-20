# 20260313 - Requirement doc completion trigger

## 1. User requirement

### Requirement description

Improve the Cursor hook behavior for requirement-document maintenance so that the completion action runs only when a single requirement document reaches its final completed state. The current hook model is based on file edits, so document-related automation can run too often and at an ambiguous timing during ordinary authoring. The improved behavior must detect a semantic completion transition for one requirement document instead of reacting to every save.

### User scenario

1. An author creates or edits a requirement document under `docs/requirements/` and saves it many times while filling §1, §2, §3, checklist items, and later verification details.
2. **Problem**: the current hook/event model is edit-driven, so it cannot distinguish an intermediate save from the moment when the requirement document is actually considered complete.
3. The author finishes one requirement document and marks it complete as part of the final workflow update.
4. The system must run the requirement-completion action exactly once for that document at the moment of completion, and must not run it for unrelated edits or repeated saves of the already-completed document.

### Expected outcome

- The completion action must apply only to `docs/requirements/yyyyMMdd-*.md` requirement documents and must exclude `docs/requirements/TOPIC-INDEX.md` and unrelated documentation files.
- The hook must treat a requirement document as entering the automation-visible final completed state only when that single document transitions from `- [ ] Requirement doc completed` to `- [x] Requirement doc completed`.
- Ordinary edits to an incomplete requirement document must not trigger the completion action.
- Edits to unrelated documents, workflow docs, templates, or index files must not trigger the requirement-document completion action.
- A repeated save of a document that is already in the completed state (`- [x] Requirement doc completed`) must not retrigger the completion action.
- If a completed document is later reverted to incomplete (`- [x]` to `- [ ]`) and then completed again (`- [ ]` to `- [x]`), the system may trigger again exactly once on the new completion transition; it must not trigger on the reset itself.
- If the saved content is malformed, partial, unreadable, or does not allow the hook to determine the completion marker safely, the hook must fail safe and perform no completion action.
- The completion action must be idempotent at the event boundary: one qualifying transition of one requirement document produces one completion action execution.

---

## 2. Design

### 2.1 Security review (optional; when PII / decryption / access control)

Not applicable. This requirement changes hook timing and requirement-document workflow semantics only. It does not expand PII, decryption, or access-control scope.

### Technical design

#### Codebase summary

- `.cursor/hooks.json` currently defines only one hook entry: `afterFileEdit`, which invokes `.cursor/hooks/after-file-edit-treemap.sh`.
- `.cursor/hooks/after-file-edit-treemap.sh` is file-path based. It regenerates the treemap for edits in `.cursor/rules/`, `.cursor/agents/`, `.cursor/commands/`, `.cursor/skills/`, `docs/workflow/`, `docs/template/`, and treemap-related script/template/i18n files. It does not model semantic completion of a requirement document.
- `docs/template/REQUIREMENT_TEMPLATE.md` defines the checklist item `- [ ] Requirement doc completed` in §4 and instructs maintainers to add the document to `docs/requirements/TOPIC-INDEX.md` after verification.
- `scripts/generate-requirements-index.sh` checks which requirement documents are not yet listed in `docs/requirements/TOPIC-INDEX.md`, but it is not currently tied to a semantic completion transition.
- Existing requirement documents in `docs/requirements/` commonly use `- [x] Requirement doc completed` as the machine-readable completion marker after the workflow reaches a completed state.

#### Problem analysis

1. The current hook entry point is an edit event, not a completion event. It cannot distinguish a draft save from the final save that marks a requirement document complete.
2. The completion timing is ambiguous unless the workflow documents and template define one canonical, machine-readable completion signal for hooks to inspect.
3. Without per-document state tracking, repeated saves of an already-completed document can retrigger the same action even though no new completion transition happened.
4. If the hook evaluates partially written or malformed content without a fail-safe rule, it can create false-positive completion actions.
5. Requirement-document completion maintenance and generic treemap regeneration are different concerns and must not be conflated for every documentation edit.

#### Solution approach

**Cursor tools:**

- Keep `afterFileEdit` as the raw Cursor event source, but add a semantic transition gate for requirement-document completion.
- The hook logic must inspect only requirement-document paths matching `docs/requirements/yyyyMMdd-*.md` and must exclude `TOPIC-INDEX.md`.
- The canonical automation trigger for this workflow must be the checklist transition of a single requirement document from `- [ ] Requirement doc completed` to `- [x] Requirement doc completed`. For hook purposes, this is the explicit definition of the final completed state.
- The hook implementation must maintain or derive the prior completion state per requirement-document path so it can detect only `incomplete -> complete` transitions.
- `complete -> complete` must produce no action.
- `complete -> incomplete` must produce no action, but it must reset the tracked state so that a later `incomplete -> complete` transition can trigger once again.
- If the hook cannot parse the file, cannot determine the prior state, or detects malformed/partial content, it must fail safe and produce no completion action.
- Requirement-document completion handling must be isolated from generic treemap regeneration. A requirement-document save must not trigger treemap regeneration unless the edited file independently matches the treemap-owned path rules.
- The completion action should execute requirement-finalization maintenance aligned with the workflow, including the requirement-index synchronization path, only once per qualifying transition.

**Documentation / workflow:**

- `docs/template/REQUIREMENT_TEMPLATE.md` must describe the checklist item as the canonical machine-readable completion marker used by automation, and it must clarify that authors check it only when the requirement document reaches its final completed state in the workflow.
- `docs/workflow/REQUIREMENTS-AUTHORING-WORKFLOW.md` and `docs/workflow/DEVELOPMENT_WORKFLOW.md` must align the same trigger semantics so that authors and implementers use one unambiguous completion rule.
- `docs/requirements/TOPIC-INDEX.md` and `scripts/generate-requirements-index.sh` must align with the completion action so requirement-index maintenance belongs to the completion flow rather than ordinary draft edits.
- If the repository keeps the treemap workflow for tool and workflow documents, that behavior must remain independent from the requirement-document completion trigger.

### Cursor tool update targets

- Related rules or commands must be reviewed if the workflow wording changes: `.cursor/rules/treemap-consistency.mdc`
- Workflow references that must align with the completion semantics: `docs/workflow/REQUIREMENTS-AUTHORING-WORKFLOW.md`, `docs/workflow/DEVELOPMENT_WORKFLOW.md`
- Requirement template and index flow that must align with the completion semantics: `docs/template/REQUIREMENT_TEMPLATE.md`, `docs/requirements/TOPIC-INDEX.md`, `scripts/generate-requirements-index.sh`

### Affected scopes and change targets (verification)

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|-----------|-----------------------------------------------|
| Backend | No | N/A |
| Frontend (config UI + view screen) | No | N/A |
| DB | No | N/A |
| Contract / Spec | No | N/A |
| Cursor tools (skills, specs) | Yes | Yes |

**Change target verification:** This requirement affects Cursor hook behavior and requirement/workflow documentation only. Backend, frontend, DB, and API contract scopes are not changed by the requested behavior.

### Planned change file list (expected change targets)

**(Planned at authoring. Implementing agent (Step 4) confirms or amends this list when implementation is complete.)**

#### Cursor tools

- `.cursor/hooks.json`
  - Must keep or adapt the hook entry so requirement-document completion logic is invoked from the actual Cursor edit event.
- `.cursor/hooks/after-file-edit-treemap.sh`
  - Was reviewed so treemap regeneration remains separate from requirement-document completion handling.
- `.cursor/hooks/after-requirement-doc-complete.sh`
  - Was added as the dedicated completion script, and it detects only the `incomplete -> complete` transition for one requirement document.
- `scripts/generate-requirements-index.sh`
  - Was updated to auto-add a completed requirement document to the best-matching `TOPIC-INDEX.md` section, with `misc` as the fallback when no stronger topic match exists.
- `scripts/test-requirement-doc-completion-hook.sh`
  - Was added to automate transition-detection coverage for incomplete saves, single completion, no retrigger, reset/re-complete, malformed hook input, and automatic `TOPIC-INDEX.md` insertion without duplicates.
- `.cursor/rules/treemap-consistency.mdc`
  - Was updated to keep treemap automation documentation aligned with the new independent requirement-completion hook.

#### Workflow / requirement documentation

- `docs/template/REQUIREMENT_TEMPLATE.md`
  - Was updated to define the canonical completion marker and explain that automation uses the checklist transition only when the document is finally completed.
- `docs/workflow/REQUIREMENTS-AUTHORING-WORKFLOW.md`
  - Was updated to align requirement-authoring guidance with the single completion-trigger rule.
- `docs/workflow/DEVELOPMENT_WORKFLOW.md`
  - Was updated to align the final documentation/update step with the same completion-trigger rule.
- `docs/requirements/TOPIC-INDEX.md`
  - Was updated to document that the completion hook auto-adds completed requirement docs to the best-matching topic section and falls back to `misc` when no clear topic match exists; it still must not be treated as a trigger source itself.
- `docs/cursor-tools-treemap.html`
  - Was regenerated after workflow/rule updates so the treemap stays aligned with the current hook configuration.

---

## 3. Test approach

### Test case list (required)

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|----|-------|------|------------------------------|-----------------|--------------------------------------------|
| TC-01 | Integration | Normal | Edit and save a requirement document that still contains `- [ ] Requirement doc completed` | No requirement-completion action runs | Integration (script/hook test) |
| TC-02 | Integration | Normal | Edit and save an unrelated document such as `docs/template/REQUIREMENT_TEMPLATE.md` or another workflow doc | No requirement-document completion action runs | Integration (script/hook test) |
| TC-03 | Integration | Normal | Save one requirement document after changing its checklist marker from `- [ ] Requirement doc completed` to `- [x] Requirement doc completed` | The completion action runs exactly once for that document | Integration (script/hook test) |
| TC-04 | Integration | Edge | Save the same requirement document again while it already contains `- [x] Requirement doc completed` and no reset happened | No second completion action runs | Integration (script/hook test) |
| TC-05 | Integration | Edge | Change one completed requirement document from `- [x]` back to `- [ ]`, save, then later change it again from `- [ ]` to `- [x]` | No action runs on the reset save; exactly one action runs on the later re-completion save | Integration (script/hook test) |
| TC-06 | Integration | Exception | Save `docs/requirements/TOPIC-INDEX.md` or run index maintenance without changing a requirement document from incomplete to complete | No requirement-document completion action runs | Integration (script/hook test) |
| TC-07 | Integration | Exception | Feed the hook malformed input, unreadable file content, or a partially written requirement file where the completion marker cannot be determined safely | The hook fails safe and runs no completion action | Unit or integration |
| TC-08 | Integration | Normal | Complete one requirement document, then complete a different requirement document later | Each document gets its own single completion action on its own completion transition; actions do not duplicate across saves | Integration (script/hook test) |
| TC-09 | Integration | Normal | Complete a requirement document that is not yet listed in `TOPIC-INDEX.md` | The hook adds exactly one `- doc-id | summary` line to the best-matching topic section, or to `misc` if no stronger topic match exists | Integration (script/hook test) |
| TC-10 | Integration | Edge | Complete a requirement document that was already auto-added to `TOPIC-INDEX.md`, then save it again without resetting completion | The index entry is not duplicated and no second completion action runs | Integration (script/hook test) |

### Test scenarios

#### Scenario 1: Incomplete requirement document save

1. Create or edit `docs/requirements/yyyyMMdd-sample.md` and keep the checklist line as `- [ ] Requirement doc completed`.
2. Save the file several times while editing other sections.
3. Verification: the completion action does not run.

#### Scenario 2: Single completion transition

1. Start with a requirement document whose checklist line is `- [ ] Requirement doc completed`.
2. Change only that marker to `- [x] Requirement doc completed` and save.
3. Save the file again without changing the marker.
4. Verification: the first qualifying save triggers exactly once; the second save triggers nothing.

#### Scenario 3: Reset and re-complete

1. Start with a requirement document already tracked as completed.
2. Change the marker to `- [ ] Requirement doc completed` and save.
3. Change it back to `- [x] Requirement doc completed` and save.
4. Verification: no action runs on the reset save; one action runs on the later re-completion save.

#### Scenario 4: Fail-safe parsing

1. Provide hook input with missing path metadata, unreadable file content, or a partially written requirement file.
2. Run the hook logic.
3. Verification: the hook exits safely without triggering the completion action.

### Test data

- Sample requirement document under `docs/requirements/` with the canonical checklist line in §4.
- One unrelated workflow or template document for negative testing.
- One completed requirement document and one incomplete requirement document for transition tracking tests.

### Test environment

- Workspace: local repository root
- Hook entry point: Cursor `afterFileEdit`
- Shell/runtime: repository-supported shell environment with the hook scripts available

## 4. Checklist

### Frontend verification
- [ ] Not applicable

### Backend verification
- [ ] Not applicable

### Integration
- [x] Transition detection verified for incomplete -> complete
- [x] No retrigger verified for complete -> complete
- [x] Fail-safe behavior verified for malformed or partial input
- [x] TOPIC-INDEX auto-add verified for matching topic and fallback topic

### Documentation
- [ ] Requirement doc completed
- [x] Hook trigger semantics aligned in workflow/template docs
- [x] Requirement-index maintenance path aligned with completion flow

## 5. Test results

### Test run date
- 2026-03-13

### Test results

#### Integration
Pass
- `bash scripts/test-requirement-doc-completion-hook.sh`
- Verified that incomplete requirement-doc saves do not trigger, unrelated docs do not trigger, a single `- [ ] Requirement doc completed` -> `- [x] Requirement doc completed` transition triggers exactly once, repeated saves do not retrigger, reset and re-complete trigger once again on the new completion transition, malformed hook input fails safe, and completed docs are auto-added to `TOPIC-INDEX.md` exactly once under the best-matching topic or `misc` fallback.

**Commands:**

```bash
bash scripts/test-requirement-doc-completion-hook.sh
node scripts/generate-treemap.js
```

**Outcome:**
- Requirement-document completion automation now runs only on the canonical completion transition for one requirement doc.
- Requirement-index maintenance now auto-reflects on completion instead of only reporting missing docs, while avoiding duplicate entries on repeated saves.
- Treemap regeneration remains independent from requirement-document completion handling and the treemap was regenerated after the workflow/rule updates.

### Issues found and resolution

- None yet

### Next steps
1. If this workflow is promoted further, QA can add a repository-level verification step for the completion hook alongside existing tool-workflow checks.

## 7. Final version (Korean) — add after all verification is complete

### Final Korean summary
- To be added after QA verification is complete.

---

**Author**: Requirements subagent
**Date**: 2026-03-13
**Status**: Ready for implementation
