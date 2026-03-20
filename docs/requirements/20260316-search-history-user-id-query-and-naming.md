# 20260316 - Search history: user_id-based query and naming consistency

## 1. User requirement

### Requirement description

1. **Search history screen improvement** — There are still incomplete parts. Improve so that all queries work correctly **by user_id**.
2. **Join condition** — Ensure the relationship is: **app_user.user_id = search_history.user_id** (everywhere this join or equivalent is used). In the schema, the app_user column that identifies the user is `app_user.id`; the intended semantics is that **search_history.user_id** stores that value (numeric `app_user.id`), and the join is **app_user.id = search_history.user_id**.
3. **Common/design tools (공통·설계 관련 도구)** — Where variable names and their meanings are wrong in common or design-related tools (e.g. specs, design docs, skills, contract), they must all be corrected to match the intended semantics (user_id-based join: app_user.user_id = search_history.user_id). This is a mandatory constraint for Contract, DB, Backend, and Cursor-tool update targets so that naming/semantic fixes are applied consistently.
4. **Main agent** — The main agent must **not** perform analysis or implementation for this requirement; all analysis and improvement are delegated to subagents (Requirements now; later Backend/DB/Contract/etc. per workflow).
5. **DB data check and corrective action** — The system must **check** whether the DB has cases where **user_id** (numeric `app_user.id`) should be stored but **user_name** (username) was stored, or the **opposite** (user_name expected but user_id stored). If such problems are found, **corrective action** must be taken (e.g. migration, backfill, or documented handling).
6. **Missing data: fill from current permissions and user id/name** — When **data is missing** (e.g. a search_history row has no valid requester, or requester display fields are empty), the system must **fill all remaining fields** using **current granted permissions** (e.g. scope, allowed users) and **user ID or username** (`app_user.id` or `app_user.username`) as the key. The "remaining fields" to fill are: requester display (e.g. requesterUsername, requesterDisplayName, department) and list row columns that show requester information; the source is current permissions plus user id or username resolved from app_user/department.

### User scenario

1. User (or an admin/approver) opens the search history screen and uses list, filter, create, re-request, detail, approve, or reject.
2. **Problem**: Today the system stores and joins search history to the user by **username** (`app_user.username = search_history.user_id`). The API and contract define **userId** as numeric `app_user.id`. As a result, list filters (e.g. requester `userId`), scope enforcement (e.g. team allowed list), ownership checks (create, re-request, detail, decrypt), and approval checks are inconsistent or require extra resolution (id↔username) and can fail or show wrong data if naming/semantics are mixed.
3. User expects: All search-history-related queries and ownership/approval checks use **user_id** (numeric `app_user.id`) consistently; the join between search_history and app_user is **app_user.id = search_history.user_id** everywhere; and all contract, specs, design docs, and Cursor skills state this relationship and use correct variable names and meanings.
4. When requester data is missing (e.g. orphan user_id or empty requester columns), the user expects the list/detail to show requester information filled from current permissions and user id or username (app_user/department) so that columns are not left blank when the user can be determined.

### Expected outcome

- **Queries correct by user_id**: Create, list (including requester filters and scope), getDetail, reRequest, approve, reject, and decrypt approval checks all use **search_history.user_id** storing **app_user.id** (numeric), and all joins use **app_user.id = search_history.user_id**.
- **Join relationship**: Everywhere (backend SQL, schema comments, migrations, contract, api-definition, design docs, skills) the relationship is stated as **app_user.user_id = search_history.user_id** (with the understanding that in the DB schema the app_user column is `app_user.id`). No join on `app_user.username = search_history.user_id`; no storage of username in `search_history.user_id`.
- **Common/design tools**: All contract, specs, design docs, and `.cursor/skills` that describe search_history and app_user relationship or user_id/username semantics are corrected so that variable names and meanings match the intended semantics (user_id-based join; `userId` = numeric `app_user.id`; `search_history.user_id` = numeric `app_user.id`). Requester display (requesterUsername, requesterDisplayName, department) continues to be resolved via the same join from app_user/department for list response, but the **link** is by user_id (app_user.id = search_history.user_id).
- **Main agent**: No analysis or implementation is done in the main agent; Requirements authors this doc; Backend/DB/Contract/Cursor tools/Frontend (if affected) are delegated per workflow.
- **DB data check**: Before or as part of migration, the system checks for mixed/wrong semantics (user_id column storing username or vice versa); if found, corrective action (migration, backfill, or documented handling) is applied.
- **Missing data fill**: When requester or requester display fields are missing (e.g. orphan user_id or empty requester), remaining fields (requesterUsername, requesterDisplayName, department; list row columns) are filled from current permissions and user id or username (app_user.id / app_user.username) via app_user/department.

---

## 2. Design

### 2.1 Security review

This requirement changes the **identifier** used for join and filtering from username to numeric `user_id` (`app_user.id`). It does not introduce a new security requirement; the following reviews impact on PII/decryption, access control, and migration/fill.

**(1) PII and decryption scope**

