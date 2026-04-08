# 20260408 - Activity statistics: decrypt KPI = unique decrypted rows per calendar day

## 1. User requirement

### Requirement description

Activity statistics must show **decryption** KPIs that reflect **actual decrypt execution** recorded in the activity audit trail, **not** decrypt-approval workflow counts. The decrypt metric must count **distinct logical log rows** that were successfully represented in that audit trail, **deduplicated per calendar day** (in the application’s configured timezone / date boundary used for statistics): if the same row is decrypted multiple times on the same date, it contributes **once** to that date’s decrypt count (Korean product wording: *로그 중복 없이 해당 일자에 복호화한 row*).

This requirement aligns statistics with the same **source of truth** as the Activity Log for decrypt **events** (`action_type = 'DECRYPT'`), while changing the **aggregation** from raw event rows to per-day unique row keys.

### User scenario

1. An operator decrypts the same Image Log row (same business identity) several times in one day while reviewing results.
2. Another operator opens **Activity statistics** for that day (and log type / scope as applicable).
3. **Problem**: The UI shows a decrypt count that looks like “number of decrypt clicks” or mixes in non-execution events; it **overstates** real unique-row coverage, or appears inconsistent with “rows touched” expectations.
4. **Expectation**: The decrypt KPI for that day counts **unique rows decrypted**, not approvals and not redundant decrypt calls for the same row on the same day.

### Expected outcome

- **Data source**: Decrypt KPIs are derived from **`user_activity_log`**, same table as Activity Log list/search, limited to **`action_type = 'DECRYPT'`** (actual decrypt API execution audit). **Must not** use `search_history`, `search_history_approved_row`, or approval-only action types for this KPI.
- **Semantics**: `totalDecrypts` (daily bucket and rolled-up summary for the selected range) reflects **per-calendar-day deduplicated row counts** as defined in §2 (dedup key).
- **Log-type filter**: Existing statistics **log type** filter (`LOGIN` / `pb_feplog` / `java_fw_imglog` / empty = sum of configured types) continues to apply; decrypt counts respect the same filter contract as today (`action_detail` association to `logType`).
- **Scope**: Existing **scope=self / team / all** behavior (allowed user list, empty team → no rows, etc.) **must** continue to apply; deduplication is computed **within** the same filtered population of activity rows.
- **Related fix**: Req **`20260408-user-stats-decrypt-count-logtype-json-escape`** (plain `logType` in `action_detail`) remains a **prerequisite** so per-log-type filters can see DECRYPT rows at all.

**Note**: Numeric KPI labels on the UI (e.g. whether to rename “복호화 횟수” to clarify *unique rows per day*) are **optional**; if product confirms copy changes, Frontend must follow design docs for wording.

---

## 2. Design

### 2.1 Security review (PII / audit / access)

- **Data processed**: Aggregation reads only existing `user_activity_log` fields already used for statistics (`action_type`, `action_detail`, `user_id`, timestamps, filters). Dedup keys use **non-content** identifiers already allowed in audit (`logType`, `guid`, `status`, and future row ids per contract).
- **Exposure**: API responses remain **aggregate counts** only; no new per-row listing in statistics.
- **Path**: Security review **recommended** to confirm identifiers in `action_detail` are acceptable for server-side distinct counting and that no new plaintext fields are introduced.

- [ ] Security review performed (recommended)
- Risks: Low if counts only; verify no requirement to expose dedup keys to clients.
- Acceptance: Keep counts only; follow `activity-action-types.spec.yaml` / contract rules for `action_detail` content.

### Technical design

#### Codebase summary (authoritative facts)

- `ActivityStatisticsService` documents and implements statistics **from `user_activity_log`**.
- Decrypt counts today use **`COUNT(*) FILTER (WHERE action_type = 'DECRYPT')`** per day (and per-user breakdown), i.e. **one per audit row**, with no deduplication.
- Log-type filtering for non-`LOGIN` types uses **`action_detail::text LIKE '%"logType":"<id>"%'`** (TEXT column).
- `DecryptController` currently supports **`java_fw_imglog` only**; activity logging stores structured decrypt audit fields under `requestParams` (path `logType` + body map with **`guid`**, normalized **`status`**, optional `searchHistoryId`).
- **`action_detail`** is **TEXT** (not JSONB); PostgreSQL may still cast to `json`/`jsonb` in queries with error handling, or use **documented** parsing rules—Backend **must** choose a safe approach (see Solution).

#### Problem analysis

