# 20260318 - Search history create server error (bugfix)

## 1. User requirement

### Requirement description

When the user clicks **"복호화 승인 요청"** (decryption approval request), enters a request reason, and submits, the UI shows **"서버에서 오류가 발생했다"** or **"서버 오류가 발생했습니다"** instead of success. The user expects the search to be saved to search history (POST /api/search-history) and to see a success message (e.g. "저장되었습니다").

### User scenario

1. User performs a search (e.g. image log) and decides to request decryption approval.
2. User clicks "복호화 승인 요청" and enters a request reason in the modal/form.
3. User submits the form.
4. **Problem**: The UI displays a server error message ("서버에서 오류가 발생했다" or "서버 오류가 발생했습니다"); the request does not complete successfully.
5. **Expected**: The backend creates a search history row (POST /api/search-history returns 201 with an id), and the UI shows success (e.g. "저장되었습니다").

### Expected outcome

- **POST /api/search-history** with a valid body `{ logType, searchParams, requestReason }` and valid auth completes successfully: **201** and response includes the created id (and relevant fields).
- The user sees a **success message** in the UI (e.g. "저장되었습니다") after submitting the decryption approval request.
- If the failure was due to **client/validation** (e.g. missing required field), the backend returns **400** with a clear reason where appropriate, instead of a generic 500.
- Root cause is identified and fixed so the "복호화 승인 요청" flow is reliable.

---

## 2. Design

### 2.1 Security review (optional)

- [ ] Security review performed (not required for this bugfix; flow is existing create with auth)

### Technical design

#### Problem analysis

1. **Observed behaviour**: Frontend calls **POST /api/search-history** with body `{ logType, searchParams, requestReason }`. The backend returns **500** and the GlobalExceptionHandler maps the exception to a response with message "서버 오류가 발생했습니다." (or similar), which the frontend shows as "서버에서 오류가 발생했다".
2. **Backend flow (summary)**: `SearchHistoryController.create()` obtains `userId` via `getCurrentUserId()`, then calls `searchHistoryService.create(userId, request)`. The service INSERTs into `search_history` (user_id, log_type, search_params, request_reason, requested_at, expires_at, approval_status, …). On **SQLException** the service throws `RuntimeException("검색 이력 저장 중 오류가 발생했습니다: " + e.getMessage())`, which is caught by GlobalExceptionHandler and returned as **500** with a generic server error message.
3. **Root cause (TBD by Backend)**: The actual exception must be identified via logs or reproduction. Possible causes include:
   - **DB schema mismatch**: e.g. `search_history` missing `request_reason` column, or `user_id` still VARCHAR while code binds Long.
   - **FK violation**: `user_id` not present in `app_user` (e.g. wrong type or value).
   - **Request body validation**: `searchParams` (or other required field) null or invalid leading to NPE or constraint violation.
   - **Other**: SQL constraint, encoding, or unexpected NPE in the create path.

#### Solution approach

**Backend (primary):**

1. **Identify the actual exception**: Reproduce the flow or inspect server logs to determine the exact exception (e.g. SQLException message, constraint name, NPE stack trace).
2. **Fix the cause**:
   - If **schema**: ensure `search_history` has `request_reason` (and correct type) and `user_id` is BIGINT referencing `app_user(id)`; apply or verify migration (e.g. `migrate-search-history-user-id-to-bigint.sql`, request_reason column migration) per `backend/DB_SETUP_GUIDE.md` and schema.
   - If **validation**: validate request body (e.g. logType, searchParams required; requestReason optional or required per product) and return **400** with a clear error code/message for invalid or missing required fields instead of letting NPE or DB constraint surface as 500.
   - If **FK or data**: ensure `getCurrentUserId()` returns a valid `app_user.id` and that the insert uses that value correctly; fix any type mismatch (e.g. Long vs VARCHAR).
3. **Optionally improve error response**: Where appropriate (e.g. missing required field, invalid format), return **400** with a specific reason or error code so the client can show a clearer message; avoid generic 500 for client-side mistakes.

**Frontend:**

- No change **unless** the contract or response shape changes (e.g. new error code or 400 payload). If Backend introduces a 400 with a specific code/message for validation failures, Frontend may surface that message in the UI; this is optional and can be a follow-up.

**DB:**

- Only if the root cause is schema: ensure migrations are applied (request_reason column, user_id BIGINT). No new migration is required if existing migrations already provide the correct schema; verification only.

### Affected scopes and change targets (verification)

