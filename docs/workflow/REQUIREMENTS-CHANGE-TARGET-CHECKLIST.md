# Requirements authoring — change target checklist

**Purpose**: When the **Requirements** subagent (or whoever authors the requirement doc) writes §2 and the change file list, it must **systematically enumerate all affected scopes and touchpoints**. Missing a change target (e.g. Frontend configuration UI) leads to incomplete implementation. This checklist reduces that risk.

**When to use**: **Before finalizing §2** (and before marking the requirement doc complete). The Requirements subagent must run through this checklist so that no implementing scope (Backend, Frontend, DB, Contract, Cursor tools) is omitted.

**Reference**: Root cause of under-specified change targets is documented in `docs/workflow/ANALYSIS-pending-approvals-scope-frontend-incomplete.md` — the requirement authoring process did not verify change targets by scope; improving **this checklist** and the workflow (`docs/workflow/REQUIREMENTS-AUTHORING-WORKFLOW.md`) is the corrective action.

---

## 1. Change target verification (mandatory before §2 final)

For **each** of the following scopes, answer: **Does this requirement affect it?** If yes, ensure §2 has a subsection and the **change file list** includes every relevant file/touchpoint.

| Scope | Question | If yes → ensure §2 and change file list include |
|-------|----------|--------------------------------------------------|
| **Backend** | Does the requirement change API behavior, services, or controllers? | Controllers, services, DTOs, config; list every file. |
| **Frontend** | Does the requirement change UI, API calls, or client state? | **(a) Configuration/setup UI** (e.g. permission group edit, settings screens) **and (b) User-facing screen(s)** that use the feature. List both when the feature is configurable and also shown somewhere. |
| **DB** | Does the requirement change schema, migrations, or init-data? | schema.sql, migrations, setup scripts; list every file. |
| **Contract / Spec** | Does the requirement change API contract or permission/screen spec? | docs/contract.md, docs/api-definition.md, specs/*.spec.yaml; list sections. |
| **Cursor tools** | Does the requirement change domain model (permission, scope, screens, API shape)? | §2 "Cursor 도구 업데이트 대상": list `.cursor/skills/*` and `specs/*` to update per `docs/workflow/REQUIREMENTS-AUTHORING-WORKFLOW.md` §1.4. |

**Frontend sub-check**: For Frontend, explicitly ask:
- **Configuration UI**: Where is this feature **configured** (e.g. admin sets scope for a screen, admin assigns permission)? List those components (e.g. ScreenSelectionTree, PermissionGroupPanel, settings form).
- **View screen**: Where is this feature **used or displayed** (e.g. the screen that shows the list, the page that uses the new API)? List those components.

If the requirement adds or changes something that is **both** configurable and displayed (e.g. "scope for pending-approvals"), **both** the configuration UI and the view screen must appear in §2 and in the change file list.

---

## 2. Domain-specific patterns (apply when requirement type matches)

When the requirement matches one of the patterns below, **additionally** ensure the listed touchpoints are in §2 and in the change file list. These patterns come from past gaps (e.g. pending-approvals scope).

### 2.1 Adding or changing a scope-supporting screen (self / team / all)

**Terminology (권한관리 UI·문서)**: Use **"조회"** (not "조회만"); scope labels **본인** | **부서** | **전체** (API values self | team | all). **조회(목록) 범위**만 선택 가능(본인/부서/전체). **승인 범위**는 부서로 고정·변경 불가(spec §Scope values).

When the requirement adds a **new screen** that supports permission-group scope (self/team/all), or extends scope to an existing screen:

| Area | Touchpoints (all must be in §2 and change file list) |
|------|-------------------------------------------------------|
| **Backend** | ScreenConstants (SCREENS_WITH_SCOPE or equivalent), PermissionGroupService.getScreenScopesForUser (include new screen_id in query), controller that serves the list/detail (resolve scope via ScopeHelper, pass scopeAll / allowedUserIds), service (filter by scope). |
| **Frontend — configuration** | ScreenSelectionTree.js: add screen to `SCOPE_SUPPORTING_SCREENS` so scope dropdown appears when the screen is selected. PermissionGroupPanel.js: add screen to `scopeScreens` in normalizeAllowedScreens so scope is normalized and sent on save. |
| **Frontend — view** | The screen component that shows the list or data: use `user.screenScopes[screenId]` for scope hint or filter behavior if applicable. |
| **Contract / Spec** | permission-group-hierarchy.spec.yaml §4.2 (screenScopes), §4.3 (scope enforcement row for the screen), §1.1 / §2.1 if scope list is enumerated. api-definition.md: document scope for the API. |
| **Cursor skills** | auth-permission-domain (scope-supporting screens list, checklist for "adding a new scope-supporting screen"), domain skill for that screen (e.g. search-history-decrypt-domain). |

**Why**: If the configuration UI (ScreenSelectionTree, PermissionGroupPanel) is missed, admins cannot set scope for that screen and the feature is incomplete. See `ANALYSIS-pending-approvals-scope-frontend-incomplete.md`.

### 2.2 Permission group or screen access change

When the requirement changes which screens exist, which APIs require which screen, or how permission groups are stored:

| Area | Touchpoints (ensure covered) |
|------|-------------------------------|
| **Backend** | ScreenAccessInterceptor or equivalent, AuthService (allowedScreenIds, screenScopes, screenFunctions), controller access checks. |
| **Frontend** | Menu/sidebar (menuTree, canAccessView), permission group edit UI (ScreenSelectionTree, PermissionGroupPanel — screen list, scope, read/write/approve), any screen that checks allowedScreenIds or screenFunctions. |
| **Contract / Spec** | specs/permission-group-hierarchy.spec.yaml §4 (screen IDs, API mapping, scope enforcement). |
| **Cursor skills** | auth-permission-domain, api-permission-map, ui-ux-domain if menu/screen list changes. |

### 2.3 API contract or error code change

When the requirement adds or changes an API or error code:

| Area | Touchpoints |
|------|-------------|
| **Contract / Spec** | docs/contract.md, docs/api-definition.md, specs/*.spec.yaml. |
| **Backend** | Controller, service, DTO; error code constants/messages. |
| **Frontend** | API client (service layer), error handling (map code to message). |
| **Cursor skills** | error-codes-domain, api-permission-map if applicable. |

---

## 3. How Requirements subagent uses this

1. **After** drafting §2 (codebase summary, problem analysis, solution approach) and the **tentative** change file list:
2. **Run** §1 above: for each scope (Backend, Frontend, DB, Contract, Cursor tools), confirm the requirement affects it or not; if yes, confirm §2 and the change file list include **all** touchpoints. For Frontend, confirm both configuration UI and view screen are considered.
3. **Run** §2: if the requirement matches a domain-specific pattern (scope-supporting screen, permission group, API/error change), confirm every touchpoint of that pattern is in §2 and in the change file list.
4. **If something is missing**: add the missing subsection or file to §2 and to the change file list before finalizing the doc.
5. **Then** finalize §3 (test cases with Scope tags) and complete the requirement doc.

This checklist is **mandatory** for the Requirements subagent when authoring requirement docs. It is referenced from `docs/workflow/REQUIREMENTS-AUTHORING-WORKFLOW.md` (step 5.5) and from the requirement template.
