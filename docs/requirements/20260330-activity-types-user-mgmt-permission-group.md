# 20260330 - Activity types taxonomy and audit for user management and permission group flows

## 1. User requirement

### Requirement description

The product treats **user activity history** as the cross-cutting record of **actions performed in this web application**. Today, **User management** and **Permission group settings** flows do not produce visible, typed entries in the **activity history** experience (list/API), while other behaviors (e.g. login, log search) are recorded via the shared `user_activity_log` pipeline.

The product must:

1. Define a **clear, extensible taxonomy of activity types** (stable codes and human-readable labels) covering at least **login**, **search**, and other behaviors including **user administration**, **permission group changes**, **decrypt approval**-related actions, and other app actions as needed.
2. Ensure **User management** and **Permission group configuration** flows **emit auditable events** into the same activity store used for activity history, with **correct type codes** and sufficient **non-sensitive detail** in `action_detail` for operators (e.g. target group id, target user id) per security guidance.
3. Make activity history **queryable**: users with access to the activity-log screen/API must be able to **filter by activity type** together with existing filters (**date range**, **user**, **department**, **IP**, etc.) per `docs/contract.md` and `docs/api-definition.md`.

### User scenario

1. An administrator changes a permission group (create/update/delete) or assigns/removes users to/from groups on the **Permission group** screens.
2. An administrator performs **User management** actions that the product defines as auditable (e.g. viewing user list is optional; mutating assignments or settings must be auditable).
3. A security officer opens **Activity history** (`activity-log`), sets a **date range**, optionally narrows by **department / user / IP**, and selects an **activity type** such as “permission group update” or “login”.
4. The list shows matching rows; the **action type** column and **detail** reflect the admin or end-user behavior.
5. A user performs **login** or **log search**; those rows continue to appear with types **LOGIN**, **SEARCH**, etc., and remain filterable alongside new types.

### Expected outcome

- A **documented canonical list** of **activity type codes** (single source: backend enum/constants + contract/spec) aligned with `user_activity_log.action_type` string values; labels are available for the UI (Korean labels as used elsewhere in the app).
- **Permission group** write operations (create/update/delete, user–group assignment changes) **persist** to `user_activity_log` with dedicated types (e.g. `PERMISSION_GROUP_CREATE`, `PERMISSION_GROUP_UPDATE`, `ASSIGN_USER_TO_PERMISSION_GROUP` — exact codes are finalized in implementation per §2).
- **User management** APIs that perform mutations the product classifies as auditable **persist** to `user_activity_log` with appropriate types; purely informational list views may be excluded if product confirms to avoid noise.
- **Search history** / **decrypt approval** flows that represent distinct user-visible actions are represented by **explicit types** (e.g. create search-history entry, approver approve/reject) where those actions are not already captured with sufficient specificity; implementers **map** existing `SEARCH`/`DECRYPT` usage vs new types per §2 taxonomy.
- **Activity history** search form includes **all** product-defined types in the **activity type** filter (empty = all), sourced from the **same canonical list** as the backend (prefer **API-provided options** rather than a duplicated hardcoded list in the frontend).
- **Statistics** (`ActivityStatisticsService`): existing dashboards that aggregate `LOGIN` / search-like / `DECRYPT` counts **remain correct**; new admin-only types are either **included in a defined bucket** (e.g. “admin actions”) or **excluded from numeric KPIs** but still visible in the activity-log list — product must confirm (see §2 open points).
- **Scope rules** for `POST /api/activity-log/search` (`self` | `team` | `all`) continue to apply; new row types do not weaken enforcement. Regression tests cover **scope + actionType** combinations.
- **User block** presentation on **activity-log** remains aligned with existing design references (`docs/design/search-fields-by-screen.md`, `docs/design/forms-and-filters.md`); this requirement does **not** change user-block width rules unless a separate UX alignment requirement is opened.

**References (design / contract):** `docs/contract.md` (activity-log scope, shared department filter contract), `docs/api-definition.md` § `/api/activity-log`, `docs/design/search-fields-by-screen.md`, `docs/design/search-field-definition-items.md`.

