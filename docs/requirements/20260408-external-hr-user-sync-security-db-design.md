# 20260408 - External HR user sync security and DB design

## 1. User requirement

### Requirement description

Design a secure synchronization policy between externally collected HR data and current user management.

Out of scope: external HR data collection, ETL tooling, and upstream source reliability.
In scope: how the application applies externally provided HR snapshots to user management with strict access-control safety.

The requirement must satisfy:

1. Immediate permission revocation for internal-transfer cases (department change only; role-only and position-only change excluded).
2. New user registration based on external HR snapshot records.
3. Resigned employee handling (immediate hard delete policy with auditability and controlled point-in-time restore).

The design must minimize implementation scope by reusing existing user-management permission model and session flow wherever possible.

### User scenario

1. Operations loads a new HR snapshot into integration tables (`ext_*`).
2. Sync trigger is executed (manual API or scheduled job call) with a snapshot identifier.
3. System compares external identity records with current app users and classifies records into: `TRANSFER`, `NEW_HIRE`, `RESIGNED`, `UNCHANGED` (or `PROFILE_UPDATE_NON_SECURITY` when profile-only fields change).
4. For `TRANSFER` (department change only), system revokes prior screen/function permissions immediately before applying new assignment rules.
5. For `NEW_HIRE`, system creates application user and baseline mapping.
6. For `RESIGNED`, system applies approved lifecycle policy (default: immediate hard delete), and any recovery is allowed only through approved point-in-time restore (PITR) flow.
7. Security/operations reviewers inspect rollback/audit logs, and only approved admin action can move a failed batch forward.

### Expected outcome

- Sync processing is deterministic per snapshot and follows batch atomicity (all-or-nothing).
- External identity matching uses stable keys and does not depend on mutable display fields, and position-only profile updates do not alter authorization state.
- Permission revocation timing is explicit and enforced before new grants for transfer events (department based only).
- Resigned-user handling follows immediate hard delete, and recovery is controlled by admin-approved PITR with immutable audit trails.
- There is no automatic accidental-deletion detector; all restore decisions are explicit, approved, and audited operations.
- All high-risk actions are traceable by batch id, actor, reason, and before/after security state.
- If one item fails, all changes in the batch are rolled back.
- Failed batches remain blocked for explicit admin action before any retry.
- Rollback and reprocessing strategy exists without ad-hoc DB manipulation in production.
- Automation operates under a guard-railed full automation model: preview/apply separation, risk-tiered controls, canary rollout, and policy-as-code governance.

## 2. Design

### 2.1 Security review (required by scope: access control / identity lifecycle)

- [ ] Security review performed (formal Step 2 review pending)
- Risks:
  - Delayed revocation can leave stale permissions after department transfer.
  - Hard delete can break forensic continuity and foreign-key references.
  - Ambiguous identity keys can map HR rows to wrong users.
  - Reprocessing without idempotency can duplicate users or reapply grants.
- Security acceptance criteria:
  - Revocation-first processing for `TRANSFER` (department-driven only) is mandatory.
  - A unique idempotency key per snapshot+employee must be enforced.
  - Sensitive lifecycle actions must emit immutable audit events.
  - `RESIGNED` default lifecycle is immediate hard delete, with no deactivate-first fallback in normal flow.
  - Failed batch must transition to admin-action-required state with no automatic retry.
  - Preview/apply 2-step execution is mandatory for production mutation.
  - High-risk changes require pre-approval even for normal scheduled batch runs.
  - No automatic accidental-delete detection/reversal is allowed; recovery is explicit PITR only.
  - PITR must require dual approval, bounded TTL, single-use token, and restore-scope limit validation.
  - PoC mode must operate fail-closed: when any PoC guard is uncertain, all write/apply paths are denied.
  - PoC mode must block API bypass: apply/write endpoints are denied unless feature flag, role gate, and approval gate all pass.
  - PoC mode must record immutable audit fields for attempted misuse and denied write operations.

### Technical design

#### Problem analysis

1. Current user management focuses on interactive admin operations, not batch HR-driven lifecycle transitions.
2. Permission assignments can drift when department changes are applied without atomic revoke/regrant boundaries.
3. Resignation policy is often mixed between compliance retention and account hygiene; explicit precedence is required.
4. Partial failure and targeted retry can produce inconsistent authorization state; batch atomicity is required.

#### Solution approach

**Backend:**

- Define a sync orchestrator service that accepts `syncBatchId` and `snapshotAt`.
- Build a classification stage (`TRANSFER`, `NEW_HIRE`, `RESIGNED`, `UNCHANGED`, optional `PROFILE_UPDATE_NON_SECURITY`) from external-vs-app diff.
- Classification policy: `TRANSFER` is triggered only by department changes; role-only or position-only change must be treated as `UNCHANGED` or `PROFILE_UPDATE_NON_SECURITY` without auth recalculation.
- Enforce batch atomicity transaction order:
  1) mark batch start,
  2) run all item mutations within one atomic transaction boundary,
  3) if one item fails, rollback whole batch,
  4) persist batch state `FAILED_ROLLED_BACK`,
  5) transition to `PENDING_ADMIN_ACTION`.
- Partial success persistence is disallowed. No item can remain `SUCCESS` when parent batch is rolled back.
- Expose controlled trigger endpoint for authorized operators only; include dry-run mode returning counts only.

**DB:**

- Keep external source tables separate (e.g. `ext_employee`, `ext_department`) and never treat them as authorization truth directly.
- Introduce upstream completion signal model for auto-linked execution:
  - `hr_ingest_run` (or equivalent): upstream ingest run unit with `ingest_run_id`, status, started/completed timestamp, source count.
  - `hr_ingest_manifest`: immutable completion manifest per run/snapshot with `snapshot_id`, row counts, checksum digest, producer identity, completion marker timestamp.
  - Sync execute path must accept only completion-validated (`COMPLETED`) ingest runs and manifest-linked `snapshot_id`.
