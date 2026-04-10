# 20260407 - External org replica, admin registration, AD login, no-permission modal

**Language**: §1, §2, §3 authored in **English** first. **Korean final section (§7)**: add after QA completes verification per `docs/workflow/DOCUMENT-LANGUAGE-POLICY.md`.

**Commit**: Commits closing this requirement must reference this document (e.g. `req 20260407-external-dept-employee-ad-login` or `docs/requirements/20260407-external-dept-employee-ad-login.md`).

---

## 1. User requirement

### Requirement description

The organization will **periodically replicate** real company **department and employee** data into this system (or a connected database). The product must evolve from **one-off manual** user entry toward flows that **align with replicated external** organizational data.

**User registration (administrative provisioning):** Authorized administrators (or a **designated role** — align with existing patterns: `is_system_admin` and/or access to **user management** screens per `AuthService.canAccessUserManagementView` and `UserController` guards; confirm with product) must be able to **search** replicated external department and employee tables and **register** selected persons into the application’s **`app_user`** store with **stable linkage** to external keys (e.g. external employee id, external department id, source system identifier) as applicable.

**Login (configurable mode):** The system must support **exactly one** authentication mode per deployment (or per Spring **profile**), selected via **`application.yml`** (and optional **`application-{profile}.yml`**) — see §2 for property naming and switching rules. The product must implement **two mutually exclusive** modes:

1. **Local / table-backed** — existing **`password_hash`** path (numeric `app_user.id` + password compared to stored hash; dev-style or legacy), **or**
2. **AD-based** — password verification against the corporate directory (bind or other agreed secure flow).

**Production expectation:** When **AD mode** is selected, end users authenticate against the directory (not reliance on `password_hash` alone). When **local mode** is selected (e.g. dev/test or explicitly approved environments), behavior follows the existing table-backed model. **Misconfiguration** (invalid mode, missing AD settings when AD is selected, etc.) must **fail closed** (no silent fallback to the other mode) — verify in §3.

**No application permission:** If the authenticated user **has no permission** in this system (not a system administrator and **no** allowed screens / empty effective access), the UI must show a **modal** with **exact** text:

`접근 권한이 없습니다. 사용을 위해 보안담당자에게 권한을 요청하시기 바랍니다.`

Then the user must be returned to the **login screen** with **session cleared** (server-side invalidation and client state cleared).

**Login entry URL:** The login page must be reachable via **an additional or alternate URL** (configurable route or reverse-proxy path) in addition to any existing entry point; **behavior must be identical** for all supported entry paths (same bundle, same auth flow).

**Sample data:** Provide **separate** tables for **external (replicated) department** and **external (replicated) employee** data with **names that clearly indicate** imported/replicated sources (distinct from canonical `department` / `app_user`). **User registration** must **search** these tables and create users **from search results** (not only free-text manual fields).

### User scenario

1. Operations runs **ETL or replication** on a schedule; `ext_*` (or equivalent) tables receive updated org and employee rows.
2. An **admin** opens **user registration / provisioning** UI, enters search criteria against **external employee** (and optionally **external department**) data, and receives a result list from replicated tables.
3. The admin selects one or more rows and **registers** them into **`app_user`**, with **external keys** stored or mapped per §2 so future logins can resolve identity.
4. A **registered user** opens the app (via **primary or alternate** login URL), enters credentials; the backend validates per the **configured login mode** — **AD** (or agreed secure flow) when `auth.login.mode` (or equivalent) selects directory auth, or **local `password_hash`** when local mode is selected — then establishes **app session** and loads permissions.
5. If the user **has no screens** granted, the app shows the **fixed-message modal**, then **logs out** and shows the **login** screen — **no** usable main application shell.
6. A **user with permissions** proceeds to the main shell as today.

### Expected outcome

- Replicated external org data is **modeled in dedicated tables** with **clear naming** and **search** support for admin registration.
- **Login mode** is **declared in Spring configuration** (`application.yml` / profile-specific YAML): operators must be able to choose **local** vs **AD** without code changes; **only one** mode is active at runtime for that deployment/profile.
- **AD-backed authentication** is available for production-style deployments when configuration selects AD mode; **local `password_hash`** remains available when configuration selects local mode (migration rules for existing dev users TBD with product).
- **Zero-permission** post-auth users see **only** the specified modal, then **login** again with **no** active session.
- **Multiple login URLs** resolve to the **same** SPA behavior and auth contract.
- **Server-side** enforcement prevents using APIs when the user has **no** effective permission, not UI-only checks.
- **Contract and specs** describe new APIs, auth shapes, and error codes; **skills** list in §2 is updated when the domain model changes.

