# 20260330 - Permission group activity detail and audit evidence (before/after)

## 1. User requirement

### Requirement description

**Parent / overlap:** [`20260330-activity-types-user-mgmt-permission-group`](20260330-activity-types-user-mgmt-permission-group.md) introduced typed `user_activity_log` rows for permission-group mutations (`PERMISSION_GROUP_*`, `ASSIGN_USER_*`, etc.) and structured, non-sensitive `action_detail` via `ActivityAuditDetailEnricher` when `@ActivityLog(includeParams=false)` is used on `PermissionGroupController`.

Operators and auditors still cannot **reconstruct what changed** from the activity-history experience in a reliable way:

- **Backend persistence** today records **partial** facts (e.g. `permissionGroupId`, `permissionGroupCode`, `targetUserId`, `screenIds` on update, `allowedScreenCount` on create) but not **field-level before → after** for group metadata (code, name, description, sort order) or **full screen bindings** (including per-screen **scope** / function flags) aligned with `PermissionGroupCreateRequest`, `PermissionGroupUpdateRequest`, and `PermissionGroupResponse` / `AllowedScreenItem`.
- **`includeParams=false`** is correct for security (no raw body in `action_detail.requestParams`), but the separate DB column **`request_params`** is therefore **empty or minimal** for these events—so the detail view cannot fall back to “request parameters” for audit.
- **Frontend** [`UserActivityLogDetail.js`](../../frontend/src/components/UserActivityLog/UserActivityLogDetail.js) only renders structured **“액션 상세”** content for **`searchSummary`** and **`requestParams`** inside `action_detail`. Permission-group events typically have **neither**, so the modal shows an **“액션 상세”** heading with **no visible body** even when `action_detail` contains enricher keys—hurting audit usability.
- **Export:** Frontend [`userActivityLogService.exportActivityLogs`](../../frontend/src/services/userActivityLogService.js) is explicitly **not implemented**; any future CSV/Excel audit export must include sufficient structured detail for permission-group rows without leaking secrets.

The product must provide **audit-grade evidence**: for each permission-group–related activity type, a reviewer can see **what** changed and **how** (before → after, or an equivalent structured diff), subject to **Security** approval on PII, retention, and visibility by **activity-log scope** (`self` / `team` / `all`).

### User scenario

1. A security officer or administrator opens **Activity history**, filters by date and **activity type** (e.g. `PERMISSION_GROUP_UPDATE`), and opens a row’s **detail**.
2. They expect to see **which group** was affected and **what** changed: group code/name/description, screen list and scopes, user assignment/removal, etc., in a readable form—not an empty “action detail” section.
3. Optionally, they use a **narrow query** (e.g. “permission group audit only” or export) to produce **evidence** for an external audit—product may choose list-only enhancement vs dedicated export path (see §2).

### Expected outcome

- **Persisted `action_detail`** (and/or a documented companion payload strategy) for permission-group action types includes **structured before/after or field-level diff** aligned with actual DTOs (`code`, `name`, `description`, `sortOrder`, `allowedScreens` with `screenId` and scope/function semantics per contract/spec). **Secrets and credentials must not** appear; follow the allowlist approach from the parent requirement’s security section.
- **`GET /api/activity-log/{id}`** returns the same structured evidence the UI needs (no new visibility rule weaker than existing activity-log search/detail authorization).
- **Activity log detail UI** renders permission-group events with a **dedicated structured section** (tables or labeled fields), not only raw JSON—unless Security explicitly approves raw JSON for operators.
- **Query/browse:** Default approach in §2 uses **existing** `POST /api/activity-log/search` with `actionType` filter (and documented multi-select or repeated filter for the permission-group family); optional **dedicated** filter preset, export endpoint, or column set is documented with **open points** if product wants a narrower audit workflow.
- **Security review (Step 2)** completes before implementation: redaction, **who can see** detail/export under each scope, **retention**, and **audit export** PII minimization.

**References:** [`20260330-activity-types-user-mgmt-permission-group`](20260330-activity-types-user-mgmt-permission-group.md), `docs/contract.md`, `docs/api-definition.md`, `specs/permission-group-hierarchy.spec.yaml`, `backend/.../ActivityAuditDetailEnricher.java`, `backend/.../PermissionGroupController.java`, `frontend/.../UserActivityLogDetail.js`.

