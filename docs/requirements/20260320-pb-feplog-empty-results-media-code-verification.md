# 20260320 - PB FEP Log empty results verification (media code SAAAA100, from 2023)

## 1. User requirement

### Requirement description

On the **PB FEP Log** screen (`pb-feplog` / log type `pb_feplog`), a user searched with **media code `SAAAA100`** and a **date range starting from 2023** (through an appropriate end date). The UI shows **no data** (empty result set). The organization needs to **determine whether there is genuinely no matching data** in the backing store for that combination, or whether the absence is due to an **application, API, or filter bug** (e.g. wrong parameter mapping, date handling, or SQL predicates).

This work is **investigative and verification-first**: the outcome must be a **documented conclusion** (data absent vs defect) supported by evidence (API request/response, DB read-only checks, and logs as needed). **No root cause shall be assumed** before evidence is collected.

### User scenario

1. User opens the **PB FEP Log** screen (same **LogGrid** + **SearchForm** flow as other DB-backed log search; `logType` = `pb_feplog`).
2. User sets **start/end datetime** to cover periods from **2023** onward (and end boundary as used when the issue was observed).
3. User enters **매체코드 (media code)** `SAAAA100` (and any other fields required by the form, e.g. **TR Code** if the UI requires it).
4. User runs search; the grid shows **zero rows** (or total count zero).
5. **Problem**: Stakeholders cannot tell if the environment has **no rows** for that media code and range, or if **something in the stack** filters them out incorrectly.

### Expected outcome

- A **clear, evidence-based statement**: empty results are because **(A)** there are **no matching rows** in `pb_send` / `pb_recv` (or equivalent deployed schema) for the interpreted predicates, **or (B)** a **defect** mis-maps parameters, applies wrong date logic, or otherwise excludes valid rows.
- **Reproduction package**: exact **UI field values**, **HTTP request body** (or browser network capture) for `POST /api/logs/db-refactored/search` (or current contract path), and **response** (`total` / pagination metadata).
- **Read-only DB verification** aligned with backend semantics: filters on `log_timestamp`, `media_code`, and optional `tr_code` / `user_id` (**loginId**) as used by `LogDbService.searchPbFeplog` (UNION of `pb_send` and `pb_recv` with the same predicates on each branch).
- If **(B)** is indicated: follow the project **diagnostic phase** (debug-level or dev-only logging, then fix) per error-fix workflow; record root cause in **§6** when applicable.
- If **(A)** is indicated: document that **no code change** is required for correctness; optional follow-up is **data ingestion / operations** (out of scope unless a separate requirement is opened).

**Note**: Numeric layout and search-field standards (`docs/design/search-fields-by-screen.md`, etc.) are **not** the primary driver of this requirement; the focus is **data presence vs filter correctness**. Pattern **§2.4** (search/filter UI consistency across screens) **does not apply** unless a separate alignment request is made.

## 2. Design

### 2.1 Security review (optional; when PII / decryption / access control)

- [ ] Security review performed (check if applicable)

**Risks**: Tables `pb_send` / `pb_recv` may contain **PII or sensitive payloads** (`request_data`, `response_data`, `user_id`, `ip_address`, etc.). Ad-hoc verification queries must **minimize exposure**.

**Acceptance / recommendations**:

- Prefer **aggregate / count-only** queries on `log_timestamp`, `media_code`, and optional filter columns; avoid selecting **payload columns** in shared logs or ticket comments.
- Run read-only checks with **least privilege** and per organizational **data-handling policy**; do not paste row-level sensitive content into requirement §5/§6.
- API verification uses **authenticated** test users already permitted for **pb-feplog** per permission model.

### Technical design

#### Codebase summary

**Backend**

- Entry: `LogDbController` → `LogDbService.searchLogs(LogDbSearchRequest)`.
- For `logType` **`pb_feplog`**, `searchPbFeplog` builds a **UNION ALL** of `pb_send` and `pb_recv` with:
  - `log_timestamp >= ?` and `log_timestamp <= ?` when start/end are present (from `getStartDateAsDateTime` / `getEndDateAsDateTime`).
  - `media_code = ?` when media code is non-blank (`getMediaCode()`).
  - `tr_code = ?` when TR code is non-blank.
  - `user_id = ?` when login ID filter is non-blank (`getLoginId()`).
