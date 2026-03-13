# Dry-run handoff verification — after search-field design doc reference improvements

**Date**: 2026-03-11  
**Purpose**: Verify handoff flow after applying improvements from `docs/workflow/ANALYSIS-search-field-design-doc-reference-gaps.md` (checklist §2.4 Design doc references, REQUIREMENTS-AUTHORING-WORKFLOW note, template §1 note, statistics req doc retrofit). No code changes; prompts only.  
**Procedure**: Per `.cursor/commands/dry-run-handoff.md`.

---

## 1. Virtual requirement

**Chosen**: Add **scope support (self/team/all)** to the **pending-approvals** screen so admins can set 조회 범위 to 본인 | 부서 | 전체. Backend (scope resolution, API), Frontend (config UI + view screen), Contract/spec, and optionally Security (access) are involved. This exercises the handoff checklist non-trivially (Backend + expert touchpoints).

---

## 2. Simulated handoff chain (Requirements → Backend → QA)

### 2.1 Requirements handoff (main → Requirements)

**Task invocation the main agent would send:**

- **subagent_type**: Requirements  
- **Prompt**:  
  *"Author per docs/workflow/REQUIREMENTS-AUTHORING-WORKFLOW.md: User request — Add scope support (self/team/all) to pending-approvals screen; admins set 조회 범위 (본인/부서/전체). Parallel input from experts (Security if access applies), codebase investigation, change target verification per REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md (including §2.1 scope-supporting screen and §2.4 if search/filter is touched), finalize §3. Do not implement; output requirement doc only."*

**Checks:**

- [x] Main does **not** author §1·§2·§3 (per `agent-collaboration.mdc` Step 1). **Pass.**
- [x] Prompt instructs Requirements to follow `docs/workflow/REQUIREMENTS-AUTHORING-WORKFLOW.md` (hybrid consultation: skills-first → codebase investigation → selective experts). **Pass.**
- [x] After our improvements, when the requirement touches search/filter field design, the **checklist** §2.4 now requires §1 or §2 to reference both `search-fields-by-screen.md` and `search-field-definition-items.md`; the **workflow** step 3 reminds the author to list those design docs. This virtual requirement is **scope-supporting screen**, not search/filter alignment, so the new design-doc bullet does not apply to this run; the dry-run confirms the **Backend/QA handoff** path still satisfies the checklist.

### 2.2 Backend handoff (main → Backend)

**Task prompt the main agent would build (excerpt):**

```
Implement the Backend scope for: Add scope support (self/team/all) to pending-approvals screen.

§1 Summary:
- User request: Admins can set 조회 범위 for pending-approvals to 본인 | 부서 | 전체 (API: self | team | all). Users see only pending approvals in the selected scope.
- Expected: ScreenConstants (or equivalent) includes pending-approvals in scope-supporting screens; PermissionGroupService.getScreenScopesForUser returns scope for this screen; controller that serves pending-approvals list resolves scope (ScopeHelper, scopeAll / allowedUserIds) and filters results accordingly.

§2 Backend (excerpt):
- ScreenConstants: add pending-approvals to SCREENS_WITH_SCOPE (or equivalent).
- PermissionGroupService: include pending_approvals screen_id in getScreenScopesForUser query.
- Controller serving pending-approvals list: resolve scope via ScopeHelper; pass scopeAll / allowedUserIds; service filters by scope.
- Change file list: [list of backend files].

§2.1 Security: [If Security reviewed: include §2.1 summary or "No PII/decryption change; access follows existing permission group scope."]

Contract/spec: permission-group-hierarchy.spec.yaml §4.2 (screenScopes), §4.3 (scope enforcement row for pending_approvals); api-definition.md document scope for pending-approvals API.

§3 Backend-scope TCs: [All TCs with Scope: Backend — e.g. scope stored and returned, list filtered by scope, API returns 403 when no access.]

Cross-scope: Frontend will call [API] with scope from user.screenScopes.pending_approvals; implement to contract.

References: docs/workflow/CONSISTENCY-STANDARDS.md (if naming/error codes touched). Doc–code sync: update docs/api-definition.md and specs if API or scope contract changes.
```