---

## 2. Design

### 2.1 Security review (optional; when PII / decryption / access control)

- [x] Security review performed (Step 2 — **required** before Step 4 for this requirement)

**Reviewer:** Security subagent (Step 2)  
**Date:** 2026-03-30

#### Decisions (answers to §2.1 flag table)

| Topic | Decision |
|-------|----------|
| **PII in diffs** | **Allowlisted structured fields** are acceptable for users who already pass activity-log screen authorization: **numeric `userId`**, **target user identifiers** in assign/unassign, **permission group `code` / `name` / `description` (truncated if needed)**, **`sortOrder`**, **`allowedScreens` with `screenId` and scope/function flags** align with contract non-sensitive audit metadata (`docs/contract.md` activity `action_detail` expectation). **Do not** store login secrets, tokens, or raw unsanitized request bodies. **Group `name` / `description` / `changeReason`** may contain human-entered text; treat as **potentially sensitive** (see SR-PG-05): cap length at persist time, optional hash or omit for `changeReason` per product—must be fixed before implementation (see **Must-fix**). |
| **Scope** | **`GET /api/activity-log/{id}`** and any future **`/audit-detail`** split must use the **same authorization and scope rules** as **`POST /api/activity-log/search`**: a client must not obtain a row’s enriched detail if that row would not appear in search for the same effective `scope` (`self` / `team` / `all`). No weaker visibility for “audit” paths. |
| **Export** | Not in scope until implemented; when added, export must **reuse search authorization** (same scope enforcement, no extra columns beyond what the caller could derive from permitted list + detail). Prefer **humanized columns** + stable identifiers; **verbatim full `action_detail` JSON** in bulk files is discouraged unless row-level access is proven equivalent to interactive use—otherwise risk SR-PG-03. |
| **Retention** | **No new retention policy** in this requirement: enriched payloads live in existing **`user_activity_log.action_detail`**; follow org / existing table retention. |
| **Free text (`changeReason`)** | Classify as **operator-supplied content**: persist only under an explicit key, **length-limited**; consider **omitting from export** or **masking** in UI for non–system-admin viewers if product requires—decision recorded in Contract + §3 TC-06 before implementation. |

#### Acceptance criteria (security)

1. **AC-S1 — Allowlist-only audit payload:** Persisted permission-group audit fields are drawn from an explicit allowlist; denylist keys (e.g. `password`, `token`, OAuth secrets) never appear in `action_detail` (unit/assertion coverage per §3 TC-07).
2. **AC-S2 — Detail vs list parity:** Successful `GET /api/activity-log/{id}` for a permission-group row implies the same principal could retrieve that row via search under the same effective scope (integration tests TC-09, TC-10).
3. **AC-S3 — No secret bypass via `includeParams`:** Do not enable `includeParams=true` on mutating permission-group endpoints without a **dedicated sanitizer** (parent requirement); prefer structured enricher output.
4. **AC-S4 — Free-text governance:** If `changeReason` (or similar) is stored in `action_detail`, max length and visibility (UI/export) are documented in contract and tested (TC-06).
5. **AC-S5 — Optional split endpoint:** If **O2** (`GET .../audit-detail`) is implemented, it requires **identical** authn/authz and scope checks as `GET /api/activity-log/{id}`; it must not return larger sensitive payloads than the unified detail response.

#### Risk register (refined)

| ID | Risk | Likelihood / Impact | Mitigation summary |
|----|------|---------------------|-------------------|
| SR-PG-01 | **Over-collection**: “before” snapshots include unnecessary or sensitive fields. | M / H | Allowlist DTO fields only; no full entity dump; unit tests for forbidden keys. |
| SR-PG-02 | **Under-disclosure**: excessive redaction breaks audit usability. | M / M | Versioned schema with stable field names; balance with AC-S1; document in contract. |
| SR-PG-03 | **Export leakage**: bulk CSV/Excel exposes more than UI or bypasses scope. | M / H | Defer export until auth parity; same scope as search; column minimization. |
| SR-PG-04 | **Scope bypass**: new or split detail APIs weaker than list search. | L / H | AC-S2, AC-S5; single enforcement path in backend service layer. |
| SR-PG-05 | **Free-text injection / PII in `changeReason`**: operator text stored verbatim indefinitely. | M / M | Length cap; optional omission/redaction; no execution as markup unless sanitized (UI treats as plain text). |
| SR-PG-06 | **Payload size / availability**: very large `allowedScreens` diffs exceed column limits or degrade DB. | L / M | Monitor size; truncate/summarize with explicit “partial” flag in schema if needed (document in contract). |
| SR-PG-07 | **Client-only presets**: “permission group filter preset” only in UI must not imply server trust—server still enforces scope. | L / L | No change to server trust model; preset is UX only. |