**Note:** This requirement **does not** mandate **search/filter field alignment** with activity-log/statistics **user-block width** pattern (REQUIREMENTS-CHANGE-TARGET-CHECKLIST §2.4) unless product explicitly aligns admin registration search with those screens; admin registration UI should still follow `docs/design/forms-and-filters.md` and related design docs for **new** forms where applicable.

---

## 2. Design

### 2.1 Security review (PII / AD / access control)

Input merged from Security (readonly) review for requirement authoring.

- [x] Security review performed (initial, for §2)
- [x] Implementation verified against §2.1 after Step 4 + QA (smoke: provisioning access control, local login; AD production path not exercised in this run)

#### Risks

| Risk | Notes |
|------|--------|
| **PII in replicated tables** | Names, org structure, identifiers increase breach impact; minimize columns replicated; restrict who may query. |
| **AD/LDAP credentials** | Service account compromise; cleartext or weak TLS; misconfigured trust (MITM). |
| **Session** | Session fixation if not rotated on login; **authenticated but unauthorized** users must not reach protected APIs. |
| **Logging** | Passwords, bind secrets, verbose LDAP diagnostics, full external rows in logs; user enumeration via error messages. |
| **Operational** | ETL or tools with write access to replica tables enable tampering that looks like official org data. |

#### Acceptance criteria / recommendations

- **Never** persist end-user **AD passwords** in `app_user`, session stores, or logs. Authentication = successful directory bind or approved token flow; **no** copying AD password into `password_hash`.
- **LDAPS or LDAP + StartTLS** with certificate validation in production; **no** plain LDAP for production binds.
- **Dedicated service account** for directory bind with **least privilege** (minimal attributes); secrets **only** in environment / secrets management.
- **Application DB role**: **SELECT-only** on external replica tables at runtime; **INSERT/UPDATE/DELETE** only for **ETL/maintenance** principals.
- **Server-side** denial: APIs return **401/403** consistent with no permission; modal on frontend must match **session invalidation**.
- **Registration audit**: Log **actor**, **timestamp**, **target** identifiers and linkage keys; avoid full PII snapshots in application logs (structured audit per project standards).
- **Separation**: External replica data vs **`app_user`** credentials clearly separated; no dual storage of AD password alongside legacy hash (migration policy TBD).
- **Dual code paths, single active mode**: The application **may** ship with both local-password and AD integration in the same artifact; **only one** path must be **selected by configuration** at runtime. **Local password mode in production** must be **explicitly opted in** via configuration (e.g. a profile or explicit property — final keys in Contract step); **must not** default to local table auth in production without a deliberate operator choice. **Verify**: staging/production runbooks document which profile sets AD vs local.
- **Misconfiguration**: Invalid or ambiguous login-mode configuration must **fail closed** (startup failure or consistent **401**/refusal to authenticate — behavior defined in Contract/implementation) rather than silently picking a mode.

#### Recommendations (registration audit)

- Append-only or tamper-evident audit of **create/link** actions: admin actor, external keys, resulting `app_user.id`.

### Technical design

#### Codebase summary (investigation)

- **Auth today**: `POST /api/auth/login` with **`userId` (numeric `app_user.id`)** and **`password`**; `AuthService.login()` loads `app_user` by id and compares **`password` to `password_hash`** (dev may store plaintext in `password_hash`). **No LDAP, AD, OAuth2, or SAML** in the repository — **greenfield** for directory integration. This path is the **baseline for “local mode”** once a **configurable login mode** is introduced.
- **Session**: Cookie-based session after login; `GET /api/auth/check`, `GET /api/auth/me` expose `allowedScreenIds`, `isSystemAdmin`, `screenFunctions`, `selfContext`, etc. (see `docs/contract.md`).
- **User management API**: `UserController` exposes **`GET /api/users`** (list) and deprecated/removed role endpoints; **no `POST /api/users`** for create in current backend — provisioning from external search is **new** work.
- **Admin access pattern**: `requireUserManagementAccess` allows **`is_system_admin`** OR **`allowedScreenIds`** containing **`user-management`** or **`user-permission-hierarchy`** (see `AuthService.canAccessUserManagementView`).
- **Frontend**: `App.js` uses **conditional render** for `LoginForm` — **no React Router**. `index.js` mounts `App` only. After login, **`getFirstAllowedScreen`** may fall back to **`ORDERED_SCREEN_IDS[0]`** when allowed lists are empty — **undesired** for zero-permission users; this requirement replaces that with **modal + logout** (no main shell).
- **Canonical org data**: `department` and **`app_user.department_code`** FK exist in `schema_sys.sql`; replicated tables are **additional**, not a drop-in replacement for `department` unless product adds a separate sync requirement.

