# 20260407 - Permission group assign/unassign audit before-after snapshots

## 1. User requirement

### Requirement description

When an administrator changes a user’s permission group from **User management** (or any client that calls the same permission-group user APIs), the system must persist **auditable before and after state** in `user_activity_log.action_detail` for **`ASSIGN_USER_TO_PERMISSION_GROUP`** and **`UNASSIGN_USER_FROM_PERMISSION_GROUP`**, so reviewers can reconstruct **which group the user belonged to before** and **which group applies after** (or that membership was removed). Passwords, tokens, and other denylisted secrets must not appear in stored JSON.

This closes the gap where `permissionGroupAuditV1.before` / `after` are currently **null** for assign/unassign enrichment, which makes the detail view look like “after-only” or empty diff, unlike **`PERMISSION_GROUP_UPDATE`** which already captures snapshots via `PermissionGroupAuditContext`.

### User scenario

1. An administrator opens **User management**, opens a user, and assigns them to permission group **B** (replacing prior membership in group **A**, or assigning from no group).
2. The administrator (or auditor) opens **Activity log** and inspects the row for **권한 그룹 사용자 배정** (`ASSIGN_USER_TO_PERMISSION_GROUP`).
3. **Problem**: `action_detail` / `permissionGroupAuditV1` shows **identifiers** (`permissionGroupId`, `targetUserId`) but **`before` and `after` are null or missing**, so the **previous group** and **full group snapshot** cannot be reconstructed from the stored payload alone.
4. Similarly, when a user is **removed** from a group via **UNASSIGN_USER_FROM_PERMISSION_GROUP**, the **prior group state** should be visible in **`before`**, with **`after`** reflecting removal.

### Expected outcome

- For **`ASSIGN_USER_TO_PERMISSION_GROUP`**, persisted `permissionGroupAuditV1` must allow reconstruction of:
  - **`before`**: `PermissionGroupSnapshot` of the user’s **previous** permission group **before** the assignment transaction applied (or **`null`** if the user had **no** prior group membership).
  - **`after`**: `PermissionGroupSnapshot` of the **new** group **after** assignment (the group identified by `permissionGroupId` / `permissionGroupCode` for this operation).
- For **`UNASSIGN_USER_FROM_PERMISSION_GROUP`**, persisted `permissionGroupAuditV1` must allow reconstruction of:
  - **`before`**: `PermissionGroupSnapshot` of the group the user is **removed from** (the group in the API path).
  - **`after`**: **`null`** (membership in that group no longer applies).
- Top-level **`permissionGroupId`**, **`permissionGroupCode`**, and **`targetUserId`** remain as today; snapshots must be **consistent** with `specs/activity-permission-group-audit.spec.yaml` (`PermissionGroupSnapshot` shape, nested `allowedScreens` as `AllowedScreenItem[]` where included).
- **No** passwords, reset tokens, or denylisted keys (see spec §6) in `action_detail`.
- **Activity log detail UI** (`UserActivityLog`): consumers can **see** structured before/after in the detail payload; if the existing JSON/detail renderer already surfaces nested `permissionGroupAuditV1`, **verify** readability; **optional** UX polish (labels, ordering) only if the default rendering is insufficient.

**References (existing parent specs):** `docs/requirements/20260330-permission-group-activity-detail-audit.md`, `docs/requirements/20260330-activity-types-user-mgmt-permission-group.md`; machine-readable schema: `specs/activity-permission-group-audit.spec.yaml`, `specs/activity-action-types.spec.yaml`.

## 2. Design

### 2.1 Security review (optional; when PII / decryption / access control)

- [ ] Security review performed (check if applicable)

**Risks:** Audit payloads include permission group **names**, **codes**, and **screen/function flags** (`allowedScreens`). This is administrative audit data; it must remain within the same **authorization and scope** rules as other `user_activity_log` rows (e.g. `GET /api/activity-log/{id}` and search). Do not add raw request bodies, passwords, or tokens.

**Acceptance / recommendations:**