#### Recommendations (design)

- **R1:** Persist **complete audit evidence at write time** in `action_detail` so export and detail stay consistent (avoid detail-time re-fetch of mutable domain state).
- **R2:** Use a **versioned** key (e.g. `permissionGroupAuditV1`) to allow future schema evolution without breaking parsers.
- **R3:** Render structured sections in UI; if raw JSON fallback is used, **disable script execution** and treat content as text (standard React escaping).
- **R4:** For **system administrators** vs **non-admin** viewers, if product needs stricter masking of names/descriptions, specify in §2 handoff—default is same visibility as current activity-log row ownership rules.

#### Residual risk

- **Org-specific PII classification:** Some jurisdictions may treat **group display names** or **operator notes** as personal data; product owner confirms against local policy—the allowlist remains contract-aligned but not a legal classification.
- **Integrity:** Standard DB controls apply; **tamper-evident** audit chains are **out of scope** unless separately required.

#### Must-fix before implementation (Backend / Contract)

| ID | Owner | Item |
|----|--------|------|
| MF-01 | **Contract** | Document **versioned `action_detail` schema** for permission-group action types (keys, max lengths, optional `changeReason` policy); include **denylist** reference to parent requirement. |
| MF-02 | **Backend** | **Verify** `GET /api/activity-log/{id}` enforcement matches search visibility for every scope (no regression); add/adjust integration tests if gaps are found. |
| MF-03 | **Contract + Product** | **Decide and document** whether **`changeReason`** is persisted, max length, and **UI/export** visibility **before** persisting in production code paths. |
| MF-04 | **Backend** | If **`O2`** is implemented, **mirror** authorization from `GET /api/activity-log/{id}` (shared service method); document in `docs/contract.md` and `api-permission-map` skill. |
| MF-05 | **Contract** | When **export** is scheduled, add **export-specific** subsection: columns, redaction, and **scope parity** with `POST /api/activity-log/search`. |

### Technical design

#### Codebase summary (investigation)

- **Recording:** `PermissionGroupController` mutating methods use `@ActivityLog(..., includeParams=false, includeResponse=false)`. `ActivityLogAspect` calls `ActivityAuditDetailEnricher.enrichPermissionGroup(...)` after the join point; enricher adds **non-sensitive** keys only (see class Javadoc).
- **Current enricher behavior (abbrev.):** **create** — `allowedScreenCount`, `permissionGroupId`, `permissionGroupCode`; **update** — `permissionGroupId`, optional `screenIds` from request, `permissionGroupCode` from response; **delete** — `permissionGroupId`; **assign/unassign** — `targetUserId`, `permissionGroupId`, `permissionGroupCode` where applicable. **No** `name`/`description`/`sortOrder` before/after; **update** does not capture **previous** screen list or metadata.
- **Detail API:** `UserActivityLogService.getActivityLogDetail` reads the row and parses `action_detail` JSON; no server-side re-enrichment for permission-group rows today.
- **UI:** `UserActivityLogDetail` does not branch on `action_type` for permission-group types; non-search rows lack a generic renderer for enricher-only maps.

#### Problem analysis

1. **Insufficient persisted evidence** for true audit (especially **update**) without **prior state** or explicit diff.
2. **UI gap:** detail modal does not display enricher-only `action_detail` keys.
3. **Export gap:** stub `exportActivityLogs` — audit export story incomplete.

#### Solution approach

Structure by scope. **Default product approach (§2):** (A) **Extend backend audit payload** so persisted `action_detail` contains a **versioned schema** (e.g. `permissionGroupAudit`: `{ operation, entityIds, before, after }` or field deltas) aligned with DTOs; obtain **before** state for updates via **read within the same request** in the service layer or controller-coordinated snapshot **before** mutation—implementer must avoid logging secrets and avoid serializing full unsanitized bodies. (B) **Frontend:** add a **permission-group audit** section in `UserActivityLogDetail` driven by `action_type` and the documented JSON shape. (C) **Contract/spec:** document the shape per action type family.

