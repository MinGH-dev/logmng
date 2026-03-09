# Analysis: Pending approvals scope — why the screen and related UI were under-specified

**Related requirement**: `docs/requirements/20260305-pending-approvals-scope-same-as-search-history.md`

**Purpose**: Document the **root cause** of the incomplete analysis (승인 대기 화면 변경 사항이 요구사항에 충분히 반영되지 않은 이유) and the **missing touchpoints** so that the requirement doc and future scope-supporting changes can be corrected.

---

## 0. Root cause (requirement authoring process, not “only frontend”)

The primary cause is **not** that “only the frontend was missed.” It is that **requirement authoring** did not use a **systematic change-target-by-scope verification**. When the Requirements subagent (or whoever authored the doc) wrote §2:

- There was **no mandatory checklist** that asked: for each scope (Backend, Frontend, DB, Contract, Cursor tools), is it affected? For **Frontend**, have you listed both **(a) configuration UI** and **(b) view screen** where the feature is both configurable and displayed?
- There was **no domain pattern** (e.g. “adding a scope-supporting screen”) that forced the author to include Backend + Frontend config UI + Frontend view + Contract + skills in one go.

So the fix is **improving the tools that the Requirements (subagent) uses** when authoring: a **change target checklist** and a **workflow step** that runs it before §2 is finalized. The corrective actions below include (1) updating the requirement doc with the missing frontend touchpoints, and (2) **adding and mandating** `REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md`, a step in the requirements authoring workflow (`REQUIREMENTS-AUTHORING-WORKFLOW.md` step 5.5, referenced from `AGENT-COLLABORATION-ON-REQUIREMENT.md`), and the “Affected scopes and change targets” verification in the requirement template, so that **role-based delegation** (Requirements → Backend/Frontend/DB) does not miss change targets in the future.

---

## 1. What was missing in the requirement doc

The requirement doc described:

- **Backend**: Scope resolution for `GET /api/search-history/pending`, `listPending` filter by self/team/all, `screenScopes['pending-approvals']` in auth — **correctly and in detail**.
- **Frontend**: A single optional line — *"If scope-dependent label/hint is required: use user.screenScopes['pending-approvals']"* and one change file: `PendingApprovals.js`.

**Missing from the doc:**

1. **Where scope is configured**  
   Scope for pending-approvals is set in the **permission group edit UI**. The frontend has two places that define which screens support scope:
   - **`ScreenSelectionTree.js`**: `SCOPE_SUPPORTING_SCREENS = ['activity-log', 'statistics', 'search-history']` — **pending-approvals is not included**. So when an admin selects "승인 대기" in the permission group dialog, **no scope dropdown** (본인 | 부서 | 전체) is shown. Without this, the admin cannot set scope for pending-approvals; the backend would have no scope value from `permission_group_screen.scope` for pending-approvals. **Terminology**: Current UI and contract use scope labels 본인 | 부서 | 전체 and function labels 조회 | 승인 (not 조회만, not 팀) per specs/permission-group-hierarchy.spec.yaml §1.1 and docs/workflow/CONSISTENCY-STANDARDS.md §7.
   - **`PermissionGroupPanel.js`**: `scopeScreens = ['activity-log', 'statistics', 'search-history']` — same gap. When loading/saving a permission group, scope for pending-approvals is not normalized or sent to the API.

2. **How the pending approvals screen should change**  
   The doc did not specify:
   - That the **configuration UI** (ScreenSelectionTree, PermissionGroupPanel) must include pending-approvals in the scope-supporting list so that scope can be set and persisted.
   - Whether the **viewing screen** (PendingApprovals.js) should show a scope hint (e.g. "표시: 본인 요청만" / "팀 요청" / "전체") for consistency or user clarity; it only said "if required."

3. **Test cases for frontend**  
   No TC for: "Admin edits permission group, selects pending-approvals, sets scope to team → scope dropdown visible and value saved; after save, user's auth has screenScopes['pending-approvals']='team'."

