# 20260408 - User Management v2 mutations: full activity audit and visible detail in activity log

## 1. User requirement

### Requirement description

Administrators who perform **User Management v2** operations (manual department tree changes, direct user registration, and related user-admin actions reachable from the same product area) must leave a **complete, reviewable trail** in **사용자 활동 이력** (activity log).

Today, some mutations may be missing dedicated `action_type` values, structured `action_detail`, or UI rendering parity so that reviewers cannot see **what changed**, **for which department/user**, and **why** (`changeReason`) from the activity log list and **detail** view.

This requirement extends audit coverage so that **every state-changing behavior** in scope produces a `user_activity_log` row that complies with `specs/activity-action-types.spec.yaml` (OP-01), populates **non-sensitive** structured `action_detail`, persists **`changeReason`** where the API already requires it, and is **visible** in the activity-log UI (filter by type + readable detail), consistent with existing patterns (`permissionGroupAuditV1`, `user_admin`, `request_params` parsing).

**Related requirements (must stay aligned):**

- `docs/requirements/20260408-user-management-v2-manual-department-tree-and-quick-user-entry.md` — tree UX and mutations.
- `docs/requirements/20260407-user-management-consistency-delete-reason-activity-audit.md` — user delete/create audit, `changeReason`, `USER_DELETE` / provisioning `USER_CREATE`.

### User scenario

1. An authorized operator opens **User Management v2** and creates a **root** department (`POST /api/user-management-v2/departments/root`) with a mandatory reason.
2. The operator adds a **child** department under a parent (`POST .../departments/{parent}/children`).
3. The operator **edits** or **deletes** a department when the product exposes those mutations (see **TODO** if endpoints are not yet in contract).
4. The operator registers a user via **direct** registration (`POST /api/user-management-v2/users/direct`) with a mandatory reason.
5. The operator **deletes** a user or changes **permissions** / **permission group** assignment using existing user-management APIs (e.g. `DELETE /api/users/{userId}`, assign/unassign flows).
6. A compliance or admin reviewer opens **활동 이력**, filters by the relevant **`action_type`**, opens **상세**, and verifies **action_detail** (and, where persisted, **`request_params`**) shows enough structured information: targets (`departmentCode`, `targetUserId`, etc.), operation outcome context, and **`changeReason`** without secrets.

### Expected outcome

- **Coverage**: Each successful **mutation in scope** emits one auditable **`user_activity_log`** row with a **closed-set** `action_type` registered in `specs/activity-action-types.spec.yaml` and surfaced by **`GET /api/activity-log/action-types`** (unless product explicitly marks a type as internal-only — **TODO** if any exception).
- **Structured detail**: `action_detail` is JSON obeying **category denylists** (no passwords, tokens, raw request bodies, decrypted content) per `specs/activity-action-types.spec.yaml` §3 and `docs/api-definition.md` §8.
- **Reason**: Where the v2 contract requires **`changeReason`**, that value must appear in persisted audit material (minimum: inside **`action_detail.changeReason`**; **TODO** whether duplicate top-level key is required for legacy parsers).
- **Detail API parity**: `GET /api/activity-log/{id}` returns **`action_detail`** (and **`request_params`** when stored) consistent with **`POST /application/activity-log/search`** visibility rules (MF-02 / AC-S2 spirit: only rows visible in search are detail-viewable).
- **UI**: Activity log **list** shows type label from server action-types; **detail** view renders new structures comparably to existing audit-heavy types (e.g. collapsible JSON trees or field rows for `permissionGroupAuditV1`-style objects — exact UX **TODO** with UX/Frontend).
- **Scope**: Auditors with **`activity-log`** read access see rows according to existing **`scope`** rules (`self` / `team` / `all`); no widening of actor visibility beyond contract.

**Note**: Search-filter UI alignment (§2.4 field width) between activity-log and statistics is **out of scope** unless explicitly extended; this requirement does not invoke `REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md` pattern §2.4.

## 2. Design

### 2.1 Security review (optional; when PII / decryption / access control)

- [ ] Security review performed (check when Step 2 Security completes)
- **Risks**: Department names, employee numbers, usernames in `action_detail` / `request_params` are **operational metadata**; must still avoid **secrets** and follow denylist; **`request_params` must not** store full passwords or refresh tokens.
- **Acceptance / recommendations**: Redact or omit sensitive keys in aspect/param capture; confirm whether **real name** (`name`) in direct user create is allowed in `action_detail` or must be truncated — **TODO** Security/product.