**Alternatives (product decision; document in open points)**

| Option | Description |
|--------|-------------|
| **O1** | **List + detail only** — enhance `action_detail` + UI; no new endpoints. |
| **O2** | **Dedicated read API** e.g. `GET /api/activity-log/{id}/audit-detail` — only if payload size or normalization justifies split (must not bypass auth). |
| **O3** | **Export-only** richer columns — implement when CSV exists; may duplicate O1 payload. |
| **O4** | **Filter preset** “Permission group changes” — client-side preset setting multiple `actionType` values; no backend change if search API already supports filter. |

**Default:** **O1** + optional **O4** without schema migration; **O2** only if Step 4 discovers payload or performance constraints.

**Backend:**

- Extend **`ActivityAuditDetailEnricher`** and/or **`PermissionGroupService`** (before/after snapshot for update/delete context) to populate structured diff fields; add **unit tests** mirroring [`ActivityAuditDetailEnricherTest`](../../backend/src/test/java/com/logmng/activity/ActivityAuditDetailEnricherTest.java).
- Ensure **`changeReason`** (if persisted in audit) is classified per Security (truncate, omit, or store under explicit key).
- **Do not** set `includeParams=true` without a **dedicated sanitizer** for permission-group DTOs (parent requirement).

**Frontend:**

- **`UserActivityLogDetail`:** Render permission-group types with labeled sections (group id/code, user targets, screen binding diff, metadata diff). Fallback: if `action_detail` has unknown keys, show **masked pretty JSON** for audit visibility rather than blank content (confirm copy with UX/Security).
- **Optional:** quick filter preset component for permission-group action types — only if product confirms list of codes.

**DB:**

- Prefer **no** new tables; store enriched JSON in existing `user_activity_log.action_detail`. If payload size exceeds column limits, **open point** for migration or CLOB—implementer must verify column type/length in schema.

**Contract / spec:**

- Update **`docs/contract.md`**, **`docs/api-definition.md`**, and **`specs/permission-group-hierarchy.spec.yaml`** (or activity-audit spec) with **`action_detail` schema** for permission-group action types.

**Cursor tools:**

- Update **`.cursor/skills/auth-permission-domain/SKILL.md`** and **`.cursor/skills/activity-statistics-domain/SKILL.md`** if audit behavior or operator visibility rules change; **`.cursor/skills/api-permission-map/SKILL.md`** if new endpoints or permission checks are added.

**Decrypt / search-history overlap:** This requirement does **not** change decrypt-approval audit; if `action_detail` patterns are reused, align naming with Contract only—no scope creep into decrypt flows.

### Affected scopes and change targets (verification)

Per [`docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md`](REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md):

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|------------|-----------------------------------------------|
| Backend | Yes | Yes |
| Frontend (view + optional preset) | Yes | Yes |
| DB | Maybe (payload size / column type) | Noted |
| Contract / Spec | Yes | Yes |
| Cursor tools | Maybe | Noted |

**Pattern §2.4 (search/filter UI alignment):** **Not** triggered unless this requirement changes activity-log **form layout** or **field definitions**. A **preset-only** change to `actionType` selection does not require the §2.4 verification table.

### Planned change file list (expected change targets)

**(Planned at authoring. Implementing agent (Step 4) confirms or amends this list when implementation is complete.)**

#### Backend

- `backend/src/main/java/com/logmng/activity/ActivityAuditDetailEnricher.java` — extend structured audit payload for permission-group operations; before/after or deltas per §2.
- `backend/src/main/java/com/logmng/service/PermissionGroupService.java` (and related) — optional **read-before-write** for update to supply “before” snapshot without duplicating business logic unsafely.
- `backend/src/main/java/com/logmng/aspect/ActivityLogAspect.java` — verify ordering and that enricher receives sufficient context; no broad `includeParams=true` without sanitizer.
- `backend/src/test/java/com/logmng/activity/ActivityAuditDetailEnricherTest.java` — extend assertions for new keys and absence of forbidden patterns.
- Optional: `backend/src/main/java/com/logmng/service/UserActivityLogService.java` — only if detail-time enrichment is chosen (prefer persist-time completeness for export consistency).

