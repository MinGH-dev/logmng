# 20260330 - Audit evidence: user activity log (conservative security defaults)

## 1. User requirement

### Requirement description

Define product requirements that **resolve open items** in `docs/security/audit-evidence-user-activity-log-manual-draft.md` **Section 8** by adopting **conservative security defaults**: assume **worst-case PII exposure** in any logged payload, **minimize collection**, enforce **strict role-based access**, require **encryption in transit and at rest** aligned with organizational standards, align **retention and legal hold**, and treat **third-party audit / data export** with **approval workflows and scope limits**. The requirement is the **single authoritative basis** for separate **SVG wireframe** production: screen layout regions, control names (English labels acceptable in this document for §1–§3), and visible states must be explicit enough that designers do not infer behavior.

This document does **not** replace legal review; **TODO** items flag **product owner (PO) or legal** confirmation where organizational policy must override defaults.

**Alignment with manual §4, §4.5, §4.6, §7, §8**

| Manual §8 open item | Conservative product decision (this requirement) |
|---------------------|-----------------------------------------------------|
| Same depth for Delete / Update / Create logging | Use a **uniform event envelope** (actor, time, action type, resource identifiers, structured `action_detail`) for all mutation classes. **Payload depth** follows a **field classification matrix**: non-sensitive fields may appear as plaintext in allowlisted keys; **sensitive fields** are **never** stored in plaintext in logs—use **omit**, **mask**, or **one-way hash / token surrogate** per field class. |
| Physical vs soft delete; snapshot / archive | **Default**: prefer **soft delete** for user-visible entities where feasible. If **physical delete** is required, a **mandatory delete snapshot** (minimal allowlisted fields + identifiers) **must** be persisted in `action_detail` (or an equivalently controlled audit payload) **in the same security class** as other mutations **before** physical removal. PO confirms domain exceptions. |
| Sensitive fields in change logs | **Never** log secrets/credentials in plaintext. **Change**: persist **before/after** only for **allowlisted** non-secret fields; for sensitive classes use **masking**, **hash of value**, or **omission** with a stable **field touched** indicator. |
| Privacy policy / third-party audit / export | **Conservative**: any **export** of activity or audit packages for **third parties** goes through an **approval workflow**, **minimum necessary scope**, and **documented purpose**. UI surfaces for approval are **optional** until PO confirms process (see Screen 4). |
| Cost / key rotation | **Retention** aligns with **legal hold** and organizational records management; **encryption at rest and in transit** per deployment standard; **key rotation** follows **organizational KMS / infra policy** (reference only—do not invent key names in specs). |
| Point-in-time DB proof vs log-only | **Default evidence path**: **append activity log** plus **rich snapshots in `action_detail`** where required. **Per-entity versioning tables** are **out of scope** unless explicitly added in a future requirement; remain **optional / TBD** in one tracked bullet. |

### User scenario

1. **Auditor / security operator** opens the **activity log** screen, filters by date range, user, and action type, and reviews rows for delete/update/create/copy events with **consistent columns** and **masking** appropriate to their role.
2. **Privileged auditor** opens **activity log detail** for one row and sees **structured `action_detail`** (including delete snapshot or before/after for allowlisted fields). **Copy events** show **truncated** copy body by default; a **separate privileged action** may reveal **full** body where policy allows.
3. **Compliance user** (if enabled) reviews **who viewed** full copy body or other **sensitive detail** via an **access audit** screen/list tied to the same security policy.
4. **Administrator** (if export approval is in scope) **submits** or **approves** a **limited-scope export** for third-party audit; unauthorized users cannot export raw sensitive payloads.

### Expected outcome