#### Problem analysis

1. **Identity split**: Corporate directory (authentication) vs **`app_user` + permission groups** (authorization) must be explicitly ordered: **AD success → resolve/link `app_user` → enforce screens**.
2. **Provisioning**: Registration must **query replicated** tables and **create** `app_user` rows + external key mapping; current codebase has **no** end-to-end API for create-from-external.
3. **Local password model vs AD**: Production deployments **must** select **AD mode** via configuration when directory authentication is required; **local mode** remains for dev/legacy paths **only when explicitly configured** (see §2.1). Migration from dev sample users needs an explicit policy (TBD).
4. **Single mode at runtime**: Configuration must **not** allow two active authenticators without a clear precedence; **invalid** mode settings must **fail closed**.
5. **Zero-permission UX**: Today’s shell may show **before** permission is meaningful; must **not** flash sidebar/main content for users with **no** allowed screens.
6. **Alternate URL**: SPA has **no** path routing; alternate URL likely needs **reverse-proxy** same `index.html`, **and/or** future **router** introduction — tradeoff in §2 (Architecture).

#### Solution approach

Structure by scope. **Vendor-specific** AD details (LDAP attribute names, DN patterns, AD FS vs Azure AD endpoints, certificate stores) are **TBD** unless discovered in a later codebase pass; keep integration behind **interfaces** so Backend can swap providers.

**Frontend:**

- After **successful login**, if **`isSystemAdmin` is false** and **`allowedScreenIds` is empty or missing**, **do not** render main shell: show **blocking modal** (prefer **MUI `Dialog`**, `theme.zIndex.modal` ≥ 1300 per UX input) with the **exact** Korean message; **single primary** action (e.g. 확인); **Escape/backdrop** either disabled or equivalent to primary; on action: **`POST /api/auth/logout`**, **`clearUserData`**, return to **login** view; use **history replace** where applicable so **Back** does not return to unauthorized state.
- **Avoid flash**: Do not paint sidebar/main until permission check completes (or use loading gate).
- **Alternate login URL**: Same bundle; ensure **runtime API base** and static assets work for **both** entry paths (env/`PUBLIC_URL` / proxy — Architecture); bookmarkable login path if router is introduced or hash documented.
- **Admin registration UI**: Search external employee (and department as needed), result list, **register** action calling new backend APIs; align new forms with `docs/design/forms-and-filters.md` where applicable.

**Backend:**

- **Login mode configuration (single source of truth):** **`application.yml`** and optional **`application-{profile}.yml`** (e.g. `application-dev.yml`, `application-prod.yml`) **must** be the **only** place operators set **which** authentication implementation runs for that deployment. **Placeholder property** (names **must** be finalized in the Contract step): e.g. **`auth.login.mode`** with values such as **`local`** (table-backed `password_hash` path) vs **`ad`** (directory bind or agreed secure flow) — **verify** enum/value names and any nested `auth.ad.*` / LDAP URLs in contract and sample YAML.
  - **Must**: **Exactly one** mode is **active** at runtime; the backend **must** route `POST /api/auth/login` (or successor) through **either** the existing local verifier **or** the directory integration — **not** both, and **not** ambiguous precedence.
  - **Must**: **Invalid** mode value, **missing** required AD settings when mode is `ad`, or other **misconfiguration** **fails closed** (startup failure and/or consistent authentication refusal per Contract — **verify** in §3).
  - **Switching** between local and AD: **default product rule** — **deploy-time / profile-based** only (edit YAML, use the correct Spring profile, **restart** or **redeploy**). **Hot-reload** of login mode without process restart is **out of scope** unless product explicitly requires it; if required later, add a separate requirement and Security review (credential caches, session invalidation).