**Before finalizing §2**, the Requirements author has run the change target checklist per `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md`.

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|-----------|-----------------------------------------------|
| Backend | Yes | Yes (investigation, fix, optional 400 improvement) |
| Frontend (config UI + view screen) | No (unless request/response shape changes) | N/A |
| DB | Only if schema fix needed (migration verification/apply) | Yes (migration scripts referenced) |
| Contract / Spec | Optional (if error response or 400 shape is documented) | Optional |
| Cursor tools (skills, specs) | No | — |

**Change target verification:** This is a bugfix with root cause TBD by Backend. Primary scope is **Backend**. Frontend is affected only if the API contract or error response shape changes (e.g. new 400 body). DB is affected only if a schema migration is missing or must be applied.

### Planned change file list (expected change targets)

**(Planned at authoring. Implementing agent (Step 4) confirms or amends this list when implementation is complete.)**

#### Frontend

- No change unless Backend introduces a new error response shape (e.g. 400 with code) that the UI should display. If so, update the component that calls POST /api/search-history and shows the error message.

#### Backend (confirmed)

- **Identify and fix**
  - `backend/src/main/java/com/logmng/controller/SearchHistoryController.java` — No change; create() already uses @Valid and getCurrentUserId(); IllegalArgumentException already returns 400.
  - `backend/src/main/java/com/logmng/service/SearchHistoryService.java` — **Changed**: added defensive check for `searchParams == null` and throw IllegalArgumentException so controller returns 400.
  - DTO `SearchHistoryCreateRequest` already has @NotNull(searchParams), @NotBlank(logType); no change.
- **Optional**
  - GlobalExceptionHandler — No change; validation already returns 400 via MethodArgumentNotValidException and IllegalArgumentException.
  - docs/api-definition.md — No new error codes; 400/VALIDATION_ERROR already documented for validation failures.
- **Tests**
  - `backend/src/test/java/com/logmng/controller/SearchHistoryControllerTest.java` — **Changed**: added `create_whenSearchParamsMissing_returns400`, `create_whenLogTypeMissing_returns400` (TC-02).
  - `backend/src/test/java/com/logmng/service/SearchHistoryServiceTest.java` — **Changed**: added `create_whenSearchParamsNull_throwsIllegalArgumentException`.
  - TC-01 (valid → 201) covered by existing `create_withRequestReason_returns201`.

#### DB

- Verify or apply existing migrations only (no new file required unless a new migration is needed):
  - `request_reason` column on `search_history` (see e.g. migrate-search-history-request-reason or schema.sql).
  - `search_history.user_id` BIGINT (see migrate-search-history-user-id-to-bigint.sql, SearchHistoryUserIdMigrationCheck).

---

## 3. Test approach

### Test case list (required)

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|----|-------|------|------------------------------|-----------------|--------------------------------------------|
| TC-01 | Backend | Normal | POST /api/search-history with valid body { logType, searchParams, requestReason } and valid auth | 201; response includes id (and requestedAt, approvalStatus, etc.); DB row exists with user_id, request_reason set | Unit or Integration (mvn test or curl) |
| TC-02 | Backend | Exception | POST /api/search-history with missing required field (e.g. logType or searchParams null/absent) | 400 with clear reason/code (not 500) | Unit or Integration |
| TC-03 | Backend | Edge | POST /api/search-history with invalid user (e.g. userId not in app_user) | 401 or 400 as appropriate (not 500 with generic message) | Unit or Integration |
| TC-04 | Integration | Normal | Manual: user clicks "복호화 승인 요청", enters request reason, submits | UI shows success (e.g. "저장되었습니다"); search history list shows the new row | Manual / browser |

### Test scenarios

#### Scenario 1: Happy path (create success)

1. Log in as a user with search-history access.
2. Perform a search and open "복호화 승인 요청".
3. Enter request reason and submit.
4. **Verification**: Response 201; UI shows success message; GET /api/search-history includes the new row with requestReason.

#### Scenario 2: Validation error (optional after fix)

1. Send POST /api/search-history with body missing logType or searchParams (or invalid shape).
2. **Verification**: 400 with error message/code; no 500.

#### Scenario 3: After fix – full flow

1. After Backend fix and deployment, repeat Scenario 1.
2. **Verification**: No "서버 오류가 발생했습니다"; flow completes and user sees "저장되었습니다" (or equivalent).

### Test data

- At least one `app_user` with permission to create search history (e.g. search-history screen access).
- DB schema with `search_history` containing `user_id` (BIGINT), `request_reason` (TEXT or VARCHAR), and other required columns per schema.sql.

### Test environment

- Frontend: http://localhost:3001 (or per contract)
- Backend: http://localhost:9200
- Database: PostgreSQL (per project)

---

## 4. Checklist

### Frontend verification

- [ ] API parameters validated (no change expected unless contract changes)
- [ ] UI shows success after fix; error message behaviour unchanged or improved if 400 is returned

### Backend verification