- Existing **debug** logging: `searchLogs` logs `logType`, date range, `mediaCode`, `trCode`, `loginId`, page (no production-verbose requirement for this investigation beyond existing levels).

**Request mapping**

- `LogDbSearchRequest` accepts both JSON names **`mediaCode`** and **`media_gb`**, and **`trCode`** / **`tr_code`**; getters resolve aliases so frontend can send either shape.

**Frontend**

- PB FEP Log uses **`SearchForm`** with field **`media_gb`** (label 매체코드) and **`tr_code`** (TR Code, **required** in form validation). Submitted params are spread into **`LogGrid` `handleSearch`** → API body includes `logType: 'pb_feplog'`.
- Screen id **`pb-feplog`** is wired in `App.js` with `LogGrid` and decrypt permission gating per log type.

**Database**

- Operational data lives in **`pb_send`** and **`pb_recv`** (see `LogDbService` SQL). Verification must use the **same** environment the user tested against.

#### Problem analysis

1. **Legitimate empty set**: No rows in `pb_send`/`pb_recv` for `media_code = 'SAAAA100'` within the interpreted time range (and other filters).
2. **Semantic / data issues**: Stored `media_code` differs by **whitespace**, **casing**, or **format** (application uses **exact equality** `media_code = ?`).
3. **Date interpretation**: Mismatch between UI datetime-local values, API date encoding (`SearchForm` formats time to `HHmmssSSS`-style strings for API), and backend parsing into `LocalDateTime` — could narrow or shift the effective window.
4. **Accidental extra filters**: Non-empty **TR Code** or **loginId** narrowing results to zero when the user believed only media code mattered.
5. **Environment mismatch**: UI pointed at a DB or deployment **without** 2023+ data for that media code.

#### Diagnostic phase (mandatory only if classified as application defect after verification)

If evidence shows **valid rows exist** for the same predicates the API should apply, treat the remainder as an **error/bug fix**:

- **Phase 0 (diagnostic):** (1) Add diagnostic (**DEBUG** or dev-only) logs at parameter binding / SQL boundary if needed. (2) Reproduce once and capture logs. (3) Analyze logs to confirm root cause. (4) Only then apply logic/code fix.
- **Production safety:** Diagnostic logs must not run verbosely in production (DEBUG off, feature flag, or removed after verification).

*If investigation concludes **(A) no rows in DB**, skip this subsection for the closing state of this requirement (no defect fix).*

#### Solution approach

Structure by scope. **Phase 1** is **verification only**; code changes are **conditional** on confirmed defect.

**Investigation (all scopes — procedure)**

1. **Reproduce** on the target environment with **recorded** form values and network **request JSON** + **response** (`total`, `page`, sample if policy allows).
2. **Read-only DB**: Run **COUNT**-style checks on `pb_send` and `pb_recv` mirroring service predicates (date bounds, `media_code`, and any other non-empty filters from the request). Compare totals to API `total`.
3. **Sanity checks**: Optionally `COUNT` without `media_code` but with date range; optionally `SELECT DISTINCT media_code` (or prefix counts) **only if approved** and without exposing sensitive columns in artifacts.
4. **Conclusion**: Document **(A)** vs **(B)** in §5 / §6 with evidence references (not raw PII).

**Frontend:** *(only if Phase 1 proves parameter mis-mapping or date submission bug)*

- Verify `SearchForm` → API field names and values for `pb_feplog`; align with `LogDbSearchRequest` and documented API.

**Backend:** *(only if Phase 1 proves server-side predicate or parsing bug)*

- Adjust `LogDbService` / DTO parsing with tests; preserve contract unless explicitly extended.

**DB:** *(no schema change for this verification requirement)*

- Read-only queries only; if ingestion gaps are found, open a separate ops/data requirement.

### Affected scopes and change targets (verification)