- Introduce/confirm stable mapping table (`app_user_external_identity`) with:
  - `source_system`
  - `external_employee_id` (natural key from source)
  - `app_user_id`
  - uniqueness on (`source_system`, `external_employee_id`)
- Add sync ledger tables for idempotency and audit-grade replay:
  - `hr_sync_batch` (batch metadata: trigger actor, started/ended, status, snapshot reference, rollback linkage)
  - `hr_sync_item_result` (per-employee classification/result/error code; final success persisted only on batch commit)
  - `hr_sync_rollback_audit` (rollback history + admin-action records)
- Define mandatory batch status set:
  - `PENDING`, `WAITING_UPSTREAM_COMPLETION`, `READY_TO_START`, `RUNNING`, `COMPLETED`, `FAILED_ROLLED_BACK`, `PENDING_ADMIN_ACTION`, `ADMIN_APPROVED_RETRY`, `ADMIN_ABORTED`, `SKIPPED_UPSTREAM_INVALID`
- Add lifecycle/audit fields for resigned deletion and controlled recovery:
  - `employment_status` (`ACTIVE`, `LEAVE`, `RESIGNED`)
  - immutable deletion audit metadata (`deleted_by_sync_batch_id`, `deleted_at`, `delete_reason_code`)
  - PITR linkage fields (`restore_request_id`, `restore_token_id`, `restored_from_snapshot_id`)
- Enforce referential and uniqueness constraints to prevent duplicate identity linkage.
- Enforce immutable rollback linkage (`rollback_id`) and FK linkage to `sync_batch_id`.
- Enforce one-time processing per snapshot and run:
  - unique constraint on `hr_sync_batch(snapshot_id)` to block replay/re-run of same snapshot.
  - optional unique pair on (`ingest_run_id`, `snapshot_id`) for strict upstream linkage.
  - start lock (`SELECT ... FOR UPDATE` or advisory lock by `snapshot_id`) to prevent concurrent duplicate starts.
- Start guard must block execution when:
  - upstream ingest run is not terminal `COMPLETED`,
  - manifest checksum/signature verification fails,
  - manifest row-count completeness check fails (partial completion detected),
  - existing `COMPLETED`/`RUNNING`/`PENDING_ADMIN_ACTION` batch already claims same `snapshot_id`.

**Security policy decisions:**

- **Sync trigger**
  - Manual trigger: admin/security role with dedicated permission.
  - Scheduled trigger: service account principal with scoped permission.
  - Auto-linked scheduler trigger must run only when upstream ingest batch emits a validated completion signal for the target `snapshot_id`.
  - Every trigger must include `changeReason` and traceable actor identity.
  - Trigger must record `trigger_mode` (`MANUAL`/`SCHEDULED`/`AUTO_LINKED`) and upstream reference (`ingest_run_id`, `snapshot_id`).
- **Execution model (mandatory: preview/apply)**
  - `PREVIEW` stage is mandatory before any `APPLY` execution and must output deterministic impact summary (classification counts, risk tier, target scope, policy-gate results).
  - `APPLY` must reference a valid preview artifact (`preview_id`) and must be rejected when preview is stale, policy version changed, or target snapshot differs.
  - Scheduled/auto-linked runs must also follow preview->apply sequence (preview may be machine-generated but still mandatory and auditable).
- **Risk-based automation tiers**
  - `AUTO`: low-risk, policy-compliant changes can proceed without human approval after successful preview gates.
  - `CONDITIONAL`: medium-risk changes require additional runtime checks and canary success gate before full apply.
  - `APPROVAL_REQUIRED`: high-risk changes require explicit pre-approval before apply, including normal scheduled batches.
  - Tier assignment must be policy-driven and evaluated per batch/item (not operator discretion).
- **High-risk pre-approval gate scope**
  - Pre-approval is mandatory for high-risk actions including (at minimum): hard delete candidates, large permission revocation/regrant blast radius, canonical Tree remap affecting critical org roots, policy override attempts, and replay/recovery runs after security-invalid upstream signal.
  - This gate applies to both manual and scheduled/auto-linked runs; approval-for-retry-only policy is insufficient.
- **Upstream completion validation gate**
  - Required checks before `RUNNING`: ingest-run terminal completion, manifest checksum match, expected vs actual row-count consistency, producer identity trust check.
  - Any failed completion-signal validation must transition batch to `SKIPPED_UPSTREAM_INVALID` and block all mutations.
  - Partial completion or delayed upstream completion must keep batch blocked (`WAITING_UPSTREAM_COMPLETION`) without partial item processing.
- **Identity keys**
  - Primary match key: (`source_system`, `external_employee_id`).
  - Secondary verification (non-authoritative): normalized name + birth/date or org code if available.
  - If primary key collision or mismatch detected, classify as `CONFLICT` and block mutation.
- **Permission revocation timing**
  - `TRANSFER` (department change only) must revoke existing grants in the same transaction boundary before any regrant.
  - Role-only/position-only change must not enter revoke/regrant path and must be handled as `UNCHANGED` or `PROFILE_UPDATE_NON_SECURITY`.
  - Active sessions for affected user must be invalidated immediately after revoke step.
  - During rollback and `PENDING_ADMIN_ACTION`, authorization must apply deny-by-default to impacted users.
- **Delete/deactivate policy**
  - Default for `RESIGNED`: immediate hard delete with permission/session invalidation in the same batch execution.
  - Deactivate-first is not used as default lifecycle path for resigned users.
  - There is no automatic accidental-delete detector and no automatic undelete workflow.
  - Any recovery from mistaken delete must use admin-controlled PITR flow only.