- **Logging semantics**: Delete / update / create events use the **same structural depth** at the API/logging layer; **sensitive data** is never logged in plaintext; secrets are excluded.
- **Delete**: **Soft delete** preferred; else **mandatory delete snapshot** in `action_detail` before physical delete.
- **Update**: **Before/after** for allowlisted fields only; no secret plaintext.
- **Copy (in-app)**: `copy`-class `action_type` (or equivalent taxonomy) with **selection text** in `action_detail`, **max length** and **truncation flag**; **authorized roles** for **full body** view; **access audit** when viewing full copy body (see §2.1).
- **Retention / crypto / legal hold**: Requirements are **stated** for alignment; implementation follows **org standard** without naming specific vendor key IDs in this doc.
- **Third-party / export**: **Approval + scope limitation**; UI optional pending PO.
- **Point-in-time DB**: Default **log + snapshots**; entity versioning **TBD** until a future requirement explicitly lists entities.
- **SVG readiness**: **Numbered screens** in §1.1 list **regions, controls, states** (including masked vs privileged unmasked) so wireframes can be drawn without guessing.

### 1.1 Screen specification for SVG wireframes (authoritative for layout)

Design docs for **search/filter field standards** (when aligning with existing activity log list): `docs/design/forms-and-filters.md`, `docs/design/search-fields-by-screen.md`, `docs/design/search-field-definition-items.md` (implementers must not hardcode undefined standards—see `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md` §2.4 if pattern applies).

---

#### Screen 1 — Activity log search & list

| Attribute | Specification |
|-----------|----------------|
| **Working name** | User Activity Log — List |
| **Route / view id (placeholder)** | `activity-log` (matches `frontend/src/App.js` view key) |
| **Purpose** | Search and browse `user_activity_log` rows within scope. |

**Regions (top → bottom)**

1. **Page header**: Title (e.g. “User activity log”), short description line.
2. **Filter panel** (collapsible optional; default expanded for wireframe): grouped search fields.
3. **Toolbar**: Primary actions **Search**, **Reset**; optional **Export** (gated—only if export approval requirement is in scope and role allows).
4. **Results grid**: scrollable table area.
5. **Grid footer**: pagination (page size, prev/next, total count), optional “rows per page”.
6. **Auxiliary**: global **loading** overlay region; **inline error** banner region below header or above grid.

**Filter form fields (minimum set; labels English for wireframe)**

| Field | Control | Notes |
|-------|---------|--------|
| Date range | Start date, end date (date pickers) | Row 1 preferred per existing design standards |
| User | Department, user name, user ID | Same **user block** width rules as aligned screens when the shared filter pattern applies (see `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md` §2.4 where applicable) |
| Action type | Multi-select or dropdown | Populated from `getActivityLogActionTypes` pattern |
| Scope | Hidden or read-only for non-admin | Reflects `screenScopes['activity-log']` |

**Table columns (minimum)**

| Column | Content |
|--------|---------|
| Timestamp | `created_at` (org timezone display) |
| User | `user_id` / `username` (masking rules per role) |
| Action type | `action_type` |
| Summary | Short string derived from `action_detail` or fixed label |
| IP | `ip_address` (may be masked for non-privileged) |
| Actions | **View detail** (opens detail region/modal) |

**Controls**

- Buttons: **Search**, **Reset**, **View detail** (per row), optional **Export**.
- Dropdowns: action type, page size.
- Date pickers: start/end.

**States**

| State | UI behavior |
|-------|-------------|
| Default | Empty table or last search cleared; filters at defaults per product rules. |
| Loading | Skeleton rows or spinner in grid region; disable Search/Export. |
| No results | Empty state illustration/message in grid body. |
| Error | Banner + message; grid may show last good data or empty per UX choice. |
| **Masked vs unmasked** | Non-privileged: **no** full `action_detail` in grid; privileged: may show **richer summary** column only—**never** full secrets in grid. |

---

#### Screen 2 — Activity log detail (row detail)

| Attribute | Specification |
|-----------|----------------|
| **Working name** | User Activity Log — Detail |
| **Route / view id (placeholder)** | `activity-log-detail` (modal or right **drawer**; implementation choice) |
| **Purpose** | Show one row’s metadata + parsed `action_detail` JSON with field-level masking. |

**Regions**

1. **Header**: Title “Activity detail”, close button, optional **Copy log id**.
2. **Metadata block**: `id`, `user_id`, `username`, `action_type`, `created_at`, `ip_address`, `request_method`, `request_path`, `response_status`, `success`, `error_message` (as applicable).
3. **Structured detail**: JSON tree or key-value for `action_detail` (permission group audit, delete snapshot, before/after, copy payload).
4. **Copy payload subsection** (when `action_type` indicates in-app copy): **truncated text** preview, **“truncated” badge**, character count, **View full content** (privileged only).
5. **Footer**: Close; optional **Open access audit for this resource** link (if Screen 3 exists).