**HANDOFF-CHECKLIST.md (Backend) verification:**

| Item | Present in prompt? |
|------|--------------------|
| §1 summary | Yes |
| §2 Full Backend subsection | Yes (solution + change file list) |
| §2.1 Security | Yes (included or N/A stated) |
| Contract/spec | Yes |
| §3 Backend-scope TCs | Yes (all TCs that involve Backend) |
| Cross-scope | Yes (Frontend call / contract) |
| CONSISTENCY-STANDARDS | Yes when applicable |
| Doc–code sync | Yes when API/contract change |

**Pass**: All Backend checklist items are covered in the described prompt.

### 2.3 QA handoff (main → QA)

**Task prompt the main agent would build:**

```
Verify implementation for requirement: Add scope support (self/team/all) to pending-approvals screen.

§1 Summary: Admins set 조회 범위 (본인/부서/전체) for pending-approvals; list filtered by scope.

§3 Full test case list:
- TC-01 (Backend): Scope stored and returned for pending_approvals.
- TC-02 (Backend): List filtered by scope (self/team/all).
- TC-03 (Frontend): Config UI shows scope dropdown when pending-approvals selected.
- TC-04 (Frontend): View shows only items in selected scope.
- TC-05 (Integration): API returns 403 when no access.
- [Other TCs with Scope tag.]

Build and restart: Confirm when done (or QA runs them). Requirement doc path: docs/requirements/yyyyMMdd-pending-approvals-scope.md. Update §5 after verification. Frontend: browser check for config and view if applicable.
```

**HANDOFF-CHECKLIST.md (QA) verification:**

| Item | Present? |
|------|----------|
| §1 summary + §3 full TC list | Yes |
| Build/restart confirmation | Yes |
| Requirement doc path for §5/§6 | Yes |
| Frontend browser automation | Yes when frontend in scope |
| Failure scope ux | Handoff doc referenced |

**Pass**: All QA checklist items covered.

---

## 3. Verification table

| Rule / Document | Check | Pass? |
|-----------------|-------|-------|
| `agent-collaboration.mdc` Step 1 gate | Main does not author §1·§2·§3 when delegating to Requirements | Yes |
| `agent-collaboration.mdc` §3 gate | §3 exists before Step 4 | Yes (simulated doc has §3) |
| `REQUIREMENTS-AUTHORING-WORKFLOW.md` | Hybrid consultation (skills + tools + selective experts) in Requirements prompt | Yes |
| `HANDOFF-CHECKLIST.md` Backend | All 6+ items present in Backend handoff | Yes |
| `HANDOFF-CHECKLIST.md` QA | All 3+ items present in QA handoff | Yes |
| `REQUIREMENT_TEMPLATE.md` §3 Scope tag | TCs have Scope column | Yes (Backend/Frontend/Integration) |
| `CONTEXT-QUALITY-AND-ORCHESTRATION-MITIGATION.md` §4.1 | Scope-specific excerpts (not full doc) in Backend handoff | Yes |
| **New** REQUIREMENTS-CHANGE-TARGET-CHECKLIST §2.4 | Design doc references touchpoint added; when req is search/filter field design, §1 or §2 must reference both search-fields-by-screen and search-field-definition-items | Yes (checklist and workflow updated; this run did not need them for the virtual scope-supporting-screen req) |

---

## 4. Report

- **Dry-run result**: **Pass.** Handoff prompts for Requirements, Backend, and QA satisfy the checklist. The improvements (checklist §2.4 Design doc references, workflow step 3 note, template §1 note, and statistics req doc retrofit) do not break the handoff flow; they add a required touchpoint when the requirement involves search/filter **field** design or documentation.
- **No code changes** were made in this dry-run; only workflow/checklist/template and one requirement doc were edited in the preceding improvement step.