- **PITR (point-in-time restore) operations model**
  - PITR requests are admin-initiated and must specify target snapshot timestamp and restore scope.
  - PITR execution requires dual approval (requester != approver), token TTL validation, and single-use token enforcement.
  - PITR token reuse, expiry, missing approval, or scope-overrun must be denied and immutably audited.
  - PITR scope must be limited to approved user/org boundaries; out-of-scope records must be blocked.
- **Audit logging**
  - Log at two levels: batch-level summary + per-user mutation event.
  - Mandatory fields: `syncBatchId`, `itemId`, actor, source keys, old/new status, revoked-grant count, granted-grant count, decision (`DEACTIVATE`/`DELETE`), reason code.
  - Never store raw secrets or unnecessary PII payloads in logs.
- **Rollback / reprocess**
  - Any single item failure triggers full rollback and batch status `FAILED_ROLLED_BACK`.
  - Rollback-complete batch must move to `PENDING_ADMIN_ACTION`.
  - Force session invalidation must run for impacted users during rollback handling.
  - Retry without admin approval is prohibited and must be denied/audited.
  - Approved retry requires explicit admin action (`ADMIN_APPROVED_RETRY`) and reason.
  - Use idempotency guard on (`syncBatchId`, `source_system`, `external_employee_id`) with status gate checks.
  - For wrong snapshot ingestion, only controlled admin-approved re-execution or approved PITR is allowed; no manual direct DB edits.
- **Failure/delay/missing upstream signal operations**
  - If completion signal is missing or delayed beyond threshold, emit operations alert and keep auto-linked run blocked.
  - If completion signal is invalid (checksum/manifest mismatch), mark security alert and require manual investigation before re-enable.
  - Define runbook for operations: detect -> classify (`DELAYED`/`MISSING`/`INVALID`) -> acknowledge -> remediate upstream -> re-arm scheduler.
- **Canary / staged rollout policy**
  - Apply execution must support staged rollout (`CANARY` -> `PHASED` -> `FULL`) with explicit promotion gates.
  - Canary gate failure must trigger immediate stop/rollback and block full rollout until admin/security decision.
  - Stage progression must be auditable with actor (or system principal), decision reason, and policy version.
- **SLO/KPI-driven operations**
  - Sync operation decisions must be measured against SLO/KPI thresholds (availability, failure rate, rollback rate, apply latency, approval lead time).
  - Breach of defined SLO/KPI must automatically switch execution to stricter tier (`AUTO` -> `CONDITIONAL` or `APPROVAL_REQUIRED`) and emit alert.
- **Policy-as-code governance**
  - Automation/risk/approval/canary rules must be versioned as policy artifacts (not only wiki/process text).
  - Every apply decision must record `policy_version`, approval reference, and release/change history linkage.
  - Policy change release must require approver metadata and immutable audit trail.

### 2.2 Rollback audit and history required fields

- Rollback/audit records are mandatory and immutable for every rolled-back batch.
- Required fields:
  - `syncBatchId` (batch ID)
  - `rollbackId` (unique rollback identifier)
  - `failedItemKey` (`source_system` + `external_employee_id` or equivalent stable key)
  - `failedStage` (classification/revoke/grant/profile-update/deactivate/delete)
  - `errorCode`, `errorMessageDigest` (sanitized; no secrets/raw PII)
  - `actorType` (`SYSTEM`/`ADMIN`)
  - `actorId` (service principal or admin user ID)
  - `reasonCode`, `reasonText`
  - `rollbackStartedAt`, `rollbackCompletedAt`
  - `adminActionStatus` (`PENDING_ADMIN_ACTION`/`ADMIN_APPROVED_RETRY`/`ADMIN_ABORTED`)
  - `adminActionBy`, `adminActionAt`, `adminActionReason`
  - `originSnapshotId`, `retryOfBatchId`
- Audit chain must be traceable end-to-end: failed batch -> rollback -> admin decision -> approved retry (if any).

### 2.3 Upstream completion signal security and misuse controls

- Integrity / anti-tamper:
  - Completion manifest must be signed or HMAC-protected; verifier must reject unsigned or invalid-signature payload.
  - Persist digest and verification result immutably in audit ledger.
- Anti-replay / resend:
  - Enforce single acceptance of completion event by (`ingest_run_id`, `snapshot_id`, `manifest_digest`) uniqueness.
  - Duplicate completion event must be recorded as replay attempt and ignored.
- Abuse control:
  - Only trusted producer identity can publish completion signal.
  - Scheduler/service account can consume signal but cannot forge producer identity fields.
  - Rate-limit and permission-scope auto trigger endpoint/message consumer.
- Emergency stop:
  - Support emergency kill switch (`SYNC_AUTO_LINK_ENABLED=false`) to immediately block automatic start while preserving manual admin controls.
  - Emergency stop actions must be audited with actor, reason, and effective time.
- Evidence reinforcement:
  - Store verification evidence set: upstream run metadata, manifest digest, signature verification status, checksum comparison, completeness check result.
  - Evidence retention must satisfy audit/legal policy and be queryable by `snapshot_id` / `ingest_run_id`.

### 2.4 User-management Tree compatibility and canonical mapping rules

- Compatibility baseline:
  - Existing user-management Tree structure is the canonical runtime structure for authorization and menu visibility.
  - External HR sync must adapt to the canonical Tree model; it must not redefine runtime authorization hierarchy semantics.
  - All Tree compatibility rules in this section are applied without relaxing batch atomicity, upstream validation gate, or revoke-first security order.