**Controls**

- **Close**, **View full copy body** (privileged, triggers access audit).
- Expand/collapse for JSON sections.

**States**

| State | UI behavior |
|-------|-------------|
| Loading | Spinner in panel. |
| Error | Error message if fetch fails. |
| Masked (default for mixed audiences) | Sensitive keys show **MASKED** or redacted; no plaintext PII for unauthorized role. |
| **Privileged unmasked** | Full allowlisted detail; **copy body** full text only after explicit action; **must** log access (Screen 3). |

---

#### Screen 3 — Access audit (sensitive detail / copy body views)

| Attribute | Specification |
|-----------|----------------|
| **Working name** | Activity log — Access audit |
| **Route / view id (placeholder)** | `activity-log-access-audit` |
| **Purpose** | List **who viewed** sensitive activity detail or **full copy body**, **when**, and **which parent log id** (conservative policy: **required** if full copy body viewing is implemented). |

**Regions**

1. **Page header**: Title, description (“Records access to sensitive activity detail and full copy content.”).
2. **Filter panel**: date range, accessor user (department / name / ID), target activity log id (optional).
3. **Results table**: accessor, timestamp, target `user_activity_log.id`, access type (e.g. `DETAIL_VIEW`, `COPY_BODY_FULL`).
4. **Footer**: pagination.

**Controls**: Search, Reset, export (if allowed—same approval gates as Screen 1).

**States**: Default, loading, empty, error; **no** masking of **accessor identity** for auditors viewing this screen (auditors are privileged; PO may define narrower roles).

---

#### Screen 4 — (Optional) Retention summary & export approval

| Attribute | Specification |
|-----------|----------------|
| **Working name** | Audit policy — Retention & export (read-only + approval) |
| **Route / view id (placeholder)** | `audit-policy-activity-log` |
| **Purpose** | **Implement only if PO confirms** operational need: **read-only** display of **retention class** / **legal hold** alignment statement; **list of pending export approvals** for third-party audit packages. |

**Regions**: Header; **Retention summary** (static text from config); **Approval queue** table (request id, requester, scope, status, approver); detail drawer for request.

**Controls**: Approve / Reject (approver role only).

**States**: Default, loading, empty queue, error.

**TODO (PO)**: Confirm whether Screen 4 is in scope; conservative policy **recommends** either read-only retention disclosure **or** explicit “contact DBA” text instead of fabricated numbers.

---

## 2. Design

### 2.1 Security review (optional; when PII / decryption / access control)

**Applicable**: Yes — activity logs, copy payloads, and audit exports may contain PII and sensitive business data.

**Traceability**: Criteria **S-1–S-7** below are aligned with `docs/security/audit-evidence-user-activity-log-manual-draft.md` **§4.6** (implementation/operations security acceptance criteria). This requirement **tightens** the manual’s “prefer separate access audit where possible” to a **must** for **full** stored copy-body viewing (see **Additional conservative criteria**).

- [x] Security review performed (formal Security subagent review of §2.1, §3, §1.1 screens — 2026-03-30)

**Risks**

- **Over-collection** of clipboard text or mutation payloads.
- **Unauthorized** viewing of full copy body or unmasked `action_detail`.
- **Export** leakage to third parties without approval.
- **Secrets** accidentally logged via `request_params` or copy events.

**Acceptance criteria (aligned with manual §4.6 S-1–S-7)**

