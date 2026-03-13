# Recommendation: How to express requirement-doc refs (writing vs search)

**Context**: For Backend, DB, Requirements, RequirementsPastSearch, the path `docs/requirements/yyyyMMdd-name.md` is a **writing convention** (where to create new docs). **Search** of existing docs is done via **RequirementsPastSearch** + **TOPIC-INDEX.md**. Showing only the path pattern in "other docs" is misleading. This doc recommends how to express this correctly.

---

## 1. Principle

| Concept | Correct expression |
|--------|---------------------|
| **Where to write** (new requirement doc) | Path pattern: `docs/requirements/yyyyMMdd-name.md` (CONSISTENCY-STANDARDS, agent role text). |
| **How to search** (find existing docs by topic) | **RequirementsPastSearch** + **docs/requirements/TOPIC-INDEX.md**. |
| **Who invokes RequirementsPastSearch** | **Requirements** only (during authoring). Backend/DB do not invoke it. |

So we should **separate** "writing convention" and "search" in how we display and refer to requirement docs.

---

## 2. Treemap (other docs)

**Goal**: In agent detail "other docs", avoid implying that the path pattern is the way to **search** requirement docs. Make "search" visible where relevant.

### Option A (recommended): Prefer TOPIC-INDEX in "other", treat pattern as convention

- **When building agent refs**: If an agent ref is exactly the **path pattern** `docs/requirements/yyyyMMdd-name.md` (or `yyyyMMdd-name.md` under docs/requirements), do **not** add it to "other docs" as a standalone document ref. Instead, ensure **docs/requirements/TOPIC-INDEX.md** is present in that agent’s refs when the agent’s role touches requirement docs (Backend, DB, Requirements, RequirementsPastSearch).
- **Display**: In "other docs", show **TOPIC-INDEX.md** with a short label, e.g. `TOPIC-INDEX.md (requirement doc index; search via RequirementsPastSearch)`. The writing convention stays in the agent **prompt text** only; it is not shown as a clickable "document" in other docs.
- **Rationale**: One clear ref for "requirement docs" in the treemap = the **search index** (TOPIC-INDEX). Writing convention is part of role description, not a "doc to open".

### Option B: Show both with labels

- Keep both refs in "other docs":
  - `docs/requirements/yyyyMMdd-name.md` → label: **Writing convention** (new requirement doc path).
  - `docs/requirements/TOPIC-INDEX.md` → label: **Search index** (find docs by topic; used by RequirementsPastSearch).
- **Rationale**: Explicit separation of "write" vs "search" in the UI. Slightly more verbose.

### Implementation note (Option A)

In `generate-treemap.js`, when categorizing refs for agents:

- Treat refs that match the **path pattern** (e.g. `docs/requirements/yyyyMMdd-name.md` or `yyyyMMdd-name.md`) as **writing-convention only**: do not add them to the `other` array for the treemap (or add them to a separate "conventions" bucket that is not shown as "other docs").
- For agents that reference requirement docs (Backend, DB, Requirements, RequirementsPastSearch), ensure **TOPIC-INDEX.md** is included in their refs (e.g. by adding it when the agent’s content mentions `docs/requirements/` or requirement docs, or by a small allow-list for these four agents). Then "other docs" shows TOPIC-INDEX as the requirement-doc ref.

---

## 3. Agent prompts (optional but useful)

- **Backend, DB**: Keep the current line: "Write or update requirement docs in `docs/requirements/yyyyMMdd-name.md` for …". Optionally add one line: "To **search** existing requirement docs by topic, use **RequirementsPastSearch** (see `docs/requirements/TOPIC-INDEX.md`)." So the role text clearly separates write vs search without changing behavior.
- **Requirements**: Already invokes RequirementsPastSearch; prompt already references REQUIREMENTS-AUTHORING-WORKFLOW. No change required; optionally mention TOPIC-INDEX in References.
- **RequirementsPastSearch**: Already states "Topic index: `docs/requirements/TOPIC-INDEX.md` — read first." No change.

---

## 4. Invokes (no change)

- **Requirements** → invokes **RequirementsPastSearch** (already correct).
- **Backend, DB** → do **not** add RequirementsPastSearch to invokes; they do not call it.

---

## 5. Summary

| Where | Correct expression |
|-------|---------------------|
| **Treemap other docs** | Prefer showing **TOPIC-INDEX.md** (with label "requirement doc index / search via RequirementsPastSearch"). Do not show the path pattern as the main "requirement doc" ref, or show it only as "Writing convention" if both are shown. |
| **Agent prompts** | Keep path pattern for **writing**. Optionally add one line that **search** = RequirementsPastSearch (TOPIC-INDEX) for Backend/DB. |
| **Invokes** | Only Requirements → RequirementsPastSearch. |

This way, "this case" is expressed as: **writing** = path convention, **search** = RequirementsPastSearch + TOPIC-INDEX, and the treemap does not suggest that opening `yyyyMMdd-name.md` is how to search requirement docs.

**Consistency**: To keep these conventions applied when agents or workflow change, follow **`.cursor/rules/treemap-consistency.mdc`** and run `node scripts/generate-treemap.js` after edits. The treemap script is the single source for MAIN_INVOKES, AGENT_INVOCATION_MAP, and requirement-doc ref handling.