- Tree canonical key and mapping:
  - Canonical Tree key is `tree_node_canonical_key = (source_system, canonical_org_code)` and must be immutable once registered.
  - External node identifiers are treated as alias keys and mapped to canonical keys via versioned mapping set.
  - User assignment mutation must resolve external department -> canonical Tree node before any permission calculation.
  - If canonical mapping is missing, item must be classified as `ORPHAN` and mutation is blocked.
  - If one external record resolves to multiple canonical candidates or violates one-to-one mapping policy, item must be classified as `CONFLICT` and mutation is blocked.
- `TRANSFER` processing order (mandatory sequence; applies only to department-driven transfer):
  1) resolve current user and canonical target parent node,
  2) revoke existing grants and invalidate active sessions,
  3) update Tree parent link (`parent_node_id`) and assignment relation,
  4) recompute role/permission grants based on target node and policy,
  5) persist item result only when the whole batch commits.
  - Any failure at steps 1-4 triggers full-batch rollback and keeps previous Tree state intact.
- Orphan/conflict policy:
  - `ORPHAN` and `CONFLICT` are hard-block classes; they cannot be auto-corrected during batch execution.
  - A batch containing any unresolved `ORPHAN`/`CONFLICT` item must follow existing rollback + `PENDING_ADMIN_ACTION` flow.
  - Admin retry is allowed only after canonical mapping correction and explicit approval.
- Manual edit vs sync conflict resolution (version/lock):
  - Tree nodes and user-assignment rows must use optimistic versioning (`row_version`) and conflict detection on update.
  - Sync batch start must acquire Tree-scope execution lock (`SYNC_START_LOCK_STRATEGY`) to prevent parallel sync starts for same scope.
  - If manual admin edit changes `row_version` during sync window, affected item is classified as `CONFLICT_VERSION_LOCK` and batch follows rollback policy.
  - No last-writer-wins overwrite is allowed for Tree parent/assignment mutations in sync path.

### 2.5 Frontend Tree data contract and UI consistency rules

- Tree data contract:
  - `nodeId` must be immutable and stable across refresh/retry/rollback views.
  - Payload must be normalized with explicit `children[]` and `users[]` arrays; null-or-mixed polymorphic node payloads are disallowed.
  - Parent-child relation updates from `TRANSFER` must be represented by node movement with preserved `nodeId` identity.
- State rendering contract:
  - Node/user state markers must be explicit (`NORMAL`, `PENDING_SYNC`, `SYNC_APPLIED`, `SYNC_FAILED_ROLLBACK`, `CONFLICT`, `ORPHAN`).
  - Sorting is deterministic: primary by Tree order index, secondary by display name, tertiary by immutable `nodeId`.
  - Rollback-complete state must render as pre-batch topology and permissions; no transient moved state can remain visible after rollback finalization.
- Pending/rollback UI consistency:
  - While batch is `RUNNING` or `PENDING_ADMIN_ACTION`, UI must show consistent pending badge/lock indicator for impacted nodes/users.
  - If batch transitions to `FAILED_ROLLED_BACK`, Tree view must return to baseline snapshot with no structural drift.
  - Retry approval and re-execution must not duplicate nodes/users or alter ordering rules.

### 2.6 PoC mode operating principles (zero-impact to existing production system)

- PoC baseline:
  - PoC operation must not mutate existing production user/permission/Tree data by default.
  - Default execution mode is `PREVIEW_ONLY`; any write path must be blocked unless explicit gated approval is satisfied.
  - Existing batch atomicity/revoke-first/Tree compatibility/control-automation policies remain mandatory in PoC.
- Apply gate and fail-closed:
  - `APPLY` is disabled by default in PoC and can be enabled only for an explicitly approved and time-bounded evaluation window.
  - All PoC guards are mandatory (`feature flag` + `role gate` + `approval gate` + `scope isolation`); guard uncertainty or missing evidence must deny execution (fail-closed).
  - Apply/write APIs must reject direct invocation when PoC is in preview-only status, even from scheduler paths.
- Feature flag control:
  - PoC behavior must be fully controlled by dedicated flags (for example: `HR_SYNC_POC_ENABLED`, `HR_SYNC_POC_APPLY_ENABLED`).
  - `flag off` means no PoC API/UI entry and no background execution path.
  - Flag changes must be audited with actor, reason, effective time, and rollback plan.
- Data and authority isolation:
  - PoC data scope must be separated from production runtime authorization truth (separate PoC batch namespace and constrained target scope).
  - PoC service principals/roles must be dedicated and least-privilege; production trigger principals must not inherit PoC-apply privilege by default.
  - PoC evidence and logs must be queryable independently from production run history.
- Rollback and baseline preservation:
  - PoC rollback must preserve current production UI baseline and authorization baseline when apply is disabled or rolled back.
  - If PoC guard fails at runtime, system must stop at non-mutating state and keep baseline unchanged.

### 2.7 Screen composition for PoC and operations

- Existing `User Management` screen role:
  - Remains the canonical baseline screen for current user/Tree/permission state.
  - In PoC, it must expose read-only PoC summary signals only (for example: preview impact count badge), without enabling implicit write.
  - Existing user-management flows must remain unaffected when PoC feature flags are off.
- Additional minimum screens:
  - **Must**: `HR Sync PoC Preview` screen
    - Purpose: run/view preview results, risk tier, policy-gate outcomes, and expected impact by classification.
    - Constraint: read-only by default, no write controls unless PoC apply gate is explicitly opened.
  - **Must**: `PoC Apply Approval / Gate` screen
    - Purpose: explicit approval workflow for any PoC apply attempt, including scope, reason, approver, TTL, and one-time token state.
    - Constraint: accessible only to authorized approval roles; all approvals/rejections must be audited.
  - **Should**: `PoC Batch Audit Timeline` screen
    - Purpose: visualize preview/apply/deny/rollback timeline with immutable event chain for forensic review.
    - Constraint: no data mutation action; evidence export follows least-privilege.
  - **Could**: `PoC Feature Flag & Kill Switch` screen
    - Purpose: controlled visibility for PoC flags and emergency stop state.
    - Constraint: strict admin-only access and dual-control policy if enabled.
