# Dry-run: Tool changes verification (도구 수정)

**Purpose**: Verify that the recent **tool changes** (release command, Cursor hooks, treemap consistency, requirement-doc ref display) produce correct behavior and handoffs. **No code changes.** Verification only.

**Date**: 2026-03-10

**Scope of tool changes**:
- `/release` command → Release subagent with commit + push.
- Cursor hooks (`.cursor/hooks.json`, `after-file-edit-treemap.sh`) → treemap auto-regenerate on tool file edit.
- Treemap consistency rule (`treemap-consistency.mdc`) → single source for MAIN_INVOKES, AGENT_INVOCATION_MAP, requirement-doc ref.
- Requirement-doc ref: path pattern excluded from "other", TOPIC-INDEX for Backend/DB/Requirements/RequirementsPastSearch; Backend.mdc/DB.mdc search line.

---

## 1. Release command → Push

### 1.1 Virtual scenario

User runs **/release**. Expected: Main agent invokes **Release** subagent via Task with a handoff that instructs Release to update CHANGELOG/checklist, commit if needed, and **run git push**.

### 1.2 Simulated handoff (Main → Release)

**Task invocation** the main agent would send when the user runs `/release`:

- **subagent_type**: `Release`
- **description**: `Release (CHANGELOG + commit + push)`
- **prompt**: Contains "User requested **release including push**"; steps 1 (CHANGELOG/checklist), 2 (commit), 3 (**Push**: run `git push`).

### 1.3 Verification

| Check | Source | Pass? |
|-------|--------|-------|
| Command exists | `.cursor/commands/release.md` | ✓ |
| Command says "invoke Release via Task" (no release in main chat) | release.md | ✓ |
| Handoff includes "release including push" and step 3 Push | release.md prompt excerpt | ✓ |
| Release agent accepts "release including push" and performs push | `.cursor/agents/Release.mdc` § When the user asks to push | ✓ |
| SUBAGENT-DELEGATION §5: delegate to Release for "commit and push" | SUBAGENT-DELEGATION.md §5 | ✓ |

**Result**: Pass. Release 도구 사용 시 push까지 수행되도록 handoff와 Release.mdc가 일치함.

---

## 2. Cursor hooks → Treemap auto-update

### 2.1 Virtual scenario

User (or agent) edits a file under `.cursor/agents/` or `.cursor/rules/`. Expected: Cursor runs `afterFileEdit` hook; hook script detects path; runs `node scripts/generate-treemap.js`; treemap HTML is regenerated.

### 2.2 Verification

| Check | Source | Pass? |
|-------|--------|-------|
| hooks.json exists and has afterFileEdit | `.cursor/hooks.json` | ✓ |
| afterFileEdit command points to script | `.cursor/hooks/after-file-edit-treemap.sh` | ✓ |
| Script is executable | chmod +x | ✓ |
| Script parses file_path from stdin JSON | after-file-edit-treemap.sh (sed for file_path) | ✓ |
| Paths matched: .cursor/rules, .cursor/agents, .cursor/commands, .cursor/skills, docs/workflow, docs/template, scripts/generate-treemap.js, treemap-template.html, treemap-i18n.json | case statement in script | ✓ |
| Script runs from project root and executes `node scripts/generate-treemap.js` | script body | ✓ |
| treemap-consistency.mdc documents Cursor hooks and run-after-edit | treemap-consistency.mdc § Automatic treemap update | ✓ |

**Result**: Pass. 도구 파일 수정 시 Cursor 훅으로 트리맵이 자동 갱신되도록 설정됨.

---

## 3. Treemap consistency rule

### 3.1 Verification

| Check | Source | Pass? |
|-------|--------|-------|
| Rule exists and describes single source (MAIN_INVOKES, AGENT_INVOCATION_MAP) | `.cursor/rules/treemap-consistency.mdc` | ✓ |
| Rule references RECOMMENDATION-requirement-doc-ref-display and SUBAGENT-DELEGATION | treemap-consistency.mdc § References | ✓ |
| Rule says run generate-treemap.js after changes; Cursor hooks auto-run on edit | treemap-consistency.mdc § Automatic treemap update | ✓ |
| agent-collaboration.mdc references treemap-consistency and generate-treemap | agent-collaboration.mdc Reference | ✓ |
| SUBAGENT-DELEGATION §6 References includes Treemap consistency | SUBAGENT-DELEGATION.md §6 | ✓ |

**Result**: Pass. 트리맵 일관성 규칙이 문서화되어 있고, 위임/요건문서 ref와 연결됨.

---

## 4. Requirement-doc ref (other docs / TOPIC-INDEX)

### 4.1 Verification

| Check | Source | Pass? |
|-------|--------|-------|
| generate-treemap.js excludes path pattern from agent "other" | isRequirementDocPathPattern(), scanAgents continue | ✓ |
| generate-treemap.js adds TOPIC_INDEX_REF for Backend, DB, Requirements, RequirementsPastSearch | AGENTS_WITH_REQUIREMENT_DOC_REF, categorized.other.push | ✓ |
| Backend.mdc / DB.mdc have "To search ... use RequirementsPastSearch (see TOPIC-INDEX.md)" | .cursor/agents/Backend.mdc, DB.mdc | ✓ |
| RECOMMENDATION-requirement-doc-ref-display and treemap-consistency reference each other | RECOMMENDATION §5; treemap-consistency References | ✓ |

**Result**: Pass. 요건 문서 검색은 TOPIC-INDEX/RequirementsPastSearch로 표현되며, 경로 패턴은 other에서 제외됨.

---

## 5. Standard handoff chain (sanity check)

Confirm that the existing Requirements → Backend → QA handoff flow is still required and checklist-driven (no regression from tool changes).

| Rule / Document | Check | Pass? |
|-----------------|-------|-------|
| agent-collaboration.mdc Step 1 gate | Main does not author §1·§2·§3 | ✓ |
| agent-collaboration.mdc §3 gate | §3 exists before Step 4 | ✓ |
| HANDOFF-CHECKLIST.md Backend | §1, §2, §2.1, contract, §3, cross-scope, CONSISTENCY, Doc–code sync | ✓ |
| HANDOFF-CHECKLIST.md QA | §1 summary + §3, build/restart confirmation, doc path for §5/§6 | ✓ |
| Main invokes Backend only (Step 4); Backend may delegate | SUBAGENT-DELEGATION §1, §3; generate-treemap.js MAIN_INVOKES | ✓ |

**Result**: Pass. 도구 수정이 기존 핸드오프 체크리스트 및 위임 구조를 바꾸지 않음.

---

## 6. Summary

| Section | Scope | Result |
|---------|--------|--------|
| 1 | Release command → push | Pass |
| 2 | Cursor hooks → treemap auto-update | Pass |
| 3 | Treemap consistency rule | Pass |
| 4 | Requirement-doc ref (TOPIC-INDEX, path pattern excluded) | Pass |
| 5 | Standard handoff chain (sanity) | Pass |

**Overall**: All checks passed. 도구 수정에 대한 dry-run 완료.