- **AD authentication options** (Architecture input — choose one primary for implementation with product):
  - **Spring Security LDAP** (`LdapAuthenticationProvider` / bind): closest to replacing password verification; **vendor TBD**.
  - **Custom LDAP bind** in or behind `AuthService`: explicit control; higher risk if pooling/errors are hand-rolled.
  - **OAuth2/OIDC** (AD FS, Azure AD): **redirect** flows differ from current **POST JSON login** — larger contract/frontend change unless BFF exchanges code.
  - **SAML 2.0**: enterprise-common; heavier; **TBD**.
- **Recommended direction for minimal contract churn**: Prefer **LDAP bind** or **Spring Security LDAP** preserving **server session** after mapping to **`app_user`**; **OIDC/SAML** if SSO is mandatory — then document **new** endpoints or browser flow.
- **Mapping**: After directory success, resolve **`app_user`** by **`external_employee_id`** (and `source_system`) or agreed stable key; **fail closed** if no row when registration is required before first login (policy TBD).
- **APIs**: New **admin-only** endpoints to **search** `ext_*` tables and **create** `app_user` + mapping (or call existing internal services); enforce same admin rules as user management where applicable.
- **Authorization middleware**: Ensure **no** protected resource is callable with “authenticated but zero permission” except auth logout/check — return **403** where appropriate.

**DB:**

- Add **separate** tables for replicated **external department** and **external employee** (see DBA input): e.g. **`ext_department`**, **`ext_employee`** (or schema-qualified `ext_sample.*` vs prod); include **`source_system`**, **`imported_at`**, **`external_*` keys**; **UNIQUE** constraints for natural keys.
- **Canonical `department` / `app_user`**: Keep **`department`** as app FK target for `app_user.department_code`; registration may **map** replica dept to existing `department.code` or leave null per product rules.
- **Linkage**: Add **`external_employee_id`** (+ optional **`external_source_system`**) on `app_user` **or** a dedicated **`app_user_external_identity`** mapping table if replicas are volatile/truncated — DBA recommends mapping table when FK to volatile replica is unsafe.
- **Indexes**: btree on **employee number**, **name** (consider `pg_trgm` for fuzzy search), **external_dept_id**; partial indexes for **active** rows if applicable.
- **Grants**: App runtime role **SELECT-only** on `ext_*`; ETL role **write**; document in `setup.sh` / `check-db.sh`.

**Integration / operations:**

- **ETL** job ownership and schedule are **out of band** but must be referenced in ops docs when this requirement is implemented.
- **LDAP outage** (when **AD mode** is active): Fallback behavior (read-only error vs emergency profile) — **TBD** with product; **must not** silently fall back to **local** `password_hash` unless explicitly configured via a **separate** profile or product-approved break-glass procedure (document in ops). **Local mode** deployments use the **local** profile only.

#### Diagnostic phase (error/bug fix only)

*Not applicable — feature requirement.*

### Affected scopes and change targets (verification)

Per `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md`:

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|------------|-----------------------------------------------|
| Backend | Yes | [x] |
| Frontend (config UI + view screen) | Yes — admin registration + login modal + entry URL | [x] |
| DB | Yes | [x] |
| Contract / Spec | Yes | [x] |
| Cursor tools (skills, specs) | Yes — auth, API map, DB, UX | [x] |

**Pattern 3.2 (Permission / screen access):** Applies — backend checks, frontend menu/shell, contract, auth skills.

**Pattern §2.4 (search/filter UI consistency):** **Does not apply** to the same mandatory user-block width matrix unless product extends scope; admin search should still reference design docs for form layout.

### Cursor tool update targets

When the domain model changes (AD auth, `ext_*` tables, provisioning APIs, no-permission flow), implementing agents must update:

| Target | Reason |
|--------|--------|
| `.cursor/skills/auth-permission-domain/SKILL.md` | Login identifier, **`auth.login.mode`** (or equivalent), AD vs local, session contract |
| `.cursor/skills/api-permission-map/SKILL.md` | New admin search/register endpoints → permission checks |
| `.cursor/skills/db-domain/SKILL.md` | `ext_*` tables, grants, migrations |
| `.cursor/skills/ui-ux-domain/SKILL.md` | Login entry points, modal behavior if menu doc affected |
| `specs/*.spec.yaml` | New or `specs/external-identity-auth.spec.yaml` (name TBD in Contract step) |
| `docs/contract.md`, `docs/api-definition.md` | API shapes, auth flow, **config keys** for login mode (reference) |
| `backend/src/main/resources/application.yml`, `application-*.yml` | **Single source** for login mode — skills may reference for operator handoff |

Run `node scripts/generate-treemap.js` if delegation or workflow display files change per project rules.

### Planned change file list (expected change targets)

**(Planned at authoring. Implementing agent (Step 4) confirms or amends when implementation is complete.)**

#### Frontend

- `frontend/src/App.js` — Gate authenticated shell on non-empty permission or system admin; **no-permission modal** + logout; prevent shell flash.
- `frontend/src/components/LoginForm.js` — Adjust if login request/response shape changes for AD or multi-mode auth (TBD).
- New component (e.g. `NoPermissionDialog.js`) — Exact modal copy, primary action, a11y per UX.
- `frontend/src/components/UserManagement/*` (and/or new wizard) — **Search external** + **register** flows.
- `frontend/src/utils/security.js` / client state — Ensure clear on no-permission path.
- `frontend/src/config/runtimeApi.js`, `frontend/package.json` (`homepage` / `PUBLIC_URL`) — If alternate base path required.
- `frontend/public/index.html` — Base href / static path if required by ops.
- Tests: `App.test.js`, `LoginForm.test.js`, UserManagement tests, new dialog tests.

#### Backend

**(Implemented Step 4 — actual files)**

- `backend/pom.xml` — `spring-boot-starter-data-ldap` for LDAP bind (`auth.login.mode=ad`).
- `backend/src/main/resources/application.yml` — **`auth.login.mode`**, **`auth.login.allow-local-in-production`**, **`auth.ad.*`**, **`auth.provisioning.default-source-system`** (env overrides).
- `backend/src/main/resources/application-dev.yml` — dev profile: **`auth.login.mode: local`** by default.
- `backend/src/main/java/com/logmng/config/AuthProperties.java` — `@ConfigurationProperties(prefix = "auth")`.
- `backend/src/main/java/com/logmng/config/AuthConfigurationValidator.java` — fail-closed startup validation (invalid mode, local-in-prod, missing `auth.ad.*` when mode=ad).
- `backend/src/main/java/com/logmng/config/LdapClientConfig.java` — `LdapContextSource` / `LdapTemplate` when mode=ad.
- `backend/src/main/java/com/logmng/config/WebConfig.java` — `@EnableConfigurationProperties(AuthProperties.class)`.
- `backend/src/main/java/com/logmng/service/LdapBindAuthenticator.java` — directory bind via `LdapTemplate.authenticate`.
- `backend/src/main/java/com/logmng/service/ExternalIdentityService.java` — principal → `app_user_id` via `ext_employee` + `app_user_external_identity`.
- `backend/src/main/java/com/logmng/service/AuthService.java` — **`loginLocal`** / **`loginAd`** branches; shared **`buildLoginResponse`**.
- `backend/src/main/java/com/logmng/service/ProvisioningService.java` — search `ext_*`, **`provisionFromExternalEmployee`**.
- `backend/src/main/java/com/logmng/controller/ProvisioningController.java` — `/api/provisioning/*`.
- `backend/src/main/java/com/logmng/dto/request/LoginRequest.java` — optional **`userId`** / **`principal`** (mode-dependent).
- `backend/src/main/java/com/logmng/dto/request/ExternalEmployeeSearchRequest.java`, `ExternalDepartmentSearchRequest.java`, `ProvisionFromExternalEmployeeRequest.java`.
- `backend/src/main/java/com/logmng/dto/response/*` — provisioning/search result DTOs (`ExternalEmployeeItemResponse`, `ExternalDepartmentItemResponse`, `PaginationResponse`, `ExternalEmployeeSearchResult`, `ExternalDepartmentSearchResult`, `ProvisionUserResultResponse`).
- `backend/src/main/java/com/logmng/config/ScreenAccessInterceptor.java` — **zero-permission** authenticated users → **403** on all non-excluded `/api/**` (except auth already excluded).
- `backend/src/main/java/com/logmng/exception/CustomException.java` — **`conflict`**, **`serviceUnavailable`**.
- Tests: `AuthConfigurationValidatorTest.java`, `ScreenAccessInterceptorTest.java` (zero-perm), plus updated stubs for `AuthService` constructor; all `backend/src/test/java/...` stubs adjusted for new **`AuthService`** dependencies.