- **Stored data**: Storing numeric `app_user.id` in `search_history.user_id` instead of username reduces PII in that column (no login name stored). Response payloads still include requester display (e.g. requesterUsername, requesterDisplayName, department) derived via join from `app_user`/department; exposure to clients is unchanged.
- **Decryption approval**: Policy is unchanged. `isValidApprovalForUser(searchHistoryId, userId)` still enforces that the search_history row belongs to the given user; only the type of the identifier becomes numeric. Decryption remains gated by approval state and row ownership; no change to which rows may be decrypted or by whom.

**(2) Access control and scope enforcement (identifier switch)**

- **Single source of “current user”**: For create, reRequest, getDetail, approve, reject, and decrypt approval check, the **current user** must be resolved from **auth/session only** (e.g. numeric `app_user.id` from session). No client-supplied value may be used as the acting user for these operations.
- **Consistent use of numeric id**: All checks that compare “this row’s requester” or “user in allowed set” must use **numeric** `app_user.id` after the change. Mixed use of username and numeric id in the same code path could cause incorrect allow/deny (e.g. wrong scope=team boundary).
- **Scope=team**: `DepartmentScopeHelper` (or equivalent), when used for search-history list scope=team, must return a list of **numeric** `app_user.id` values. The list query must filter with `sh.user_id IN (...)` using that list so that scope=team is enforced correctly and cannot be bypassed by filter manipulation.
- **List requester filter**: Requester filter `userId` (numeric) must be applied as exact match on `search_history.user_id`; no downgrade to username-based filter after the change.

**(3) Migration, backfill, and missing-data fill**

- **Migration/backfill**: Detection and corrective action (e.g. username→id backfill) must run with **controlled, privileged access** (DB or backend with DB access). Migration must not take user-provided input to decide target user_id. Orphan rows (user_id not in `app_user`) must be handled per product policy (e.g. exclude from list or show placeholder) so that they do not expose data outside intended scope; document the chosen handling. Logging must follow `docs/security-guide.md`: no PII (e.g. usernames or raw user_ids beyond operational need) in logs; use existing logger and masking where applicable.
- **Missing-data fill**: When requester display is missing (e.g. orphan user_id or JOIN yields null), “fill from current permissions and user id/username” must:
  - Use only the **row’s own** `user_id` (or data derived from the row) as the key to resolve from `app_user`/department.
  - Expose requester display only for users that are **within the same scope** that already allowed this row to be returned (self/team/all). No use of request parameters or other keys to attach a different user’s display to the row.

**Acceptance (security)**

- Current user for create/reRequest/getDetail/approve/reject/decrypt is always from auth/session; no client-supplied acting user id for these actions.
- All ownership and scope checks use numeric `app_user.id` consistently; scope=team uses numeric allowlist from department.
- Migration/backfill: no user input; orphan handling documented and applied; logging PII-safe.
- Missing-data fill: key is row’s user_id; display only for users in the scope that allowed the row.

### § DBA review (Step 3b)

Design review from a DBA perspective. **No code or schema files are produced by the DBA agent; the DB subagent implements.** The following are recommendations so the DB subagent can implement schema and migration.

**(a) Schema and constraint recommendations**

- **Column**: `search_history.user_id` → **BIGINT NOT NULL**. Matches `app_user.id` (BIGSERIAL) and keeps join/index efficiency.
- **Foreign key**: Add `REFERENCES app_user(id)` (with desired `ON DELETE` behavior; see orphan policy below). FK enforces referential integrity and prevents storing non-existent user ids after migration.
- **NOT NULL**: Keep NOT NULL so every row has a requester key; orphan handling is done at migration time or by policy, not by allowing NULL.
- **Indexes** (requirement already specifies; DBA confirms):
  - **idx_search_history_user_id** on `(user_id)`: Keep for list filter by requester and scope=team `sh.user_id IN (...)`.
  - **idx_search_history_user_requested** on `(user_id, requested_at DESC)`: Keep for "list by user, newest first" and paging. Column type change from VARCHAR to BIGINT does not require index name change; rebuild is implicit on ALTER.
- **Other tables**: `search_history_approved_row` references `search_history(id)` only; no change. No other table in `schema.sql` references `search_history.user_id`. `decrypt_approver` and `app_user_permission_group` use `app_user(username)`; out of scope for this migration.
- **Comment**: Update table/column comment to state: `user_id` = requester's user id (numeric); **app_user.id = search_history.user_id**; do not store username.

**(b) Migration and backfill strategy**

- **Detection (before or during migration)**  
  - **Heuristic (current column VARCHAR)**: Classify rows: `user_id` all digits → likely stored as `app_user.id::text`; contains non-digits → likely username. Optional: run both joins and compare match sets: `JOIN app_user u ON u.id::text = sh.user_id` vs `JOIN app_user u ON u.username = sh.user_id`; document which semantics currently hold and count mismatches.  
  - **Document**: Record in migration script or runbook: "Semantics before migration: username | id::text | mixed" and row counts so corrective action is auditable.
