# Analysis: Requirement doc search — path pattern vs RequirementsPastSearch / TOPIC-INDEX

**Context**: In the treemap (and agent prompts), Backend and other agents show "other docs" that include `docs/requirements/yyyyMMdd-name.md`. The user asked: that path is just a **document path pattern** (naming convention); shouldn’t **document search** for requirement docs use **RequirementsPastSearch** (via **TOPIC-INDEX.md**) instead?

**Conclusion**: **Yes.** For **discovering/searching** existing requirement docs, the project design is RequirementsPastSearch + TOPIC-INDEX. The path `docs/requirements/yyyyMMdd-name.md` is a **writing convention** (where to create new docs), not the mechanism for **finding** relevant docs.

---

## 1. Two distinct uses

| Use | Meaning | Correct mechanism |
|-----|--------|--------------------|
| **Where to write** a new requirement doc | Naming convention: create files like `docs/requirements/20260310-activity-log-filter.md`. | Path pattern `docs/requirements/yyyyMMdd-name.md` (CONSISTENCY-STANDARDS, agent prompts). |
| **How to find/search** existing requirement docs | Discover which past docs are relevant by topic (e.g. activity-log, decryption) and get §1 summaries. | **RequirementsPastSearch** using **TOPIC-INDEX.md** (REQUIREMENTS-AUTHORING-WORKFLOW, past-requirements-search.md). |

So:

- **yyyyMMdd-name.md** = convention for **path shape** when **creating** a doc. It does **not** identify a specific file and is **not** the way the project does doc **search**.
- **TOPIC-INDEX.md** = index of real doc IDs by topic. **RequirementsPastSearch** is defined to read TOPIC-INDEX first, then read only §1 of the listed docs for token-efficient search. So **search** is RequirementsPastSearch + TOPIC-INDEX.

---

## 2. What the docs say

- **REQUIREMENTS-AUTHORING-WORKFLOW.md** §1.1: When the user has not explicitly requested a change, **invoke RequirementsPastSearch** with a topic; "RequirementsPastSearch uses `docs/requirements/TOPIC-INDEX.md` and reads only §1 of relevant docs".
- **past-requirements-search.md**: "**First**: Read `docs/requirements/TOPIC-INDEX.md` when given a topic. Use it to find relevant doc IDs before reading full docs."
- **Backend/Frontend/DB prompts**: "Write or update requirement docs in `docs/requirements/yyyyMMdd-name.md`" — that is about **writing** (path convention), not about **searching** existing docs.

So: **search** = RequirementsPastSearch + TOPIC-INDEX; **write** = yyyyMMdd-name path pattern.

---

## 3. Treemap "other docs" and agent refs

- The treemap scans agent content and extracts refs. When it sees the literal `docs/requirements/yyyyMMdd-name.md` (e.g. in Backend.mdc or backend.md), it adds it to that agent’s **other** refs and shows it in the detail view under "other docs".
- That makes it look like "this agent uses this document". But:
  - It is a **path pattern**, not a concrete document.
  - The project’s **search** mechanism is RequirementsPastSearch + TOPIC-INDEX, not "open yyyyMMdd-name.md".

So in the treemap (and in how we describe requirement-doc usage):

- Showing **only** `docs/requirements/yyyyMMdd-name.md` under "other docs" suggests a single doc path and does **not** reflect how requirement docs are **searched** in this project.
- For **search/discovery** of requirement docs, the correct references are:
  - **RequirementsPastSearch** (subagent that performs the search), and
  - **docs/requirements/TOPIC-INDEX.md** (index used by that subagent).

---

## 4. Who uses which

- **Requirements** (authoring): Invokes **RequirementsPastSearch** with a topic; RequirementsPastSearch uses **TOPIC-INDEX.md** and returns a summary of past user-requested content. So Requirements **does** use RequirementsPastSearch/TOPIC-INDEX for **search**.
- **Backend / Frontend / DB**: Use the path **yyyyMMdd-name.md** as the **convention for writing** new requirement docs. They are typically **given** a specific requirement doc path in the handoff (e.g. `docs/requirements/20260310-activity-log-filter.md`); they do **not** run RequirementsPastSearch themselves to discover docs. So for them, "other docs" showing only the path pattern is about **writing**, not about **search**.

So:

- **Search** (find relevant past docs by topic): RequirementsPastSearch + TOPIC-INDEX — and that is what should be emphasized when we talk about "requirement doc search".
- **Write** (where to create a new doc): yyyyMMdd-name path pattern — correct to keep in agent prompts and in CONSISTENCY-STANDARDS.

---

## 5. Summary

- **Is it correct that requirement doc *search* should use RequirementsPastSearch (TOPIC-INDEX)?**  
  **Yes.** The design and docs clearly assign **discovery/search** of requirement docs to **RequirementsPastSearch** using **TOPIC-INDEX.md**; the path `docs/requirements/yyyyMMdd-name.md` is only a **naming convention** for **writing** new docs.
- **Implication for treemap / "other docs"**: Showing `docs/requirements/yyyyMMdd-name.md` as a standalone "other doc" is ambiguous: it reflects the **writing** convention but not the **search** mechanism. If we want the UI and refs to reflect how requirement docs are **searched**, we should surface **TOPIC-INDEX.md** and (where relevant) **RequirementsPastSearch** as the search path, and treat **yyyyMMdd-name.md** as the writing-convention pattern rather than as "the" requirement doc to open.

**References**: REQUIREMENTS-AUTHORING-WORKFLOW.md §1.1, past-requirements-search.md (Token optimization), TOPIC-INDEX.md header, SUBAGENT-DELEGATION.md Step 1 / 1 support.