#### DB

- `backend/src/main/resources/db/schema_sys.sql` (and/or new migration files) — `ext_department`, `ext_employee` (or agreed names), optional **mapping** table; **`app_user`** columns if product chooses direct linkage.
- `backend/src/main/resources/db/migrate-*.sql` — Incremental deploy.
- `backend/src/main/resources/db/init-data.sql` (or sample seed) — Sample rows for **external** tables.
- `backend/src/main/resources/db/setup.sh`, `check-db.sh` — Grants and validation.

#### Contract / spec

- `docs/contract.md` — Auth, provisioning, external tables summary; **reference** config property names for **`auth.login.mode`** (or equivalent) for traceability.
- `docs/api-definition.md` — New endpoints and error codes.
- `specs/external-identity-auth.spec.yaml` (or split specs) — Provisioning + auth; TBD filename in Contract step.

#### Documentation / operations (when requirement is delivered)

- Ops or deployment docs (path TBD in Documentation step) — **Must** document **which Spring profile** and **`application-*.yml`** set **local** vs **AD** mode per environment; **warn** against enabling **local** password mode in production without explicit approval.

---

## 3. Test approach

### Test case list (required)

**api-permission-map completeness**: Include TCs that trace **admin provisioning** and **auth** endpoints to **permission checks** and denial behavior (`403`/`401`). Include regression: **zero-permission** user cannot use protected APIs except logout/check as designed.

**Login mode (local vs AD):** Backend/Integration TCs **must** cover **both** configured modes where applicable: **`local`** uses the existing **`password_hash`** verification path; **`ad`** uses directory bind (or test double). **Misconfiguration** (invalid `auth.login.mode` or equivalent, **AD** mode without required directory settings) **must** be covered with **fail-closed** expectations. Tests **may** use Spring **`@ActiveProfiles`**, profile-specific **`application-*.yml`** under `src/test/resources`, or other project-supported test configuration — **verify** behavior matches **deploy/profile** semantics (no reliance on undeclared hot-reload).

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification |
|----|-------|------|------------------------------|-----------------|--------------|
| TC-B01 | Backend | Normal | **`auth.login.mode`** (or equivalent) selects **AD**; valid AD bind (or test double) for user with `app_user` row and permissions | Session created; `GET /api/auth/me` returns user payload | Unit / integration (`mvn test`) |
| TC-B02 | Backend | Exception | **AD** mode; AD bind fails (bad password) | **401**; no session cookie | Unit / integration |
| TC-B03 | Backend | Normal | User authenticated (either mode), `is_system_admin=false`, empty `allowedScreenIds` | Response shape supports frontend modal; protected APIs **403** (or agreed contract) | Unit / integration |
| TC-B04 | Backend | Normal | Admin session searches `ext_employee` by name/employee number | 200; result rows from replica only | Unit / integration |
| TC-B05 | Backend | Exception | Non-admin session calls external search API | **403** | Unit / integration |
| TC-B06 | Backend | Normal | Admin registers from `ext_employee` row | New `app_user` + mapping; no AD password stored | Unit / integration |
| TC-B07 | Backend | Edge | Register duplicate external key | Idempotent behavior or **409**/clear error per contract | Unit / integration |
| TC-B08 | Backend | Normal | **`local`** mode; valid `app_user` + password matches **`password_hash`** path | Session created via **local** verifier; directory bind **not** invoked (mock/spy **verify**) | Unit / integration |
| TC-B09 | Backend | Normal | **`ad`** mode; valid directory authentication + mapped `app_user` | Session created; **no** AD password persisted; local hash path **not** used for verification | Unit / integration |
| TC-B10 | Backend | Exception | Invalid **`auth.login.mode`** value, **or** **ad** mode with **missing/incomplete** AD/LDAP configuration | **Fail closed**: process refuses to start **or** login consistently refused per Contract; **no** silent fallback to the other mode | Unit / integration |
| TC-B11 | Backend | Edge | Test contexts with **different** active profiles / YAML (e.g. **local** vs **ad** fixture) | At most **one** authentication path effective per context; switching requires **reload** of test context / profile change — aligns with **deploy/profile** switching (not hot-reload in production unless separately specified) | Unit / integration |
| TC-F01 | Frontend | Normal | Login success with empty `allowedScreenIds`, non-admin | Modal shows **exact** Korean text; single primary control | Jest (`npm test`) |
| TC-F02 | Frontend | Normal | User confirms no-permission modal | Logout invoked; client cleared; login view shown | Jest |
| TC-F03 | Frontend | Edge | No flash of `AppSidebar`/main before modal | Snapshot or DOM order test | Jest |
| TC-F04 | Frontend | Normal | Admin registration search + select + submit | Calls backend; success feedback | Jest / RTL |
| TC-D01 | DB | Normal | App DB role runs SELECT on `ext_*` | Success | `check-db` / migration test |
| TC-D02 | DB | Exception | App role attempts INSERT on `ext_*` | Denied (or migration-only role succeeds) | SQL/CI |
| TC-I01 | Integration | Normal | Alternate login URL (per deploy config) | Same login behavior as primary | Manual / browser / curl |
| TC-I02 | Integration | Normal | End-to-end: admin registers test user → user logs in using the **active login mode** (e.g. AD test harness when **`auth.login.mode`** selects **ad**; **local** `password_hash` path when **local** profile/mode) | Session + permissions as expected; **verify** behavior matches **declared** YAML/profile | Manual / scripted |
| TC-I03 | Integration | Exception | User with no permission completes login | Modal → login screen; **session invalid** on retry to protected API | Manual / browser |
| TC-I04 | Integration | Exception | **Misconfiguration** in deploy/test (invalid mode or **ad** without directory settings) | **Fail closed** per TC-B10 expectations in a running environment (no usable accidental **local** login in **ad**-intended deploy) | Manual / scripted / smoke |

