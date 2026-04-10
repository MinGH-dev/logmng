# 20260323 - Approver eligibility from permission group tables only

## 1. User requirement

### Requirement description

Decrypt-approval flows (search history and pending approvals) currently require operators to maintain **two** sources: (1) permission group configuration (`permission_group` / `permission_group_screen`, including `approve` for `search-history` and `pending-approvals`), and (2) the separate **`decrypt_approver`** table. Setting “승인” in permission management is not sufficient; rows in `decrypt_approver` must also be updated. The product goal is a **single source of truth**: whether a user may act as a decrypt approver must be determined **only** from permission-group management data (`permission_group`, `permission_group_screen`, `app_user_permission_group`). For decrypt-related approve/reject, **“administrator account” means `app_user.is_system_admin = true` only** (`docs/contract.md` — 복호화 승인 자격). **`ADMIN_EXT` 등 권한 그룹만으로 넓은 권한을 가진 사용자는 본 정책에서 관리자가 아니다**; 그들은 **권한 그룹의 화면별 `approve`만** 따른다. **`is_system_admin = true`** 인 사용자는 권한 그룹에 승인이 있어도 **복호화 승인·반려 불가**(그룹 기준 계산 후 플래그로 상쇄). **비시스템관리자** 중 그룹 **승인** 보유자만 **P2-2**(동일 부서) 하에 승인 가능 — [`20260323-approver-eligibility-from-permission-group-only-PRODUCT-QA.md`](./20260323-approver-eligibility-from-permission-group-only-PRODUCT-QA.md). Redundant synchronization with `decrypt_approver` must be eliminated and the table **dropped** after a controlled migration.

**UI / contract naming**: In product copy, align one canonical phrase with the contract (e.g. **user permission hierarchy** vs **permission group management**): both refer to the same admin surfaces where groups and screens are configured; this doc uses “permission group management” for the configuration UI and expects contract terms to stay the single authority for API and spec text.

### User scenario

1. An administrator opens permission group management and assigns a user to a group that includes **승인(approve)** for **검색 이력(search-history)** and/or **승인 대기(pending-approvals)**.
2. The administrator expects that user to **immediately** be able to perform approver actions (view pending items they are allowed to act on, call approve/reject APIs) without any separate step to update another table or screen.
3. **Problem**: Today the same administrator must also ensure `decrypt_approver` contains the user (and possibly department scope), or the UI/API still treats the user as a non-approver despite the permission group showing approve enabled.

### Expected outcome

- A user is treated as having **approver capability** for decrypt-approval flows **if and only if**: (1) **`permission_group_screen.approve=true`** on `search-history` and/or `pending-approvals` via **assigned** `app_user_permission_group`; (2) **`is_system_admin` is false** (시스템 관리자 계정은 그룹에 승인이 있어도 본 흐름에서 승인 불가 — 계약: 그룹 판정 후 상쇄); (3) existing screen access rules still apply; (4) per request, **P2-2** — approver and requester **same `department_code`**.
- **No ongoing requirement** to manually sync `decrypt_approver` for eligibility; administrators manage approvers **only** via permission group configuration (and user–group assignment). **`decrypt_approver` is dropped** after migration (§2 DB).
- **Department-scoped approval** (`canApproveForRequester`): **P2-2** when the approver has **effective** group-based decrypt approve and **`is_system_admin` is false**; `approver.department_code` **equals** `requester.department_code`. **No global** business approver (§2, PRODUCT-QA Q1).
- **Migration**: Backfill/migrate legacy data; **cutover (A) new only** (§2.1). Users who had `decrypt_approver` but **no** permission group are handled per **M-3** with **pre-go-live manual group assignment** by operators (PRODUCT-QA Q4). Department-code mismatches are **manually verified** by product/ops (PRODUCT-QA Q5).
- **UI / API observability**: **Remove** dedicated “승인자” / **`isApprover`** from user list and user-permission hierarchy responses; consumers infer capability from **assigned group and screen functions**. Backend uses **one shared eligibility function** evaluated on each request (PRODUCT-QA Q7).
- **Information exposure**: When comparing old vs new behavior, include **list/detail visibility** of pending items. Automated parity scripts are **reference only**; final sign-off may rely on **manual verification** (PRODUCT-QA Q5), not a mandatory automated zero-delta gate.

**Note**: This requirement does **not** apply search/filter UI pattern §2.4 (forms-and-filters alignment). No §2.4 verification table run.