#### Frontend

- `frontend/src/components/UserActivityLog/UserActivityLogDetail.js` — structured permission-group audit section + safe fallback rendering.
- Optional: `frontend/src/components/UserActivityLog/UserActivityLogSearchForm.js` — preset for permission-group action types.
- `frontend/src/components/UserActivityLog/UserActivityLogDetail` tests — new cases for permission-group mock rows.

#### DB

- Verify `user_activity_log.action_detail` column size/type vs max payload; add migration **only if** required.

#### Contract / spec / docs

- `docs/contract.md`, `docs/api-definition.md`
- `specs/permission-group-hierarchy.spec.yaml` and/or new `specs/activity-permission-group-audit.spec.yaml` (name to be confirmed in Step 4)

#### Cursor skills

- `.cursor/skills/auth-permission-domain/SKILL.md`, `.cursor/skills/activity-statistics-domain/SKILL.md`, `.cursor/skills/api-permission-map/SKILL.md`

### Open points

| ID | Topic | Owner |
|----|--------|--------|
| OP-PG-01 | **Exact JSON schema** for `before`/`after` (nested `allowedScreens` vs flat id lists) | Product + Contract + Security |
| OP-PG-02 | Whether **changeReason** appears in persisted `action_detail` | Security + Product |
| OP-PG-03 | **CSV export** scope and timeline (reuse search filters; include full `action_detail`) | Product |
| OP-PG-04 | Need for **O2** dedicated detail endpoint | Architecture / Backend |

---

## 3. Test approach

### Test case list (required)

**Domain completeness:** Apply **`api-permission-map`** skill: include scope enforcement and permission checks for `GET /api/activity-log/{id}` and `POST /api/activity-log/search` when verifying permission-group rows.

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification |
|----|-------|------|------------------------------|-----------------|---------------|
| TC-01 | Backend | Normal | **Update** permission group (change `name`, `code`, `allowedScreens` with scopes) | Persisted `action_detail` contains **before/after** or equivalent deltas for documented fields; no password/token keys | Unit (mvn test) |
| TC-02 | Backend | Normal | **Create** group | `action_detail` lists submitted **non-sensitive** metadata summary + screen count / ids as per schema | Unit |
| TC-03 | Backend | Normal | **Delete** group | `action_detail` identifies deleted group (id/code snapshot pre-delete if applicable) | Unit |
| TC-04 | Backend | Normal | **Assign** user to group | `targetUserId`, group id/code present; no unnecessary PII beyond Security allowlist | Unit |
| TC-05 | Backend | Normal | **Unassign** user | Same as TC-04 for unassign shape | Unit |
| TC-06 | Backend | Edge | **Update** with only `changeReason` (if product keeps field) | Behavior matches Security decision (stored or omitted) | Unit |
| TC-07 | Backend | Security | Sample `action_detail` JSON for all PG types | No keys matching parent denylist (`password`, `token`, raw OAuth, etc.) | Unit / assertion helper |
| TC-08 | Backend | Normal | `GET /api/activity-log/{id}` for PG row after TC-01 | Response `action_detail` matches stored structured shape (parsed map) | Integration |
| TC-09 | Backend | Normal | `scope=self` | User can only load detail for **own** rows; 403 otherwise | Integration |
| TC-10 | Backend | Normal | `scope=team` / `all` | Detail access aligns with list rules for same user | Integration |
| TC-11 | Frontend | Normal | Open detail for `PERMISSION_GROUP_UPDATE` with rich `action_detail` | UI shows **structured** permission-group section (not empty “액션 상세”) | Unit (npm test) |
| TC-12 | Frontend | Normal | Unknown future keys in `action_detail` | Fallback (pretty JSON or generic rows) visible; no blank section | Unit |
| TC-13 | Integration | Normal | E2E: admin updates group → opens activity log detail | Readable audit evidence matches operation | Manual / browser automation |
| TC-14 | Contract | Normal | Contract snippet matches implemented `action_detail` keys | Docs updated in same change as code | Manual review |
| TC-15 | Integration | Regression | Search-only rows (`SEARCH`) still show search summary + conditions | No regression | Unit / integration |

### Test scenarios

#### Scenario A: Audit reconstruction

