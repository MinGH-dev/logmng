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
- **Shared primitive ownership**: If the symptom is reported on one screen but may come from a shared component/shared stylesheet/shared layout contract, list the **shared fix target** (component, shared CSS, design standard) and also the **consumer-screen verification targets**. Do not list only the first screen where the defect was noticed.
- **Shared footer contract**: If one-page data hides total count, rows-per-page, or the footer region itself, list the shared table component/shared CSS/shared design standard first. Treat page navigation visibility separately: navigation buttons may be absent or disabled on one-page data, but the footer region must stay visible when footer metadata exists.

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

### 2.4 Search/filter UI consistency (activity-log, statistics, etc.)

When the requirement is to **align** search/filter UI between two user-context screens (e.g. statistics with activity log), ensure §2 and the change file list cover:

| Area | Touchpoints |
|------|-------------|
| **Layout** | Group title placement (above fields, not inline), block structure (`role="group"`, `aria-labelledby`). |
| **Block tiers and same-row layout** | Date/period block, user block, extra block are the **same tier**; use **block-level width** (e.g. `var(--sf-field-user-block-max)`, `var(--sf-field-date-block-max)`) so blocks can sit in one row and 기타 조건 appears in one column to the right. See `docs/design/forms-and-filters.md` § Filter block tiers, § Width by role. |
| **Form per mode (일별/월별)** | When the screen has a period mode selector (일별/월별), §2 may recommend **separate form structure per mode** (load 일별 form vs 월별 form when mode changes) so the date/period block is one block and each form has clear structure. Ref: `docs/design/forms-and-filters.md` § Form per mode. |
| **Form/panel width** | Same width constraints (full width of page container or same explicit max-width) so both screens present the same visual footprint. See `docs/design/forms-and-filters.md` § Search form panel width. |
| **Field width by role (user block)** | When aligning activity-log and statistics (or other user-context screens), §1 Expected outcome must include that user block fields (부서, 사용자명, 사용자 ID) have the **same width/size** on both screens; §2 and change file list must cover applying same min/max or block-level width (e.g. `var(--sf-field-user-block-*)`) so field sizes match. Ref: `docs/design/search-fields-by-screen.md` §3 "활동 이력과 동일", §4; `docs/design/search-field-definition-items.md` §4, §4.5. |
| **Spacing** | Compact variant (row/field gap, block-to-block gap) per `docs/design/forms-and-filters.md` § Compact variant. |
| **a11y** | Form landmark, group/label association. |
| **Design doc references** | When the requirement involves search/filter **field** design or documentation (e.g. adding a statistics section to search-fields-by-screen), §1 or §2 must explicitly reference **both** `docs/design/search-fields-by-screen.md` (per-screen field definitions — **표준정의 단일 소스**, 화면별 필드 값은 이 문서만 수정) and `docs/design/search-field-definition-items.md` (field-level schema, §4 cross-field rules only; do not add screen-specific field content there). See `docs/workflow/ANALYSIS-search-field-design-doc-reference-gaps.md`, `docs/design/README.md` §표준정의 단일 소스. |
| **Implementation note for Frontend** | When §2.4 applies, §2 must include a short **implementation note** that the handoff builder will pass to Frontend: "Implementer must read and apply field-level and layout values from **docs/design/search-field-definition-items.md**, **docs/design/search-fields-by-screen.md**, and **docs/design/forms-and-filters.md** when changing form/filter CSS or components; apply layout and structural rules from forms-and-filters.md (e.g. § Single row for non-date, § Form per mode, § Width by role). Requirement §2 numeric values (e.g. 8–12px) are consistent with those docs but must be verified or sourced from the docs. **If any required standard for layout, field sizing, spacing, icon usage, label placement, or control semantics is not defined or is ambiguous in the design docs, the implementer must not infer or hardcode a solution. The implementer must first inform the user of the undefined standard items, explain why each is needed, propose a recommended standard draft, and request feedback so the standard can be explicitly defined before implementation proceeds.**" See `docs/design/ux-frontend-standard-principles.md` §2 and §10, and `docs/workflow/ANALYSIS-implementation-phase-design-doc-usage.md`. |
| **CSS standard and exceptions** | When the requirement involves search/filter **CSS** (styling, spacing, layout, or user-requested screen-specific override), §2 must reference `docs/design/css-standard-and-exceptions.md` and `frontend/src/styles/search-filter-standard.css`. Implementation note for Frontend: use standard CSS (var(--sf-*) or .sf-*), including block-level width vars (--sf-field-user-block-*, --sf-field-date-block-*); for exceptions, component CSS only + comment + Exception index (§5). |