### Test scenarios

#### Scenario 1: Zero-permission post-login

1. Provision `app_user` with **no** permission groups and not system admin.
2. Authenticate via AD (or test harness).
3. Verify modal text **exactly**, then login screen, no main shell.

#### Scenario 2: Admin registration from replica

1. Load sample `ext_department` / `ext_employee` rows.
2. Admin searches and registers a new user.
3. Verify `app_user` linkage and that new user can authenticate per policy.

#### Scenario 3: Configurable login mode (local vs AD)

1. With **`local`** mode (or **`dev`** profile) active, authenticate using **`app_user.id` + password** against **`password_hash`**; **verify** session and **no** directory bind.
2. With **`ad`** mode active, authenticate using directory credentials; **verify** mapping to `app_user` and **no** AD password persisted.
3. **Verify** switching modes requires **different** profile/YAML + **restart** (or new deployment), consistent with §2 — not a runtime toggle unless product adds a separate requirement.

### Test data

- Provide **executable SQL** (INSERT) for sample **`ext_department`**, **`ext_employee`** rows and at least one **mappable** `department.code` for FK tests.
- Document AD test doubles or mock LDAP for CI (TBD in implementation).
- Provide or reference **sample** `application-*.yml` (or test resources) illustrating **`auth.login.mode`** (or equivalent) for **local** vs **ad** test fixtures — **verify** alignment with TC-B08–TC-B11.

### Test environment

- Frontend: `http://localhost:3001` (per project)
- Backend: `http://localhost:9200`
- Database: PostgreSQL per `docs/contract.md`

### 3.5 Browser automation verification (optional)

- **Applicable TCs**: TC-I01, TC-I02, TC-I03, TC-I04 (as feasible for environment smoke).
- **Procedure**: Navigate to primary and alternate login URLs; perform login; snapshot for modal text and post-logout login view.
- **Reference**: `docs/workflow/BROWSER-AUTOMATION-VERIFICATION-POLICY.md`.

---

## 4. Checklist

### Frontend verification

- [x] API parameters validated (Jest; browser smoke: login shell)
- [x] UI behavior confirmed (modal, no shell flash) — covered by unit tests; browser: login form only in this run
- [x] Error handling verified (tests + provisioning 401/403 smoke)

### Backend verification

