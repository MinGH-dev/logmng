# Decryption Approval Snapshot — Final Design (Option 1 + Option 2 Unified)

This is the **single authoritative final design** agreed by Architecture, DBA, and Security agents.  
For implementation, use this document together with `docs/requirements/20260224-decryption-approval-snapshot-guide.md`.

---

## 1. Document role

- **Guide document** (`20260224-decryption-approval-snapshot-guide.md`) **§6.5 (high QPS)** and **§6.6 (Architecture final option)** describe the review and decision for **Option 1**.
  - **Option 1**: Snapshot table uses a **single column `row_id` (VARCHAR)**. Single keys are stored as-is; composite keys are stored as a **serialized string**.
- This document integrates **Option 2 (composite key)** considerations so that **Option 1 and Option 2 are covered by one final design**.
  - **Option 2**: Store composite keys in **row_key_json (JSONB)** or **row_id_part1/part2/part3**, etc. Agent agreement: for **single code path and extensibility**, the design is consolidated as in §2 and §3 below.

---

## 2. Agent agreement summary

| Perspective | Key agreement vs Option 2 |
|-------------|----------------------------|
| **Architecture** | Keep a single table and single column (`row_id`). Store composite keys as **serialized** values (e.g. canonical JSON string) in `row_id`. No separate `row_key_json` column until Phase 1/2 (cache, batch API); same string format throughout. |
| **DBA** | If composite keys grow in number, consider migrating to a **single `row_key_json` (JSONB)** column. PK: `(search_history_id, log_type, row_key_json)`. Do not use md5(…) as PK. With only single-key log types for now, `row_id` (VARCHAR) serialization is sufficient. |
| **Security** | **Server as single source**: client sends **key parts only**; server builds serialized form / `row_key_json` and compares with snapshot. Do not have the client send the serialized string or JSON directly. If `row_key_json` is ever accepted from client: enforce key whitelist, type and length limits. Audit log records **serialized string** as row identifier. |

---

## 3. Final design decisions (Option 1 + Option 2–ready)

### 3.1 Schema and storage format (unified)

- **Current / short term (Phase 1)**  
  - **Single table, single column `row_id` (VARCHAR)**.  
  - Single key (e.g. `guid` for java_fw_imglog): store value directly in `row_id`.  
  - Composite key (e.g. pb_feplog type+id): build string with **log_type–specific canonical serialization** and store in `row_id` (e.g. `"send|123"` or canonical JSON string).  
  - PK: `(search_history_id, log_type, row_id)`.  
  - This allows both Option 1 and Option 2–style composite keys to be handled with **one column + serialization**, so one code path, cache, and batch API all use the same "row identifier string".

- **Future extension (many composite-key log types or need to use key structure in DB)**  
  - Consider migrating to a **single `row_key_json` (JSONB)** column.  
  - Single keys can be stored as `{"guid":"xxx"}` in JSONB; PK `(search_history_id, log_type, row_key_json)` can represent both options.  
  - DBA: use `row_key_json` in PK; do not use md5(row_key_json::text) as PK.  
  - Until migration, keep mapping from existing `row_id` (VARCHAR) serialization to canonical string so cache, batch API, and audit log stay on the same "serialized string" basis.

### 3.2 Decrypt and snapshot check (security invariant)

- **Order**: (1) `isValidApprovalForUser(searchHistoryId, userId)` → (2) snapshot existence (DB or cache). Unchanged.  
- **Client request**:  
  - **Even for composite keys, client sends only key parts** (e.g. type, id).  
  - Server builds **serialized string or row_key_json** from log_type rules and compares with snapshot.  
  - **Do not** design the API so the client sends "serialized row_id" or "full row_key_json object"; that increases tampering risk.  
- **If accepting row_key_json from client is unavoidable**: apply key whitelist, value type/length and total size limits. Prefer receiving parts only and building JSON on the server.

### 3.3 Performance and Phase 2 (aligned with Option 1 §6.5, §6.6)

- **Phase 1**: Snapshot check from DB only, no cache. Ensure PK and `search_history_id` index. Monitor decrypt latency, QPS, and DB load.  
- **Phase 2**: If P95 latency or QPS exceeds thresholds, add per–searchHistoryId in-memory cache (set of approved row identifiers, TTL ≤ approval expiry, size cap). Optionally add batch check API.  
- **Cache and batch API**: Row identifier is the **same "canonical string"** as in Option 1. For composite keys use serialized string or (when row_key_json is introduced) the same canonical string for Set/batch requests.

### 3.4 Audit log

- Record row identifier as **serialized string** (including composite keys): searchHistoryId, logType, rowId, userId, timestamp, etc.  
- Use the same serialization rules as snapshot storage and decrypt check for traceability and searchability.

---

## 4. Implementation checklist (final design)

- [ ] Snapshot table: **for now** PK `(search_history_id, log_type, row_id)`. Composite keys stored in `row_id` via log_type–specific serialization.  
- [ ] **Extension**: If moving to `row_key_json` (JSONB), PK `(search_history_id, log_type, row_key_json)`; single key as `{"guid":"x"}`.  
- [ ] Serialization/deserialization: one shared util; **same rules** for approval snapshot save and decrypt check.  
- [ ] Decrypt API: **accept key parts only**; server builds serialized form (or row_key_json) and queries snapshot.  
- [ ] Security invariant: approval check → snapshot existence; cache TTL ≤ approval expiry.  
- [ ] Audit log: row identifier = serialized string.  
- [ ] Phase 2 cache and batch API: row identifier = canonical string.

---

## 5. References