**Consistency note**: **Approver eligibility** and **`canApproveForRequester`** (department/requester scope) are distinct from **CONSISTENCY-STANDARDS** §7 **scope labels** (`self` / `team` / `all` on list filters). List filters govern which rows appear in search/list APIs; approver eligibility + `canApproveForRequester` govern who may approve and whose requests are in scope for approval actions. See `docs/workflow/CONSISTENCY-STANDARDS.md`.

---

## 2. Design

### 2.1 Security review (access control and migration)

**Security review performed**: Yes (expert feedback incorporated into this section). Formal Security subagent Step 2 may still record sign-off; **implementation gate** below does not depend on vague “may follow” wording.

#### Implementation gate (product + security)

- **Product decisions** for P1, P2, cutover, Q4–Q8 are **recorded** in [`20260323-approver-eligibility-from-permission-group-only-PRODUCT-QA.md`](./20260323-approver-eligibility-from-permission-group-only-PRODUCT-QA.md) (2026-03-23).
- **No production cutover** (read switch / DROP) until **security** accepts: **`is_system_admin` users never effective decrypt-approve** (group 승인 있어도 상쇄), **`ADMIN_EXT` 등은 그룹 `approve`만**, **P2-2** (per org policy).
- **No Backend or DB implementation** that changes production eligibility behavior until **cutover rule** is understood: **(A) new only**; **default deny when ambiguous** (agreed in PRODUCT-QA).

#### Risks

- **Transition-period privilege expansion**: If eligibility uses **OR** across two sources or the **widest** of old vs new during migration without a signed cutover rule, users may gain approver or visibility they should not have.
- **Privilege loss**: If eligibility uses **AND** or a **single** source before parity is proven, or cutover is premature, legitimate approvers may lose capability.
- **Information exposure**: Parity must cover **list/detail visibility** of pending items (and related decrypt-approval surfaces), not only the matrix “who can approve whom.”
- **Audit weakening**: Any change that reduces attribution of approve/reject actions is unacceptable.

#### Cutover rule during migration (mandatory documentation)

Product must choose **one** and document privilege effect before cutover:

| Mode | Eligibility source | Typical privilege effect (document actual) |
|------|-------------------|---------------------------------------------|
| **(A) New only** | Permission group tables only | Legacy rows ignored for eligibility; risk of **loss** if backfill incomplete |
| **(B) Legacy only** | `decrypt_approver` only | New group flags ignored for eligibility; **not** end state for this requirement |
| **(C) Union** | New **OR** legacy | **Widest** source — **expansion** risk unless time-boxed and signed |
| **(D) Intersection** | New **AND** legacy | **Narrowest** — **loss** risk unless both fully aligned |

- **Recommendation**: **Default deny when ambiguous** (e.g. disagreeing signals during transition → treat as non-approver or block action until resolved), unless product explicitly accepts another rule in writing.
- **Post-migration target**: **No** application **read/write** path uses `decrypt_approver` for **eligibility**. **Seed data, manual `INSERT` into `decrypt_approver`, or ops-only scripts must not grant** decrypt-approver rights in the running app’s authorization model after hard cutover.

#### Audit and logging

- **Same as today**: approver **numeric** user id, request identifiers, time, result; **no new plain PII** in logs for eligibility paths beyond current baselines.
- **Regression**: §3 includes audit parity TCs.

#### Error codes (contract alignment)

- Align with `docs/contract.md` / `docs/api-definition.md`: e.g. **`FUNCTION_NOT_ALLOWED`**, **`FORBIDDEN_NOT_APPROVER`** / **`NOT_APPROVER`** as applicable per endpoint. **401** where unauthenticated access applies. Implementers and §3 TCs must match the contract after doc updates.

#### Admin UI: `isApprover` removed

- **Remove** `isApprover` (and equivalent 승인자 columns) from user list and user-permission hierarchy **UI and API responses** where they existed for approver display; document deprecation/removal in `docs/api-definition.md` and contract. Approver capability is determined at **authorization time** via the shared eligibility function (PRODUCT-QA Q7).

#### Acceptance / recommendations