---

## 2. Why the authoring process allowed the gap

1. **No change-target verification step**  
   The workflow (requirements authoring, now in `REQUIREMENTS-AUTHORING-WORKFLOW.md`) did not require the Requirements author to run a **change target checklist** before finalizing §2. So it was easy to list Backend in detail and treat Frontend as a single optional line, without asking: “Where is scope **configured**?” and “Where is it **displayed**?”.

2. **No domain pattern for “scope-supporting screen”**  
   There was no project checklist that says: when adding a **new scope-supporting screen**, update (a) Backend, (b) Frontend **configuration UI** (ScreenSelectionTree, PermissionGroupPanel), (c) Frontend **view** (screen component). Without that pattern, the author did not automatically include the configuration UI.

3. **Frontend not split into config vs view**  
   The template and handoff checklist did not explicitly ask: for Frontend, list **(a) configuration/setup UI** and **(b) user-facing screen(s)**. So “Frontend” was treated as one block and only the view (PendingApprovals.js) was mentioned; the configuration UI was omitted.

---

## 3. Corrective actions

1. **Requirement doc (this requirement)**  
   - Expand §2 Frontend to include configuration UI (ScreenSelectionTree, PermissionGroupPanel) and view screen (PendingApprovals.js), and add §3 TCs for Frontend (permission group edit + scope, optional scope hint). Done in the requirement doc update.

2. **Tools for Requirements authoring (prevent recurrence)**  
   - **Change target checklist**: Add `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md`. Requirements **must** run it before finalizing §2. It asks: for each scope (Backend, Frontend, DB, Contract, Cursor tools), is it affected? For Frontend, list both (a) configuration UI and (b) view screen. It also includes **domain-specific patterns** (e.g. “adding a scope-supporting screen”) so all touchpoints are enumerated.
   - **Workflow**: In `REQUIREMENTS-AUTHORING-WORKFLOW.md`, **step 5.5 — Change target verification (mandatory)** was added and references the checklist.
   - **Template**: In `docs/template/REQUIREMENT_TEMPLATE.md`, add **“Affected scopes and change targets (verification)”** and reference the checklist so the author explicitly ticks each scope and, when applicable, domain patterns.
   - **Skill**: In auth-permission-domain, add the “adding a new scope-supporting screen” checklist (already done). The **primary** fix is the Requirements-side checklist and workflow step so that **role delegation** (Requirements → Backend/Frontend) does not miss change targets.

3. **Implementation**  
   - Phase 2 Frontend is **not** optional for the configuration UI: without it, scope for pending-approvals cannot be set. The "optional" part is only the scope hint on the PendingApprovals view screen.

---

## 4. Summary

| Touchpoint | Doc said | Actually needed |
|------------|----------|-----------------|
| Backend listPending + auth screenScopes | ✓ Detailed | ✓ Correct |
| Frontend: permission group edit (scope dropdown for pending-approvals) | ✗ Not mentioned | **Required** — otherwise scope cannot be set |
| Frontend: PendingApprovals.js scope hint | "If required" | Optional but recommended for consistency |

**Root cause**: **Requirement authoring** did not use a **systematic change-target-by-scope verification**. So the doc listed Backend in detail and Frontend as a single optional line, and did not ask “where is scope configured?” or “where is it displayed?”. The fix is **tooling for the Requirements process** (change target checklist, workflow step 5.5, template verification table), not only documenting frontend. See §0 and §3 above.

**Reference**: Requirement doc `20260305-pending-approvals-scope-same-as-search-history.md`; frontend `ScreenSelectionTree.js`, `PermissionGroupPanel.js`, `PendingApprovals.js`; workflow `REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md`, `REQUIREMENTS-AUTHORING-WORKFLOW.md` step 5.5, `REQUIREMENT_TEMPLATE.md` (Affected scopes and change targets).
