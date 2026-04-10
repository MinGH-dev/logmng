# 20260407 - Admin-configurable sidebar parent group and leaf order (extends screen display labels)

**Language**: §1, §2, §3 authored in **English** first. **Korean final section (§7)**: pending verification — add after QA completes verification per `docs/workflow/DOCUMENT-LANGUAGE-POLICY.md`.

**Parent requirement**: `docs/requirements/20260406-menu-display-names-admin.md` — admin-configurable **display labels** (`labelUser`, optional `labelAdmin`) for screens. This document adds **presentation tree** controls: **which top-level group** each configurable **leaf** belongs to and **sort order** among siblings, in the **same admin area** as “화면 표시 이름” (screen display names).

**Commit**: Commits closing this requirement must reference this document (e.g. `req 20260407-screen-menu-parent-order` or `docs/requirements/20260407-screen-menu-parent-order.md`).

---

## 1. User requirement

### Requirement description

Administrators must be able to configure, per **screen** (leaf), **which top-level sidebar group** the screen appears under and the **order** of that screen **among its siblings** in that group. Configuration lives in the **same admin settings context** as the existing **screen display labels** feature (`docs/requirements/20260406-menu-display-names-admin.md`, `specs/menu-display-labels.spec.yaml`).

**Routing and identity are unchanged**: `screen_id` / `currentView` values remain the **stable technical keys** for navigation, permission checks, and APIs. Only the **presentation tree** (group membership and ordering of leaves) is configurable.

**Parent group identifier** is a **closed set** of **group ids** aligned with `frontend/src/constants/menuTree.js` → `MENU_TREE` top-level entries, e.g. `log-search`, `history`, `statistics`, `admin`. Values are **not** free-form strings: the **server must validate** against this allowlist. **Moving a leaf from one group to another** is **allowed by default** for system administrators (subject to the same admin-only write rules as labels).

**Order** is a **non-negative integer** (`sortOrder`, also referred to as `menuOrder` in discussions) **per leaf within the same parent group**: **lower values appear higher** in the list. If two leaves share the same `sortOrder`, **tie-break by `screenId` ascending (lexicographic)**.

**Fallback**: If the API **does not** supply `parentGroupId` and/or `sortOrder` for a given screen (or the feature is partially rolled out), the UI **must** use **defaults from the hardcoded `MENU_TREE` hierarchy** in `menuTree.js` (same as today’s built-in structure).

**Security and audit** align with the labels feature: **system-admin-only** mutations, **whitelist** validation of ids, and **audit** records for changes (who/when/what; before/after optional per project standards).

### User scenario

1. A system administrator opens the admin screen for **screen display names** (and related menu presentation settings).
2. The admin assigns screen `activity-log` to top-level group `history` with `sortOrder` **2** (within that group’s leaves).
3. The admin saves. The **sidebar** shows **activity-log** under **이력·승인** (history group) at the position consistent with order **2** and tie-break rules among other leaves.
4. A standard user navigates using the sidebar: **`currentView`** when opening **activity-log** remains `activity-log`; URLs and permission checks are unchanged.
5. If configuration fetch fails partially, missing fields fall back to **`MENU_TREE`** defaults so the app remains usable.

### Expected outcome

- Sidebar **group placement** and **leaf order** reflect admin-configured `parentGroupId` and `sortOrder` where provided.
- **`screen_id` / `currentView`** and permission semantics are **unchanged**; no regression in access control or API routing.
- **Non-admin users** cannot change parent/order; **admin-only** APIs enforce least privilege (**403** on unauthorized writes).
- **Invalid parent group id** → **400** with a contract-aligned error code (e.g. `INVALID_INPUT` or a dedicated code documented in `docs/contract.md`).
- **Audit**: Parent/order changes are recorded consistently with label changes (same or related activity types per backend standards).
- **Resilience**: When the API omits parent/order, **defaults from `MENU_TREE`** apply without breaking navigation.

**Note**: Exact control layout (table columns, drag-and-drop vs numeric fields) is left to implementation; align with existing admin patterns and `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md`.

---

## 2. Design

### 2.1 Security review (extends labels feature)

This extension reuses the **same threat model** as `20260406-menu-display-names-admin.md` §2.1 (admin-only writes, validation, audit). Additional considerations:

| Risk | Notes |
|------|--------|
| **Broken access control on writes** | If PUT is not gated on `is_system_admin`, users could reorder or re-parent menu entries (integrity / confusion). |
| **Invalid or injected `parentGroupId`** | Must reject values outside the closed group-id set (**400**); do not persist unknown groups. |
| **Inconsistent tree** | Multiple leaves may share order; **tie-break** must be deterministic (`screenId` lexicographic). |
| **Audit gaps** | Parent/order changes should appear in audit trails alongside label edits where applicable. |