Per `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md`:

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|------------|-----------------------------------------------|
| Backend | Yes (possible, after diagnosis) | Yes — investigation + conditional fix |
| Frontend (config UI + view screen) | Yes (possible, after diagnosis) | Yes — view screen / SearchForm only if mapping bug |
| DB | Yes (read-only verification only) | Yes — no migration |
| Contract / Spec | No (unless API shape changes) | N/A |
| Cursor tools (skills, specs) | No | N/A |

**Pattern §2.4 (search/filter UI consistency):** **Does not apply** to this requirement (no cross-screen layout alignment).

### Planned change file list (expected change targets)

**(Planned at authoring. Implementing agent (Step 4) confirms, amends, or replaces this list when scope is known. Investigation may result in no code changes.)**

#### Frontend

- `frontend/src/components/SearchForm.js` — *Only if* verification proves incorrect param names, date serialization, or validation blocking intended searches.
- `frontend/src/components/LogGrid.js` — *Only if* verification proves `pb_feplog` request assembly drops or overwrites filters.

#### Backend

- `backend/src/main/java/com/logmng/service/LogDbService.java` — *Only if* verification proves wrong SQL predicates or date/media handling for `searchPbFeplog`.
- `backend/src/main/java/com/logmng/dto/request/LogDbSearchRequest.java` — *Only if* verification proves JSON mapping / getter resolution bug for `media_gb` / `tr_code`.
- `backend/src/main/java/com/logmng/controller/LogDbController.java` — *Only if* verification proves request handling or logging gaps needed for diagnosis (prefer DEBUG-only).
- `backend/src/test/java/com/logmng/service/LogDbServiceTest.java` — *If* service logic changes; extend scenarios for media code + date range.

#### DB

- None for schema; **read-only** SQL scripts or ad-hoc queries may be attached in §5 as **procedures** (no repo file required).

## 3. Test approach

### Test case list (required)

**Note**: Until a defect is confirmed, most TCs are **Manual / Integration (evidence gathering)**. **Automated** unit/integration tests apply **when** Backend or Frontend code changes are made to fix a confirmed bug.

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification |
|----|-------|------|------------------------------|-----------------|--------------|
| TC-01 | Integration | Normal | PB FEP Log UI: set date range from **2023-01-01** (or user-reported start) through reported end; media **SAAAA100**; TR Code and other fields **exactly as user used**; search | Capture **network** `POST` body and response; record **total** count and HTTP status | Manual / browser (network tab) |
| TC-02 | Integration | Normal | Repeat **same** request as TC-01 via **curl** or API client with same auth session/cookie policy | Response **total** matches TC-01; same `logType` and filter fields | Integration (curl) |
| TC-03 | DB | Normal | Read-only **COUNT** on `pb_send` and `pb_recv` with same date bounds and `media_code = 'SAAAA100'` (and same `tr_code` / `user_id` if non-empty in TC-01) | Sum of counts **matches** API `total` if stack is consistent | Manual (DB client) |
| TC-04 | DB | Edge | If TC-03 is zero: **COUNT** with date range only (no media filter) | If >0, data exists for range but not that **exact** `media_code` (supports hypothesis of legitimate empty or format mismatch) | Manual (DB client) |
| TC-05 | DB | Edge | If zero rows for exact code: check **distinct** `media_code` patterns (trim/case) **only with ops approval** and **without** exporting sensitive payloads | Informs whether stored values differ from `SAAAA100` | Manual (DB client) |
| TC-06 | Backend | Normal | *If defect found:* unit test for `searchPbFeplog` with fixed clock/data — media + date range returns expected count | Assert total and row count | Unit (`mvn test`) |

### Test scenarios

#### Scenario 1: End-to-end reproduction

1. Log in as a user with **pb-feplog** access.
2. Open PB FEP Log; set dates and **SAAAA100**; submit.
3. Save request/response evidence for TC-01/TC-02.

#### Scenario 2: DB cross-check

1. From TC-01, copy parsed `startDate`/`endDate`, `media_gb`/`mediaCode`, `tr_code`/`trCode`, `loginId`.
2. Run TC-03 (and TC-04/TC-05 if needed).
3. Reconcile API `total` with DB counts; record conclusion **(A)** or **(B)**.

### Test data