1. **Wrong semantics for product KPI**: Multiple decrypt executions for the same logical row on the same day inflate the decrypt metric.
2. **Confusion with approval**: Operators may think the KPI tracks approvals; contractually, approval actions use separate action types (e.g. `DECRYPT_APPROVAL_*`) and **must not** be mixed into decrypt **execution** KPIs.
3. **Composite row identity**: For `java_fw_imglog`, **`guid` alone is not unique**; contract requires **`(guid, status)`** as the business row key. Statistics **must** use the same composite as decrypt execution and decryption-allowed store.
4. **`action_detail` shape drift**: Rows missing `guid`/`status`/`logType` cannot form a stable dedup key; policy must avoid silent double-counting.

#### Diagnostic phase

This requirement is a **semantic/aggregation change**, not an error-only fix. **Optional**: Before changing aggregation, Backend may capture **DEBUG-level** samples (counts of DECRYPT rows vs distinct keys per day) in a **non-production** or **dev-only** path to validate volume drift expectations **after** implementing dedup logic.

#### Solution approach

**Data source decision (confirmed in this requirement)**:

- **Primary source**: **`user_activity_log`** rows with **`action_type = 'DECRYPT'`**.
- **Exclude**: Any **approval-only** types (`DECRYPT_APPROVAL_APPROVE`, `DECRYPT_APPROVAL_REJECT`, etc.) and any tables whose primary purpose is **approval workflow** (`search_history`, `search_history_approved_row`), unless product explicitly opens a separate KPI (out of scope here).

**Dedup key (stable, contract-aligned)**:

| Log type (statistics id) | Dedup key components | Notes |
|--------------------------|----------------------|--------|
| `java_fw_imglog` | **`logType` + `guid` + normalized `status`** | Aligns with **`docs/contract.md`** / **`20260320-imagelog-guid-status-composite-key`**. `logType` must match the statistics filter id (`java_fw_imglog`). |
| `pb_feplog` | **TBD until decrypt execution is implemented** for PB | Today `POST /api/logs/decrypt/{logType}` returns **unsupported** for `pb_feplog`. When enabled, dedup key **must** follow **`docs/contract.md`** PB row identity (e.g. numeric **`id`** + **send/recv** or equivalent union discriminator documented for wireframe row keys)—**must not** guess; update this doc + contract when PB decrypt audit shape is fixed. |

**Normalization**:

- **`status`**: Use the same normalization as **`DecryptionRowKey.normalizeStatus`** (or equivalent SQL expression) so logically identical rows are not split across buckets.

**Per-calendar-day rule**:

- Partition `created_at` (or the statistics date field already used in queries) by **calendar date** in the **same timezone semantics** already used for daily statistics.
- **Daily aggregate** (chart / total for the current filter: all included users): for each calendar date, **decrypt count** = **`COUNT(DISTINCT dedup_key)`** over DECRYPT rows with a **complete** dedup key **across all users in scope** (same logical row decrypted by two different users on the same day counts **once** in this aggregate).
- **Per-user breakdown** (`getAllUserStatistics` / user table): for each `user_id`, **decryptCount** = for each calendar date in range, **`COUNT(DISTINCT dedup_key)`** **for that user’s** DECRYPT rows only, then **sum over dates** in range (same user, same row, same day, many clicks → **1** for that user-day).
- **Summary `totalDecrypts`** for a range = **sum of daily aggregate unique counts** over that range (consistent with summing the daily series shown to operators for the whole filtered population). *(If product later requires period-global uniqueness on top of daily buckets, that would be a separate explicit requirement.)*

**Rows with missing key fields**:

- DECRYPT rows where **`logType`**, **`guid`**, or **`status`** cannot be extracted (malformed JSON, legacy rows, partial audits) **must not** be counted toward the deduplicated decrypt KPI **nor** fall back to counting each as 1 (to avoid inflating when keys are missing). Implementers **must** log **DEBUG** aggregate diagnostics (e.g. count skipped) **only** behind dev/debug gates—not noisy **INFO** in production.

**SQL / implementation note (Backend handoff)**:

- Replace raw `COUNT(*) ... DECRYPT` with **`COUNT(DISTINCT ...)`** over an expression built from parsed `action_detail`, or an inner subquery that emits **(date, user_id, dedup_key)** then counts.
- Because `action_detail` is **TEXT**, prefer **`CAST(action_detail AS jsonb)`** inside a guarded expression (invalid cast → treat as unparseable and apply missing-key policy) or a small **Java** layer that only PostgreSQL cannot easily maintain—**Backend chooses** maintainability vs performance; add tests accordingly.
- Ensure dedup works with existing **`LIKE '%"logType":"..."%'`** filter or move to **equivalent JSON extraction** for consistency.