### Technical design

#### Codebase summary

- **Activity log storage**: `user_activity_log` with `action_type`, `action_detail` (JSON text), **`request_params`** (JSON/CLOB), HTTP metadata; parsing in `UserActivityLogService` for list/detail.
- **Taxonomy**: `specs/activity-action-types.spec.yaml` §2.4 includes **`USER_CREATE`**, **`USER_UPDATE`**, **`USER_DELETE`**; provisioning **`USER_CREATE`** is tied to external provision path in contract notes; **department** lifecycle codes for v2 are **not yet** fully enumerated in §2.4 — **gap**.
- **User Management v2 spec**: `specs/user-management-v2.spec.yaml` §5 states mutations **MUST** require `changeReason` and audit trail; does not yet codify final `action_type` strings per operation.
- **API docs**: `docs/api-definition.md` §8 describes `action_detail` categories (`user_admin`, `permission_group`, etc.) and detail view behavior including **`USER_CREATE` / `USER_DELETE`** shapes.
- **Frontend**: Activity log list/detail consume `actionType` filter options from **`GET /api/activity-log/action-types`** and render `action_detail` (e.g. permission-group audit blocks).

#### Problem analysis

1. **Department mutations** from v2 may log as **`UNKNOWN`** or lack rows if not instrumented — reviewers cannot trace org changes.
2. **Direct user create** may duplicate or conflict semantically with HR **`USER_CREATE`** if both share one code without a **discriminant** — filters may be ambiguous.
3. **Detail UI** may not render new **`action_detail`** keys, so “상세정보” appears empty or raw JSON without labels.
4. **`request_params`** capture policy (include/exclude body fields) may omit **`changeReason`** or leak overly large payloads unless normalized — needs explicit rules.

#### Solution approach

**Contract / Spec (Step 3 — own scope):**

- Extend **`specs/activity-action-types.spec.yaml`** §2.4 (or new §2.x) with **department admin** codes, for example (**naming TBD — TODO** confirm with OP-01):
  - `DEPARTMENT_CREATE_ROOT` / `DEPARTMENT_CREATE_CHILD` **or** a single `DEPARTMENT_CREATE` with `action_detail.operation` ∈ { `ROOT`, `CHILD` } — **TODO** pick one style for filter UX.
  - `DEPARTMENT_UPDATE`, `DEPARTMENT_DELETE` when those APIs exist.
- Add **`action_detail` category** (e.g. **`department_admin`**) in §3 with allowlisted keys, for example:
  - **`changeReason`** (string, required when API requires reason; max **500** chars aligned with `user_admin` / permission group audit unless Security sets another cap).
  - **`departmentCode`** (string), **`parentDepartmentCode`** (string | null).
  - Optional **`name`**, **`sortOrder`**, **`before` / `after`** allowlisted snapshots for updates — follow **`mutation_before_after`** style in §3 where appropriate.
- Clarify **`USER_CREATE`** emission for **`POST /api/user-management-v2/users/direct`**:
  - **Option A**: Reuse **`USER_CREATE`** with `action_detail` **`user_admin`** keys plus optional **`source`: `"USER_MANAGEMENT_V2_DIRECT"`** (or `registrationSource`) — **TODO** product choice.
  - **Option B**: New code e.g. **`USER_CREATE_DIRECT`** — **TODO** only if product rejects Option A (adds filter surface).
- Update **`specs/user-management-v2.spec.yaml`** §5 to reference the **exact** `action_type`(s) per endpoint and the **`action_detail`** schema.
- Update **`docs/api-definition.md`** §8.0 / §8.2 with the same shapes so detail view documentation matches DB.

**Backend:**

- On **each successful mutation** in scope (v2 department create child/root, v2 direct user create, department update/delete when implemented), emit activity via existing **`@ActivityLog`** / aspect pattern or explicit service call — **same success-only semantics** as other admin audits (**TODO** confirm whether **failed** attempts must log — default **no** unless Security requires).
- Ensure **`ActivityActionType`** (or equivalent) enum includes new codes; **`GET /api/activity-log/action-types`** returns them.
- Normalize **`request_params`**: store **sanitized** JSON (method, path, redacted body fields) — **must** align with `ActivityLogAspect` capabilities; **never** persist denylisted keys from `specs/activity-action-types.spec.yaml` §6 / §3.
- Reuse **`USER_DELETE`**, **`USER_UPDATE`**, **`ASSIGN_USER_TO_PERMISSION_GROUP`**, **`UNASSIGN_USER_FROM_PERMISSION_GROUP`** where those operations already map to user-management APIs — verify missing emitters — **TODO** enumerate exact Java controller methods in Step 4 handoff.