#### Acceptance criteria (incremental to 20260406)

- [ ] **Write authorization**: Only **system administrators** can mutate **parent group** and **sort order**; same rule as label PUT (**403** `FORBIDDEN` otherwise).
- [ ] **Validation**: `parentGroupId` (or equivalent field name) **must** be one of the server-defined allowlist aligned with `MENU_TREE` group `id` values; unknown value → **400** (document exact `code` in contract).
- [ ] **`sortOrder`**: Non-negative integer; reject negative or non-integer representations per API rules (**400** `INVALID_INPUT` if applicable).
- [ ] **GET**: Authenticated clients receive enough data to render the sidebar after merge; **non-admin** clients **must not** gain write access via GET.
- [ ] **Audit**: Successful admin mutations that change parent/order emit audit/activity records with actor and affected `screenId`(s).

### Technical design

#### Problem analysis

1. **Labels alone do not control structure**: `20260406` fixes **text**; `MENU_TREE` still fixes **which group** contains which **view** and default **order**.
2. **Product need**: Operations want **one admin surface** to adjust **grouping and ordering** without code deploys, while keeping **stable `screen_id`** values.
3. **Consistency**: Frontend already centralizes defaults in `menuTree.js`; runtime merge must combine **labels** + **parent/order** overrides **deterministically**, then apply **permission filtering** (`allowedScreenIds`) unchanged.

#### v1 scope: top-level group labels and group order

**Decision (v1): Out of scope** — configuring **top-level group titles** (e.g. renaming “로그 검색”) and **order of top-level groups** in the sidebar is **not** required in v1.

**Justification**: The user ask targets **which 상위 메뉴 each screen belongs to** and **order within that group**. Delivering **leaf-level `parentGroupId` + `sortOrder`** satisfies that with **lower API, DB, and UI surface** than also persisting group-level labels and group ordering. Group header text and group order remain **hardcoded** from `MENU_TREE` until a follow-up requirement explicitly adds **minimal** group overrides (if ever needed).

#### Solution approach

**Data model (conceptual)**

- Per **configurable screen** (same whitelist universe as screen display labels / contract): optional **`parentGroupId`** (enum string) and **`sortOrder`** (non-negative integer).
- **Omission** in stored data or API response → use **`MENU_TREE` default** for that `screenId` (parent + default sibling order as defined in code).

**Ordering algorithm (normative)**

1. Partition leaves by effective `parentGroupId` (API override or `MENU_TREE` default).
2. Within each group, sort by **`sortOrder` ascending**, then by **`screenId` ascending** if tied.
3. Apply existing **permission** and **admin-only group** rules (`adminOnly`, `systemAdminOnly` leaves) **after** structure is computed — requirement does not change those gates.

**Frontend**

- Extend the **screen display labels** admin UI (same route/section as “화면 표시 이름”) with fields or columns for **parent group** (dropdown: closed set) and **sort order** (non-negative integer).
- Extend **merge** logic (e.g. `useScreenDisplayLabels` / `mergeMenuLabels`) so sidebar building uses **defaults from `MENU_TREE`** merged with **label** + **parent/order** from GET.
- **Sidebar** (`AppSidebar` and any consumer that builds menu from `MENU_TREE`): render structure from **merged** tree, not only label text replacement.

**Backend**

- Extend **GET** `/api/screen-display-labels` (or contract-agreed resource) to include optional `parentGroupId` and `sortOrder` per item where persisted.
- Extend **PUT** request items to accept optional `parentGroupId` and `sortOrder`; validate group id allowlist and integer rules; **admin-only**.
- Reject **unknown `parentGroupId`** with **400** (specific `code` per contract).

**DB**

- Add columns or normalized storage for `parent_group_id` and `sort_order` associated with `screen_id` (or extend existing `screen_display_label` table — **DB agent** chooses shape consistent with migrations).

**Contract / spec**

- Update `docs/contract.md` and **`specs/menu-display-labels.spec.yaml`** (or a dedicated spec revision) with new fields, validation rules, and error codes.
- Keep **single resource** preference if product agrees (same endpoint family as labels) to minimize admin client complexity.

### Affected scopes and change targets (verification)

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|------------|-----------------------------------------------|
| Backend | Yes | [x] |
| Frontend (admin UI + sidebar) | Yes | [x] |
| DB | Yes | [x] |
| Contract / Spec | Yes | [x] |
| Cursor tools (skills, specs) | Optional — e.g. `ui-ux-domain` menu notes | [ ] |

### Planned change file list (expected change targets)

**(Planned at authoring. Implementing agent (Step 4) confirms or amends this list when implementation is complete.)**

#### Frontend