- Uses **production-like** or **reported** environment; no synthetic requirement unless local reproduction is needed.
- If local H2/sample data is used for **automated** tests after a fix, document **insert** statements in §5 (non-sensitive columns only).

### Test environment

- Frontend: per `docs/contract.md` (e.g. `http://localhost:3001` for local dev).
- Backend: per contract (e.g. `http://localhost:9200`).
- Database: PostgreSQL (or actual deployment DB for TC-01–TC-05); confirm connection targets the same instance the UI uses.

### 3.5 Browser automation verification (optional)

- **Applicable TCs**: TC-01 (network capture may still need manual export depending on tooling).
- **Procedure**: Navigate → login → PB FEP Log → fill form → search → snapshot empty state; attach HAR or documented JSON if policy allows.

## 4. Checklist

### Frontend verification

- [ ] API parameters validated (when Frontend changes)
- [ ] UI behavior confirmed
- [ ] Error handling verified

### Backend verification

- [ ] API test cases written and run (when Backend changes)
- [ ] Logs checked
- [ ] Performance checked (if applicable)

### Integration

- [x] DB cross-check (local): TC-03 / TC-04 executed; see §5
- [ ] End-to-end flow tested (browser + network) on **reported** environment
- [x] Edge case: date range has rows but exact `media_code` has none (local)

### Documentation

- [x] Requirement doc completed (§5 evidence for **local dev DB**; production/staging still requires same SQL on target DB)
- [ ] Code comments added (if applicable)

## 5. Test results

### Test run date

- 2026-03-20 (local PostgreSQL: `jdbc:postgresql://localhost:5432/logmng`, credentials per `backend/src/main/resources/application.yml`)

### Test results

#### Frontend

- **SearchForm** (`frontend/src/components/SearchForm.js`): **TR Code is required** (`tr_code` validation). **매체코드** maps to `media_gb` → API `mediaCode` / `media_gb`. Users who enter a value in **매체코드** are filtering `pb_send` / `pb_recv`.`media_code` (exact match), not `tr_code`.

#### Backend

- `LogDbService.searchPbFeplog` applies `AND media_code = ?` when media is non-blank, and `AND tr_code = ?` when TR code is non-blank (`LogDbService.java` predicates). Semantics match the SQL below.

**Read-only verification SQL (TC-03 / TC-04 style):**

```sql
-- TC-03: same as API when only media filter + start date (adjust end date if UI sends one)
SELECT 'pb_send' AS tbl, COUNT(*) AS cnt FROM pb_send
  WHERE log_timestamp >= TIMESTAMP '2023-01-01' AND media_code = 'SAAAA100'
UNION ALL
SELECT 'pb_recv', COUNT(*) FROM pb_recv
  WHERE log_timestamp >= TIMESTAMP '2023-01-01' AND media_code = 'SAAAA100';

-- TC-04: rows exist in range with other media codes?
SELECT 'pb_send_any_media', COUNT(*) FROM pb_send WHERE log_timestamp >= TIMESTAMP '2023-01-01'
UNION ALL
SELECT 'pb_recv_any_media', COUNT(*) FROM pb_recv WHERE log_timestamp >= TIMESTAMP '2023-01-01';
```

**Local DB results (executed 2026-03-20):**

| Check | Result |
|--------|--------|
| TC-03 `media_code = 'SAAAA100'`, from 2023-01-01 | `pb_send`: **0**, `pb_recv`: **0** |
| TC-04 date range only (2023+) | `pb_send`: **33**, `pb_recv`: **33** |
| `media_code = 'SAAAA100'` all time | **0** / **0** |

**Repository seed note (`backend/src/main/resources/db/init-data.sql`):** sample PB rows use **single-letter** `media_code` (`A`, `B`, `C`) and put codes such as **`SAAAA100` in `tr_code`** (send) / `RAAAA100` (recv), not in `media_code`. If production data follows the same convention, searching **매체코드** = `SAAAA100` yields **no rows by design**; the value may belong in **TR Code**.

**Commands:**

```bash
# After any Backend code change
cd backend && mvn test

# Optional: scripted API check (replace cookie/body with values from TC-01)
# curl -s -b "JSESSIONID=..." -H "Content-Type: application/json" \
#   -d '{ ... LogDbSearchRequest JSON ... }' \
#   http://localhost:9200/api/logs/db-refactored/search
```