- Read-only/permission/isolation conditions:
  - Any PoC screen must honor `flag off => hidden/inaccessible`.
  - Users without PoC roles must receive deny response and must not see actionable controls through direct URL/API calls.
  - PoC screens must operate on isolated PoC dataset/view-model and must not allow direct edits to production baseline entities.

### Affected scopes and change targets (verification)

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|-----------|-----------------------------------------------|
| Backend | Yes | [x] |
| Frontend (config UI + view screen) | No (trigger can start as API-only) | [x] |
| DB | Yes | [x] |
| Contract / Spec | Yes | [x] |
| Cursor tools (skills, specs) | Yes | [x] |

### Planned change file list (expected change targets)

#### Backend

- `backend/src/main/java/com/logmng/service/*Sync*Service*.java`
  - Must implement batch classification and revoke-first transfer flow.
- `backend/src/main/java/com/logmng/controller/*Sync*Controller*.java`
  - Must expose authorized trigger endpoint with dry-run and execution modes.
- `backend/src/main/java/com/logmng/service/AuthService.java`
  - Must support forced session invalidation on transfer/resigned events.
- `backend/src/main/java/com/logmng/aspect/*ActivityLog*.java`
  - Must emit required audit fields for sync lifecycle actions.

#### DB

- `backend/src/main/resources/db/schema_sys.sql`
  - Must include mapping and sync-ledger schema or references.
- `backend/src/main/resources/db/migrate-*.sql`
  - Must add `hr_sync_batch`, `hr_sync_item_result`, lifecycle fields, constraints, and indexes.
- `backend/src/main/resources/db/setup.sh`
  - Must align grants for trigger account and least-privilege runtime access.

#### Contract / spec

- `docs/contract.md`
  - Must document sync trigger contract, authorization, and lifecycle outcomes.
- `docs/api-definition.md`
  - Must define request/response for dry-run/execute/retry and error codes (`CONFLICT`, `POLICY_BLOCKED`, etc.).
- `specs/hr-user-sync.spec.yaml`
  - Must define item classification schema, idempotency key, and policy gates.

#### Cursor tool update targets

- `.cursor/skills/auth-permission-domain/SKILL.md`
  - Must include transfer/resigned revocation behavior and trigger permission mapping.
- `.cursor/skills/db-domain/SKILL.md`
  - Must include sync-ledger and identity mapping design.
- `.cursor/skills/api-permission-map/SKILL.md`
  - Must include endpoint-to-permission requirements for sync trigger/retry.

## 3. Test approach

