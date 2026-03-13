# Release Checklist

This checklist defines the release sequence. The **Release** subagent maintains this document and uses it when the workflow reaches release scope.

## Role boundary

- Release owns: `CHANGELOG.md`, release notes, version guidance, release checklist, and the final release-and-push step when the user explicitly asks for it.
- Documentation owns: README, QUICK_START, runbooks, and other user or operations guides.

## Language rule

- This checklist and any copied handoff instructions must be in **English**.
- Stakeholder-facing release artifacts such as changelog entries or release notes may be **Korean**.

## Sequence

1. Confirm the requirement is complete.
   - Verification passed.
   - Requirement doc §5 and §6 are up to date if applicable.
   - Local changes are ready to commit.
2. Update release artifacts.
   - Add the release entry to `CHANGELOG.md`.
   - Update or create `docs/RELEASE_NOTES-yyyyMMdd-*.md` if the release scope requires it.
   - Document any version recommendation if needed.
3. Commit release-scoped changes.
   - Use `.cursor/commands/commit-on-complete.md` when the release closes a requirement.
   - For release-only doc updates, use a sensible release/chore commit message.
4. Run build/test checks if the release scope requires them.
   - Backend: `cd backend && mvn clean package` or equivalent
   - Frontend: `cd frontend && npm run build` and any required tests
5. Push the current branch when the user explicitly requested push or when the handoff says **release including push**.
   - Preferred: `git push origin <current-branch>`
   - If a tag is created, push the tag separately.
6. Deploy according to the environment-specific process when deployment is in scope.

## Execution rule

The Release subagent may execute git operations when the user explicitly requests push, for example:

- "push this"
- "release including push"
- "finish with Release and push"

In that case, Release may:

1. inspect `git status`
2. stage and commit pending release-scoped changes
3. run `git push` without force

## Notes

- Do not force-push unless the user explicitly requests it.
- Do not commit secrets.
- Do not treat this checklist as a replacement for QA verification. Release follows verification; it does not replace it.

## References

- Language policy: `docs/workflow/DOCUMENT-LANGUAGE-POLICY.md`
- Release agent: `.cursor/agents/Release.mdc`
- Release command: `.cursor/commands/release.md`
- Collaboration flow: `docs/workflow/AGENT-COLLABORATION-ON-REQUIREMENT.md`