- **Parity analysis** (before cutover, **reference + manual**): Compare old vs new rules for approval actions and **list/detail visibility** where useful. Automated scripts/reports are **optional reference** (PRODUCT-QA Q5); **manual verification** by product/ops for edge cases (e.g. department-code misalignment) is explicitly in scope. If a script path is used, it may be recorded under `scripts/` or `docs/qa/` — not a blocking gate on automated zero-delta sign-off.
- **Contract-first**: Update `docs/contract.md`, `docs/api-definition.md`, and `specs/permission-group-hierarchy.spec.yaml` per §2 (Contract block); follow **`docs/workflow/DOC-CODE-SYNC.md`** so screenIds, API paths, and error codes change in the **same PR** across contract + spec + code where applicable.
- **Minimize disagreement window**: **Backfill + manual remediation** → **read switch (A)** → stop reading `decrypt_approver` (see Architecture, PRODUCT-QA).

### Technical design

#### Contract, API definition, and error-code mapping

**Completion criteria (documentation)**:

- **`docs/contract.md`**: (**Done** — 「복호화 승인 자격」.)
- **`docs/api-definition.md`**: (**Done** — §2.4 `screenFunctions` 승인, §6.1.5–6.1.7 effective 승인·동일 부서, §7.1 `isApprover` 제거, 에러 코드 표.)
- **`specs/permission-group-hierarchy.spec.yaml`**: (**Done** — v1.5, §1.1.1 기본값, §4.4 derivation, §5.2, pending-approvals 행, metadata requirement 20260323.)

**Approve API error mapping** (implementation + §3 must match contract after change):

| Situation | Expected HTTP / code (per contract) |
|-----------|--------------------------------------|
| (a) Not an approver | **`NOT_APPROVER`** / **`FORBIDDEN_NOT_APPROVER`** as applicable |
| (b) Approver but scope / function check fails | **`FUNCTION_NOT_ALLOWED`** |
| (c) Unauthenticated where required | **401** |

- **§3**: Add TC for **doc–code alignment** after contract/spec edits; optional **dual-read** TC **only** if product chooses a dual-read window (default: **hard cutover** per DBA).

#### Consistency standards reference

- **`docs/workflow/CONSISTENCY-STANDARDS.md`**: error response shape, **403** generic messages, logging / PII expectations for **eligibility** paths — implementers must not diverge without explicit exception.
- **Scope labels** (`self` / `team` / `all`) vs approver rules: see §1 consistency note; do not conflate list-filter scope with approver eligibility.

#### Architecture: eligibility vs department scope

- **Eligibility** (effective decrypt approve on `search-history` / `pending-approvals`): **one shared function** — (1) compute **group-based** `approve` from `app_user_permission_group` → `permission_group_screen`; (2) if **`is_system_admin`**, treat as **no** effective 승인 for those screens (**override**, not a standalone “admin branch only”); (3) **no** `decrypt_approver`. **`ADMIN_EXT` 등 비시스템관리자**: (1)만 적용. `screenFunctions.*.approve` 및 승인 API는 이 **effective** 값 사용.
- **`canApproveForRequester`**: **true** only if approver has **effective** group 승인 (after step 2), **P2-2** same `department_code`, and contract screen access. **One backend component**; **`AuthService.resolveScreenFunctions` delegates**; **no duplicate joins**.
- **Migration sequencing**: **Backfill / manual group assignment** as needed → **read switch (A) new only** → verify → stop all app use of `decrypt_approver` → **DROP** with backup per §2 DB.
- **NFR (login / auth / me)**: **N-1** — changes effective from **next request**; short session/cache TTL allowed — document in contract or ops notes (PRODUCT-QA Q6). Prefer **one query or batch** per resolution path.
- **One user, one group**: `uq_user_permission_group_user` — users with **no** group but legacy `decrypt_approver`: **M-3** with **manual group assignment** before go-live (PRODUCT-QA Q4); no automatic template group required unless implementation chooses to add one.

#### Codebase summary

- **Eligibility flag `isApprover(Long appUserId)`** is implemented in `DecryptApproverService` by querying **`decrypt_approver`** (`SELECT 1 FROM decrypt_approver WHERE app_user_id = ?`).
- **`canApproveForRequester(approverUserId, requesterUserId)`** uses **`decrypt_approver`**: global row (`department_code IS NULL`) or a row whose `department_code` matches an ancestor of the requester’s department (`DepartmentService.getAncestorCodesIncludingSelf`).
- **`AuthService.resolveScreenFunctions`** sets `approve` using **`decrypt_approver` / admin bypass today** — **target**: shared function = **group `approve` then `is_system_admin` override**; **no** `decrypt_approver`.
- **Search history** list filtering and **approve/reject** enforcement call **`decryptApproverService.canApproveForRequester`** (and controllers also check `isApprover` where applicable).
- **User list / hierarchy**: `UserListItemResponse.isApprover` and hierarchy paths depend on **`DecryptApproverService.isApprover`** today — **target**: **remove** `isApprover` from list/hierarchy API and UI; capability visible via **group/screen assignment** only (PRODUCT-QA Q7).
- **Spec** (`specs/permission-group-hierarchy.spec.yaml` §4.4) documents: `approve = (group.approve) AND (decrypt_approver or is_system_admin)` — **replace** with: **`screenFunctions.*.approve` (decrypt screens) = group `approve` 산출 후 `is_system_admin`이면 false로 상쇄**; **`ADMIN_EXT`는 `is_system_admin` 아니면 그룹만**; **`canApproveForRequester`** = effective approve AND P2-2. Align with `docs/contract.md`.