| ID | Criterion |
|----|-----------|
| **S-1** | **Scope**: Only **in-app** selection/copy events are logged; **OS-wide** clipboard and other apps are **out of scope**; documented in operator runbook (cf. manual §4.6 범위 포함·제외). |
| **S-2** | **Forbidden data**: Passwords, session tokens, API keys, payment data, and other **secret** classes **must not** appear in `action_detail`, `request_params`, or copy payload. Client and server **strip or reject** forbidden regions; **password-type / secret input** surfaces should **omit body or suppress copy logging** where manual §4.6 excludes such content. |
| **S-3** | **PII control**: Where PII may appear, **mask** in UI for non-privileged roles; **full** view only for **explicitly authorized** roles; **purpose-appropriate** use (no **off-purpose** browse/export of copy bodies — operational policy **TODO** with legal/compliance per manual §4.6). |
| **S-4** | **Least privilege**: Roles that can see **full copy body** or **unmasked** detail are **minimal** and **enumerated** in permission model; periodic review is an operational **TODO** for PO/Security. |
| **S-5** | **Transparency**: Public / employee-facing privacy notices must **align** with actual collection (legal **TODO**). |
| **S-6** | **Retention / deletion**: Activity logs and access-audit rows follow **same retention and legal hold** classes as other audit data; destruction is **irreversible** per org procedure (cf. manual §4.6 보존·삭제). |
| **S-7** | **Change control**: Changes to **in-app copy logging feature enable/disable**, **max copy length**, **field allowlists**, or **role mappings** require **tracked change approval** (process **TODO** for PO; cf. manual §4.6 투명성·거버넌스 / S-7). |

**Additional conservative criteria**

- **Access audit**: Any **view** of **full** in-app copy text from storage **must** generate an **append-only** access-audit record (who, when, target activity log id).
- **Encryption**: **TLS** for transport; **at-rest** encryption per **database / storage org standard**; **key rotation** per **organizational KMS policy** (no vendor-specific key names in this requirement).
- **Third-party export**: **Approval** + **minimum scope**; audit trail of export action.

**Security reviewer notes (2026-03-30)**

- **Residual risks**: (1) **Client-originated copy payloads** are not cryptographically bound to server state — strong integrity claims need **correlation IDs** and operational validation (manual §4.5 A–E). (2) **PO/legal/compliance TODOs** (S-5, Screen 4, periodic access reviews) remain until organizational sign-off. (3) **Screen 3** intentionally shows **accessor identity** without masking for auditor accountability — confirm **role scope** so this list is not exposed beyond intended audit roles.
- **Recommendation**: **Go-with-conditions** — security acceptance criteria **S-1–S-7** and access-audit **must** for full copy-body view are **accepted** as stated; implementation may proceed when **Contract/DBA** lock schemas for masked vs `reveal=full` APIs and access-audit store, and **PO** tracks open governance TODOs (S-5, S-7 process, Screen 4 scope).
- **Must-fix before implementation** (security): None beyond embedding the above in contract/spec — i.e. **no hold** on criterion acceptance; **must-fix in delivery** includes **server-side enforcement** of masking/reveal gates, **append-only** access-audit on privileged reveal, and **tests** mapped to §3 (TC-01–TC-08).

### 2.2 Architecture notes (performance & consistency)

- **Shared patterns**: Field classification and `action_detail` JSON shapes for copy, delete snapshot, and allowlisted before/after should be defined in a **single canonical spec** (alongside existing audit specs) and consumed by contract, backend serialization, and frontend masking to avoid divergence.
- **Payload size**: Enforce **maximum stored lengths** per event class at the persistence boundary; list/search APIs should return **summaries or projections**, not full `action_detail` for every row, to control response size, DB I/O, and memory.
- **Indexing & search**: Existing btree indexes support typical date/user/action filters. **Full-text search** on copy or `action_detail` content is **discouraged by default** (storage, write cost, PII surface); prefer structured filters. Add **JSONB/expression indexes** only for justified, bounded query patterns.
- **Access audit storage**: Prefer a **dedicated append-only access-audit table** (or clearly separated rows) indexed by accessor, target log id, and time—unless a single-table retention story is mandatory—so Screen 3 queries stay efficient and taxonomy stays clear.
- **Append-only semantics**: Align documented “append-only” behavior with actual DB/application rules (e.g., whether row updates are permitted).
- **Async vs sync logging**: If logging is asynchronous, document **delivery guarantees** and whether critical mutations (e.g., physical delete with mandatory snapshot) must **fail closed** if audit persistence fails.
- **Operations**: Monitor growth and latency of `action_detail`-heavy paths, access-audit volume, and approved exports.