- Flow, schema examples, edge cases: `docs/requirements/20260224-decryption-approval-snapshot-guide.md`  
- Option 1 high QPS, Phase 1/2, batch API: same guide **§6.5, §6.6**  
- Approver designation and security: `docs/requirements/20260224-decryption-approver-designation.md`  
- Agent roles: `docs/workflow/CURSOR-SUBAGENTS-DESIGN.md`

---

## 6. For development and test agents

Use this section as the single reference for implementation and testing. Contract and API definitions: `docs/contract.md`, `docs/api-definition.md`.

### 6.1 Backend

- **Approve flow (snapshot creation)**  
  - **Entry**: `SearchHistoryService.approve(Long id, String approverUserId)` (called from `SearchHistoryController` `POST /api/search-history/{id}/approve`).  
  - **Order of operations**:  
    1. Load `log_type` and `search_params` for the given `id` (after PENDING validation).  
    2. Set `search_history.approval_status = 'APPROVED'` (and approved_by, approved_at) only **after** snapshot insert.  
    3. Run search with `search_params` (e.g. via `LogDbService.searchLogs` or equivalent).  
    4. For each result row, compute **row identifier** per log_type (see serialization below).  
    5. Insert rows into `search_history_approved_row`: `(search_history_id, log_type, row_id)`.  
  - Use a **single transaction**: search run + snapshot INSERT + APPROVED update; on snapshot failure do not set APPROVED (or roll back).

- **Decrypt flow (snapshot check)**  
  - **Entry**: `DecryptController` (e.g. `POST /api/logs/decrypt/{logType}`).  
  - **Order of checks**:  
    1. Resolve `searchHistoryId` and `userId` from request/session.  
    2. Call `isValidApprovalForUser(searchHistoryId, userId)`; if false → 403 `DECRYPTION_NOT_APPROVED` (existing behavior).  
    3. **New**: Build `row_id` for the requested row from request parameters using **log_type–specific serialization** (server-side only; client sends key parts, e.g. `guid` for java_fw_imglog).  
    4. Call `isRowInApprovedSnapshot(searchHistoryId, logType, rowId)` (DB or future cache).  
    5. If false → **403** with code **`ROW_NOT_IN_APPROVED_SNAPSHOT`** (and optional user-facing message).  
    6. If true, proceed to existing decrypt logic.

- **Row identifier (row_id)**  
  - **Single column** only in Phase 1: `search_history_approved_row.row_id` (VARCHAR).  
  - **java_fw_imglog**: use **guid** as `row_id`.  
  - **Other log_type(s)**: define one canonical serialization per log_type (e.g. `"send|123"` for pb_feplog).  
  - **Serialization**: centralize rules per log_type in one place (e.g. `RowIdCodec` or equivalent); use the same rules when **writing** the snapshot and when **checking** on decrypt.

- **Relevant files**: `backend/src/main/java/com/logmng/service/SearchHistoryService.java`, `backend/src/main/java/com/logmng/controller/DecryptController.java`, and the service/DAO that will implement `isRowInApprovedSnapshot` and snapshot insert.

### 6.2 DB

- **Table**: `search_history_approved_row`.  
- **Columns**: `search_history_id` (BIGINT, FK to `search_history(id)` ON DELETE CASCADE), `log_type` (VARCHAR), `row_id` (VARCHAR).  
- **PK**: `(search_history_id, log_type, row_id)`.  
- **Index**: `CREATE INDEX idx_search_history_approved_row_history ON search_history_approved_row(search_history_id);`  
- **Migration**: add a migration script under `backend/src/main/resources/db/` (e.g. `migrate-search-history-approved-row.sql`) and update `backend/src/main/resources/db/schema.sql` so the table and index are reflected in the canonical schema.

### 6.3 Frontend

- **Decrypt request**: No change to request shape; backend enforces snapshot. Continue sending `searchHistoryId` and key parts (e.g. `guid` for java_fw_imglog) as defined in the API.  
- **Optional**: When backend returns **403** with code **`ROW_NOT_IN_APPROVED_SNAPSHOT`**, show a user-friendly message (e.g. "Only rows from the approved search result can be decrypted").

### 6.4 QA / Test

Test cases to cover:

1. **Approve creates snapshot rows**  
   - Approve a PENDING search history; then assert that `search_history_approved_row` contains one row per result row for that `search_history_id` and `log_type`, with correct `row_id` (e.g. guid for java_fw_imglog).

2. **Decrypt allowed only for row in snapshot**  
   - For an APPROVED search history with snapshot populated, call decrypt with a `row_id` that exists in the snapshot → expect 200 and decrypted content (or existing success behavior).

3. **Decrypt forbidden for row not in snapshot (403)**  
   - Same APPROVED search history; call decrypt with a `row_id` (e.g. guid) that is **not** in `search_history_approved_row` → expect **403** and error code **`ROW_NOT_IN_APPROVED_SNAPSHOT`**.

4. **Decrypt forbidden when no snapshot (e.g. legacy approved row)**  
   - Use an APPROVED search history that has **no** rows in `search_history_approved_row` (e.g. approved before snapshot feature). Decrypt with any valid key parts → expect **403** (e.g. `ROW_NOT_IN_APPROVED_SNAPSHOT` or equivalent), since the row cannot be in the snapshot.

**Test hooks**: Backend tests can call `SearchHistoryService.approve` and then query `search_history_approved_row`, or insert/delete snapshot rows to simulate "no snapshot" and "row not in snapshot" cases. Contract and base URL: `docs/contract.md` (e.g. backend API base `http://localhost:9200/api`).