#### Problem analysis

1. **Duplicate administration**: Permission group UI already models **approve** per screen; a second table (`decrypt_approver`) duplicates “who is an approver” and **department/global binding**, causing operational errors and support cost.
2. **Inconsistent mental model**: Administrators assume permission group is authoritative; implementation contradicts that for approver **eligibility**.
3. **Schema expressiveness gap**: `permission_group_screen` is **per group**, not per user–department pair. Today’s **per-user global vs department-scoped** approver rows live only in `decrypt_approver`. Removing that table requires an explicit **replacement rule** (and possibly a small schema or product rule extension) so behavior stays acceptable — **cannot** be inferred solely from code without product confirmation.

#### Diagnostic phase (mandatory for error/bug fix only)

*Not applicable — feature / redesign requirement.*

#### Solution approach

Structure by scope. **`canApproveForRequester`** is **P2-2** (same department only) — **confirmed** in PRODUCT-QA.

**Product / design decisions (recorded 2026-03-23)**

**Authoritative detail**: [`20260323-approver-eligibility-from-permission-group-only-PRODUCT-QA.md`](./20260323-approver-eligibility-from-permission-group-only-PRODUCT-QA.md)

- **P1 — No global approver; drop `decrypt_approver`; `is_system_admin` 승인 상쇄**: No global business decrypt approver. **`is_system_admin = true`** 는 그룹에 `approve=true`여도 **유효 승인 없음**. **`is_system_admin = false`** 인 사용자( **`ADMIN_EXT` 등 그룹 기반 운영자 포함**)는 **권한 그룹 화면별 `approve`만**으로 승인 자격 판단(운영 배치는 운영자 책임). **P2-2** 동일 부서. “부서장 전용” = 운영상 그룹 배치.
- **P2 — `canApproveForRequester`**: **P2-2** — `approver.department_code` **equals** `requester.department_code`. Not ancestor-chain.
- **Cutover**: **(A) New only** after read switch; **default deny when ambiguous** — agreed.
- **Q4**: **M-3** with **pre-go-live manual permission-group assignment** for users who otherwise lack a group.
- **Q5**: Parity automation **reference only**; **manual verification** for mismatches.
- **Q6**: **N-1** — next request; document cache/TTL if any.
- **Q7**: **Single shared eligibility function**; **remove** `isApprover` / 승인자 UI from list and hierarchy; replace legacy queries with real-time group-based checks.
- **Q8**: No dual-read window.

**Backend:**

- Implement **one shared method/component** (e.g. refactored `DecryptApproverService` or `ApprovalEligibilityService`): (1) **`hasGroupDecryptApproveFlag(userId)`** — join `app_user_permission_group` → `permission_group_screen` for `search-history` / `pending-approvals` and `approve = true`; (2) **`effectiveDecryptApprove(userId)`** — if **`is_system_admin`** then **false** for those screens **else** result of (1); (3) **`canApproveForRequester`** — **effective** approve AND **P2-2** same `department_code`. **`is_system_admin`만** “시스템 관리자”로 상쇄; `ADMIN_EXT`는 (1)만 본다. Numeric `app_user.id` (req 20260316).
- **Replace** all **`decrypt_approver` and system-admin bypass** paths: `AuthService.resolveScreenFunctions`, `SearchHistoryController`, `SearchHistoryService`, approve/reject APIs, `UserController` list/summary, `PermissionGroupService` hierarchy — **remove `isApprover` field** from DTOs/responses where it denoted decrypt approver; stop populating it from `decrypt_approver`.
- **Verify all approve-related endpoints** via **`.cursor/skills/api-permission-map/SKILL.md`** mapping and **codebase search** (`decrypt_approver`, approve, `SearchHistory`, pending) so no path is missed.
- **Refactor `DecryptApproverService`**: Either rename/repurpose to “ApprovalEligibilityService” or keep class but **reimplement** `isApprover` / `canApproveForRequester` **without** reading `decrypt_approver` after cutover; remove dead SQL paths once table is dropped.
- **Tests**: Update `AuthServiceTest`, `SearchHistoryServiceTest`, `SearchHistoryControllerTest`, `PermissionGroupServiceTest`, `DecryptApproverServiceUpdateRoleTest`, stubs, and any integration tests that seed `decrypt_approver` for approver behavior.