**Outcome:**

- **Local dev environment — (A) No matching rows** for `media_code = 'SAAAA100'` in the interpreted range; the empty grid is **consistent with the database**, not evidence of an API/SQL bug for that filter.
- **Field semantics:** Strong likelihood of **column confusion** (value `SAAAA100` entered as **매체코드** while stored as **TR Code** in sample schema). **Production/staging:** run the same read-only SQL on the **same DB instance** the UI uses to confirm; counts must match API `total` for a definitive **(A)/(B)** there.

### Issues found and resolution

- **None (defect not indicated on local DB).** If production COUNT matches API `total` at zero for `media_code = 'SAAAA100'`, no code change; train users or document which column holds `SAAAA100`-style values. If production COUNT > 0 but API `total` = 0, treat as **(B)** and proceed with diagnostic phase per §2.

### Next steps

1. On **the environment where the issue was reported**, run TC-01 (network capture) and TC-03 SQL; reconcile with API `total`.
2. If **(B)** on that environment, open Backend handoff with diagnostic logs. If **(A)**, close; optional ops follow-up for ingestion or field labeling UX.

## 6. Error remedy result (cause and action) — for error/bug fix requirements only

Fill when a **confirmed defect** was fixed (see `docs/template/ERROR_FIX_RESULT_TEMPLATE.md`).

- **Requirement ID**: 20260320-pb-feplog-empty-results-media-code-verification
- **Root cause**: `SearchForm` sent `startDate` / `endDate` as **time-only** strings (`HHmmssSSS`). `LogDbSearchRequest.parseDateTime` treats digit-only input as **time on `LocalDate.now()`**, so the UI’s chosen **calendar range (e.g. from 2023)** was ignored; queries were scoped to **today only**, yielding **zero rows** for historical PB FEP data and making **TR Code** searches appear broken.
- **Actions taken**: Align `SearchForm` `formatDateForAPI` with `ImageLogSearchForm`: send **`yyyy-MM-dd HH:mm:ss`** so backend applies the correct date bounds. File: `frontend/src/components/SearchForm.js`.
- **Result**: PB FEP Log searches use the user-selected start/end dates; TR Code + wide date range can return rows when DB data falls in that range. `npm test -- --watchAll=false` — pass (2026-03-20).
- **Completed**: 2026-03-20

---

**Author**: Requirements (subagent)  
**Date**: 2026-03-20  
**Status**: Local DB verified (§5); **SearchForm date bug fixed** (§6). Re-verify on target env after deploy.

---

## Final version (Korean)

**로컬 개발 DB 기준 결론:** `media_code = 'SAAAA100'` 이고 기간이 2023-01-01 이후인 행은 `pb_send` / `pb_recv` 모두 **0건**이었습니다. 같은 기간에 **매체코드 조건 없이** 조회하면 양쪽 테이블에 각각 **33건**이 있어, “데이터가 아예 없다”가 아니라 **해당 매체코드 값으로는 행이 없다**는 뜻입니다.

**주의:** 샘플 시드(`init-data.sql`)에서는 `SAAAA100`이 **매체코드가 아니라 TR Code(`tr_code`)** 쪽에 들어가 있습니다. 화면에서 **매체코드** 칸에 `SAAAA100`을 넣으면 DB의 `media_code`와만 비교하므로 **0건이 정상 동작**일 수 있습니다. 실제 업무 DB에서도 동일한지는 §5의 SQL을 **그 DB**에서 실행해 API `total`과 맞춰 확인해야 합니다.

**추가 (TR Code로도 안 나오던 경우):** PB FEP용 `SearchForm`이 날짜를 **시각만** 보내고 있어 백엔드가 **항상 “오늘”** 기준으로만 범위를 잡았습니다. 2023년부터로 넓혀도 실제 쿼리는 당일만 조회되어 과거 로그·TR Code 검색이 0건으로 보일 수 있었습니다. §6대로 `yyyy-MM-dd HH:mm:ss` 전송으로 수정했습니다.