## 2. Design

### 2.1 Security review (optional; when PII / decryption / access control)

- [x] Security review performed (Step 2; before Step 4)

#### Risks

| ID | Risk | Notes |
|----|------|--------|
| SR-01 | **`action_detail` over-collection** | `ActivityLogAspect` with `includeParams=true` serializes method arguments; nested DTOs may carry **tokens**, **refresh bodies**, **approval payloads**, or **free-text** not fully covered by `isSensitiveField` (name-based only). |
| SR-02 | **Decryption / search content in audit** | Reusing **SEARCH**/**DECRYPT** or new types without a strict schema could store **unmasked keywords**, **decrypted row fragments**, or **log payload excerpts** in `action_detail`. |
| SR-03 | **Identifier disclosure** | Storing **numeric `userId`**, **permission group id/code** is acceptable for operators but is still **identity/relationship metadata**; unnecessary fields (e.g. email, phone, full request dumps) increase exposure if scope is `team`/`all`. |
| SR-04 | **Scope bypass via new types** | New `action_type` values must **not** introduce a code path that skips `POST /api/activity-log/search` scope normalization (`self` / `team` / `all`). A bug could expose **cross-user** admin events to the wrong viewer. |
| SR-05 | **Metadata endpoints** | Optional **`GET …/action-types`** must not leak **business** data; if authentication/authorization is weaker than activity-log search, it could become an **enumeration** channel for privileged codes. |
| SR-06 | **Failure / error text** | Logging **exception messages** or stack fragments into `action_detail` can leak internals or identifiers; keep error detail out of persisted audit or **dev-only** per product policy. |

#### Acceptance criteria (security)

- [ ] **`action_detail` allowlist**: For **user-management** and **permission-group** events (and any new decrypt-approval types), persisted JSON uses **documented keys only** (e.g. `targetUserId`, `targetUsername` if needed for display contract, `permissionGroupId`, `permissionGroupCode`, `operation`, `screenIdsChanged` summary). **No** raw request body, **no** `password` / `token` / `secret` / `refreshToken` / `authorization` header values, **no** decrypted log content or **unmasked** search keywords.
- [ ] **`includeParams` default**: New `@ActivityLog` on admin controllers uses **`includeParams=false`** unless Backend implements a **dedicated sanitizer** for that method’s parameter types (generic `ObjectMapper` serialization is **not** sufficient as sole control).
- [ ] **Contract alignment**: `docs/contract.md` / `docs/api-definition.md` document **per-category** `action_detail` shape; implementers add **unit assertions** (extends TC-17) that sample rows for each new type omit forbidden keys/patterns.
- [ ] **Scope unchanged**: `scope=self` forces **current user only** and ignores widening filters; `scope=team` remains **department allowlist first**; `scope=all` is the only cross-user mode — **identical** enforcement when `actionType` is set to any new code (see TC-07–TC-09).
- [ ] **Action-types API**: Same **authentication** and **screen-level** access rules as activity-log search (or stricter); response is **code + label** only **no** PII.
- [ ] **Statistics / KPIs**: If new types are excluded from charts but present in the DB, document that **exclusion does not** grant extra visibility; list visibility remains governed by activity-log APIs only.

#### Recommendations

1. **Prefer explicit builders** — Build `action_detail` from **explicit fields** in the aspect or service (e.g. after success), not from full DTO serialization.
2. **Extend denylist** — If any path must use `includeParams=true`, extend `isSensitiveField` / sanitization for **auth**, **approval**, and **user-update** DTOs (e.g. fields named `newPassword`, `currentPassword`, nested `credentials`).
3. **Do not log approval decisions with requester PII beyond ids** — Match **minimization**: ids needed for operators; avoid full **search-history** or **decrypt** snapshot bodies in `action_detail`.
4. **Regression tests** — Keep **TC-08**/**TC-09** as **mandatory** integration coverage for **new `actionType` values**; add a negative test: user without `scope=all` cannot retrieve others’ admin-audit rows by **filtering only** by new type.
5. **Optional `security-guide.md` note** — Add a short **“Activity audit (`user_activity_log`)”** bullet: allowed keys, forbidden content, alignment with `ActivityLogAspect` behavior.