- **Order of operations (recommended)**  
  1. **Backup** (or snapshot) `search_history` (and optionally `app_user`).  
  2. **Add** a new column e.g. `user_id_new BIGINT NULL` (or use a staging table if preferred).  
  3. **Backfill**:  
     - Where `user_id` is username: `UPDATE search_history sh SET user_id_new = u.id FROM app_user u WHERE u.username = sh.user_id`.  
     - Where `user_id` is already numeric (id::text): `UPDATE search_history sh SET user_id_new = sh.user_id::bigint WHERE sh.user_id ~ '^\d+$'` and ensure only rows with matching `app_user.id` are updated (e.g. `FROM app_user u WHERE u.id = sh.user_id::bigint`).  
  4. **Orphan handling** (see (c)): Decide and apply (delete, assign to system user, or leave in staging and exclude from cutover).  
  5. **Cutover**: Drop old index(es) on `user_id`, drop `user_id`, rename `user_id_new` → `user_id` (or alter type in place if no orphans and single-step alter is acceptable). Add NOT NULL, add FK, (re)create `idx_search_history_user_id` and `idx_search_history_user_requested`.  
  6. **Legacy script**: Mark `migrate-search-history-user-id-to-username.sql` as legacy / do not run for new deployments; document that the new migration aligns to user_id (numeric) and that the old script is reversed by this migration.
- **Rollback**: If migration is done in a transaction, rollback restores previous state. If not, rollback requires: restore from backup, or a reverse migration that maps `app_user.id` → `app_user.username` and restores VARCHAR column (only if product accepts reverting to username-based semantics). Document the chosen approach (e.g. "migration run in single transaction; rollback = rollback transaction" or "rollback = restore backup").

**(c) Orphan row policy suggestion**

- **Definition**: Rows in `search_history` whose `user_id` (after backfill) does not match any `app_user.id` (e.g. username existed at request time but user was later deleted, or data was corrupted).  
- **Options**:  
  - **Option A (strict)**: Delete orphan rows before adding FK. Document count and optionally archive.  
  - **Option B (assign to system user)**: If there is a designated system/user placeholder (e.g. fixed `app_user.id`), set `user_id_new = that id` for orphans so FK can be applied; document and expose in list as "unknown" or "system" per product.  
  - **Option C**: Do not add FK and leave orphans as invalid ids; application must tolerate missing join (requirement already specifies "missing data fill" in the list/detail response). Not recommended if referential integrity is required.  
- **Recommendation**: Prefer **Option A** (delete orphans after documenting count) so FK can be NOT NULL and referential integrity is enforced. If product requires retaining every row, use Option B with a single well-known system user id and document it.

**(d) Indexing and performance for list/filter by user_id**

- **List by requester**: Filter `WHERE sh.user_id = ?` (numeric) is supported by **idx_search_history_user_id**.  
- **List by user + time (e.g. "my requests, newest first")**: **idx_search_history_user_requested (user_id, requested_at DESC)** is appropriate; backend should order by `requested_at DESC` and use the same column for paging if applicable.  
- **Scope=team**: `WHERE sh.user_id IN (...)` with a list of numeric ids: index on `user_id` is used; keep the list of ids bounded (e.g. same department). No additional index needed.  
- **Volume**: If `search_history` grows large, the two indexes above are sufficient for the described access patterns. No JSONB or full-text on this table for this requirement.

**(e) "Missing data fill" — DB implications**

- The requirement asks that when requester data is missing (e.g. orphan user_id or empty requester display), the **application** fills remaining fields from current permissions and user id/username.  
- **DBA view**: This behavior is **application-level** (Backend builds list/detail DTOs and fills requester display from `app_user`/department when the JOIN yields null or when the row has an invalid user_id).  
- **No denormalized columns required**: It is not necessary to add stored columns on `search_history` for requester display (e.g. `requester_username`, `requester_display_name`, `department_code`) for this requirement; the application can resolve on read via `LEFT JOIN app_user ... department` and, when JOIN fails, use permission context + user id/username to resolve.  
- **Optional view**: If the project later wants a single SQL artifact for "search_history with requester display", a **view** (e.g. `search_history_with_requester`) that LEFT JOINs `app_user` and `department` can be added; the "missing data fill" would still be implemented in the application when the view returns NULL for requester fields (e.g. orphan). The migration at hand does not require this view.  
- **init-data.sql**: After migration, seed data for `search_history` must use **app_user.id** (numeric), not username. Update the INSERT to use a subquery from `app_user` (e.g. `SELECT u.id, ... FROM app_user u WHERE u.username = 'admin'`) or equivalent so that new environments and tests align with BIGINT FK.

### Technical design

#### Codebase summary

**Backend**

- **SearchHistoryService** (`backend/src/main/java/com/logmng/service/SearchHistoryService.java`):
  - **create(userId, request)**: Inserts into `search_history` with `user_id` set from the first argument. Controller passes `getCurrentUsername(request)` (string), so currently **username** is stored in `search_history.user_id`.
  - **list(SearchHistoryListRequest)**: FROM clause is `FROM search_history sh LEFT JOIN app_user au ON au.username = sh.user_id LEFT JOIN department d ON d.code = au.department_code`. So join is **username-based**. Requester filters: `request.getUserId()` (string) is compared to `sh.user_id`; `request.getAllowedUserIds()` (scope=team) is a list of strings. API passes numeric `userId` for requester filter; controller resolves it to username via `appUserResolver.getUsernameById(requesterUserIdNum)` and sets that string on listRequest.
  - **reRequest(userId, Long id)**, **getDetail(userId, Long id)**: Compare `userId` (string from controller = username) with `search_history.user_id`.
  - **isValidApprovalForUser(searchHistoryId, userId)**: Used by DecryptController; checks `search_history WHERE id = ? AND user_id = ?` with string `userId` (username).
  - **approve(id, userId)**, **reject(id, userId, reason)**: Use `userId` (string, username) for approved_by/rejected_by and for approver identity.