### 2.3 Technical design

#### Codebase summary

- **DB**: `user_activity_log` stores append-only style activity rows with `action_type` and `action_detail` **TEXT** (JSON serialized). Key columns: `user_id`, `username`, `action_type`, `action_detail`, `ip_address`, `user_agent`, HTTP metadata, `created_at`. See `backend/src/main/resources/db/schema_user_activity_log.sql`.
- **API patterns**: Search and detail endpoints exist per `docs/api-definition.md` / contract; Frontend `UserActivityLogList`, `UserActivityLogDetail`, services in `userActivityLogService`.
- **Permission group audit**: Structured JSON for permission-group actions may follow `specs/activity-permission-group-audit.spec.yaml` (`permissionGroupAuditV1`).

#### Problem analysis

1. **Manual §8** leaves **depth**, **delete mode**, **sensitive field handling**, **export**, and **entity versioning** open—blocking consistent audit evidence and UI design.
2. **Copy** and **detail view** create **high PII risk** without **role gates** and **access audit**.
3. **Uniform evidence** for auditors requires **one field-class policy** across delete/update/create.

#### Solution approach

**Backend:**

- Implement **field classification** for serializers that write `action_detail`: **NEVER_PLAINTEXT** (omit/mask/hash), **ALLOWLIST_PLAINTEXT**, **HASH_ONLY**, **SUMMARY_ONLY**.
- **Delete**: On physical delete path, **abort** or **block** unless **delete snapshot** written in same transaction as delete (product-specific error handling **TODO** for PO).
- **Update**: Emit **before/after** objects with **allowlisted** keys only; secrets excluded.
- **Copy**: New or extended `action_type` values for in-app copy; store **truncated** text + **was_truncated** + **length**; server-side **max length** enforcement.
- **Detail API**: Return **masked** payload by default; **separate** endpoint or query param `reveal=full` for **privileged** roles only, which **writes access audit**.
- **Access audit**: New table or reuse append log with dedicated `action_type` **ACCESS_AUDIT_VIEW** — **must** be queryable (Screen 3). Exact schema **TODO** for Contract/DBA.
- **Export**: If implemented, gated API with **approval record** in DB.

**Frontend:**

- Screens per §1.1; **never render** secrets; show **masked** labels.
- **View full copy** triggers privileged API + confirm dialog optional.

**DB:**

- Possible extensions: **access_audit** table; **no** plaintext extension of forbidden fields in migrations without Security sign-off.

**Contract / specs:**

- Update `docs/contract.md`, `docs/api-definition.md`, and relevant `specs/*.spec.yaml` when APIs and `action_detail` shapes are finalized.

#### Diagnostic phase (mandatory for error/bug fix only)

*Not applicable — this requirement is a feature/policy definition, not an error fix.*

### 2.4 Affected scopes and change targets (verification)

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|------------|-----------------------------------------------|
| Backend | Yes | [ ] |
| Frontend | Yes | [ ] |
| DB | Likely (access audit) | [ ] |
| Contract / Spec | Yes | [ ] |
| Cursor tools (skills) | Yes — activity / api-permission | [ ] |

### 2.5 Planned change file list (expected change targets)

**(Planned at authoring. Implementing agent confirms or amends in Step 4.)**

#### Frontend

- `frontend/src/components/UserActivityLog/UserActivityLogList.js` — filters, export gate, states.
- `frontend/src/components/UserActivityLog/UserActivityLogDetail.js` — structured `action_detail`, copy subsection, masked vs privileged.
- `frontend/src/components/UserActivityLog/UserActivityLogTable.js` — columns, masking.
- **New** (if approved): `ActivityLogAccessAudit*.js` under `frontend/src/components/…` — Screen 3.
- **New** (optional Screen 4): policy/approval components.

#### Backend

**(Actual — Step 4 backend pass, audit evidence APIs)**