- Enforce existing **denylist** in `specs/activity-permission-group-audit.spec.yaml` §6 and `ActivityAuditDetailEnricher` conventions.
- If `allowedScreens` is truncated, set **`allowedScreensTruncated: true`** per spec §3.2 so consumers do not assume completeness.

### Technical design

#### Codebase summary

- **APIs:** `POST /api/permission-groups/{id}/users` (`PermissionGroupController.assignUser`), `DELETE /api/permission-groups/{id}/users/{userId}` (`unassignUser`). Frontend: `UserGroupAssignment` → `addUserToGroup` / `removeUserFromGroup`.
- **Activity:** `@ActivityLog` emits `ASSIGN_USER_TO_PERMISSION_GROUP` / `UNASSIGN_USER_FROM_PERMISSION_GROUP`. `ActivityAuditDetailEnricher.enrichAssign` / `enrichUnassign` build `permissionGroupAuditV1` but set **`before` and `after` to null** today (`backend/src/main/java/com/logmng/activity/ActivityAuditDetailEnricher.java`).
- **Context:** `PermissionGroupAuditContext` already holds **pre-delete/pre-update** group state for UPDATE/DELETE (`setBeforeState` / `peekBeforeState`) and **unassign group code** (`setUnassignGroupCode` / `peekUnassignGroupCode`). It is **cleared** in `enrichPermissionGroup` after enrichment.
- **Service behavior:** `PermissionGroupService.assignUser` removes existing `app_user_permission_group` rows then inserts the new assignment, so **previous group** must be captured **before** that mutation (transaction boundary per implementing design).

#### Problem analysis

1. **Semantic gap:** Assign/unassign rows do not populate `permissionGroupAuditV1.before`/`after`, unlike UPDATE, so audit and UI cannot show a true state transition.
2. **Ordering risk:** If “before” is read **after** the service deletes the old link, the previous group is lost unless captured earlier or re-read from a snapshot.

#### Diagnostic phase (mandatory for error/bug fix only)

*Not applicable — this requirement is a functional/audit enhancement, not an error-fix workflow.*

#### Solution approach

**Semantics (`permissionGroupAuditV1` for ASSIGN / UNASSIGN)**

| Operation | `operation` | `before` | `after` | Notes |
|-----------|-------------|----------|---------|--------|
| `ASSIGN_USER_TO_PERMISSION_GROUP` | `ASSIGN_USER` | Previous group **`PermissionGroupSnapshot`**, or **`null`** if the user had no prior group | **New** group **`PermissionGroupSnapshot`** after assignment | Top-level `permissionGroupId`/`permissionGroupCode` refer to the **new** group (target of assign). |
| `UNASSIGN_USER_FROM_PERMISSION_GROUP` | `UNASSIGN_USER` | Group **`PermissionGroupSnapshot`** for the group being left (path `{id}`) | **`null`** | Reflects removal from that group. |

**Snapshot field allowlist**

- Align **`PermissionGroupSnapshot`** with `specs/activity-permission-group-audit.spec.yaml` §3.3: `code`, `name`, `description`, `sortOrder`, `allowedScreens` (`AllowedScreenItem[]` per `specs/permission-group-hierarchy.spec.yaml` §1.1).
- **Volume / consistency:** Prefer **parity with UPDATE** (full nested `allowedScreens`) when storage and performance allow. If a snapshot would be **too large** or policy limits apply, use **`allowedScreensTruncated: true`** and omit or shorten `allowedScreens` per spec §3.2; **minimum** reconstruction fields must still include **`code`**, **`name`**, **`sortOrder`** (and top-level `permissionGroupId` on `permissionGroupAuditV1`). **TODO (product/implementation):** Confirm whether a **summary-only** mode (omit `allowedScreens` for assign/unassign) is acceptable when truncation is not triggered — document decision in Contract after Step 3.

**Pre-mutation capture**