**Frontend**:

- **Must** consume updated counts from API without local double-adjustment.
- **Optional**: If product confirms, clarify labels/tooltips (e.g. *unique rows per day*) per design system.

**Contract / spec**:

- **`docs/contract.md`** / **`docs/api-definition.md`**: Document that activity-statistics **decrypt** counters denote **per-day deduplicated logical rows** from `user_activity_log` DECRYPT audits (not approval counts).
- **`specs/activity-action-types.spec.yaml`**: Note under KPI / OP-02 that **DECRYPT** aggregation for statistics uses **distinct row keys per day** as above.

### Affected scopes and change targets (verification)

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|------------|-----------------------------------------------|
| Backend | Yes | Yes |
| Frontend | Optional (labels only) | Partial |
| DB | No schema change required for v1 | Yes |
| Contract / Spec | Yes (semantics) | Yes |
| Cursor tools (skills, specs) | Optional | Partial |

### Planned change file list (expected change targets)

#### Backend

- `backend/src/main/java/com/logmng/service/ActivityStatisticsService.java` — decrypt aggregation: distinct keys per calendar day; user breakdown consistent with same definition.
- `backend/src/test/java/com/logmng/service/*ActivityStatistics*` — unit tests for dedup, filters, scope, malformed `action_detail`.

#### Contract / documentation

- `docs/contract.md` and/or `docs/api-definition.md` — decrypt KPI semantics.
- `specs/activity-action-types.spec.yaml` — OP-02 / KPI note for DECRYPT.

#### Frontend (optional)

- Activity statistics UI copy / tooltip if product confirms.

---

## 3. Test approach

### Test case list

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification |
|----|-------|------|------------------------------|-----------------|--------------|
| TC-01 | Backend | Normal | Three `DECRYPT` audit rows same `user_id`, same `(logType, guid, status)`, same calendar `created_at` date | That date’s decrypt count increments by **1**, not 3 | Unit (`mvn test`) |
| TC-02 | Backend | Normal | Same key as TC-01 but **two different calendar dates** | **1** per date (total 2 over range) | Unit |
| TC-03 | Backend | Normal | Two DECRYPT rows same day, **different** `status` (same `guid`) | Count **2** for that day | Unit |
| TC-04 | Backend | Edge | DECRYPT row with missing `guid` or `status` or `logType` in `action_detail` | Excluded from deduplicated decrypt count; **DEBUG** diagnostic path only if implemented | Unit |
| TC-05 | Backend | Regression | Rows with `action_type` = `DECRYPT_APPROVAL_APPROVE` / `DECRYPT_APPROVAL_REJECT` (if present) | **Never** included in decrypt KPI | Unit |
| TC-06 | Backend | Normal | `logType` filter = `java_fw_imglog` | Only decrypt events for that log type counted | Unit |
| TC-07 | Backend | Normal | `logType` empty (whole = sum of configured types) | Decrypt subtotals per type still deduped **within** type; sum matches service contract | Unit |
| TC-08 | Backend | Normal | Team scope with user A and user B; **both** decrypt the **same** `(logType, guid, status)` on the **same** day | **Daily aggregate** for that day = **1**; **per-user** decryptCount for A = **1**, for B = **1** | Unit |
| TC-09 | Integration | Normal | Authenticated `GET` activity statistics API for date range after controlled decrypts | Response `totalDecrypts` / daily `totalDecrypts` match seeded expectations | Integration (`curl` + DB seed) |

### Test data

- Seed `user_activity_log` rows with controlled `action_detail` JSON text matching current `ActivityLogAspect` shape (`requestParams.logType`, `requestParams.request.guid` / `.status`).
- Include at least one row simulating legacy garbage JSON for TC-04.

### Test environment

- Backend: `http://localhost:9200`
- Database: PostgreSQL (per project setup)

---

## 4. Checklist

### Frontend verification (if labels change)

- [ ] KPI text matches confirmed copy / design

### Backend verification

- [ ] Unit tests for TC-01–TC-08 pass
- [ ] Integration TC-09 pass
- [ ] No production INFO spam from diagnostics

### Integration

- [ ] Statistics align with Activity Log DECRYPT rows for the same filters (counts differ only by dedup rule, not missing inclusion)

### Documentation

- [ ] Requirement doc completed
- [ ] Contract/spec updated for KPI meaning

---

## 5. Test results

### Test run date

- Pending implementation

### Test results

- Pending

---

**Author**: Requirements (subagent)  
**Date**: 2026-04-08  
**Status**: In progress