- `backend/src/main/java/com/logmng/controller/UserActivityLogController.java` — `GET /api/activity-log/{id}` applies masking after load; `POST /api/activity-log/{id}/privileged-reveal`; `GET /api/activity-log/access-audit`; list/detail masking for `IN_APP_COPY` + IP mask for non–system-admin.
- `backend/src/main/java/com/logmng/service/UserActivityLogService.java` — `privilegedRevealCopyBody`, `searchAccessAudit`, `404` for missing detail; `UserActivityAccessAuditRepository` injection.
- `backend/src/main/java/com/logmng/repository/UserActivityAccessAuditRepository.java` — JDBC insert + scoped search for `user_activity_access_audit`.
- `backend/src/main/java/com/logmng/util/ActivityLogAuditMasking.java` — copy preview max length, IP last-octet mask.
- `backend/src/main/java/com/logmng/util/ActivityLogAuditAuthorization.java` — reveal/access-audit gate (`activity-log-access-audit` or system admin).
- `backend/src/main/java/com/logmng/dto/request/PrivilegedRevealRequest.java`
- `backend/src/main/java/com/logmng/config/ScreenAccessInterceptor.java` — path rules for `privileged-reveal` and `access-audit` (alternate screen `activity-log-access-audit`).
- `backend/src/main/java/com/logmng/constants/ScreenConstants.java` — `activity-log-detail`, `activity-log-access-audit`.
- `backend/src/main/java/com/logmng/constants/ActivityActionType.java` — `IN_APP_COPY`.
- Tests: `ActivityLogAuditMaskingTest`, `UserActivityLogPrivilegedRevealTest`, updates to `UserActivityLogServiceTest`, `StubUserActivityLogServiceCapture`, `StubUserActivityLogServiceSaveCapture`.
- **Not in this pass:** delete snapshot enforcement, full field-classification matrix (TODO in masking util), export approval API.

#### DB

- Optional: `user_activity_access_audit` or equivalent — **must** align with Security/DBA.

#### Contract

- `docs/contract.md`, `docs/api-definition.md`, `specs/activity-action-types.spec.yaml` or related — **must** list new action types and payloads.

### 2.6 Cursor tool update targets

- `.cursor/skills/activity-statistics-domain/SKILL.md` — access audit and copy behavior when implemented.
- `.cursor/skills/api-permission-map/SKILL.md` — if new permissions for reveal/export.

---

## 3. Test approach

### Test case list (required)

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification |
|----|-------|------|------------------------------|-----------------|--------------|
| TC-01 | Backend | Security | Log mutation with password field in request | **No** secret plaintext in `user_activity_log.action_detail` or `request_params` | Unit (mvn test) |
| TC-02 | Backend | Security | Copy event with 100k char selection | Stored payload **truncated** to max length; `was_truncated=true` | Unit |
| TC-03 | Backend | Normal | Physical delete without snapshot path | Operation **rejected** or snapshot written per policy | Unit / integration |
| TC-04 | Backend | Normal | Update allowlisted field | `action_detail` contains **before/after** for allowlisted keys only | Unit |
| TC-05 | Frontend | Role | User **without** privileged role opens detail | **Masked** copy body; **no** “View full” | Unit (npm test) |
| TC-06 | Frontend | Role | User **with** privileged role clicks “View full” | Full body shown; **access audit** API called | Integration / manual |
| TC-07 | Backend | Security | Access audit query | Returns only events within **caller’s** audit scope | Unit |
| TC-08 | Integration | Security | Export API without approval | **403** or workflow block | Integration |
| TC-09 | Frontend | Edge | Detail API error | Error state in panel per Screen 2 | Unit |
| TC-10 | Backend | Normal | Soft delete | Row marked deleted; **delete event** logged with identifiers | Unit |

### Test scenarios

#### Scenario 1: Copy truncation and truncation flag

1. Trigger in-app copy with text longer than server max.
2. Inspect stored `action_detail` for length and `was_truncated`.
3. Confirm UI shows truncation badge.

#### Scenario 2: Privileged full copy view and access audit

1. Log in as **privileged** auditor.
2. Open detail → **View full copy body**.
3. Verify **access audit** row exists with viewer id and target log id.
4. Log in as **non-privileged** user; confirm full view **unavailable**.

### Test data