#### Residual risk

Operator-visible **numeric user ids** and **group codes** in `action_detail` remain acceptable for this product’s audit model; organizations with stricter PII policies may require **hashing** or **redaction** in exports — **out of scope** unless a separate requirement is opened.

### Technical design

#### Codebase summary (investigation)

- **Storage**: `user_activity_log.action_type` is `VARCHAR(50)` (`schema_user_activity_log.sql`). Indexes exist on `action_type` and composites with `user_id` / `created_at`.
- **Recording mechanism**: `ActivityLogAspect` records when controller methods are annotated with `@ActivityLog`; `UserActivityLogService.saveActivityLog` inserts rows. Today **`@ActivityLog` appears on** `AuthController` (LOGIN, LOGOUT), `LogDbController` (SEARCH, VIEW, DECRYPT, ADVANCED_SEARCH), and `DecryptController` (DECRYPT). **`UserController` and `PermissionGroupController` have no `@ActivityLog`** — so admin flows do not emit `user_activity_log` rows via this aspect.
- **Search API**: `UserActivityLogSearchRequest` supports `actionType` filter; `UserActivityLogService` applies `AND action_type = ?` when set.
- **Frontend**: `UserActivityLogSearchForm.js` defines a **fixed** `actionTypes` array (LOGIN, LOGOUT, SEARCH, VIEW, DECRYPT, ADVANCED_SEARCH, EXPORT); it does **not** include admin/decrypt-approval-specific types and can drift from backend.
- **Statistics**: `ActivityStatisticsService` maps `action_type` to KPIs (`LOGIN`, `SEARCH`/`ADVANCED_SEARCH`/`STATS_VIEW`, `DECRYPT`). It also uses a separate “statistics log type” dimension (`LOGIN`, `pb_feplog`, `java_fw_imglog`) for **log-type** slicing — distinct from **activity action_type** semantics (see service comments).
- **Prior docs**: `20260206-user-activity-log` listed many **action_type** examples (EXPORT, SCHEMA_VIEW, STATS_VIEW, ERROR, etc.) that are **not** all implemented in code; `20250227-user-permission-hierarchy-group` noted optional audit — this requirement **elevates** admin/permission flows to **first-class auditable events**.

#### Problem analysis

1. **Incomplete coverage**: “All app actions” are not recorded; admin and many non–log-search APIs lack `@ActivityLog` or equivalent writes.
2. **Taxonomy drift**: UI filter options, statistics bucketing, and actual emitted codes can diverge (e.g. EXPORT, STATS_VIEW listed in UI but not verified on all paths).
3. **Discoverability**: Without a **single canonical enum** and **API-driven filter options**, new types require frontend edits and are error-prone.

#### Solution approach

Structure by scope. **Product / Architecture** must confirm the **final enum table** and **statistics bucketing** for new types (see open points).

**Backend:**