### Test case list (required)

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification |
|----|-------|------|------------------------------|-----------------|--------------|
| TC-B01 | Backend | Normal | Execute mixed batch (`TRANSFER`, `NEW_HIRE`, `RESIGNED`) with no errors | Batch commits atomically; all item results finalized as committed success | Unit / integration |
| TC-B02 | Backend | Exception | One item fails during batch execution | Entire batch rolled back; no user mutation persists | Unit / integration |
| TC-B03 | Backend | Exception | Transfer-related item fails in batch | Batch state is `FAILED_ROLLED_BACK` then `PENDING_ADMIN_ACTION` | Unit / integration |
| TC-B04 | Backend | Security | Retry attempted in `PENDING_ADMIN_ACTION` without admin approval | Request denied and audited; no mutation occurs | Unit / integration |
| TC-B05 | Backend | Security | Admin approves retry with valid one-time approval context | State transitions to `ADMIN_APPROVED_RETRY`; controlled re-execution enabled | Unit / integration |
| TC-B06 | Backend | Security | `RESIGNED` item is processed | Immediate hard delete executed in batch; delete audit fields persisted | Integration |
| TC-B07 | Backend | Regression | Role-only change is present without department delta | Classified as `UNCHANGED` or `PROFILE_UPDATE_NON_SECURITY`; no revoke/regrant and no auth recalculation | Unit / integration |
| TC-B08 | Backend | Regression | Position-only change is present without department delta | Classified as `UNCHANGED` or `PROFILE_UPDATE_NON_SECURITY`; no revoke/regrant and no auth recalculation | Unit / integration |
| TC-DB01 | DB | Normal | Rolled-back batch writes rollback audit records | Required fields (`syncBatchId`, `rollbackId`, `failedItemKey`, actor, reason, timestamps) persisted | SQL/integration |
| TC-DB02 | DB | Exception | Attempt to persist partial-success item after parent rollback | Write blocked by status/constraint; consistency preserved | SQL/integration |
| TC-I01 | Integration | Normal | Dry-run trigger on snapshot | Returns classification counts without mutation | API integration |
| TC-I02 | Integration | Exception | Unauthorized principal calls execute endpoint | 403 and no batch row created | API integration |
| TC-I03 | Integration | Critical | Full batch with one forced failing item | Mandatory validation: one failure triggers full rollback across all affected tables | API + DB verification |
| TC-I04 | Integration | Security | Rolled-back impacted user tries access with existing session | Deny-by-default enforced and forced session invalidation confirmed | API + auth verification |
| TC-I05 | Integration | Governance | Admin sets failed batch to `ADMIN_ABORTED` | Batch remains closed and non-retryable without new controlled trigger | API + DB verification |
| TC-I06 | Integration | Security | Upstream completion signal has invalid signature/checksum | Start blocked; batch transitions to `SKIPPED_UPSTREAM_INVALID`; no mutations | API + DB verification |
| TC-I07 | Integration | Security | Upstream ingest run not fully completed (partial rows or non-terminal status) | Batch remains blocked at start gate (`WAITING_UPSTREAM_COMPLETION` or blocked response) | API + DB verification |
| TC-I08 | Integration | Security | Completion manifest checksum mismatches source digest | Execution denied and security alert/audit event emitted | API + DB verification |
| TC-I09 | Integration | Security | Replay/resend of already accepted (`ingest_run_id`, `snapshot_id`) completion signal | Duplicate ignored/denied; no second execution starts | API + DB verification |
| TC-I10 | Integration | Concurrency | Two concurrent execution attempts for same `snapshot_id` | Lock/unique guard allows only one run; duplicate attempt rejected/audited | API + DB verification |
| TC-I11 | Integration | Governance | Apply requested without valid preview artifact (`preview_id`) | Request denied; no mutation; denial reason audited | API + DB verification |
| TC-I12 | Integration | Governance | Scheduled run contains high-risk actions but no pre-approval | Apply blocked even in normal batch mode; approval-required status/audit recorded | API + DB verification |
| TC-I13 | Integration | Governance | Risk-tier evaluation marks batch as `APPROVAL_REQUIRED` | Execution path requires explicit approval and cannot auto-apply | API + policy verification |
| TC-I14 | Integration | Reliability | Canary stage fails on rollout gate | Rollout stops; rollback/hold applied; full rollout blocked and audited | API + DB + ops verification |
| TC-I15 | Integration | Operations | SLO/KPI threshold breach during period | Automation tier escalates and alert is emitted per policy | API + ops verification |
| TC-I16 | Integration | PoC zero-impact | Execute PoC preview-only flow with production baseline snapshot | Production user/permission/Tree data remains unchanged; preview evidence only | API + DB verification |
| TC-I17 | Integration | PoC security | Call write/apply API while PoC is preview-only or apply flag disabled | Request denied; denial audited; no mutation | API + DB verification |
| TC-I18 | Integration | PoC security | Access PoC endpoints/screens with PoC feature flag off | Access denied/hidden; no background trigger starts | API + UI verification |
| TC-I19 | Integration | PoC security | Unauthorized role/user tries PoC preview/apply/approval APIs | 403/deny with audit event; no side effects | API + auth verification |
| TC-I20 | Integration | PoC rollback | Perform PoC rollback or disable PoC apply after partial evaluation | UI and data baseline remain identical to pre-PoC state | API + DB + UI verification |
| TC-I21 | Integration | Security | PITR apply is requested without required approval | Request denied and audited; restore not executed | API + DB verification |
| TC-I22 | Integration | Security | PITR token is expired | Request denied and audited with token-expired reason | API + DB verification |
| TC-I23 | Integration | Security | PITR token is reused after one successful apply | Second request denied and audited as token-reuse | API + DB verification |
| TC-I24 | Integration | Security | PITR restore target exceeds approved scope boundary | Request denied and audited as scope-overrun | API + DB verification |
| TC-T01 | Integration | Compatibility | Same department node is updated by sync cycle multiple times | `nodeId` and canonical key mapping stay immutable; no node identity churn | API + DB + UI verification |
| TC-T02 | Integration | Compatibility | `TRANSFER` changes parent department for existing user | Parent change is reflected only after revoke-first sequence and permission recomputation | API + auth + UI verification |
| TC-T03 | Integration | Exception | Item resolves to missing canonical mapping | Classified as `ORPHAN`; mutation blocked; batch rollback/`PENDING_ADMIN_ACTION` policy enforced | API + DB verification |
| TC-T04 | Integration | Exception | Item resolves to ambiguous/multi-target mapping | Classified as `CONFLICT`; mutation blocked; no partial mutation persists | API + DB verification |
| TC-T05 | Integration | Concurrency | Manual Tree edit occurs during sync and version changes | `CONFLICT_VERSION_LOCK` raised; batch rolled back; admin action required | API + DB verification |
| TC-T06 | Frontend | Regression | Batch fails and transitions to `FAILED_ROLLED_BACK` | Tree topology/order/badges return to pre-batch baseline; no visual structural change remains | UI integration |
| TC-UI01 | Frontend | PoC screen | Open User Management with PoC flags off/on | Existing behavior unchanged; PoC summary read-only indicator only when flag on | UI integration |
| TC-UI02 | Frontend | PoC screen | Open `HR Sync PoC Preview` as authorized user | Preview results/risk/gate evidence displayed read-only | UI integration |
| TC-UI03 | Frontend | PoC screen security | Non-authorized user accesses PoC screens via menu/direct URL | Screen hidden or access denied; no actionable controls exposed | UI integration |
| TC-UI04 | Frontend | PoC screen isolation | PoC preview/apply attempt from PoC screens | Any blocked apply keeps User Management baseline unchanged | UI + API integration |

### Test scenarios

#### Scenario 1: Transfer immediate revocation
1. Prepare user with old department permissions and active session.
2. Provide snapshot marking department transfer.
3. Execute batch.
4. Verify revoke-first ordering, session invalidation, and updated grants.

#### Scenario 2: Resigned immediate delete policy
1. Prepare resigned employee row in external snapshot.
2. Execute batch.
3. Verify immediate hard delete and immutable delete audit fields.

#### Scenario 3: One-failure full rollback and admin-gated retry
1. Execute batch with one intentionally failing item.
2. Verify full rollback, `FAILED_ROLLED_BACK` -> `PENDING_ADMIN_ACTION`, and rollback audit creation.
3. Verify unapproved retry denial.
4. Verify approved retry path only after explicit admin action.

#### Scenario 4: Role-only/position-only change does not trigger auth recalculation
1. Prepare user where external snapshot changes only role or only position, with same department.
2. Execute batch.
3. Verify classification as `UNCHANGED` or `PROFILE_UPDATE_NON_SECURITY`.
4. Verify no revoke-first flow, no permission recomputation, and no forced session invalidation.