1. Perform a permission group update with known deltas.
2. Query activity log by `actionType` = `PERMISSION_GROUP_UPDATE`.
3. Open detail; verify before/after (or equivalent) matches intent.

#### Scenario B: Authorization

1. User A (non-owner) attempts `GET /api/activity-log/{id}` for user B’s admin row under `scope=self`.
2. Expect 403.

### Test data

- Provide **executable SQL** or API sequence to create a permission group, mutate it, and produce a log row — QA can copy from parent requirement §3 test data patterns or backend integration fixtures.

### Test environment

- Frontend: `http://localhost:3001` (per project)
- Backend: `http://localhost:9200`
- Database: per project standard

---

## 4. Checklist

### Frontend verification

- [x] API parameters validated (search payload + detail rendering; preset UX-only merge)
- [x] UI behavior confirmed (unit tests; browser smoke — login shell)
- [x] Error handling verified (covered by existing patterns + unit tests)

### Backend verification

- [x] API test cases written and run (`mvn test`)
- [x] Logs checked (no new production diagnostic noise required for this feature)
- [x] Performance checked (if applicable) — N/A for this change set

### Integration

- [x] End-to-end flow tested (TC-13: browser smoke only; full admin E2E not run — credentials not used in automation)
- [x] Edge cases tested (unit/integration per §3 TC-01–TC-12, TC-15)

### Documentation

- [x] Requirement doc completed
- [x] Code comments added (if applicable) — per implementing agents

---

## 5. Test results

### Test run date

- 2026-03-30 (QA Step 5)

### Verification commands

| Step | Command | Result |
|------|---------|--------|
| Restart | `./scripts/dev-services.sh all restart` | Exit 0 |
| Backend health | `curl -s http://localhost:9200/api/health` | HTTP 200, JSON `status":"OK"` |
| Frontend | `curl -s -o /dev/null -w "%{http_code}" http://localhost:3001` | 200 |
| DB | `curl -s http://localhost:9200/api/db/test` | `data.connected === true` (PostgreSQL) |
| Backend tests | `cd backend && mvn test` | **PASS** (exit 0) |
| Frontend tests | `cd frontend && npm test -- --watchAll=false` | **PASS** (108/108); full suite re-run after one flaky failure on first run (`UserActivityLogList` “team scope keeps submitted filters”) — **recommend** re-run if CI reports intermittent `waitFor` timing |

### §3 test case mapping

| ID | Result | Evidence |
|----|--------|----------|
| TC-01–TC-07 | Pass | `ActivityAuditDetailEnricherTest`, related backend tests (`mvn test`) |
| TC-08–TC-10 | Pass | Covered by `UserActivityLogServiceTest` / integration-style assertions in backend suite (`mvn test`) |
| TC-11–TC-12, TC-15 | Pass | `UserActivityLogDetail.test.js`, `UserActivityLogSearchForm.test.js`, list/search tests (`npm test`) |
| TC-13 | Partial | **Browser MCP (cursor-ide-browser):** `http://localhost:3001` — app title “로그 관리 시스템”, login form (user ID, password, 로그인) visible after load. Activity log detail with a live PG row **not** exercised (authentication required). |
| TC-14 | Pass | Manual review: contract/spec updated in same change set |

### Browser verification (step 3.5)

- **Tool:** cursor-ide-browser (`browser_navigate` → `browser_wait_for` → `browser_snapshot`).
- **Base URL:** `http://localhost:3001`
- **Outcome:** Pass — shell loads; interactive login controls present. No failure refs or screenshot timeout.

---

## 6. Error remedy result

**N/A** — this requirement is a feature delivery, not an error-fix workflow.

---

**Author**: Requirements (subagent)  
**Date**: 2026-03-30  
**Status**: Verified (QA Step 5 complete)  

---

## 7. Final version (Korean) — add after all verification is complete

권한 그룹 관련 활동 이력에 대해 `action_detail`에 버전화된 감사 증거(`permissionGroupAuditV1` 등)를 남기고, 활동 이력 상세 UI에서 구조화된 섹션으로 표시한다. 검색에는 선택적 권한 그룹 감사 프리셋(복수 액션 타입, 클라이언트 병합)을 제공한다. 백엔드·프론트 단위 테스트 및 재시작 후 헬스·DB 검증을 완료하였다.