**Frontend:**

- **Discovery**: Search codebase for keywords **`decrypt_approver`**, **`승인자`**, **`approve`** (and related permission-group copy) so no stale admin guidance remains.
- **Remove** user list / hierarchy **승인자 column** and any copy tied to `isApprover` / dual-table maintenance.
- **Verify** no UI still references maintaining `decrypt_approver` separately.
- **Configuration UI**: Permission group modals — 화면별 **“승인”** 은 그룹에 부여된 사용자에게 승인 자격의 **기준**이 됨; **`is_system_admin` 계정은 백엔드에서 항상 승인 불가로 상쇄**; **`ADMIN_EXT` 등은 그룹 설정만** 따름(동일 부서 규칙은 API).

**DB:**

- **Pre-migration validation SQL** (non-zero counts → fix or **explicit policy** before M1): NULL `app_user_id` in relevant tables, orphan users, invalid `department_code`. Document queries in migration runbook.
- **M1 vs M2**: DBA recommends **M2 pattern**: **backup table retained read-only** for ops; **application reads permission group only** after backfill — **no OR dual-read** for eligibility (privilege expansion risk). Migration must be **idempotent** with clear **transaction boundaries** per script.
- **During transition**: **Stop application writes** to `decrypt_approver` (or enforce trigger/deny) so eligibility does not diverge from intended cutover.
- **M2 timeline (numbered)**:
  1. **Read switch**: App uses permission group only for eligibility (after backfill + validation).
  2. **Verification**: §3 DB + security TCs + **manual** checks (PRODUCT-QA Q5); optional reference scripts.
  3. **DROP** (or archive): `decrypt_approver` removed from active schema after retention policy; **backup** `decrypt_approver` before DROP.
- **Rollback**: Reference `backend/src/main/resources/db/migration-20260225-decrypt-approver-note.md`; incremental migrations only; restore from backup if rollback required.
- **Indexes**: User→group→screen path should use existing PK/UNIQUE; add **partial index** on approve=true only if **EXPLAIN** shows need.
- Update **`init-data.sql`** / **`schema_sys.sql`** / **`schema.sql`** to **remove** or **stop seeding** `decrypt_approver` when no longer authoritative.

**Contract / spec:**

- As in **Contract, API definition, and error-code mapping** above.

**Cursor skills:**

- `.cursor/skills/auth-permission-domain/SKILL.md`, `search-history-decrypt-domain`, `api-permission-map`, `db-domain` — update approver source narrative.
- **`.cursor/skills/department-approver-domain/SKILL.md`** — align with **P2-2**, **admin never approves**, **DROP `decrypt_approver`**.

### Migration phase reference (for §3 TC linkage)

| Phase | Description |
|-------|-------------|
| **Pre** | Validation SQL clean or waived; PRODUCT-QA decisions + security acceptance (**admin never approves**, non-admin + group 승인 + P2-2) |
| **Backfill** | M1 data movement / group updates |
| **Read switch** | App eligibility from group tables only |
| **Hard cutover** | Legacy `decrypt_approver` rows ignored for eligibility; no app read/write for eligibility |
| **DROP** | Table removed or archived per DBA |

### Affected scopes and change targets (verification)

**Change target verification** was run per `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md` (§1 scope table + pattern **3.2 Permission or screen-access change**).

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|------------|-----------------------------------------------|
| Backend | Yes | Yes |
| Frontend (config UI + view screen) | Yes (verification + copy; approve/pending-approvals UX) | Yes |
| DB | Yes | Yes |
| Contract / Spec | Yes | Yes |
| Cursor tools (skills, specs) | Yes | Yes |

**Pattern 3.2 touchpoints**: backend access checks and auth payload shape for `screenFunctions.approve`; frontend menu/sidebar if gated by approve; permission configuration UI; contract permission mapping; auth/permission and decrypt-domain skills.

### Planned change file list (expected change targets)