**§2.4 verification (mandatory when pattern applies)**  
Before finalizing §2, the Requirements author **must** confirm each row below. Missing any row has led to "user block field size not aligned" and similar omissions (see `docs/workflow/ANALYSIS-user-field-size-activity-log-vs-statistics.md`).

| Check | §1 | §2 / change list | §3 |
|-------|----|-------------------|-----|
| **User block field size** | §1 Expected outcome includes an **explicit** bullet: user block fields (부서, 사용자명, 사용자 ID) have the **same width/size** on all aligned screens (e.g. "User block field size (동일 크기): … per search-fields-by-screen.md §3, §4 and search-field-definition-items.md §4, §4.5"). | §2 Solution and change file list include: apply same user block field width (e.g. `var(--sf-field-user-block-min/max)`) or same grid/field sizing on both screens; ensure layout does **not** squeeze the user block (e.g. avoid user block and another control sharing a single `1fr` cell). | At least one TC compares user block fields (부서, 사용자명, 사용자 ID) across the aligned screens — same min/max width and visual size. |
| **Form/panel width** | §1 mentions same panel/container width for aligned screens. | §2 and change list cover page container max-width and filter panel width. | Optional TC for container width consistency. |
| **Layout / spacing / a11y** | §1 mentions group title above fields, compact spacing, form semantics, a11y as applicable. | §2 and change list cover layout, spacing vars, Implementation note for Frontend, CSS standard refs. | TCs for layout, buttons, a11y as applicable. |

Reference: `docs/design/forms-and-filters.md`, `docs/design/search-fields-by-screen.md`, `docs/design/search-field-definition-items.md`, `docs/design/css-standard-and-exceptions.md`, `docs/analysis-search-consistency-by-screen.md`. When the user mentions "검색 창 크기" or "동일 크기", include form/panel width **and** user block field size in §2 and solution.

### 2.5 Shared UI primitive defect (shared grid/table/layout/CSS)

When the symptom is first reported on one consumer screen but the ownership may be in a shared UI primitive:

| Area | Touchpoints |
|------|-------------|
| **Frontend — shared fix target** | Shared component, shared stylesheet, and shared design contract that own the behavior (for example shared table component/CSS and `docs/design/grid-and-table.md`). |
| **Frontend — consumer verification** | The screen where the issue was reported, plus other known consumers of the same primitive that can confirm the shared fix did not regress local layout. |
| **Workflow / docs** | Minimal workflow or standards docs only when the issue exposed a repeated scoping mistake (for example `CONSISTENCY-STANDARDS.md`, authoring checklist, handoff checklist). |

**Shared footer note**: When the shared primitive is a data-table footer, treat one-page footer suppression as a **shared footer-contract** defect. The footer region remains visible whenever footer metadata exists; total count and rows-per-page are part of that contract. One-page navigation buttons may be absent or disabled, but that does not justify hiding the shared footer region.

**Why**: A screen-specific fix can hide the symptom locally while leaving the same broken shared contract available to other consumers and future authors.

---

## 3. How Requirements subagent uses this

1. **After** drafting §2 (codebase summary, problem analysis, solution approach) and the **planned** change file list (expected change targets):
2. **Run** §1 above: for each scope (Backend, Frontend, DB, Contract, Cursor tools), confirm the requirement affects it or not; if yes, confirm §2 and the change file list include **all** touchpoints. For Frontend, confirm both configuration UI and view screen are considered.
3. **Run** §2: if the requirement matches a domain-specific pattern (scope-supporting screen, permission group, API/error change, **search/filter UI consistency** §2.4), confirm every touchpoint of that pattern is in §2 and in the change file list. When **§2.4** applies, run the **§2.4 verification** table above and confirm every row (§1 explicit user block field size bullet; §2/change list same width + layout not squeezing user block; §3 TC for user block field size comparison).
4. **If something is missing**: add the missing subsection or file to §2 and to the change file list before finalizing the doc.
5. **Then** finalize §3 (test cases with Scope tags) and complete the requirement doc.

This checklist is **mandatory** for the Requirements subagent when authoring requirement docs. It is referenced from `docs/workflow/REQUIREMENTS-AUTHORING-WORKFLOW.md` (step 5.5) and from the requirement template.