- **ASSIGN:** In the service layer (or equivalent transaction), **before** removing existing user–group rows, load **`PermissionGroupResponse`** (or equivalent) for the **previous** group and **`PermissionGroupAuditContext`-style clone** it for audit (reuse `cloneForAudit` patterns). After successful assign, build **`after`** from the **new** group state (consistent with `toSnapshotMap` / `PermissionGroupResponse` returned to the client).
- **UNASSIGN:** Before removing membership, ensure **`before`** is populated from a **full** group snapshot for `{id}` (not only `peekUnassignGroupCode`). Existing **`setUnassignGroupCode`** may be superseded or complemented by a **full snapshot** in context for enricher use.
- **Thread-local / context:** **Extend** `PermissionGroupAuditContext` (preferred for locality with existing UPDATE/DELETE/unassign hooks) with dedicated slots, e.g. **previous-group snapshot for assign**, **full snapshot for unassign before-state**, without conflating with `peekBeforeState()` used for UPDATE/DELETE unless the implementing agent proves safe reuse. **Clear** all new slots in the same `clear()` path used after enrichment.

**Frontend**

- **Verify** `UserActivityLog` detail displays nested `permissionGroupAuditV1` including non-null **`before`/`after`** (existing JSON tree or formatted block).
- **Only if** the default detail view hides or flattens nested objects poorly, add **minimal** presentation tweaks (labels for before/after, ordering) — list exact files in the planned change list when Frontend scope is confirmed.

**Contract / spec**

- Update **`specs/activity-permission-group-audit.spec.yaml`** §3.5 table and narrative so ASSIGN/UNASSIGN **require** populated `before`/`after` per the semantics above (replacing “omit or null” for full audit).
- Align **`docs/contract.md`** / **`docs/api-definition.md`** if they describe `action_detail` shapes for these action types (per DOC-CODE-SYNC).

### Affected scopes and change targets (verification)

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|------------|-----------------------------------------------|
| Backend | Yes | Yes |
| Frontend (config UI + view screen) | Verify only (detail readability) | Yes — verify; optional polish |
| DB | No | N/A |
| Contract / Spec | Yes | Yes |
| Cursor tools (skills, specs) | Yes (spec + optional activity/permission skills) | Yes |

**Pattern §2.4 (search/filter UI consistency):** Does **not** apply — no search/filter layout change.

### Planned change file list (expected change targets)

#### Backend

- `backend/src/main/java/com/logmng/activity/ActivityAuditDetailEnricher.java` — Populate `permissionGroupAuditV1.before`/`after` for assign/unassign from context; set `allowedScreensTruncated` when applicable.
- `backend/src/main/java/com/logmng/activity/PermissionGroupAuditContext.java` — Thread-local fields and clear semantics for assign/unassign snapshots (names and structure per implementation).
- `backend/src/main/java/com/logmng/service/PermissionGroupService.java` (or the service that performs assign/unassign) — Capture **pre-mutation** previous-group state for assign; load full **before** snapshot for unassign before delete.
- `backend/src/test/java/com/logmng/activity/ActivityAuditDetailEnricherTest.java` — Assert before/after maps for assign/unassign scenarios.
- Additional backend tests if service-layer capture is tested (e.g. `PermissionGroupService` test with mocked repos) — **must** align with §3 TCs.

#### Frontend (conditional)

- `frontend/src/components/UserActivityLog/UserActivityLogDetail.js` (and related CSS/tests) — **Only if** verification shows poor readability of nested `permissionGroupAuditV1`; otherwise **verify-only**, no file change.

#### DB

- None expected (audit JSON only).

#### Contract / spec

- `specs/activity-permission-group-audit.spec.yaml` — §3.3/§3.5 ASSIGN/UNASSIGN semantics and truncation rules.
- `docs/contract.md` / `docs/api-definition.md` — If activity-log `action_detail` documentation must reflect mandatory before/after for these types.

### Cursor tool update targets

- `specs/activity-permission-group-audit.spec.yaml` — authoritative schema for `permissionGroupAuditV1`.
- Optionally refresh `.cursor/skills/activity-statistics-domain/SKILL.md` or auth/permission skills **only if** narrative references assign/unassign audit shape; not required unless domain docs mention null before/after.

## 3. Test approach