**(Planned at authoring. Implementing agent (Step 4) confirms or amends this list when implementation is complete.)**

#### Backend

- `backend/src/main/java/com/logmng/service/DecryptApproverService.java` — Reimplement or replace eligibility logic; remove `decrypt_approver` dependency after migration.
- `backend/src/main/java/com/logmng/service/AuthService.java` — `resolveScreenFunctions`: derive approver eligibility from permission group, not `decrypt_approver`; **delegate** to single eligibility component (no duplicate joins).
- `backend/src/main/java/com/logmng/service/SearchHistoryService.java` — Align list/action checks with new `canApproveForRequester` / eligibility.
- `backend/src/main/java/com/logmng/controller/SearchHistoryController.java` — Approve gating consistent with new rules.
- `backend/src/main/java/com/logmng/service/PermissionGroupService.java` — **Remove** hierarchy `isApprover` (or stop exposing decrypt-approver flag); group/screen data remains source for admins.
- `backend/src/main/java/com/logmng/controller/UserController.java` — **Remove `isApprover`** from list/summary DTOs per contract; adjust clients.
- `backend/src/test/java/com/logmng/service/*` — Stubs and tests referencing `decrypt_approver` for approver behavior.
- `backend/src/main/resources/db/schema.sql` / `schema_sys.sql` / `init-data.sql` — Deprecate/remove `decrypt_approver`; add migration under `backend/src/main/resources/db/` per DBA.

#### Frontend

- `frontend/src/components/UserManagement/UserManagement.js` (and related) — **Remove 승인자 column**; rely on group/permission display per PRODUCT-QA Q7.
- Permission group management components — locate via search: **`decrypt_approver`**, **`승인자`**, **`approve`** under `frontend/src/`.

#### DB

- New migration SQL: backfill + cutover + optional table drop; align `setup.sh` / apply order if required.

#### Contract / documentation

- `docs/contract.md`
- `docs/api-definition.md` (§6.1.5, §6.1.6, §6.1.7, §7.1)
- `specs/permission-group-hierarchy.spec.yaml`
- `export/design/permission-by-screen.md` (if still maintained as design export)

#### Cursor tool update targets

- `.cursor/skills/auth-permission-domain/SKILL.md` — Remove or replace “decrypt_approver” as approver source; document new rule.
- `.cursor/skills/department-approver-domain/SKILL.md` — Align with post-`decrypt_approver` model (**depends on P1**).
- `.cursor/skills/search-history-decrypt-domain/SKILL.md` — Approval path eligibility description.
- `.cursor/skills/api-permission-map/SKILL.md` — Map approve APIs to new checks.
- `.cursor/skills/db-domain/SKILL.md` — If schema changes.

---

## 3. Test approach

### Test case list (required)