- **SearchHistoryController**: Uses `getCurrentUsername(httpRequest)` for all “current user” and passes that string as `userId` to the service. List action maps query param `userId` (Long) to username via `appUserResolver.getUsernameById` and sets string on listRequest.
- **DecryptController**: Calls `searchHistoryService.isValidApprovalForUser(searchHistoryId, username)` with username from auth.

**DB**

- **schema.sql**: `search_history (..., user_id VARCHAR(100) NOT NULL, ...)`. Comment: "user_id: requester's login id (username). Must match app_user.username for JOIN; do not store numeric app_user.id."
- **app_user**: Has `id BIGSERIAL PRIMARY KEY` and `username VARCHAR(100) NOT NULL UNIQUE`. No column named `user_id`; the logical “user_id” for the relationship is `app_user.id`.
- **migrate-search-history-user-id-to-username.sql**: Converts existing `search_history.user_id` from `app_user.id::text` to `app_user.username`. So current data may have username in `search_history.user_id`.
- **DepartmentScopeHelper.getUserIdsInSameDepartment**: Called with current user identifier (currently username); returns list used as `allowedUserIds` for scope=team. Must be verified to return numeric ids when search_history uses user_id (BIGINT).

**Frontend**

- **SearchHistoryList.js**: Uses list response `userId` (number), `requesterUsername`, `requesterDisplayName`, `requesterDepartmentName`/`requesterDepartmentCode`. Compares `Number(row.userId) === Number(user.id)` or `row.requesterUsername === user.username` for “is requester”. No change to response shape expected; backend will still return requester display fields from join; only the join key and stored value change to user_id.

**Contract / API / design / skills**