**Frontend:**

- Ensure activity log **detail** renderer handles **`department_admin`** (or agreed schema) with human-readable labels (Korean labels **TODO** i18n source).
- Ensure **filter dropdown** includes new codes once backend publishes them (no stale hardcoded exclude list).
- If list row preview truncates `action_detail`, **detail** view must still show full structured object within masking rules.

**DB:**

- **TODO**: Confirm `action_type` **VARCHAR(50)** length for longest new code; migrate if any code exceeds column limit.

### Affected scopes and change targets (verification)

**Before finalizing §2**, verify touchpoints per `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md`.

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|------------|-----------------------------------------------|
| Backend | Yes | [ ] |
| Frontend (activity-log UI) | Yes | [ ] |
| DB | Yes (conditional — column length) | [ ] |
| Contract / Spec | Yes | [ ] |
| Cursor tools (skills, specs) | Yes (api-permission-map / activity domains if paths or types added) | [ ] |

### Planned change file list (expected change targets)

**(Planned at authoring. Implementing agent (Step 4) confirms or amends this list when implementation is complete.)**

#### Frontend

- `frontend/src/components/UserActivityLog/*` (detail and/or list row rendering)
  - Must render new **`action_detail`** shapes for department and direct-create audits; keep parity with masking contract.
- `frontend/src/components/UserActivityLog/*.test.js`
  - Must cover detail rendering for at least one row per new `action_type`.

#### Backend

- `backend/src/main/java/com/logmng/controller/UserManagementV2Controller.java` (or equivalent)
  - Must emit activity rows for each mutation success path.
- `backend/src/main/java/com/logmng/service/UserManagementV2Service.java` (or equivalent)
  - Must build structured **`action_detail`**; validate **`changeReason`** presence before persist.
- Activity logging infrastructure (`ActivityLogAspect`, `ActivityActionType`, `UserActivityLogService` if insert helpers change)
  - Must register new types; **TODO** confirm single insertion path.
- User management controllers/services for **delete**, **update**, permission assign/un — **if audit gaps found**
  - Must align with `USER_DELETE`, `USER_UPDATE`, assign/unassign types per existing specs.
- `backend/src/test/java/...`
  - Must assert `action_type` and `action_detail` content for each instrumented endpoint (unit or WebMvcTest).

#### DB

- `backend/src/main/resources/db/*` — **only if** `action_type` width migration required.

#### Contract / spec / docs

- `specs/activity-action-types.spec.yaml` — new codes + §3 category.
- `specs/user-management-v2.spec.yaml` — §5 tie to concrete `action_type` / `action_detail`.
- `docs/api-definition.md` §8 — detail shapes and examples.
- `docs/contract.md` — if cross-cutting DOC-CODE-SYNC needed.

#### Cursor tool update targets

- `.cursor/skills/activity-statistics-domain/SKILL.md` — if new `action_detail` patterns need documentation.
- `.cursor/skills/api-permission-map/SKILL.md` — if new endpoints affect permission map.

### Action → audit mapping (authoring baseline)

| Operation (product / API family) | Expected `action_type` (initial) | `action_detail` notes |
|----------------------------------|----------------------------------|------------------------|
| V2 root department create | **TODO** `DEPARTMENT_CREATE_*` | `changeReason`, `departmentCode`, `parentDepartmentCode: null`, name/sortOrder as allowed |
| V2 child department create | **TODO** same family as root | `parentDepartmentCode` set |
| V2 department update | **TODO** `DEPARTMENT_UPDATE` | allowlisted `before`/`after` or field diff — **TODO** schema |
| V2 department delete | **TODO** `DEPARTMENT_DELETE` | target code + reason + **TODO** soft-delete flag in detail if applicable |
| V2 direct user create | **TODO** `USER_CREATE` + discriminant **or** new code | `user_admin`-style keys + `targetUserId` after create, `departmentCode`, **TODO** `name` policy |
| User delete | `USER_DELETE` | per `20260407` / `user_admin` |
| HR provisioning user create | `USER_CREATE` | per contract; discriminant must distinguish from direct if both use same code |
| User field / permission update | `USER_UPDATE` / assign-unassign types | per existing permission-group audit specs when applicable |