- Sample users: one **privileged**, one **standard**, per permission matrix **TODO** (PO/QA to fill from `init-data`).
- SQL fixtures **TODO**: insert `user_activity_log` rows with synthetic **non-production** payloads for copy and delete snapshot tests.

### Test environment

- Frontend: `http://localhost:3001` (per contract)
- Backend: `http://localhost:9200`
- Database: PostgreSQL per project setup

### 3.5 Browser automation verification (optional)

- **Applicable TCs**: TC-05, TC-06, TC-09 (UI states).
- **Procedure**: Login → navigate to `activity-log` → open detail → assert masked/privileged behavior per role.
- **Reference**: `docs/workflow/BROWSER-AUTOMATION-VERIFICATION-POLICY.md`

## 4. Checklist

### Frontend verification

- [x] API parameters validated (per implementing agents + UserActivityLog test run 2026-04-06)
- [x] UI behavior confirmed (core states via tests; full §1.1 matrix — manual for privileged paths)
- [x] Error handling verified (UserActivityLogDetail tests)

### Backend verification

- [x] API test cases written and run (mvn test; audit-focused subset re-run 2026-04-06)
- [x] Logs checked (no secrets) — spot-check: no secrets in test output; production log policy unchanged
- [ ] Performance checked if heavy payloads — optional / NFR follow-up

### Integration

- [x] End-to-end flows tested (automated coverage where applicable; privileged reveal + access-audit list **manual** without credentials)
- [x] Edge cases: truncation, forbidden fields — covered in unit tests where in scope
- [ ] Export gate — N/A (export not implemented)

### Documentation

- [x] Requirement doc completed (§5 updated 2026-04-06)
- [x] Contract/spec updated when implemented (this slice)

## 5. Test results

### Test run date

- **2026-04-06** — QA verification (health, API smoke, targeted unit tests, browser spot-check).

### Test results

#### Verification (runtime)

| Check | Result | Notes |
|-------|--------|--------|
| Backend health `GET /api/health` | Pass | HTTP 200, JSON OK |
| Frontend `GET http://localhost:3001` | Pass | HTTP 200 |
| DB `GET /api/db/test` | Pass | `connected: true` |
| API smoke (unauthenticated) | Pass | `GET /api/activity-log/access-audit`, `GET /api/activity-log/1`, `POST /api/activity-log/1/privileged-reveal` → **401** (routes registered, auth required) |

#### Unit / integration (automated)

| Layer | Command | Result |
|-------|---------|--------|
| Backend (audit-focused) | `cd backend && mvn test -Dtest=ActivityLogAuditMaskingTest,UserActivityLogPrivilegedRevealTest` | **Pass** |
| Frontend (activity log UI) | `cd frontend && npm test -- --watchAll=false --testPathPattern=UserActivityLog` | **Pass** (4 suites, 15 tests) |

Implementing-agent handoff: full `mvn test` and full frontend test + build reported **Pass** before QA verification.

#### Browser (step 3.5)

- **Tool**: cursor-ide-browser  
- **URL**: `http://localhost:3001`  
- **Result**: **Pass** — app loads; login shell visible (title “로그 관리 시스템”, user ID / password fields, 로그인 button). Viewport resized to 1920×1080 per policy.  
- **Limitation**: End-to-end **activity-log → detail → privileged reveal → access-audit** not executed in automation **without test credentials** (§3.5 / TC-05–TC-06 manual: log in as privileged vs standard user, then navigate to `activity-log` and `activity-log-access-audit`).

#### §3 test case matrix (this delivery)

| ID | Result | Notes |
|----|--------|--------|
| TC-01 | Partial / ongoing | Forbidden secret plaintext — enforced in masking/serialization tests where covered; PO runbook S-1 scope unchanged. |
| TC-02 | Pass | Truncation / `was_truncated` covered in backend masking tests (handoff + `ActivityLogAuditMaskingTest`). |
| TC-03 | N/A this pass | Physical delete snapshot policy — not in this implementation slice (see §2.5 “Not in this pass”). |
| TC-04 | Partial | Allowlisted before/after — scope as implemented in service tests / stubs. |
| TC-05 | Pass | Masked detail / no “View full” for non-privileged — covered by frontend tests where applicable. |
| TC-06 | Manual | Privileged reveal + access-audit row — **manual** with privileged account (browser blocked without credentials above). |
| TC-07 | Pass | Access-audit scoped query — `UserActivityLogPrivilegedRevealTest` / service behavior per handoff. |
| TC-08 | N/A | Export approval — not implemented (contract TODO). |
| TC-09 | Pass | Detail error state — covered in `UserActivityLogDetail` tests. |
| TC-10 | N/A this pass | Soft delete logging — not in this implementation slice. |