#### Scenario 5: PITR control gates
1. Request PITR with missing approval, expired token, reused token, and out-of-scope target in separate attempts.
2. Execute PITR apply API for each attempt.
3. Verify all attempts are denied and immutably audited with correct reason codes.

### Test data

- Seed external snapshot rows with explicit class cases: transfer/new-hire/resigned/conflict and position-only profile update.
- Include one deterministic failure row to validate one-failure full rollback.
- Prepare app users with varied permission-group assignments and active sessions.
- Include PITR control fixtures (dual approval pair, token ttl/expiry, single-use token state, scope-bound policy).
- Include admin approval fixtures for retry (approved and unapproved cases).

### Test environment

- Backend: `http://localhost:9200`
- Database: PostgreSQL (project default)
- Trigger principal: admin role + service account profile for scheduler simulation

## 4. Checklist

### Backend verification
- [ ] Trigger authorization verified
- [ ] Batch atomicity (all-or-nothing) verified
- [ ] Session invalidation verified
- [ ] Unapproved retry denied verified
- [ ] Role-only and position-only changes do not trigger permission revocation verified
- [ ] Resigned immediate hard delete flow verified (no deactivate-first fallback)
- [ ] Preview/apply mandatory gate verified
- [ ] High-risk pre-approval gate verified for scheduled and manual runs
- [ ] Risk-tier (`AUTO`/`CONDITIONAL`/`APPROVAL_REQUIRED`) evaluation verified
- [ ] Canary fail-stop and staged progression verified
- [ ] PoC preview-only default and apply-block gate verified
- [ ] PoC feature-flag off state blocks API/UI/background execution verified
- [ ] PoC unauthorized access and API bypass denial audited

### DB verification
- [ ] Idempotency constraint verified
- [ ] Rollback audit required fields verified
- [ ] Admin action status transitions verified
- [ ] Resigned delete audit metadata and PITR linkage fields verified
- [ ] Policy version, approval reference, and release history linkage persisted
- [ ] PoC and production batch/audit namespace isolation verified

### Operations / governance verification
- [ ] SLO/KPI threshold definitions applied (or explicitly TODO-scoped) and alert path verified
- [ ] Policy-as-code lifecycle (version/approval/release) traceability verified
- [ ] No automatic accidental-delete detection/reversal behavior verified
- [ ] PITR dual approval + TTL + single-use token + scope-limit controls verified
- [ ] PoC fail-closed guard matrix and emergency stop behavior verified

### Frontend / UX verification
- [ ] User Management baseline is unchanged by PoC default mode
- [ ] Must/Should/Could PoC screen access and read-only behavior verified
- [ ] Flag-off and no-permission states hide/block PoC actions consistently

### Documentation
- [ ] Requirement doc completed
- [ ] Contract/spec update completed

---

**Author**: Requirements (subagent)  
**Date**: 2026-04-08  
**Status**: In progress (batch atomicity + admin-action policy reflected; ready for DB/Security handoff)

## TODO policy values

