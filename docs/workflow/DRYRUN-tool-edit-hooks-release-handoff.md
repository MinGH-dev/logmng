# Dry-run: Tool edit → Hooks (treemap) → Release handoff → Push

**Purpose**: Simulate the **handoff** when (1) tools are modified, (2) Cursor hooks regenerate the treemap, and (3) the user runs **/release** to commit and **push** all changes (including the updated treemap). Verify that the Main → Release handoff prompt is correct and complete.

**No code execution.** Handoff prompt generation and checklist verification only.

**Date**: 2026-03-10

---

## 1. Scenario

| Step | Actor | Action |
|------|--------|--------|
| 1 | User / Agent | Modify tool files (e.g. `.cursor/rules/`, `.cursor/commands/release.md`, `scripts/generate-treemap.js`) |
| 2 | Cursor | On save, run `afterFileEdit` → `.cursor/hooks/after-file-edit-treemap.sh` → `node scripts/generate-treemap.js` → `docs/cursor-tools-treemap.html` updated |
| 3 | User | Run **/release** (or say "도구 수정했으니 release로 push까지 해줘") |
| 4 | Main agent | Invoke **Release** subagent via Task with handoff: CHANGELOG/checklist update, commit **all** uncommitted changes (including treemap), then **push** |
| 5 | Release subagent | Update CHANGELOG/RELEASE_CHECKLIST, stage and commit (treemap + tool changes + CHANGELOG), run `git push` |

---

## 2. Handoff: Main → Release (도구 수정 + 트리맵 + push)

When the user runs **/release** after tool changes, the main agent must **not** perform release in the main chat. It must invoke the **Release** subagent with the following (or equivalent) handoff.

### 2.1 Task invocation

- **subagent_type**: `Release`
- **description**: `Release (CHANGELOG + commit + push)`
- **prompt** (full):

```
User requested **release including push**. The current changes include **tool/treemap updates** (e.g. .cursor/rules, .cursor/commands, .cursor/hooks, scripts/generate-treemap.js, docs/cursor-tools-treemap.html, docs/workflow). Cursor hooks have already regenerated the treemap if relevant files were edited. Please do the following in order:

1. **CHANGELOG / release checklist**: Update CHANGELOG.md and docs/workflow/RELEASE_CHECKLIST.md for the current changes (e.g. today's date, Korean entries). Describe tool/release workflow changes (트리맵 일관성, release 명령, Cursor hooks, 요건문서 ref 등) in Korean. See docs/workflow/RELEASE_CHECKLIST.md and docs/workflow/DOCUMENT-LANGUAGE-POLICY.md §3–§4.

2. **Commit**: Stage and commit **all** uncommitted changes (including docs/cursor-tools-treemap.html, .cursor/, docs/workflow/, scripts/ as appropriate). Follow .cursor/commands/commit-on-complete.md. Use a release/chore message in Korean (e.g. "chore: 도구 수정 및 트리맵·릴리스 워크플로 (release)"). Do not add secrets or .env. Include requirement doc ref in the message only when the commit closes a specific requirement.

3. **Push**: Run `git push` (or `git push origin <current-branch>`). Do not force-push. Report success or any error.

Reference: .cursor/agents/Release.mdc (When the user asks to push / release including push), docs/workflow/RELEASE_CHECKLIST.md step 5.
```

### 2.2 Verification (handoff content)

| Check | Required | Present in handoff above | Pass? |
|-------|----------|---------------------------|-------|
| User requested release including push | Yes | Yes (first line) | ✓ |
| Context: tool/treemap updates | Yes | Yes ("current changes include tool/treemap updates", "Cursor hooks have already regenerated the treemap") | ✓ |
| Step 1: CHANGELOG + RELEASE_CHECKLIST, Korean | Yes | Yes | ✓ |
| Step 2: Commit **all** uncommitted (incl. treemap, .cursor, docs/workflow) | Yes | Yes ("Stage and commit **all** uncommitted changes (including docs/cursor-tools-treemap.html, .cursor/, docs/workflow/...)") | ✓ |
| Step 2: commit-on-complete.md, chore/release message | Yes | Yes | ✓ |
| Step 3: git push, no force-push | Yes | Yes | ✓ |
| Reference to Release.mdc and RELEASE_CHECKLIST | Yes | Yes | ✓ |

---

## 3. Alignment with commands and agent

| Source | Check | Pass? |
|--------|-------|-------|
| `.cursor/commands/release.md` | Main must invoke Release via Task, not perform in main chat | ✓ (release.md: "Do not perform ... Invoke the Release subagent") |
| `.cursor/commands/release.md` | Handoff includes "release including push" and 3 steps (CHANGELOG, commit, push) | ✓ (base prompt; scenario-specific prompt above adds tool/treemap context) |
| `.cursor/agents/Release.mdc` | Release performs (1) status, (2) commit if uncommitted, (3) push when handoff says "release including push" | ✓ |
| `docs/workflow/SUBAGENT-DELEGATION.md` §5 | Main may delegate to Release with "commit and push all current changes" | ✓ |
| `.cursor/hooks/after-file-edit-treemap.sh` | Regenerates treemap on tool file edit (no handoff; automatic) | ✓ (documented in scenario) |

---

## 4. Recommendation for `/release` command (optional enhancement)

The **default** handoff in `.cursor/commands/release.md` does not explicitly mention "tool/treemap updates". For the **도구 수정 후 release** flow to be unambiguous, either:

- **Option A**: Main agent, when the user says "도구 수정했으니 release로 push해줘" or runs `/release` after editing tool files, **appends** to the standard release handoff: "Current changes include tool/treemap updates; include all uncommitted files (e.g. docs/cursor-tools-treemap.html, .cursor/, docs/workflow/) in the commit."
- **Option B**: Add one sentence to `.cursor/commands/release.md` prompt: "If the current changes include tool or treemap updates (.cursor/, docs/workflow/, scripts/generate-treemap.js, docs/cursor-tools-treemap.html), include them in the commit."

**Dry-run verdict**: With **Option B** (or Main adding context when it detects tool-file changes), the handoff is complete. Without it, Release can still "commit all uncommitted changes" per commit-on-complete.md, so the flow works; the explicit mention of tool/treemap only makes the intent clearer.

---

## 5. Result summary

| Item | Result |
|------|--------|
| Scenario (도구 수정 → hooks → /release → push) | Defined and consistent with hooks + release command + Release.mdc |
| Main → Release handoff (full prompt) | Generated and verified; includes release including push, CHANGELOG/checklist, commit all, push |
| Checklist (handoff content) | 7/7 passed |
| Alignment (release.md, Release.mdc, SUBAGENT-DELEGATION) | Pass |
| Optional improvement | Add one line to release.md for tool/treemap context when relevant |

**Overall**: Handoff dry-run **passed**. 도구 수정 → hooks로 트리맵 갱신 → /release로 변경사항 모두 커밋·push까지 진행하는 흐름이 handoff로 수행 가능하며, 위와 같은 prompt로 Release를 호출하면 됨.
