# Bugfix Child Requirement Template (parent requirement tracking)

**Purpose**: When verification (restart and health/behavior check) fails, create a **bugfix child** under the **parent requirement** to track and iterate until fixes pass.

**File name**: `docs/requirements/{parentReqID}-bugfix-{N}.md`  
- Example: parent `20260220-activity-statistics-api-fix` → `20260220-activity-statistics-api-fix-bugfix-1.md`, `...-bugfix-2.md` …
- N is the bugfix sequence number for that parent (starting from 1).

**Language**: Author in English. See `docs/workflow/DOCUMENT-LANGUAGE-POLICY.md`. Commit message must reference this doc (e.g. `req {parentReqID}-bugfix-{N}`).

---

## Paste block

```markdown
# {parentReqID}-bugfix-{N} — {one-line summary}

**Parent requirement ID**: `{parentReqID}`  
**Bugfix sequence**: N

## 1. Discovery

- **When**: During verification (restart and health/behavior check)
- **What failed**: (e.g. GET /api/health not 200, frontend 3001 not responding, DB connection failed)

## 2. Error scope

- **Failure scope**: frontend | backend | db | security | contract | ux — Set by QA. Requirements uses this to delegate to the responsible expert (Frontend, Backend, DB, Security, Contract, UX→Frontend).
- **Layer**: frontend | backend | db
- **Symptom**: (one-line summary)
- **Impact**: (which API / screen / feature)

## 3. Cause (estimated)

- [Cause 1]
- [Cause 2]

## 4. Action

- [Change file list and fix summary]

## 5. Verification

- Record results in this document until restart and verification pass. Then QA updates §5 and commits (referencing this doc in the commit message).
```

---

## Loop

- QA hands off to **Requirements** on failure → Requirements delegates by **failure scope** to the responsible expert (Frontend, Backend, DB, Security, Contract, UX, etc.) → Expert fixes and reports **issue closed** to **QA** → QA **re-runs verification**. When all pass, QA updates §5 and **commits** (commit message references this doc).
- After fixing per this bugfix doc → **restart** → **run verification** again.
- If verification fails again, create `-bugfix-(N+1).md` for the same parent and repeat.
- Repeat until all checks pass.