- Introduce a **single source of truth** for activity type codes (e.g. `ActivityActionType` enum or constants class) used by `@ActivityLog(actionType=…)`, validation, and optional metadata (category, default Korean label key).
- Add **`@ActivityLog`** (or explicit `UserActivityLogService` calls from services where AOP is unsuitable) to **PermissionGroupController** endpoints: list is optional; **POST/PUT/PATCH/DELETE** and **user–group assignment** must emit events with structured `action_detail` (ids, not secrets).
- Add **`@ActivityLog`** (or equivalent) to **User management** mutating endpoints **as applicable**; **GET /api/users** may remain without logging if product confirms (noise reduction).
- Evaluate **SearchHistoryController**, **DecryptAllowedController**, and approval-related paths for distinct types (e.g. `SEARCH_HISTORY_CREATE`, `DECRYPT_APPROVAL_GRANT`) vs reusing **SEARCH**/**DECRYPT**; document the mapping in contract.
- Optional: **`GET /api/activity-log/action-types`** (or **`GET /api/filter-options/activity-action-types`**) returning `{ code, label }[]` for the UI filter — aligns with “authoritative option source” patterns in `docs/design/search-field-definition-items.md`.
- **Statistics**: Update aggregation SQL or documentation so new types do not **double-count** searches/decrypts; add **admin** metrics only if product requests.

**Frontend:**

- **UserActivityLogSearchForm**: Replace or supplement hardcoded `actionTypes` with **API-loaded** options (fallback: shared constant imported from generated contract doc — only if API deferred).
- **UserActivityLogTable** / **UserActivityLogDetail**: Ensure **labels** display for new codes (map code → label; unknown codes show raw code).
- **User management** / **Permission group** screens: **no** embedded “history” panel is required by this requirement; operators use **Activity history** menu. Optional future: deep links from an event detail to the affected entity — **out of scope** unless listed in §3.

**DB:**

- **Must verify** `VARCHAR(50)` length for longest code; extend column if necessary via migration.
- No new table required if `user_activity_log` remains the store; optional **check constraint** or **lookup table** only if product mandates referential integrity.

**Contract / spec:**

- Update **`docs/contract.md`** and **`docs/api-definition.md`**: document canonical **action_type** codes, optional **action-types** endpoint, and **action_detail** shape per category.
- Update **`specs/permission-group-hierarchy.spec.yaml`** or add **`specs/activity-action-types.spec.yaml`** if a dedicated machine-readable list is preferred.

**Cursor tools:**

- Update **`.cursor/skills/activity-statistics-domain/SKILL.md`** and **`.cursor/skills/auth-permission-domain/SKILL.md`** when taxonomy and recording points are fixed; **`api-permission-map`** if new endpoints are added.

### Affected scopes and change targets (verification)

Per `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md`:

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|------------|-----------------------------------------------|
| Backend | Yes | Yes |
| Frontend (config UI + view screen) | Yes (activity-log view + admin flows as emitters) | Yes |
| DB | Yes (possible VARCHAR length / optional constraint) | Yes |
| Contract / Spec | Yes | Yes |
| Cursor tools (skills, specs) | Yes | Yes |

**Pattern §2.4 (search/filter UI alignment across screens):** Not triggered for **layout** changes. **Select option source** for **activity type** on activity-log **must** be documented: prefer **GET** list from backend; if standard is incomplete, Frontend follows **`docs/design/ux-frontend-standard-principles.md` §2** (list gaps, propose draft, request confirmation).

### Planned change file list (expected change targets)

**(Planned at authoring. Implementing agent (Step 4) confirms or amends this list when implementation is complete.)**

#### Backend

- `backend/src/main/java/com/logmng/annotation/ActivityLog.java` — verify defaults; no behavior break.
- New: `backend/src/main/java/com/logmng/constants/ActivityActionType.java` (or equivalent) — canonical codes (and optional descriptions).
- `backend/src/main/java/com/logmng/controller/PermissionGroupController.java` — `@ActivityLog` on mutating methods; sanitized `action_detail`.
- `backend/src/main/java/com/logmng/controller/UserController.java` — `@ActivityLog` on selected mutating endpoints if any (confirm with product).
- `backend/src/main/java/com/logmng/controller/SearchHistoryController.java`, `DecryptAllowedController.java` — evaluate and add types for decrypt-approval–related actions.
- `backend/src/main/java/com/logmng/aspect/ActivityLogAspect.java` — verify aspect applies to new annotations; redaction hooks if needed.
- `backend/src/main/java/com/logmng/service/UserActivityLogService.java` — verify insert and search by `action_type`; length validation.
- `backend/src/main/java/com/logmng/service/ActivityStatisticsService.java` — adjust aggregations or document exclusions.
- New (optional): `backend/src/main/java/com/logmng/controller/UserActivityLogController.java` or `FilterOptionsController` — expose action-type list endpoint.
- Tests: `backend/src/test/java/com/logmng/...` — aspect, controllers, service queries.

#### Frontend

- `frontend/src/components/UserActivityLog/UserActivityLogSearchForm.js` — load action type options from API or shared source; labels.
- `frontend/src/components/UserActivityLog/UserActivityLogTable.js`, `UserActivityLogDetail.js` — label mapping for types.
- `frontend/src/components/PermissionGroupManagement/**`, `UserManagement/**` — ensure flows trigger backend logging (usually no extra UI beyond success handling unless local toast references audit).
- Tests: `frontend/src/components/UserActivityLog/*.test.js` — options + filter payload.

#### DB

- `backend/src/main/resources/db/schema_user_activity_log.sql` and/or new `migrate-*-activity-action-type-length-*.sql` — extend `action_type` length if required.

#### Contract / spec / docs

- `docs/contract.md`, `docs/api-definition.md` — action types + optional endpoint.
- `specs/permission-group-hierarchy.spec.yaml` or new `specs/activity-action-types.spec.yaml`.

#### Cursor skills

- `.cursor/skills/activity-statistics-domain/SKILL.md`, `.cursor/skills/auth-permission-domain/SKILL.md`, `.cursor/skills/log-search-domain/SKILL.md` (if search/approval types change), `.cursor/skills/api-permission-map/SKILL.md`.

### Open points (TODO for Contract / product / Statistics)

| ID | Topic | Owner |
|----|--------|--------|
| OP-01 | Final **closed set** of `action_type` codes (including decrypt-approval and search-history variants) | Product + Contract |
| OP-02 | **Statistics**: whether new admin types appear in **existing** KPI charts or only in activity-log | Product |
| OP-03 | **GET /api/users** list: log **USER_LIST_VIEW** or omit | Product |
| OP-04 | **VARCHAR(50)** sufficiency for longest code | DB |

## 3. Test approach

### Test case list (required)

**Domain notes:** Include permission verification for `POST /api/activity-log/search` (scope normalization) per `api-permission-map` skill. **Mandatory automated tests** for each TC marked Unit/Integration per template.

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification |
|----|-------|------|------------------------------|-----------------|---------------|
| TC-01 | Backend | Normal | Permission group **create** via API | `user_activity_log` row with new **action_type** and JSON `action_detail` with group id/code | Unit/Integration |
| TC-02 | Backend | Normal | Permission group **update** (screens/functions) | Row with update type; detail reflects changed ids (no secrets) | Unit/Integration |
| TC-03 | Backend | Normal | Permission group **delete** | Row with delete type | Unit/Integration |
| TC-04 | Backend | Normal | **Assign / unassign user** to group | Row(s) with assignment type; target user id and group id present in detail | Unit/Integration |
| TC-05 | Backend | Normal | User management **auditable mutation** (if any in scope) | Matching `action_type` row | Unit/Integration |
| TC-06 | Backend | Edge | `action_type` string at **max length** boundary | Insert succeeds or validation error is explicit | Unit |
| TC-07 | Backend | Normal | `POST /api/activity-log/search` with `actionType` = new code | Only matching rows; scope rules unchanged | Unit/Integration |
| TC-08 | Backend | Normal | `scope=self` + `actionType` filter | Only **current user** rows with that type | Integration |
| TC-09 | Backend | Normal | `scope=team` + department filter + `actionType` | Team allowlist + filters applied | Integration |
| TC-10 | Integration | Normal | End-to-end: admin performs group update → search activity log by new type | Row visible | Manual or automated E2E |
| TC-11 | Frontend | Normal | Activity type **dropdown** lists all canonical codes (from API) | Options match backend list; Korean labels | Unit (npm test) |
| TC-12 | Frontend | Normal | Select type + search sends **`actionType`** in request body | API receives correct field | Unit |
| TC-13 | Frontend | Normal | Table renders **label** for known type and **code** for unknown | No crash; readable display | Unit |
| TC-14 | Integration | Regression | Login + log search still produce **LOGIN** / **SEARCH** | Filters work as before | Integration |
| TC-15 | Backend | Normal | Optional **GET action-types** endpoint | Returns sorted list `code` + `label`; auth required consistent with activity-log | Unit/Integration |
| TC-16 | Backend | Normal | **ActivityStatisticsService** daily totals | LOGIN/SEARCH/DECRYPT counts **unchanged** for legacy scenarios; document behavior for new types | Unit |
| TC-17 | Security | Edge | `action_detail` does not contain password/session/token fields | Inspection / unit assertion on sample logs | Unit |
| TC-18 | Integration | Normal | Decrypt approval–related action (if instrumented) | Distinct type appears and is filterable | Integration |

### Test scenarios

#### Scenario A: Taxonomy consistency
1. Read Contract/spec action type list.
2. Compare to backend enum and frontend options.
3. Confirm **exactly one** canonical list.

#### Scenario B: Admin audit trail
1. Log in as admin with permission-group **write**.
2. Perform create/update/delete/assign.
3. Open activity log; filter by corresponding type.
4. Verify row count and detail fields.

### Test data
- SQL or API steps to create **permission groups** and **users** for assignment tests; use non-production ids. Document **numeric `userId`** (`app_user.id`) for API calls per contract.

### Test environment
- Frontend: `http://localhost:3001` (per project defaults)
- Backend: `http://localhost:9200`
- Database: PostgreSQL (per project)

### 3.5 Browser automation verification (optional)

- **Applicable TCs**: TC-10, TC-14 (and admin flows if Manual).
- **Procedure**: Login → navigate to permission group → perform change → navigate to activity log → set filters → snapshot table.

## 4. Checklist

### Frontend verification
- [ ] API parameters validated
- [ ] UI behavior confirmed
- [ ] Error handling verified

### Backend verification
- [ ] API test cases written and run
- [ ] Logs checked
- [ ] Performance checked (if applicable)

### Integration
- [ ] End-to-end flow tested
- [ ] Edge cases tested

### Documentation
- [x] Requirement doc completed (§5 updated; bugfix-1 verified 2026-03-30)
- [ ] Code comments added (if applicable)

## 5. Test results

### Test run date
- 2026-03-30 (QA Step 5 — initial run)
- 2026-03-30 (QA Step 5 — **re-verification** after bugfix-1: `UserActivityLogController` detail mapping `/{id:\\d+}`)

### Test results

#### Automated (local)

| Command | Result | Notes |
|---------|--------|--------|
| `cd backend && mvn test` | **Pass** (exit 0) | Re-verification run after bugfix. |
| `cd frontend && npm test -- --watchAll=false` | **Pass** (exit 0) | Re-verification run after bugfix. |

#### Verify script (restart + health)

| Check | Result | Notes |
|-------|--------|--------|
| `./scripts/dev-services.sh backend restart` | **Pass** (exit 0) | Backend restarted; **`mvn package -DskipTests`** run before restart so deployed JAR includes `/{id:\\d+}` (stale JAR previously still returned 400 for action-types). |
| `curl -s http://localhost:9200/api/health` | **Pass** | 200, JSON success. |
| `curl -s -o /dev/null -w "%{http_code}" http://localhost:3001` | **Pass** | 200. |
| `curl -s http://localhost:9200/api/db/test` | **Pass** | `data.connected === true`. |

#### API spot-check (authenticated session)

| Request | Expected | Actual | TC |
|---------|----------|--------|-----|
| `GET /api/activity-log/action-types` (no cookie) | 401 | **401** `UNAUTHORIZED` | TC-15 |
| `GET /api/activity-log/action-types` (after `POST /api/auth/login` with cookie) | 200, `{ code, label }[]` including `PERMISSION_GROUP_*` | **HTTP 200**; JSON `success: true`; array includes e.g. `PERMISSION_GROUP_CREATE`, `PERMISSION_GROUP_UPDATE`, `PERMISSION_GROUP_DELETE`, `ASSIGN_USER_TO_PERMISSION_GROUP`, `UNASSIGN_USER_FROM_PERMISSION_GROUP`, plus legacy types (`LOGIN`, `SEARCH`, …). **UNKNOWN** excluded from list per `ActivityActionType.filterDropdownOptions()`. | TC-15 **Pass** (runtime) |

**Bugfix-1 (resolved):** Detail route restricted to numeric id (`@GetMapping("/{id:\\d+}")`) so `/action-types` is not captured as `{id}`. See `docs/requirements/20260330-activity-types-user-mgmt-permission-group-bugfix-1.md`.

#### Browser automation (cursor-ide-browser)

- **Tool**: Cursor IDE Browser MCP. **Base URL**: `http://localhost:3001`. **Viewport**: 1920×1080 after `browser_resize`.
- **Procedure**: Login (`userId` 20269999, dev seed password) → **활동 이력** → **액션 타입** combobox. **Note:** Immediately after navigation, the a11y snapshot can still show the short fallback list; after ~4s the client finishes `GET /api/activity-log/action-types` and options refresh (e.g. **23** options including 권한 그룹 관련 라벨).

| Item | Result | Notes |
|------|--------|--------|
| App shell / login / navigate to activity-log | **Pass** | Heading "사용자 활동 이력", search form visible. |
| TC-11 (dropdown = API canonical list, not only fallback) | **Pass** | After load, combobox options include e.g. "고급 검색", "권한 그룹 사용자 배정", "복호화 승인", … (23 total); matches server-driven taxonomy, not the initial short fallback-only list. |
| TC-10 / TC-14 (full E2E admin change → filter) | **Not executed** | Optional; automated TCs cover core behavior. |

#### TC coverage vs §3 (summary)

- **TC-01–TC-09, TC-12–TC-14, TC-16–TC-17**: **Pass** (automated `mvn test` / `npm test` per handoff).
- **TC-15 (GET action-types, auth)**: **Pass** (runtime curl + 401/200 behavior).
- **TC-11**: **Pass** (browser; allow brief wait for async option load).

#### Environment notes

- **Deployed backend JAR**: Re-verification required **`cd backend && mvn package -DskipTests`** (or equivalent) **before** `./scripts/dev-services.sh backend restart`. Without rebuild, the running process could still serve an older artifact and return 400 for `action-types`.

### Issues found and resolution

1. **CLOSED — GET `/api/activity-log/action-types` 400 (handler conflict)**  
   - **Doc**: `docs/requirements/20260330-activity-types-user-mgmt-permission-group-bugfix-1.md`  
   - **Scope**: backend  
   - **Resolution**: `getActivityLogDetail` mapped to `@GetMapping("/{id:\\d+}")`; QA re-verification passed (API + browser).

### Verification outcome

- **Step 5 status**: **PASSED** (re-verification). Commit performed per `.cursor/commands/commit-on-complete.md` when this doc was updated.

**Commands (reference):**

```bash
cd /Volumes/T7/dev/logmng_frontend/dev/backend && mvn test
cd /Volumes/T7/dev/logmng_frontend/dev/frontend && npm test -- --watchAll=false
cd /Volumes/T7/dev/logmng_frontend/dev/backend && mvn package -DskipTests && cd .. && ./scripts/dev-services.sh backend restart
curl -s http://localhost:9200/api/health
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:3001
```

## 6. Error remedy result (cause and action) — for error/bug fix requirements only

N/A (feature requirement).

---

## 7. Final version (Korean) — draft for stakeholders (finalize after verification)

### 최종 요약 (한국어)
- **요건 개요**: 웹 앱에서 수행되는 행위를 **활동 유형**으로 정의하고, **사용자 관리·권한 그룹** 설정 등 관리 행위까지 **활동 이력**에 남기며, 활동 이력 화면/API에서 **유형·기간·사용자·부서·IP** 등으로 조회할 수 있게 한다.
- **기대 결과**: 로그인·검색 등 기존 유형과 **관리/승인** 관련 유형이 **동일 저장소·필터**에서 일관되게 조회되며, 계약서·API·UI 옵션 목록이 **단일 기준**으로 맞춰진다.
- **검증 결과**: 2026-03-30 QA 재검증에서 버그픽스-1 반영 후 `GET /api/activity-log/action-types` 및 활동 이력 액션 타입 드롭다운(서버 옵션) 확인. §5 참고.

---

**Author**: Requirements (subagent)  
**Date**: 2026-03-30  
**Status**: Verification passed (Step 5 re-verification 2026-03-30; see §5)