**Domain alignment**: Permission / access control — trace approve-related APIs per `api-permission-map` skill and updated contract.

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|----|-------|------|------------------------------|-----------------|--------------------------------------------|
| TC-01 | Backend | Normal | **Non-admin** user assigned to group with `search-history` + `approve=true` (and read/screen access); **no** `decrypt_approver` row | `DecryptApproverService.isApprover(appUserId)` is **true**; login payload `screenFunctions.search-history.approve` is **true** | Unit (mvn test) |
| TC-02 | Backend | Normal | Same as TC-01 with `pending-approvals` + `approve=true` only; **non-admin** | `isApprover` true; `screenFunctions.pending-approvals.approve` true | Unit (mvn test) |
| TC-03 | Backend | Normal | **Non-admin** user in group with `approve` **false** or omitted for both screens; no decrypt_approver | `isApprover` false; approve APIs/controllers deny approver path | Unit (mvn test) |
| TC-04 | Backend | Normal | Explicit `permission_group_screen.approve=false` for both approve screens | Effective approve **false**; after **hard cutover** (migration phase table), legacy `decrypt_approver` rows **ignored** for eligibility — aligns with chosen cutover rule; if product chose union/intersection during transition, expected result must match **documented** rule | Unit (mvn test) |
| TC-05 | Backend | Edge | **Non-admin** eligible user (`approve=true` on screen); approver and requester **same** `department_code`; no `decrypt_approver` | `canApproveForRequester` **true**; approve/reject **allowed** where contract allows | Unit (mvn test) |
| TC-06 | Backend | Edge | **Non-admin** eligible user but **different** `department_code` (P2-2) | **403** / **`FUNCTION_NOT_ALLOWED`** on approve-reject per contract | Unit (mvn test) |
| TC-06b | Backend | Edge | User lacks approver eligibility | **`NOT_APPROVER`** / **`FORBIDDEN_NOT_APPROVER`** per contract (not conflated with TC-06) | Unit (mvn test) |
| TC-07 | Backend | Edge | **`is_system_admin=true`**; group **without** `approve=true` on decrypt screens | Effective approve **false**; 승인 API 거부 | Unit (mvn test) |
| TC-07b | Backend | Edge | **`is_system_admin=true`**; group **with** `approve=true` and **same** `department_code` as requester | Group 플래그는 있으나 **effective 승인 false** (`is_system_admin` 상쇄); **`canApproveForRequester` false**; 승인/반려 **denied** | Unit (mvn test) |
| TC-07c | Backend | Normal | **`is_system_admin=false`**; **`ADMIN_EXT`**(또는 넓은 관리 그룹)에 `approve=true`; same dept as requester | **`canApproveForRequester` true** where contract allows — **not** treated as system admin | Unit (mvn test) |
| TC-08 | Backend | Normal | **`GET /api/users`** (or equivalent) per **`docs/api-definition.md` §7.1** after contract update | Response **does not** expose `isApprover` (or field deprecated/removed per contract); breaking change documented | Unit or integration (mvn test) |
| TC-09 | Backend | Normal | User-permission hierarchy endpoint | **No** decrypt-approver-only flag; group/screen assignment shows capability | Unit (mvn test) |
| TC-10 | Integration | Normal | Login → `GET /api/auth/me` | **Non-admin**: `screenFunctions.*.approve` match group; **admin**: approve flags **false** for decrypt screens regardless of group | Integration (curl / documented script) |
| TC-11 | Integration | Normal | Pending approval **list/detail** visible only per policy; approve action | End-to-end approver flow without `decrypt_approver`; **information exposure** verified **manually** (PRODUCT-QA Q5) | Manual / browser |
| TC-12 | DB | Normal | Run migration on DB with sample `decrypt_approver` rows | Post-migration, users retain intended approver behavior per parity checklist | Manual / SQL verification |
| TC-13 | Frontend | Regression | User management grid | **승인자** column **absent**; group/permission UI still correct | Manual / browser |
| TC-14 | Security | Regression | **Audit**: approve/reject logged with approver numeric id, request ids, time, result; **no new plain PII** in eligibility/audit logs | Matches pre-change baseline policy | Log review / integration |
| TC-15 | Security | IDOR / horizontal | **Non-approver** (or out-of-scope approver) | Cannot **view** or **approve** another user’s pending items (API + UI) | Unit + manual |
| TC-16 | Security | Transition | Cutover **(A) new only** — sample users with legacy `decrypt_approver` only | Eligibility from **group only**; **default deny** if ambiguous | Manual / scripted |
| TC-17 | Integration | Doc alignment | After contract/spec change, approve endpoints and errors match **`docs/contract.md`** and **`docs/api-definition.md`** | No stale `decrypt_approver` as authority in live behavior | Code review + integration |
| TC-18 | Security | Post-cutover | Legacy `decrypt_approver` row exists; user **not** in approving group | Eligibility **false**; row **ignored** (hard cutover) — pairs with **TC-04** | Unit (mvn test) |
| TC-DB-01 | DB | Pre-migration | Validation SQL: NULL `app_user_id`, orphans, invalid `department_code` | Zero rows or explicit waiver documented | SQL |
| TC-DB-02 | DB | Normal | Backfill + read switch | Reference script / **manual** checks per PRODUCT-QA Q5 | SQL / script |
| TC-DB-03 | DB | Normal | Re-run migration scripts | **Idempotent**; no duplicate corruption | SQL |
| TC-DB-04 | DB | Post-cutover | App eligibility ignores `decrypt_approver` | Matches TC-04 / TC-18 | SQL + app test |
| TC-DB-05 | DB | Rollback drill | Restore from backup per runbook | Documented recovery succeeds | Ops / staging |

**Note**: TC-12 may be **subsumed or extended** by TC-DB-02–TC-DB-04; QA maintains one traceable row per execution need.

**Optional**: Dual-read window TC **only** if product selects a temporary dual-read — default is **hard cutover** (no optional TC required).

### Test scenarios

#### Scenario 1: Admin grants approve in permission group only

1. Create/update a permission group with `approve=true` on `search-history` and assign user U.
2. Ensure U has **no** `decrypt_approver` row.
3. Log in as U: confirm pending-approvals/search-history approve UI and approve API succeed where contract allows.