## 3. Test approach

### Test case list (required)

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|----|-------|------|------------------------------|-----------------|--------------------------------------------|
| TC-01 | Backend | Normal | V2 root department create with valid `changeReason` | One `user_activity_log` row; expected `action_type`; `action_detail` contains reason + department identifiers | Unit or integration (`mvn test`) |
| TC-02 | Backend | Normal | V2 child department create under valid parent | Same as TC-01 with `parentDepartmentCode` populated | Unit or integration |
| TC-03 | Backend | Normal | V2 direct user create | Row emitted; **`targetUserId`** present; **`changeReason`** present; no denylisted keys | Unit or integration |
| TC-04 | Backend | Edge | V2 mutation with missing/blank `changeReason` | **400** validation; **no** activity row | Unit |
| TC-05 | Integration | Normal | `GET /api/activity-log/action-types` after deploy | Response includes all new department/direct codes with labels | Integration or manual |
| TC-06 | Integration | Normal | `POST /api/activity-log/search` filtered by new `actionType` | Rows returned for seeded actions within scope | Integration or manual |
| TC-07 | Integration | Normal | `GET /api/activity-log/{id}` for row from TC-01–TC-03 | Parsed `action_detail` matches stored shape; `request_params` present **if** aspect stores — **TODO** exact expectation | Integration or manual |
| TC-08 | Frontend | Normal | Activity log detail UI opens for seeded row | User sees reason, department/user identifiers (labeled); no script errors | Unit (`npm test`) and/or manual/browser |
| TC-09 | Backend | Regression | `USER_DELETE` / provisioning `USER_CREATE` | Still conform to `20260407` contracts | Unit |
| TC-10 | Integration | Security spot-check | Non-privileged activity-log reader | No secrets in `action_detail` / `request_params`; masking as per `activity-log-audit-evidence` if applicable | Manual |

### Test scenarios

#### Scenario 1: Department lifecycle audit

1. Perform root and child creates (and update/delete when available).
2. Search activity log by new types.
3. Open detail; verify structure and Korean/type labels.

#### Scenario 2: Direct user create vs HR create

1. Create one user via v2 direct and one via HR provision (if env available).
2. Confirm filter distinguishes events **TODO** per final `action_type` decision.

### Test data

- **TODO**: Provide executable SQL or API sequence to seed departments/users for QA (or reference `backend/src/main/resources/db/init-data` patterns).

### Test environment

- Frontend: `http://localhost:3001` (or per contract)
- Backend: `http://localhost:9200`
- Database: PostgreSQL (per project contract)

### 3.5 Browser automation verification (optional)

- **Applicable TCs**: TC-08, TC-06 (UI-heavy).
- **Procedure**: Login → 활동 이력 → filter → open detail → `browser_snapshot` assert visible fields.
- **Reference**: `docs/workflow/BROWSER-AUTOMATION-VERIFICATION-POLICY.md`

## 4. Checklist

### Frontend verification
- [ ] Detail renders new `action_detail` types
- [ ] Action type filter includes server-driven options
- [ ] Error handling verified

### Backend verification
- [ ] Each mutation in scope emits correct `action_type` and JSON
- [ ] `action-types` endpoint updated
- [ ] No denylisted data in stored JSON

### Integration
- [ ] Search + detail parity for seeded rows
- [ ] Scope rules unchanged (no regression)

### Documentation
- [ ] Requirement doc completed
- [ ] Specs and `docs/api-definition.md` updated (DOC-CODE-SYNC)

## 5. Test results

### Test run date

- **TODO** (yyyy-MM-dd after QA run)

### Test results

#### Frontend

- **TODO**

#### Backend

- **TODO**

**Commands:**

```bash
# TODO: one command per TC after implementation
```

**Outcome:**

- **TODO**

### Issues found and resolution

- **TODO**

### Next steps

1. Contract: finalize `action_type` naming and `USER_CREATE` discriminant.
2. Backend/Frontend: implement per handoff.
3. QA: run §3 TCs and update §5.

## 7. Final version (Korean) — add after all verification is complete

*(Omitted until QA completes verification per `docs/workflow/DOCUMENT-LANGUAGE-POLICY.md`.)*

---

**Author**: Requirements (subagent)
**Date**: 2026-04-08
**Status**: In progress