**Commands (reference):**

```bash
cd backend && mvn test
cd backend && mvn test -Dtest=ActivityLogAuditMaskingTest,UserActivityLogPrivilegedRevealTest
cd frontend && npm test -- --watchAll=false --testPathPattern=UserActivityLog
```

**Outcome:**

- **Pass** for verification scope: health, DB, API route smoke, targeted backend/frontend tests, browser load/login shell. **Manual** follow-up for full privileged E2E (TC-06) with real users.

### Issues found and resolution

- None blocking. Browser E2E for activity-log flows deferred to manual login (no QA test credentials in environment).

### Next steps

- Architecture notes incorporated 2026-03-30.

1. ~~Security formal review of §2.1.~~ **Done** (2026-03-30 — see §2.1 Security reviewer notes).
2. ~~Contract update for APIs and payloads.~~ **Done** for this slice (`docs/contract.md`, `specs/activity-log-audit-evidence.spec.yaml`, implementing agents).
3. PO confirmation on Screen 4 and entity-level versioning future scope.
4. Optional: run full `mvn test` / full `npm test` in CI and record here on release.

---

## 7. Final version (Korean)

### 최종 요약 (스테이크홀더·SVG 제작용)

**요구사항 개요**: `docs/security/audit-evidence-user-activity-log-manual-draft.md` **8절 미확정 사항**을 **보수적 보안 기본값**으로 해소한다. PII는 최악 노출을 가정하고 수집을 최소화하며, 접근은 역할 기반으로 엄격히 제한하고, 전송·저장 암호화와 법적 보류에 맞춘 보존을 전제로 한다. 제3자 감사·데이터 반출은 승인 절차와 범위 제한을 둔다.

**핵심 결정**: (1) 삭제·변경·추가는 **동일한 이벤트 봉투**와 필드 등급 정책으로 기록하며 **민감 필드는 평문 금지**. (2) 삭제는 가능하면 **소프트 삭제**; 물리 삭제 시 **삭제 직전 스냅샷**을 `action_detail` 등에 **필수**로 남긴다. (3) 변경은 **허용 목록 필드**에 한해 전·후 값; 비밀·자격증명은 제외. (4) 인앱 복사는 **최대 길이·잘림**, **권한 있는 역할만 전체 본문**, 전체 본문 조회 시 **접근 감사**. (5) 시점 DB 입증은 기본적으로 **활동 로그+페이로드 스냅샷**; 엔티티별 버전 테이블은 **별도 요구사항으로 명시될 때까지 선택/TBD**.

**화면(SVG 기준)**: **①** 활동 로그 검색·목록(`activity-log`), **②** 상세(복사 본문·마스킹/권한별 전체 보기), **③** 민감 상세·전체 복사 본문 열람 **접근 감사**, **④**(선택) 보존 안내·반출 승인 대기(PO 확정 시).

**검증 결과**: §5 참고 — 2026-04-06 QA 검증(헬스·DB·API 스모크·단위/프론트 테스트·브라우저 로그인 화면). 특권 E2E는 수동(자격 증명 없음).

---

**Author**: Requirements (subagent)  
**Date**: 2026-03-30  
**Status**: Implemented — QA verification recorded 2026-04-06 (§5)  

**TODO (PO / 조직)**

- 법무·개인정보 고지 문구와 실제 수집 범위 일치(S-5).
- Screen 4(보존·반출 승인 UI) 포함 여부.
- 엔티티별 시점 입증(버전 테이블) 도입 대상 목록.
- 접근 감사·반출 승인의 운영 역할 정의 및 주기적 권한 검토(S-4).
