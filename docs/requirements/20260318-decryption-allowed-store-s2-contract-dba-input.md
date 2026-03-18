# §2 Structured Input: Contract + DBA (Decryption-allowed store)

**Purpose**: Requirement authoring only. No implementation. Use this as input for §2 (design) of the parent requirement.

---

## (a) Contract — API/contract changes

- **POST /api/logs/decrypt/{logType}**  
  - **Current**: Request body requires `searchHistoryId` (number) and `guid` (string). Authorization: `isValidApprovalForUser(searchHistoryId, currentUserId)` + row in `search_history_approved_row`.  
  - **Change**: Authorization must **no longer depend on searchHistoryId** for “who can decrypt what.” Backend decides allowability from the **new decryption-allowed store** (by user_id, screen, approved GUIDs, valid_until).  
  - **Options**:  
    - **A**: Make `searchHistoryId` **optional** in the request body; when absent, backend checks only (currentUserId, screen, guid, valid_until) against the new store.  
    - **B**: Keep `searchHistoryId` in the body for **audit/trace only**; authorization is done solely via the new store.  
  - **Recommendation**: Prefer **B** (keep searchHistoryId for audit; auth from new store). No change to response shape of decrypt API; error codes (e.g. `DECRYPTION_NOT_APPROVED`, `ROW_NOT_IN_APPROVED_SNAPSHOT`) can be retained or replaced by a single “not allowed” code per product choice.

- **New endpoint for “decryption-allowed” (frontend needs approval state per search/row)**  
  - **Need**: Frontend must know (1) whether the current user has any decryption allowance for the screen, and (2) per row (e.g. per GUID) whether that row is allowed, so it can hide the decrypt button when there is no encrypted data, and show a **dimmed** decrypt button with an informative message on click for unapproved GUIDs.  
  - **Proposal**: Add **GET /api/decrypt/allowed** (or **GET /api/users/me/decrypt-allowed**) with query `screen` (e.g. `main`).  
  - **Response (data)** example: `{ "screen": "main", "validUntil": "yyyy-MM-dd'T'HH:mm:ss", "guids": ["guid1", "guid2", ...] }` — GUIDs the current user may decrypt on that screen until `validUntil`. Empty `guids` or missing/expired `validUntil` → no decryption allowed; frontend hides or dims accordingly and shows message on click for unapproved GUIDs.

- **Approval flow (existing)**  
  - **POST /api/search-history** (create request) and **POST /api/search-history/{id}/approve** (approver approve): Backend to **refresh** the decryption-allowed store for the requester (by user_id, screen, approved GUIDs, renew valid_until) and, on approve, **clean up expired** records for that user. No change to these API paths or request/response shapes; only server-side behavior (write to new store, cleanup).

- **docs/contract.md**  
  - Add env/API notes: decryption authorization source = new decryption-allowed store; POST /api/logs/decrypt authorization semantics; new GET /api/decrypt/allowed (or equivalent) response shape.  
  - **docs/api-definition.md**: Update §10 (복호화) and add the new allowed endpoint with path, method, query, response, and error cases.

---

## (b) DBA — Schema for “decryption-allowed” table

- **Suggested table name**: `user_decryption_allowed` (or `decryption_allowed`).

- **Key columns (minimal)**  
  - `user_id` BIGINT NOT NULL REFERENCES app_user(id)  
  - `screen` VARCHAR(50) NOT NULL  
  - `valid_until` TIMESTAMP NOT NULL  

- **GUID storage — two options**  
  - **Option 1 — One row per (user_id, screen, guid)**: Columns above + `guid` VARCHAR(512) NOT NULL. PK `(user_id, screen, guid)`. Index `(user_id, screen, valid_until)` for “allowed set for user+screen and not expired.” Cleanup: `DELETE FROM user_decryption_allowed WHERE user_id = ? AND valid_until < now()` (and optionally by screen). Cardinality: rows = number of approved GUIDs per user/screen; higher row count, simple lookup.  
  - **Option 2 — One row per (user_id, screen) with JSON array**: Columns above + `guids` JSONB NOT NULL (array of strings). PK `(user_id, screen)`. “Refresh” on new approval = single row upsert (replace guids, set new valid_until). Cleanup: `DELETE FROM user_decryption_allowed WHERE user_id = ? AND valid_until < now()`. Lookup: one row per user+screen; check `guid IN guids` and `valid_until > now()`. GIN index on `guids` if needed for containment. Lower row count; slightly more complex per-guid check.

- **Indexing**  
  - Lookup pattern: “allowed GUIDs for user_id + screen where valid_until > now().”  
  - Option 1: INDEX `(user_id, screen, valid_until)`; Option 2: INDEX `(user_id, screen)`, optional GIN(guids).  
  - Cleanup: INDEX on `(valid_until)` or `(user_id, valid_until)` to speed up expired deletes.

- **Cleanup of expired rows**  
  - On approver approve (or on a scheduled job): delete expired for **that user**: `DELETE FROM user_decryption_allowed WHERE user_id = ? AND valid_until < now()`.  
  - Optional: global cleanup job for all users; same predicate without `user_id`.

- **Impact on search_history_approved_row**  
  - **Keep** `search_history_approved_row` as **audit/history only**; do **not** use it for “who can decrypt what.” No row removal from that table (no DELETE). Backend continues to write snapshot rows on approve if desired for audit; decryption authorization is read only from the new table.

- **Migration note**  
  - New table only (no change to existing tables). Optional: one-time backfill from current `search_history_approved_row` + `search_history` (user_id, APPROVED, not expired) into `user_decryption_allowed` for existing approved snapshots; otherwise new approvals populate the new table going forward.