- [ ] Root cause identified (logs or reproduction)
- [ ] API test cases (TC-01, TC-02, TC-03) written and run
- [ ] Logs checked (no unnecessary stack traces for client errors after fix)

### Integration

- [ ] End-to-end "복호화 승인 요청" flow tested (TC-04)
- [ ] Edge cases (missing/invalid body) return 400 where appropriate

### Documentation

- [ ] Requirement doc completed
- [ ] §6 Error remedy result filled after fix

---

## 5. Test results

### Test run date

- [To be filled after implementation]

### Test results

#### Backend

- [To be filled]

#### Integration

- [To be filled]

**Commands:** (one per TC, to be filled by QA or implementer)

```bash
# TC-01 example (after login cookie obtained):
# curl -s -w "\nHTTP %{http_code}\n" -X POST -b cookies.txt -H "Content-Type: application/json" \
#   -d '{"logType":"java_fw_imglog","searchParams":{"logType":"java_fw_imglog","startDate":"2026-01-01 00:00:00","endDate":"2026-12-31 23:59:59","page":1,"pageSize":10},"requestReason":"테스트 사유"}' \
#   http://localhost:9200/api/search-history
```

**Outcome:** [To be filled]

### Issues found and resolution

- [To be filled when applicable]

### Next steps

1. Backend: reproduce and identify exception; fix cause; add/run tests.
2. QA: run TC-04 (manual flow) after fix.
3. Record root cause and actions in §6.

---

## 6. Error remedy result (cause and action) — for error/bug fix requirements only

Record root cause and actions under the **same requirement ID (this document)**. Template: `docs/template/ERROR_FIX_RESULT_TEMPLATE.md`.

- **Requirement ID**: 20260318-search-history-create-server-error-bugfix
- **Root cause (from log analysis)**: Backend log files were **not found** at `backend/logs/` or project root `logs/` (Spring Boot logs to `logs/application.log` relative to process CWD per `application.yml`). So the **exact exception message** could not be read. From code and schema review, the 500 is thrown when `SearchHistoryService.create()` catches `SQLException` and wraps it in `RuntimeException`. The **most likely causes** are:
  1. **DB schema not migrated**: `search_history` table missing `request_reason` column → PostgreSQL error e.g. "column request_reason does not exist" (SQLState 42703). Apply `migrate-search-history-request-reason.sql`.
  2. **user_id still VARCHAR**: If `user_id` was not migrated to BIGINT, type mismatch or FK failure can occur. Apply `migrate-search-history-user-id-to-bigint.sql` (after request_reason).
  3. **FK violation**: `user_id` value not present in `app_user(id)` (e.g. session user id not in DB) → SQLState 23503.
- **Actions taken**: (1) **Logging**: In `SearchHistoryService.create()`, SQLException is now logged with `SQLState`, `errorCode`, and message so that when the error recurs, `logs/application.log` (or console) shows the exact DB error. (2) **Apply block** (see below) for the user to run migrations and, if 500 persists, capture and share the log line containing SQLState/message. (3) Previous defensive validation and tests (searchParams null → 400, TC-02) remain in place.
- **Result**: Unit tests pass. If 500 still occurs after applying migrations, the new log line will show the exact cause (e.g. column name, constraint name).
- **Apply (user)** — run from **project root** with DB credentials that have DDL rights (e.g. `logmng` or `postgres`):
  ```bash
  # 1) Add request_reason if missing (idempotent)
  psql -U logmng -h localhost -p 5432 -d logmng -f backend/src/main/resources/db/migrate-search-history-request-reason.sql

  # 2) Migrate user_id VARCHAR → BIGINT if needed (idempotent if already BIGINT)
  psql -U logmng -h localhost -p 5432 -d logmng -f backend/src/main/resources/db/migrate-search-history-user-id-to-bigint.sql
  ```
  Then restart the backend and retry "복호화 승인 요청". If the error persists, send the **backend log line** that contains "검색 이력 저장 실패" and the SQLState/message (from `logs/application.log` or console).
- **Completed**: 2026-03-18 (Backend implementation); §6 updated after log analysis (no log file found; logging improved and Apply block provided)

---

## 7. Final version (Korean) — add after all verification is complete

After QA has completed verification and before or with the final commit, add a **Korean summary** here. See `docs/workflow/DOCUMENT-LANGUAGE-POLICY.md` §2.3.

### Final Korean summary

- **Requirement description**: [복호화 승인 요청 제출 시 서버 오류 발생 버그 수정]
- **Expected outcome**: [검색 이력 정상 저장 및 성공 메시지 표시]
- **Verification result**: [To be filled]

---

**Author**: Requirements subagent  
**Date**: 2026-03-18  
**Status**: In progress