- [x] API test cases written and run (including **login mode** / **fail closed** — `mvn test` pass)
- [ ] Logs checked (no secrets / no AD password) — spot-check recommended in ops
- [ ] Performance checked if search on large replica (optional)

### Integration

- [x] Key API smoke (local login, provisioning access control) — 2026-04-07
- [ ] Full end-to-end (admin register → new user login per mode) — follow-up when AD harness ready
- [ ] Edge cases (duplicate register, AD down — TBD)

### Documentation

- [x] Requirement doc §5 updated (QA)
- [ ] Code comments added (if applicable)

---

## 5. Test results

### Test run date

- **2026-04-07** (verification run; dates per `.cursor/CURRENT-DATE-CONVENTION.md`)

### Test results

#### Automated (unit / integration)

| Command | Result | Notes |
|---------|--------|--------|
| `cd backend && mvn test` | **Pass** (exit 0) | 2026-04-07 — includes `AuthConfigurationValidatorTest`, `ScreenAccessInterceptorTest`, auth/provisioning-related tests |
| `cd frontend && npm test -- --watchAll=false` | **Pass** (exit 0) | 2026-04-07 — 30 suites, 163 tests |

#### Verification (`docs/workflow/verify.md`)

| Step | Command / check | Result |
|------|-----------------|--------|
| Restart | `./scripts/dev-services.sh all restart` | **Pass** (exit 0) |
| Backend health | `curl -s http://localhost:9200/api/health` | **200**, JSON `success:true` |
| Frontend | `curl -s -o /dev/null -w "%{http_code}" http://localhost:3001` | **200** |
| DB (app) | `curl -s http://localhost:9200/api/db/test` | **`data.connected === true`** |

**Operational note:** After restart, `POST /api/provisioning/...` initially returned **404** until the backend was rebuilt with `cd backend && mvn package -DskipTests` and `./scripts/dev-services.sh backend restart`. The dev script starts an existing `target/*.jar` without rebuilding; operators must **build before restart** when controller code changed.

#### API smoke (local mode, post-rebuild JAR)

| Check | Result |
|-------|--------|
| `GET /api/auth/config` | **404** — not implemented; frontend uses `authConfigService` fallback to `REACT_APP_AUTH_LOGIN_MODE` (documented as follow-up) |
| `POST /api/provisioning/external-employees/search` (no session) | **401** `UNAUTHORIZED` |
| Same (session **user2** — no user-management / hierarchy screens) | **403** `FORBIDDEN` |
| Same (session **admin** or **user1** with UM access) | **200**, sample `ext_employee` rows in `data.items` |
| `POST /api/auth/login` with `{"userId":20269999,"password":"admin123"}` (local) | **200** |

#### DB bootstrap (setup.sh)

- **Not executed** in this QA run (DB was already running via `dev-services.sh`). If `setup.sh` / `init-data` is run fresh, **legacy `app_user_permission_group` init-data issue may yield exit code 3** — track separately; not a blocker for this verification.

#### Browser (step 3.5, frontend scope)

- **Tool:** cursor-ide-browser (`browser_navigate` → `browser_snapshot`, wait ~3s for shell).
- **URL:** `http://localhost:3001/`
- **Result:** **Pass** — page title "로그 관리 시스템"; login form visible (사용자 ID, 비밀번호, 로그인). TC-I01 partial: primary entry loads; alternate URL / full E2E login flows not repeated in this run.

#### Gaps / follow-ups (not blocking §5 pass for implemented scope)

| Gap | Suggested owner | Action |
|-----|-----------------|--------|
| **`GET /api/auth/config` missing** (404) | **Backend** + **Contract** (`docs/contract.md`, `docs/api-definition.md`) | Add endpoint returning `auth.login.mode` (and related safe flags) so the SPA does not rely only on `REACT_APP_AUTH_LOGIN_MODE`. |
| **TC-I01 alternate login URL**, **TC-I02/I03/I04** full manual/browser | QA / product | Run when deploy URLs and AD test harness are available. |

---

## 6. Error remedy result (cause and action)

*Not applicable unless a bugfix child is opened.*

---

## 7. Final version (Korean)

*Add after verification per `docs/workflow/DOCUMENT-LANGUAGE-POLICY.md`.*

---

**Author**: Requirements (subagent)  
**Date**: 2026-04-07  
**Status**: QA verification recorded (2026-04-07); follow-ups in §5