- `frontend/src/constants/menuTree.js` — Remain **canonical defaults** for parent/order when API omits values; document merge precedence.
- `frontend/src/components/AppSidebar.js` — Build presentation tree from merged labels + parent/order.
- `frontend/src/components/ScreenDisplayLabelsSettings/` (or equivalent) — Admin fields for parent group + sort order.
- `frontend/src/hooks/useScreenDisplayLabels.js`, `frontend/src/utils/mergeMenuLabels.js` — Extend merge and ordering (tie-break).
- **Tests**: merge/order unit tests; sidebar order integration or snapshot as appropriate.

#### Backend

- Extend `ScreenDisplayLabel*` controller/service/DTOs/repository to persist and return `parentGroupId` and `sortOrder`; validation allowlist for group ids; integration tests.

#### DB

- Migration: add `parent_group_id` / `sort_order` (or equivalent) to label storage table.

#### Contract / documentation

- `docs/contract.md`, `specs/menu-display-labels.spec.yaml` — Field definitions, enums, errors.

---

## 3. Test approach

### Test case list (required)

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification |
|----|-------|------|------------------------------|-----------------|--------------|
| TC-P01 | Backend | Normal | Admin PUT `activity-log` with `parentGroupId: "history"`, `sortOrder: 2` | 200; persisted | Integration |
| TC-P02 | Frontend | Normal | After TC-P01, reload app as user allowed for `activity-log` | Sidebar shows **activity-log** under **history** group at position consistent with order 2 and tie-break | Manual / unit (merge) |
| TC-P03 | Backend | Exception | Non-admin PUT parent/order | **403**; no partial apply | Integration |
| TC-P04 | Backend | Edge | PUT with invalid `parentGroupId` (e.g. `"unknown-group"`) | **400**; no DB change | Unit / integration |
| TC-P05 | Backend | Edge | PUT `sortOrder` negative or non-integer | **400** | Unit |
| TC-P06 | Frontend | Regression | Navigate to `activity-log` | `currentView` still `activity-log`; routing unchanged | Unit / manual |
| TC-P07 | Frontend | Edge | API returns labels without parent/order for some screens | Sidebar uses **MENU_TREE** defaults for those screens | Unit |
| TC-P08 | Frontend | Edge | Two leaves same `sortOrder` in same group | Order follows **`screenId` lexicographic** ascending | Unit |

### Test scenarios

#### Scenario 1: Admin assigns activity-log to history with order 2

1. Log in as system admin. Open screen display / menu presentation settings.  
2. Set `activity-log` → parent `history`, `sortOrder` **2**. Save.  
3. Verify persistence (reload settings) and audit entry.

#### Scenario 2: Sidebar reflects order

1. Log in as a user with access to multiple screens in `history`.  
2. Confirm order matches **sortOrder** + tie-break; **labels** still apply from `20260406`.

#### Scenario 3: Non-admin and invalid parent

1. Non-admin session: attempt PUT → **403**.  
2. Admin session: PUT invalid `parentGroupId` → **400**.

### Test data

- System admin and non-admin sessions.  
- At least screens in `history` group for ordering checks.

### Test environment

- Frontend: `http://localhost:3001` (or per contract)  
- Backend: `http://localhost:9200`  
- Database: PostgreSQL (per project)

### 3.5 Browser automation verification (optional)

- **Applicable TCs**: TC-P02 — after admin change, snapshot sidebar for group placement and relative order.  
- **Reference**: `docs/workflow/BROWSER-AUTOMATION-VERIFICATION-POLICY.md`

---

## 4. Checklist

### Frontend verification

- [ ] Merge uses **MENU_TREE** fallback when API omits parent/order  
- [ ] Sidebar order matches §2 ordering rules  
- [ ] Routing / `currentView` unchanged  

### Backend verification

- [ ] Allowlist validation for `parentGroupId`  
- [ ] Admin-only enforcement for writes  
- [ ] Tests for 400 / 403 paths  

### Integration

- [ ] End-to-end: admin configures → user sees sidebar structure  
- [ ] Labels feature (`20260406`) still works when combined  

### Documentation

- [ ] Requirement doc completed  
- [ ] Contract and spec updated  

---

## 5. Test results

### Test run date

- (Pending)

### Test results

- (Pending)

### Issues found and resolution

- (None yet)

### Next steps

1. Contract/spec update handoff per `docs/workflow/HANDOFF-CHECKLIST.md`.  
2. Implement after `20260406` baseline is aligned in codebase.

---

## 6. Error remedy result (cause and action)

Not applicable (feature requirement, not an error-fix-only doc).

---

## 7. Final version (Korean)

**Pending verification** — add after QA completes verification per `docs/workflow/DOCUMENT-LANGUAGE-POLICY.md`.

---

**Author**: Requirements (subagent)  
**Date**: 2026-04-07  
**Status**: Draft  
