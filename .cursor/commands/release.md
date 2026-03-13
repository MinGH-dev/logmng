# Release (CHANGELOG + commit + push)

Run the **release** workflow and **push to remote**. The user wants release **including push**.

## What to do

**Do not** perform the release steps in this chat. **Invoke the Release subagent** via the **Task** tool with the handoff below so that Release updates CHANGELOG/checklist, commits if needed, and **runs git push**.

## Handoff to Release

Use **Task** with:

- **subagent_type**: `Release`
- **description**: `Release (CHANGELOG + commit + push)`
- **prompt**: (copy the following)

```
User requested **release including push**. If the current changes include tool or treemap updates (.cursor/, docs/workflow/, scripts/generate-treemap.js, docs/cursor-tools-treemap.html), include them in the commit. Please do the following in order:

1. **CHANGELOG / release checklist**: Update CHANGELOG.md and docs/workflow/RELEASE_CHECKLIST.md for the current changes (e.g. today's date, Korean entries). See docs/workflow/RELEASE_CHECKLIST.md and docs/workflow/DOCUMENT-LANGUAGE-POLICY.md §3–§4.

2. **Commit**: If there are uncommitted changes, stage and commit **all** of them (including tool/treemap files above) per `.cursor/commands/commit-on-complete.md`. Use a clear release/chore message in English when the change is not requirement-driven (e.g. `chore: update changelog and release checklist (release)` or `chore: align tooling workflow language and release flow (release)`). Include the requirement doc reference when the commit closes a requirement.

3. **Push**: Run `git push` (or `git push origin <current-branch>`). Do not force-push. Report success or any error.

Reference: .cursor/agents/Release.mdc (When the user asks to push), docs/workflow/RELEASE_CHECKLIST.md step 5.
```

## Fallback

If the Task tool is unavailable, tell the user to switch to the **Release** subagent and pass the same instructions (release including push).

## References

- Release agent: `.cursor/agents/Release.mdc`
- Release checklist: `docs/workflow/RELEASE_CHECKLIST.md`
- Commit rules: `.cursor/commands/commit-on-complete.md`
- Delegation: `docs/workflow/SUBAGENT-DELEGATION.md` §5 (release with push)