- `SYNC_BATCH_ATOMICITY_MODE`: `ALL_OR_NOTHING`
- `SYNC_BATCH_FAILURE_STATUS`: `FAILED_ROLLED_BACK`
- `SYNC_BATCH_POST_FAILURE_STATUS`: `PENDING_ADMIN_ACTION`
- `SYNC_ADMIN_APPROVAL_REQUIRED_FOR_RETRY`: `true`
- `SYNC_ADMIN_PREAPPROVAL_REQUIRED_FOR_HIGH_RISK_APPLY`: `true`
- `SYNC_AUTO_RETRY_ENABLED`: `false`
- `SYNC_APPROVAL_TOKEN_TTL_MINUTES`: TODO (proposed 30)
- `SYNC_APPROVAL_TOKEN_SINGLE_USE`: `true`
- `SYNC_EXECUTION_MODEL`: `PREVIEW_THEN_APPLY`
- `SYNC_PREVIEW_REQUIRED_FOR_ALL_APPLY`: `true`
- `SYNC_PREVIEW_TTL_MINUTES`: TODO (proposed 60)
- `SYNC_APPLY_REQUIRES_PREVIEW_ID`: `true`
- `SYNC_APPLY_BLOCK_ON_POLICY_VERSION_MISMATCH`: `true`
- `SYNC_RISK_TIER_MODE`: `AUTO_CONDITIONAL_APPROVAL_REQUIRED`
- `SYNC_RISK_TIER_RULESET_VERSION`: TODO (for policy-as-code release)
- `SYNC_HIGH_RISK_CRITERIA_VERSION`: TODO (for policy-as-code release)
- `SYNC_CANARY_ENABLED`: `true`
- `SYNC_CANARY_STAGE_ORDER`: TODO (`CANARY_THEN_PHASED_THEN_FULL`)
- `SYNC_CANARY_SUCCESS_CRITERIA`: TODO
- `SYNC_CANARY_FAIL_ACTION`: `STOP_AND_BLOCK_FULL_ROLLOUT`
- `SYNC_SLO_APPLY_SUCCESS_RATE_TARGET`: TODO
- `SYNC_SLO_APPLY_LATENCY_P95_SECONDS`: TODO
- `SYNC_SLO_ROLLBACK_RATE_MAX`: TODO
- `SYNC_KPI_APPROVAL_LEAD_TIME_TARGET_MINUTES`: TODO
- `SYNC_SLO_BREACH_ESCALATION_POLICY`: TODO (`AUTO_TO_CONDITIONAL_OR_APPROVAL_REQUIRED`)
- `SYNC_POLICY_AS_CODE_ENABLED`: `true`
- `SYNC_POLICY_VERSION_REQUIRED`: `true`
- `SYNC_POLICY_APPROVAL_METADATA_REQUIRED`: `true`
- `SYNC_POLICY_RELEASE_HISTORY_REQUIRED`: `true`
- `SYNC_POLICY_CHANGE_AUDIT_IMMUTABLE`: `true`
- `SYNC_FORCE_SESSION_INVALIDATION_ON_ROLLBACK`: `true`
- `SYNC_DENY_BY_DEFAULT_DURING_PENDING_ADMIN_ACTION`: `true`
- `SYNC_AUTO_LINK_ENABLED`: TODO (default `true`)
- `SYNC_AUTO_LINK_TRIGGER_MODE`: TODO (`UPSTREAM_COMPLETION_EVENT` or `SCHEDULED_POLLING`)
- `SYNC_UPSTREAM_COMPLETION_REQUIRED`: `true`
- `SYNC_UPSTREAM_COMPLETION_TIMEOUT_MINUTES`: TODO (proposed 30)
- `SYNC_UPSTREAM_MANIFEST_SIGNATURE_REQUIRED`: `true`
- `SYNC_UPSTREAM_MANIFEST_CHECKSUM_REQUIRED`: `true`
- `SYNC_UPSTREAM_PARTIAL_COMPLETION_BLOCK`: `true`
- `SYNC_SNAPSHOT_SINGLE_PROCESS_ENFORCED`: `true`
- `SYNC_INGEST_EVENT_REPLAY_BLOCK`: `true`
- `SYNC_START_LOCK_STRATEGY`: TODO (`DB_ROW_LOCK` or `ADVISORY_LOCK`)
- `SYNC_ALERT_ON_UPSTREAM_SIGNAL_DELAY`: `true`
- `SYNC_ALERT_ON_UPSTREAM_SIGNAL_MISSING`: `true`
- `SYNC_ALERT_ON_UPSTREAM_SIGNAL_INVALID`: `true`
- `SYNC_TREE_CANONICAL_KEY_MODE`: `SOURCE_SYSTEM_CANONICAL_ORG_CODE`
- `SYNC_TREE_MAPPING_REQUIRED`: `true`
- `SYNC_TREE_ORPHAN_POLICY`: `BLOCK_AND_ROLLBACK`
- `SYNC_TREE_CONFLICT_POLICY`: `BLOCK_AND_ROLLBACK`
- `SYNC_TREE_TRANSFER_PARENT_UPDATE_REQUIRED`: `true`
- `SYNC_TREE_PERMISSION_RECALC_AFTER_PARENT_CHANGE`: `true`
- `POSITION_CHANGE_DOES_NOT_TRIGGER_AUTH_RECALC`: `true`
- `SYNC_POSITION_ONLY_CHANGE_CLASSIFICATION`: TODO (`UNCHANGED` or `PROFILE_UPDATE_NON_SECURITY`)
- `SYNC_TREE_VERSION_CONFLICT_POLICY`: `CONFLICT_VERSION_LOCK`
- `SYNC_TREE_OPTIMISTIC_LOCK_REQUIRED`: `true`
- `SYNC_TREE_NODE_ID_IMMUTABLE`: `true`
- `SYNC_TREE_UI_ROLLBACK_RESTORE_BASELINE`: `true`
- `SYNC_TREE_UI_PENDING_BADGE_MODE`: TODO (`NODE_ONLY` or `NODE_AND_USER`)
- `SYNC_TREE_UI_SORT_RULE`: TODO (`TREE_ORDER_THEN_NAME_THEN_NODE_ID`)
- `SYNC_TRANSFER_TRIGGER_FIELDS`: `DEPARTMENT_ONLY`
- `SYNC_ROLE_ONLY_CHANGE_TRIGGERS_REVOKE`: `false`
- `SYNC_RESIGNED_DEFAULT_POLICY`: `IMMEDIATE_HARD_DELETE`
- `SYNC_ACCIDENTAL_DELETE_AUTO_DETECT_ENABLED`: `false`
- `SYNC_PITR_ENABLED`: `true`
- `SYNC_PITR_DUAL_APPROVAL_REQUIRED`: `true`
- `SYNC_PITR_TOKEN_TTL_MINUTES`: TODO (proposed 30)
- `SYNC_PITR_TOKEN_SINGLE_USE`: `true`
- `SYNC_PITR_SCOPE_LIMIT_REQUIRED`: `true`
- `SYNC_PITR_DENY_ON_SCOPE_OVERRUN`: `true`
- `HR_SYNC_POC_ENABLED`: TODO (default `false`)
- `HR_SYNC_POC_DEFAULT_MODE`: `PREVIEW_ONLY`
- `HR_SYNC_POC_APPLY_ENABLED`: TODO (default `false`)
- `HR_SYNC_POC_APPLY_REQUIRES_APPROVAL_GATE`: `true`
- `HR_SYNC_POC_APPLY_APPROVAL_TTL_MINUTES`: TODO (proposed 30)
- `HR_SYNC_POC_ROLE_REQUIRED_FOR_PREVIEW`: `true`
- `HR_SYNC_POC_ROLE_REQUIRED_FOR_APPLY`: `true`
- `HR_SYNC_POC_API_BYPASS_BLOCK_ENABLED`: `true`
- `HR_SYNC_POC_FAIL_CLOSED_ENABLED`: `true`
- `HR_SYNC_POC_DATA_NAMESPACE_ISOLATED`: `true`
- `HR_SYNC_POC_AUDIT_NAMESPACE_ISOLATED`: `true`
- `HR_SYNC_POC_UI_READ_ONLY_DEFAULT`: `true`
- `HR_SYNC_POC_UI_HIDE_WHEN_FLAG_OFF`: `true`
- `HR_SYNC_POC_KILL_SWITCH_ENABLED`: `true`