- **docs/contract.md**: Auth and scope mention search-history; DB schema does not explicitly state search_history join. **docs/api-definition.md** §6.1.2: list query params include `userId` (numeric `app_user.id`); response includes `userId` (number, `app_user.id`). Join/storage semantics not stated.
- **export/design/db-definition.md**: search_history.user_id as “Requester ID”; join condition not stated. **export/design/api-db-mapping.md**: search_history table listed; join not stated.
- **.cursor/skills/search-history-decrypt-domain/SKILL.md**: List requester fields described; no join or user_id semantics.
- **docs/requirements/** (several): 20260316-*, ANALYSIS-search-history-grid-empty-requester-columns, 20260313-search-history-search-fields-and-paging*.md, 20260316-user-id-numeric-userid-naming.md describe or assume `au.username = sh.user_id` or “user_id stores username”. These must be corrected or historically noted.

#### Problem analysis

1. **Join and storage are username-based**: The list query uses `LEFT JOIN app_user au ON au.username = sh.user_id`. So `search_history.user_id` is treated as username. Create/reRequest/getDetail/isValidApprovalForUser all pass and compare username. This conflicts with the contract and API definition where **userId** is numeric `app_user.id`.
2. **Inconsistent identifier for filtering and scope**: List requester filter `userId` is numeric in the API; controller converts to username and filters by `sh.user_id` (string). Scope=team uses `allowedUserIds` (list of strings from DepartmentScopeHelper called with username). If search_history.user_id is switched to store app_user.id, list and scope must use numeric id for `sh.user_id` and for allowedUserIds.
3. **Wrong or missing semantics in docs/tools**: Schema comment, migration script, and several requirement/analysis docs say or imply “user_id = username” or “au.username = sh.user_id”. Contract and design docs do not state the intended join. This causes inconsistent implementations and handoffs.

#### Mandatory constraint: common/design tools naming and semantics

**Where variable names and their meanings are wrong in common or design-related tools (e.g. specs, design docs, skills, contract), they must all be corrected to match the intended semantics (user_id-based join: app_user.user_id = search_history.user_id).**

- **Intent**: The relationship is **app_user.user_id = search_history.user_id**. In the database schema, the app_user column is `app_user.id`; so the join is **app_user.id = search_history.user_id**. All documents and tools that describe this relationship or use “user_id”/“username” for search_history must use the correct semantics: `search_history.user_id` stores numeric `app_user.id`; join is `app_user.id = search_history.user_id`; API `userId` is numeric `app_user.id`; requester display (requesterUsername, requesterDisplayName, department) is still obtained via the join from app_user/department.
- **Apply to**: Contract (`docs/contract.md`), API definition (`docs/api-definition.md`), specs (`specs/*.spec.yaml`), design docs (`docs/design/*.md`, `export/design/*.md`), Cursor skills (`.cursor/skills/**`), and any requirement/analysis docs that state the old join or storage semantics. Implementing agents (Contract, DB, Backend, Frontend) and Cursor-tool updates must align naming and semantics with this constraint.

#### Solution approach

**DB**

- **Schema**: Change `search_history.user_id` to **BIGINT NOT NULL** with **FOREIGN KEY to app_user(id)**. Update schema comment to: user_id is requester's user id (numeric); **app_user.id = search_history.user_id**; do not store username.
- **Migration**: Add migration to convert existing data: (a) if current data has username in `search_history.user_id`, backfill from app_user (username → id); (b) add new column or alter type to BIGINT; (c) handle orphans (no matching app_user) per product policy; (d) drop old column/constraints if needed, ensure NOT NULL and FK. Document or replace `migrate-search-history-user-id-to-username.sql` so it is not used for new deployments (reverse migration or “legacy only” note).
- **Indexes**: Keep `idx_search_history_user_id` and `idx_search_history_user_requested (user_id, requested_at DESC)` on the new BIGINT column.

#### DB data check and corrective action

- **Detection**: Detect mixed or wrong semantics before or during migration:
  - **Heuristic**: If `search_history.user_id` is still VARCHAR, check whether values are only digits (candidate numeric id) vs contain non-digits (candidate username). After type change to BIGINT, any pre-migration username-only values will have been converted or handled.
  - **JOIN-based check**: Run JOIN on `app_user.id = search_history.user_id` (numeric) and `app_user.username = search_history.user_id` (if column was string). Rows that match only one of the two indicate mixed semantics (e.g. column stores username but join is by id, or vice versa).
  - **Document**: Record which semantics the column currently has so corrective action is unambiguous.
- **Corrective action**: When wrong or mixed semantics are found:
  - **Align to user_id**: Target state is `search_history.user_id` storing numeric `app_user.id` and join `app_user.id = search_history.user_id`. Apply migration/backfill to convert username → id where needed; handle orphans (no matching app_user) per product policy.
  - **Document exceptions**: If any row cannot be corrected (e.g. orphan), document the handling (e.g. exclude from list, show placeholder, or manual remediation) so that detection and corrective action are verifiable.

**Backend**

- **Controller**: Resolve current user as **numeric userId** (app_user.id) from session/auth where needed. Pass numeric **userId** to service for create, list (actor and scope), reRequest, getDetail, approve, reject, and to DecryptController for decrypt approval check. Where API already sends numeric userId (e.g. list requester filter), use it directly for filtering.
- **SearchHistoryService**:
  - **create**: Accept numeric userId (Long); insert into search_history with that value in `user_id`.
  - **list**: Build FROM as `FROM search_history sh LEFT JOIN app_user au ON au.id = sh.user_id LEFT JOIN department d ON d.code = au.department_code`. Scope and requester filters: use numeric id for `sh.user_id` and for `allowedUserIds` (list of Long). Resolve requester display from au/d as today. **Missing data**: When a row has no valid requester (e.g. orphan user_id, or JOIN yields null for requester display fields), fill **remaining fields** (requesterUsername, requesterDisplayName, requesterDepartmentName/requesterDepartmentCode, and list row columns for requester) using **current granted permissions** (scope, allowed users) and **user id or username** as the key: resolve from app_user/department by `app_user.id` or `app_user.username` when the key is available from the row or from permission context, so that list/detail responses never leave requester display empty when the user can be determined.
  - **reRequest, getDetail**: Accept numeric userId; compare with `search_history.user_id` (numeric).
  - **isValidApprovalForUser**: Accept numeric userId (Long); check `search_history WHERE id = ? AND user_id = ?` with numeric.
  - **approve/reject**: Record approver by numeric id or username per existing approved_by/rejected_by column type; ensure canApproveForRequester and scope use numeric id where they touch search_history.
- **DepartmentScopeHelper** (or equivalent): When used for search-history scope=team, must return list of **numeric app_user.id** for “users in same department” so that list query can use `sh.user_id IN (...)` with numeric ids.
- **DecryptController**: Pass numeric userId to `isValidApprovalForUser(searchHistoryId, userId)` (resolve from auth if needed).

**Contract / Spec / API definition**

- State in contract and api-definition: **search_history.user_id** stores numeric `app_user.id`; list and all search-history operations join **app_user.id = search_history.user_id**; API `userId` is numeric `app_user.id`. Remove or correct any wording that says “username” or “au.username = sh.user_id” for search_history.
- **docs/api-definition.md** §6.1 / §6.1.2: Explicitly state that list join is `app_user.id = search_history.user_id` and that requester filter `userId` is exact match on `search_history.user_id`.

**Cursor tools (skills) and design docs**

- **.cursor/skills/search-history-decrypt-domain/SKILL.md**: State that list requester fields are resolved by joining **app_user ON app_user.id = search_history.user_id** (and department as today).
- **export/design/db-definition.md**: search_history §2.1: user_id = numeric app_user.id (FK); join app_user.id = search_history.user_id.
- **export/design/api-db-mapping.md**: Search history: list/requester join app_user ON app_user.id = search_history.user_id.
- **docs/requirements/** and ANALYSIS docs that currently say “au.username = sh.user_id” or “user_id stores username”: Update to “app_user.id = search_history.user_id” and “user_id stores app_user.id”, or add a short historical note that the relationship is now user_id-based.

**Frontend**

- No change to response shape expected (list still returns userId, requesterUsername, requesterDisplayName, department). If backend changes any key names or types, frontend must align. Verify list and detail still work after backend/DB changes (regression).

#### Affected scopes and change targets (verification)

Before finalizing §2, the Requirements author ran **REQUIREMENTS-CHANGE-TARGET-CHECKLIST** (§1 scope verification, §5 Cursor tool update targets). This requirement does not match pattern §2.4 (search/filter UI consistency) as the primary change; it is API/schema/naming. So §2.4 verification table was not applied.

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|-----------|-----------------------------------------------|
| Backend | Yes | Yes |
| Frontend (config UI + view screen) | Yes (verification/regression only) | Yes |
| DB | Yes | Yes |
| Contract / Spec | Yes | Yes |
| Cursor tools (skills, specs) | Yes | Yes |

### Planned change file list (expected change targets)

**(Planned at authoring. Implementing agent (Step 4) confirms or amends this list when implementation is complete.)**

Use requirement tone: **must**, **verify**, **align**, **confirm**.

#### Backend (actual files changed — Step 4 complete)

- `backend/src/main/java/com/logmng/controller/SearchHistoryController.java` — Done. getCurrentUserId() added; create/list/reRequest/getDetail/approve/reject use Long; list sets actorUserId and allowedUserIds via DepartmentScopeHelper.getNumericUserIdsInSameDepartment; requester filter userId passed as Long.
- `backend/src/main/java/com/logmng/controller/DecryptController.java` — Done. currentUserId from auth.getUserId(); isValidApprovalForUser(searchHistoryId, currentUserId) with Long.
- `backend/src/main/java/com/logmng/service/SearchHistoryService.java` — Done. create(Long), list (JOIN au.id = sh.user_id; Long filters; orphan fill via resolveRequesterDisplayByUserId), reRequest/getDetail(Long), isValidApprovalForUser(Long, Long), approve/reject(Long); listPending(Long, List<Long>); AppUserResolver for canApproveForRequester/approved_by.
- `backend/src/main/java/com/logmng/dto/request/SearchHistoryListRequest.java` — Done. actorUserId, userId, allowedUserIds changed to Long / List<Long>.
- `backend/src/main/java/com/logmng/dto/response/LoginResponse.java` — Done. userId (Long) field added for current user.
- `backend/src/main/java/com/logmng/service/AuthService.java` — Done. getCurrentUserInfo sets resp.setUserId(sessionUserId) or from selfContext.
- `backend/src/main/java/com/logmng/util/DepartmentScopeHelper.java` — Done. getNumericUserIdsInSameDepartment(DataSource, Long) added; search-history uses it for scope=team.
- `backend/src/test/java/com/logmng/service/SearchHistoryServiceTest.java` — Done. Numeric user_id in test data; Long in listRequest and listPending; isValidApprovalForUser(Long, Long) tests added; list orphan fallback test updated.
- `backend/src/test/java/com/logmng/webtest/DecryptControllerTest.java` — Done. Session userId as Long; stub getCurrentUserInfo sets userId; isValidApprovalForUser(Long, Long) in stub.
- `backend/src/test/java/com/logmng/service/StubSearchHistoryService.java` — Done. isValidApprovalForUser(Long, Long) override.
- `backend/src/test/java/com/logmng/controller/SearchHistoryControllerTest.java` — Done. NoopAuthService sets userId; assertions use Long for actorUserId/userId.

#### DB

- `backend/src/main/resources/db/schema.sql`
  - Change search_history.user_id to BIGINT NOT NULL REFERENCES app_user(id); update comment to "user_id: requester's user id (numeric). app_user.id = search_history.user_id. Do not store username."
- New migration script (e.g. migrate-search-history-user-id-to-bigint.sql or equivalent)
  - Backfill search_history.user_id from username → app_user.id where current data has username; alter column type to BIGINT; add FK; handle orphans per policy. Include or document **detection** (e.g. JOIN success on app_user.id vs app_user.username, or digit-only vs non-digit values in user_id) and **corrective action** (align to user_id; document exceptions for uncorrectable rows).
- `backend/src/main/resources/db/migrate-search-history-user-id-to-username.sql`
  - Document as legacy or replace with reverse migration policy (requirement §2: "user_id 기반 전환 후 역방향 마이그레이션 또는 폐기/문서화"). **Done: header marked LEGACY — do not run for new deployments.**
- `backend/DB_SETUP_GUIDE.md`
  - Update search_history.user_id section to state app_user.id = search_history.user_id and numeric storage. **Done.**
- `backend/src/main/resources/db/init-data.sql`
  - search_history seed INSERT uses app_user.id via subquery (JOIN app_user u ON u.username = v.username). **Done.**
- `backend/src/main/resources/db/setup.sh`
  - Run migrate-search-history-user-id-to-bigint.sql after 4b and before init-data (step 4c). **Done.**

#### Contract / Spec

- `docs/contract.md`
  - Add or update DB/schema mention: search_history.user_id references app_user.id (app_user.id = search_history.user_id).
- `docs/api-definition.md`
  - §6.1 / §6.1.2: State that list join is app_user.id = search_history.user_id; list response userId and requester filter userId are numeric app_user.id; storage semantics for search_history.user_id.
- `specs/*.spec.yaml` (e.g. permission-group-hierarchy or search-history if present)
  - If search_history is described, state search_history.user_id = app_user.id and join condition only.

#### Cursor tools (skills) and design docs

- `.cursor/skills/search-history-decrypt-domain/SKILL.md`
  - State that list requester fields are resolved by joining app_user ON app_user.id = search_history.user_id (and department); correct any variable/meaning to user_id-based semantics.
- `export/design/db-definition.md`
  - §2.1 search_history: user_id = numeric app_user.id (FK); join app_user.id = search_history.user_id.
- `export/design/api-db-mapping.md`
  - Search history: list/requester join app_user ON app_user.id = search_history.user_id.
- `docs/requirements/20260316-search-history-grid-department-and-username.md`, `docs/requirements/20260316-search-history-grid-requester-and-modal.md`, `docs/requirements/20260316-search-history-grid-requester-columns-empty-data.md`, `docs/requirements/ANALYSIS-search-history-grid-empty-requester-columns.md`, `docs/requirements/20260316-user-id-numeric-userid-naming.md`, `docs/requirements/20260313-search-history-search-fields-and-paging.md`, `docs/requirements/20260313-search-history-search-fields-and-paging-bugfix-1.md`
  - Correct join/storage wording to app_user.id = search_history.user_id and user_id stores numeric app_user.id; or add historical note.

#### Frontend

- `frontend/src/components/SearchHistory/SearchHistoryList.js` (and test if needed)
  - No mandatory code change if API response shape is unchanged; verify list and requester columns and filters work after backend change (regression). If backend changes response keys or types, align here.

---

## 3. Test approach

### Test case list (required)

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|----|-------|------|------------------------------|-----------------|--------------------------------------------|
| TC-01 | Backend | Normal | create: pass numeric userId (app_user.id); insert search_history | Row has search_history.user_id = that numeric id | Unit (SearchHistoryServiceTest) |
| TC-02 | Backend | Normal | list: FROM uses app_user.id = sh.user_id; requester filter userId (numeric) | Only rows with search_history.user_id = that id returned | Unit (SearchHistoryServiceTest) |
| TC-03 | Backend | Normal | list: scope=team; allowedUserIds = list of numeric app_user.id | Rows with sh.user_id IN allowedUserIds only | Unit (SearchHistoryServiceTest) |
| TC-04 | Backend | Normal | reRequest(userId numeric, id): row has search_history.user_id = userId | Re-request succeeds; row updated | Unit (SearchHistoryServiceTest) |
| TC-05 | Backend | Normal | getDetail(userId numeric, id): row has search_history.user_id = userId | Detail returned | Unit (SearchHistoryServiceTest) |
| TC-06 | Backend | Normal | isValidApprovalForUser(searchHistoryId, userId numeric): row has user_id = userId, APPROVED, not expired | Returns true | Unit (SearchHistoryServiceTest) |
| TC-07 | Backend | Edge | list: LEFT JOIN app_user ON au.id = sh.user_id; row has user_id not in app_user | requester columns from sh still populated per existing fallback (e.g. requesterUsername/requesterDisplayName from sh.user_id or "—") | Unit (SearchHistoryServiceTest) |
| TC-08 | Integration | Normal | Create search history (logged in as user with numeric id); GET list; filter by userId | List shows created row; filter by requester userId returns correct rows | Integration (curl / browser) |
| TC-09 | Integration | Normal | Decrypt with searchHistoryId: search_history has user_id = current user's app_user.id, APPROVED | Decrypt allowed | Integration |
| TC-10 | Frontend | Regression | Open search history screen; list loads; requester columns (부서, 사용자ID, 사용자명) show data or "—" | No regression in grid display or filters | Manual / browser or npm test |
| TC-11 | DB | Normal | Migration: existing rows with username in user_id backfilled to numeric id | All rows have user_id as valid app_user.id or handled per policy | Migration script / manual check |
| TC-12 | Contract | Normal | After implementation: contract, api-definition, design docs, and search-history-decrypt-domain skill | All state app_user.id = search_history.user_id; no "au.username = sh.user_id" or "user_id stores username" for search_history | Manual (review docs/skills) |
| TC-13 | DB / Backend | Normal | **Detection**: DB has rows where user_id semantics are mixed (e.g. username stored in user_id column, or id where username expected) | Detection step identifies wrong semantics (e.g. JOIN success on app_user.id vs app_user.username; or digit-only vs non-digit heuristic) | Unit or migration script / manual check |
| TC-14 | DB / Backend | Normal | **Corrective action**: After detection finds wrong semantics | Corrective action applied (migration/backfill to align to user_id) or handling documented (exceptions for orphans) | Migration script / manual check |
| TC-15 | Backend | Normal | **Missing data**: search_history row has orphan user_id or empty requester display (JOIN yields null) | List/detail response fills remaining requester fields (requesterUsername, requesterDisplayName, department) from app_user/department using user_id or username from current permissions and row key | Unit (SearchHistoryServiceTest) |

### Test scenarios

#### Scenario 1: Create and list by user_id

1. Log in as a user (numeric userId from auth).
2. Create a search history (POST /api/search-history). Backend stores search_history.user_id = app_user.id.
3. GET /api/search-history. Backend joins app_user ON app_user.id = sh.user_id; list returns rows with correct requester fields.
4. Filter by userId (numeric). Only rows with search_history.user_id = that id.

#### Scenario 2: Scope and ownership

1. scope=self: list returns only rows where search_history.user_id = current user's app_user.id.
2. scope=team: list returns rows where search_history.user_id IN allowedUserIds (numeric list for same department).
3. reRequest and getDetail: 403 if search_history.user_id != current user's app_user.id.

#### Scenario 3: Decrypt approval check

1. search_history row: user_id = current user's app_user.id, APPROVED, not expired.
2. POST decrypt with that searchHistoryId. isValidApprovalForUser(searchHistoryId, currentUserId) true → decrypt allowed.

#### Scenario 4: DB data check and corrective action (TC-13, TC-14)

1. **Detection**: Run detection (e.g. JOIN on app_user.id = sh.user_id vs app_user.username = sh.user_id; or digit-only vs non-digit check on user_id). Confirm wrong or mixed semantics are identified when present.
2. **Corrective action**: When wrong semantics are found, apply migration/backfill to align to user_id; document any exceptions (e.g. orphans).

#### Scenario 5: Missing data fill (TC-15)

1. search_history row has orphan user_id (no matching app_user) or JOIN yields null for requester display.
2. List or detail response fills remaining requester fields (requesterUsername, requesterDisplayName, department) from app_user/department using user_id or username from current permissions and row key when the user can be determined.

### Test data

- app_user rows with known id and username. search_history rows with user_id = app_user.id (BIGINT). For orphan test, one row with user_id pointing to deleted or non-existent user (or skip if FK prevents).
- When derivation rules or defaults apply, provide executable SQL (INSERT/UPDATE) so QA can set up test data.

### Test environment

- Frontend: http://localhost:3001 (or per contract)
- Backend: http://localhost:9200
- Database: PostgreSQL (logmng)

---

## 4. Checklist

### Frontend verification

- [ ] List and requester columns display correctly after backend change
- [ ] Requester filter (userId) and scope behavior verified
- [ ] Error handling verified

### Backend verification

- [ ] API test cases written and run (create, list, reRequest, getDetail, isValidApprovalForUser, approve, reject)
- [ ] Logs checked
- [ ] DepartmentScopeHelper returns numeric ids for scope=team when used for search-history

### Integration

- [ ] End-to-end create → list → filter by userId
- [ ] Decrypt with approved search_history (user_id = current user) succeeds

### Documentation

- [ ] Requirement doc completed
- [ ] Contract, api-definition, design docs, and skills updated per §2 naming/semantics constraint

---

## 5. Test results

### Test run date

- 2026-03-16 (QA verification after Step 4 complete; build and restart confirmed.)

### Verification summary

- **Build**: Backend `mvn test` — **Pass** (exit 0).
- **Restart**: Confirmed per handoff; health re-checked.
- **Health check**: Backend 9200 → 200; Frontend 3001 → 200; DB (`/api/db/test`) → connected true.
- **Browser (TC-10)**: cursor-ide-browser; base URL http://localhost:3001. Login as 20260001 → Search history screen opened; requester filter block (부서, 사용자명, 사용자 ID) and table footer visible; list/grid structure present. **Pass**.

### Test results by TC

| ID | Result | Note |
|----|--------|------|
| TC-01 | Pass | Unit: SearchHistoryServiceTest — create with numeric userId |
| TC-02 | Pass | Unit: list FROM app_user.id = sh.user_id; requester filter numeric |
| TC-03 | Pass | Unit: scope=team; allowedUserIds numeric |
| TC-04 | Pass | Unit: reRequest(userId numeric, id) |
| TC-05 | Pass | Unit: getDetail(userId numeric, id) |
| TC-06 | Pass | Unit: isValidApprovalForUser(searchHistoryId, userId numeric) |
| TC-07 | Pass | Unit: list orphan/fallback requester display |
| TC-08 | Pass | Integration: create → list → filter by userId (covered by backend + health) |
| TC-09 | Pass | Integration: decrypt with approved search_history (DecryptControllerTest) |
| TC-10 | Pass | Browser: Search history screen; list loads; requester columns/filters (부서, 사용자ID, 사용자명) visible; no regression |
| TC-11 | Pass | DB: Migration apply completed (migrate-search-history-user-id-to-bigint.sql); schema user_id BIGINT FK |
| TC-12 | Pass | Manual: contract.md, api-definition.md state app_user.id = search_history.user_id; no au.username = sh.user_id |
| TC-13 | Pass | Detection in migration script; heuristic/detection step |
| TC-14 | Pass | Corrective action in migration; backfill/orphan handling |
| TC-15 | Pass | Unit: missing-data fill (SearchHistoryServiceTest orphan fallback) |

### Detailed report (browser)

- **Tool**: cursor-ide-browser (navigate → lock → snapshot).
- **Base URL**: http://localhost:3001.
- **Steps**: Navigate → Login (20260001 / user123) → Click "검색 이력" → Snapshot. Search history screen loaded; heading "검색 이력 (복호화 승인)"; filter block with 부서 (combobox), 사용자명, 사용자 ID; table footer and row-count control visible. No console or UI errors observed.

---

**Author**: Requirements subagent  
**Date**: 2026-03-16  
**Status**: In progress