#### Scenario 2: Migration and reference parity

1. Take DB snapshot with legacy `decrypt_approver` rows; assign missing groups **manually** where needed (Q4).
2. Run migration/backfill per §2; read switch **(A)**.
3. Use optional parity script **for reference**; **manually verify** department-code alignment and list/detail visibility (Q5).

#### Scenario 3: Privilege escalation guard

1. Run TC-16 + TC-15 for users at **department boundaries** (P2-2: cross-dept must deny).
2. Confirm **`is_system_admin`** cannot effective-approve **with or without** group 승인 (TC-07, TC-07b); **non-`is_system_admin` + group 승인** (e.g. ADMIN_EXT) can per TC-07c.

### Test data

- Provide **executable SQL** in implementation/QA notes: sample `permission_group`, `permission_group_screen`, `app_user_permission_group`, and `app_user` rows for approver/non-approver; optional legacy `decrypt_approver` rows for migration TC-12 / TC-DB-*.
- Document **numeric `app_user.id`** for all permission tests (align with req 20260316).

### Test environment

- Frontend: `http://localhost:3001` (per contract)
- Backend: `http://localhost:9200`
- Database: PostgreSQL (per project)

### 3.5 Browser automation verification (optional)

- **Applicable TCs**: TC-11, TC-13.
- **Procedure**: Login as admin → configure group → login as target user → open pending approvals / search history → snapshot confirms actions; user management snapshot confirms **no** 승인자 column (TC-13).
- **Reference**: `docs/workflow/BROWSER-AUTOMATION-VERIFICATION-POLICY.md`

---

## 4. Checklist

### Frontend verification

- [ ] API parameters validated
- [ ] UI behavior confirmed
- [ ] Error handling verified

### Backend verification

- [ ] API test cases written and run
- [ ] Logs checked
- [ ] Performance checked (if applicable) — include **auth/me latency** if caching or extra joins added (see §2 Architecture NFR)
- [ ] **Security**: **Audit parity** (approver id, request ids, time, result; no new plain PII) verified
- [ ] **Security**: **Cutover rule** in deployed config matches §2.1 signed choice
- [ ] **All approve-related endpoints** verified against **api-permission-map** + codebase search (`decrypt_approver`, approve)

### Integration

- [ ] End-to-end flow tested
- [ ] Edge cases tested

### Documentation

- [ ] Requirement doc completed
- [ ] Code comments added (if applicable)

### Post-implementation QA

- [ ] **Post-implementation QA checklist** completed (contract/spec/code alignment per **`docs/workflow/DOC-CODE-SYNC.md`**, §3 TC-17)

---

## 5. Test results

### Test run date

- (Pending — populate after QA verification)

### Test results

#### Frontend

(Pending)

#### Backend

(Pending)

**Commands:**

```bash
# Placeholder — implementing agent must provide one executable command per TC in §3 after implementation
```

**Outcome:**

- (Pending)

### Issues found and resolution

(Pending)

### Next steps

1. Security **sign-off** on **`is_system_admin` 승인 상쇄**, **ADMIN_EXT는 그룹만**, **P2-2** (§2.1) if required by org policy.
2. Step 4: Backend + DB + Frontend + Contract per handoff (**`docs/workflow/DOC-CODE-SYNC.md`**) — implement PRODUCT-QA + §2 Product decisions.

---

## 6. Error remedy result (cause and action) — for error/bug fix requirements only

*Not applicable.*

---

## 7. Final version (Korean) — add after all verification is complete

Per `docs/workflow/DOCUMENT-LANGUAGE-POLICY.md`, add a Korean summary here after QA completes verification (or create `20260323-approver-eligibility-from-permission-group-only-ko.md`). **Do not fill §7 until verification is complete.**

### Final Korean summary

- **Requirement description**: (Pending)
- **Expected outcome**: (Pending)
- **Verification result**: (Pending)

---

**Author**: Requirements (subagent)  
**Date**: 2026-03-23  
**Revision**: 2026-03-25 — `docs/contract.md` 「복호화 승인 자격」: **`is_system_admin`만 상쇄**, **ADMIN_EXT는 그룹 `approve`만**, 판별 **그룹→상쇄**. (2026-03-24~: P1/P2, `isApprover` 제거, 패리티 참고.)  
**Status**: Product decisions in PRODUCT-QA (incl. admin never approves) — implementation + contract/spec PR pending; security sign-off pending per §2.1  