### Test case list (required)

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|----|-------|------|------------------------------|-----------------|--------------------------------------------|
| TC-01 | Backend | Normal | `assignUser`: user had group A, assign to B | `permissionGroupAuditV1.before` matches A snapshot; `after` matches B; `targetUserId` correct; denylist keys absent | Unit (`ActivityAuditDetailEnricherTest` + service test if needed) |
| TC-02 | Backend | Edge | `assignUser`: user had **no** prior group | `before` is `null`; `after` is new group snapshot | Unit |
| TC-03 | Backend | Normal | `unassignUser`: remove user from group C | `before` matches C snapshot; `after` is `null` | Unit |
| TC-04 | Backend | Edge | Large `allowedScreens` on snapshot | `allowedScreensTruncated` set per policy; snapshot still minimally auditable | Unit (may mock large list) |
| TC-05 | Integration | Regression | POST assign + GET activity log detail | Stored row contains non-null before/after per TC-01/02 | Integration or manual with API |
| TC-06 | Frontend | Manual / verify | Activity log detail UI for assign/unassign rows | User can identify before vs after group in detail (JSON or formatted) | Manual |
| TC-07 | Backend | Security | Enrichment after assign/unassign | No denylisted keys in `action_detail` (extend existing denylist tests if present) | Unit |

### Test scenarios

#### Scenario 1: User moves from group A to group B (user management)

1. Preconditions: User in group A.
2. Assign user to group B via user management (same API as production).
3. Fetch activity log entry for `ASSIGN_USER_TO_PERMISSION_GROUP`.
4. **Verification:** `before` shows A; `after` shows B; IDs and codes consistent.

#### Scenario 2: Unassign

1. User in group C.
2. Remove user from group C.
3. **Verification:** `before` shows C; `after` null.

### Test data

- Use existing permission groups and test users from dev DB or documented seed; no special schema.

### Test environment

- Backend: `http://localhost:9200`
- Frontend: `http://localhost:3001`
- Database: per project standard (PostgreSQL)

### 3.5 Browser automation verification (optional)

- Optional for TC-06 if automated snapshot of detail panel is desired; not mandatory for backend-first requirement.

## 4. Checklist

### Frontend verification

- [ ] Detail view readability verified for assign/unassign audit rows
- [ ] If UI changed: error handling and a11y unchanged or improved

### Backend verification

- [ ] Unit tests for enricher + capture path written and run
- [ ] Logs checked (no sensitive spill)
- [ ] Performance acceptable (extra reads only on assign/unassign paths)

### Integration

- [ ] End-to-end: assign and unassign produce expected stored JSON

### Documentation

- [ ] Requirement doc completed
- [ ] Spec/contract updated in same change set as behavior

## 5. Test results

### Test run date

- _Pending (QA / implementer)_

### Test results

#### Frontend

- _Pending_

#### Backend

- _Pending_

**Commands:**

- _Implementer to add one command per TC in §3 after implementation._

**Outcome:**

- _Pending_

### Issues found and resolution

- _None yet_

### Next steps

1. Step 3 Contract: update `activity-permission-group-audit.spec.yaml` and related contract sections.
2. Step 4 Backend (+ optional Frontend): implement capture + enrichment; run tests.
3. Step 5 QA: record §5 and mark checklist.

## 6. Error remedy result (cause and action) — for error/bug fix requirements only

_Not applicable._

## 7. Final version (Korean) — add after all verification is complete

_To be added after QA verification per `docs/workflow/DOCUMENT-LANGUAGE-POLICY.md`._

---

**Author:** Requirements (subagent)  
**Date:** 2026-04-07  
**Status:** In progress  

### Open questions (TODO in doc)

| ID | Topic |
|----|--------|
| TODO-01 | **Product:** If assign/unassign must omit `allowedScreens` entirely (summary-only) when under size budget — confirm vs always including full nested list with truncation flag. |
| TODO-02 | **Implementation:** Exact threshold or policy for setting `allowedScreensTruncated` (row size limit vs count of screens). |
| TODO-03 | **Frontend:** Confirm whether any detail UI change is needed after Backend ships, or JSON rendering is sufficient (resolve in TC-06). |
