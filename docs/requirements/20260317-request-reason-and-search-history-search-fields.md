# 20260317 - Request reason and Search History search fields

## 1. User requirement

### Requirement description

1. **Request reason (요청 사유)**
   - When a user requests decryption approval (복호화 승인 요청), they must be able to enter a **request reason** (요청 사유). The system stores this reason with the search-history record and uses it for audit and search on the Search History screen.

2. **Search History screen — new search fields**
   - Add the following search/filter fields to the Search History screen, following the **common design rules** defined in `docs/design/search-fields-by-screen.md` and `docs/design/search-field-definition-items.md`:
     - **요청일시 (Request date/time)**: Start and end range (datetime). Filter list by `requested_at` within the range.
     - **복호화 승인 여부 (Decryption approval status)**: Multi-select for individual statuses (e.g. 승인, 반려, 대기, 만료) with a **“모두선택” (Select all)** option. Filter list by `approval_status` IN selected values.
     - **요청사유 (Request reason)**: Text input with **like (partial)** search. Filter list by request reason text (e.g. `ILIKE %value%`).

Field sizing, layout, and block structure must follow the same definition items and compact variant as the existing Search History requester block and as defined for activity-log/statistics where applicable (e.g. date range min 140px / max 220px, height 34px, padding 6px 8–10px per design docs).

### User scenario

1. User opens the main search screen (검색하기), enters search criteria, and clicks **복호화 승인 요청**.
2. **Problem (current)**: There is no way to enter why the user is requesting decryption approval.
3. **Expected**: A request-reason input (or modal) is shown; user enters 요청 사유 and submits. The reason is stored and displayed in Search History list/detail.
4. User opens **검색 이력 (Search History)** and wants to filter by:
   - Request date/time range (요청일시 시작 ~ 종료).
   - Approval status (승인 / 반려 / 대기 / 만료), with option to select all.
   - Request reason text (요청사유 부분 검색).
5. **Problem (current)**: Only requester filters (부서, 사용자명, 사용자 ID) exist; no 요청일시, approval status, or request-reason filters.
6. **Expected**: Toolbar includes the new filters; search and reset apply them; list results are filtered accordingly. New fields use the same control sizing and layout rules as in the design docs (search-field-definition-items.md §1, §4; search-fields-by-screen.md §4; forms-and-filters.md compact variant).

### Expected outcome

- **Request reason**
  - On “복호화 승인 요청”, user can enter 요청 사유 (required or optional per product; recommend required). Value is sent in POST body and stored in `search_history.request_reason` (or equivalent column). List and detail APIs return request reason; grid/detail can show it.
- **Search History search form**
  - **요청일시**: Start and end datetime inputs; list filtered by `requested_at` between the two values (inclusive). Same control sizing as date/datetime fields in `docs/design/search-field-definition-items.md` (§3, §4.5) and search-fields-by-screen.md (e.g. min 140px, max 220px, 34px height).
  - **복호화 승인 여부**: Multi-select or checkboxes for PENDING, APPROVED, REJECTED, EXPIRED; “모두선택” selects all. Empty selection means no filter (all statuses). List filtered by `approval_status` IN selected.
  - **요청사유**: Single text input; list filtered by request reason with like/partial semantics (e.g. backend `ILIKE %value%`). Same width rules as other text “기타 조건” fields (min 100px, max 200px per design doc).
  - Toolbar remains single compact row (or wraps per forms-and-filters.md); requester block + new block (요청일시, 승인 여부, 요청사유) + 검색 + 초기화. Panel width consistent with activity-log/statistics (max-width 1400px). New fields must follow the same width/height/padding as defined in the design docs for this screen.

**Note**: Numeric and structural values (width, height, padding, row layout) are **sourced from** `docs/design/search-fields-by-screen.md`, `docs/design/search-field-definition-items.md`, and `docs/design/forms-and-filters.md`; this requirement **references** them. When adding search/filter fields, §1 explicitly references both `docs/design/search-fields-by-screen.md` and `docs/design/search-field-definition-items.md` so implementers apply the same field schema.

## 2. Design

### 2.1 Security review

**Classification (PII / sensitivity)**  
- **Request reason (요청 사유)** is free text entered by the requester and may contain business justification, case references, or other context. It is **potentially sensitive** and may qualify as PII or business-sensitive data depending on organizational policy (e.g. if users enter names or identifiers).  
- Treat it in line with existing **rejection_reason**: same storage and access rules, and same care for logging and display.

**Access control**  
- Visibility and filtering of `request_reason` MUST follow the **existing search-history scope** only. No new permission or API is introduced.  
- **Who can see/filter by request reason**:  
  - Same as who can see the search-history list/detail: users with search-history screen access, subject to **scope** (self / team / all) and **requester/approver** rules per `docs/contract.md` and `specs/permission-group-hierarchy.spec.yaml`.  
  - List and detail APIs return `requestReason` only for rows the caller is already allowed to see. The new filter `requestReason` (like search) only narrows that same allowed set; it must not expand visibility.  
- Implementers MUST NOT add any separate permission or role for viewing/filtering request reason; reuse the existing search-history list/detail authorization.

**Audit trail**  
- Storing `request_reason` in `search_history` is part of the **approval audit trail** (why decryption was requested).  
- No separate audit log is required for the field itself; existing audit or access logging that covers search-history list/detail access implicitly covers `request_reason`.  
- Retention and backup of `search_history` MUST include `request_reason` with the same policy as the row (no early deletion of this column alone).

**Constraints for implementers (§2)**  
- **Length**: Cap request reason at a defined maximum (e.g. 500 or 1000 characters) in API validation and DB (TEXT or VARCHAR(n)); document the limit in `docs/contract.md` and API definition. Reject or truncate (per product) when exceeded.  
- **Sanitization**: Store as plain text. Reject or strip control characters and avoid storing HTML/script. If rendered in UI, escape on output to prevent XSS (same as other user-sourced text).  
- **Logging**: Do **not** log the full `request_reason` value in application logs (aligned with `docs/security-guide.md`: avoid PII/sensitive data in logs). Log only metadata (e.g. request id, presence/length) for troubleshooting.  
- **Export/backup**: Any export or backup that includes `search_history` MUST treat `request_reason` with the same sensitivity as the rest of the row (access control and retention).

Implementers must apply the length, sanitization, and logging rules above when implementing §2 Technical design (Backend, Frontend, Contract).

### Technical design

#### Codebase summary

- **Backend**
  - **SearchHistoryController**: POST `/api/search-history` uses `SearchHistoryCreateRequest` (logType, searchParams only). GET `/api/search-history` list accepts `department`, `username`, `userId`, `page`, `pageSize`, `sortField`, `sortDirection`; builds `SearchHistoryListRequest` and calls `SearchHistoryService.list(listRequest)`.
  - **SearchHistoryService**: `create(userId, request)` INSERTs into `search_history` (user_id, log_type, search_params, requested_at, expires_at, approval_status, …); no request_reason column. `list(SearchHistoryListRequest)` uses `buildListQuerySpec(request)` to add conditions for allowedUserIds, userId, department, username only; no requested_at range, approval_status IN, or request_reason like.
  - **search_history** table (schema.sql): id, user_id, log_type, search_params, requested_at, expires_at, approval_status, approved_by*, rejected_by*, rejection_reason, etc. No `request_reason` column.
- **Frontend**
  - **LogGrid.js**: “복호화 승인 요청” calls `createSearchHistory(logType.id, toSave)` with no request reason; no UI to collect it.
  - **searchHistoryService.js**: `createSearchHistory(logType, searchParams)` POSTs `{ logType, searchParams }`. `getSearchHistoryList({ page, pageSize, sortField, sortDirection, department, username, userId })`; no requestedAtFrom/To, approvalStatus, or requestReason params.
  - **SearchHistoryList.js**: Toolbar has `UserContextFilterBlock` (요청자: 부서, 사용자명, 사용자 ID) + 검색 + 초기화. State: requesterFilters, appliedRequesterFilters, page, pageSize, sortConfig. loadList() passes only requester params + pagination/sort to getSearchHistoryList. No 요청일시, 승인 여부, or 요청사유 fields.
- **Design docs**
  - `docs/design/search-fields-by-screen.md` §4 defines Search History **requester block** only (department, username, userId). No definition yet for 요청일시, 승인 여부, 요청사유.
  - `docs/design/search-field-definition-items.md` defines definition items (fieldId, label, controlType, block, width, height, padding, etc.) and §4.5 width by max character count / role.

#### Contract/spec constraints (for §2 and implementers)

- **Param names (API)**  
  - POST body: `requestReason` (camelCase).  
  - GET list query: `requestedAtFrom`, `requestedAtTo`, `approvalStatus` (multi), `requestReason`.  
  - List/detail response: `requestReason` (string | null).

- **Date format**  
  - Request query params `requestedAtFrom`, `requestedAtTo`: **yyyy-MM-dd HH:mm:ss** (same as activity-log and log search in api-definition).  
  - Response `requestedAt`: **yyyy-MM-dd'T'HH:mm:ss** (unchanged).

- **approvalStatus (multi-value)**  
  - Use **repeated query parameter**: `approvalStatus=PENDING&approvalStatus=APPROVED`.  
  - Do not use comma-separated. Allowed values: PENDING, APPROVED, REJECTED, EXPIRED. Empty = no filter.

- **requestReason**  
  - **Max length**: 500 characters (product may configure; document in contract/spec). Overlength → 400.  
  - **Optional vs required**: Product decision; recommend required for audit. Contract/spec state "optional or required (product decision)".

- **Authority**: `docs/contract.md` (search_history request reason and list query ref); `docs/api-definition.md` §6.1.1 (POST body), §6.1.2 (GET query + list response), §6.1.4 (detail response).

#### Problem analysis

1. Request reason is not collected at approval-request time and not stored; list/detail cannot display or filter by it.
2. Search History list API and UI do not support filtering by requested-at range, approval status, or request reason; users cannot narrow the list by these criteria.
3. New search fields must be defined in the design docs and implemented with consistent sizing/layout (search/filter UI consistency per REQUIREMENTS-CHANGE-TARGET-CHECKLIST §2.4).

#### Solution approach

**DB**

- Add column `request_reason TEXT NULL` (or VARCHAR(n) per product) to `search_history`. Migration script idempotent (ADD COLUMN IF NOT EXISTS). No change to init-data unless sample rows need a reason.

**Backend**

- **SearchHistoryCreateRequest**: Add optional (or required) field `requestReason` (String). Validate length if capped.
- **SearchHistoryService.create**: In INSERT, include `request_reason`; bind from `request.getRequestReason()`. RETURNING and list/detail SELECTs include `request_reason`; map to response as `requestReason`.
- **SearchHistoryListRequest**: Add `requestedAtFrom`, `requestedAtTo` (e.g. String or Instant; API contract in yyyy-MM-dd HH:mm:ss or ISO); add `approvalStatuses` (List<String>); add `requestReason` (String, for like).
- **SearchHistoryController.list**: Parse and pass `requestedAtFrom`, `requestedAtTo`, `approvalStatus` (multi: e.g. repeated param or comma-separated), `requestReason` into SearchHistoryListRequest.
- **buildListQuerySpec**: Add conditions: if requestedAtFrom present, `sh.requested_at >= ?`; if requestedAtTo present, `sh.requested_at <= ?` (or < next day for end-of-day); if approvalStatuses non-empty, `sh.approval_status IN (?, ...)`; if requestReason has text, `sh.request_reason ILIKE ?` with `%value%`. List SELECT and row mapping include `request_reason` → `requestReason`.
- **Detail API**: GET `/api/search-history/{id}` response already includes fields from SELECT; add `requestReason` from `request_reason`.
- **Contract/docs**: Update `docs/contract.md` and `docs/api-definition.md` for POST body (requestReason), GET list params (requestedAtFrom, requestedAtTo, approvalStatus, requestReason), and list/detail response (requestReason).

**Frontend**

- **Main (LogGrid)**: Before calling createSearchHistory, collect request reason (required): e.g. inline text field above the button or modal. Pass requestReason into createSearchHistory(logType, searchParams, requestReason); service POST body becomes `{ logType, searchParams, requestReason }`.
- **searchHistoryService**: createSearchHistory(logType, searchParams, requestReason); getSearchHistoryList accept requestedAtFrom, requestedAtTo, approvalStatuses (array), requestReason and send as query params (repeated or comma-separated per contract).
- **SearchHistoryList**: Add state for requestedAtFrom, requestedAtTo, approvalStatuses (e.g. ['PENDING','APPROVED','REJECTED','EXPIRED']), requestReason. Add UI block for:
  - 요청일시: two datetime-local (or date) inputs (시작일시, 종료일시); validation start ≤ end.
  - 복호화 승인 여부: checkboxes (대기, 승인, 반려, 만료) + “모두선택”; store selected list; when “모두선택”, pass all four or omit filter per product.
  - 요청사유: single text input (like search).
  Apply same control sizing and compact layout per design docs (search-field-definition-items.md §1, §4; search-fields-by-screen.md §4; forms-and-filters.md). On search submit, set applied filters and page=1; on reset, clear these and requester filters. loadList() passes new params to getSearchHistoryList.
- **Grid**: Optionally add column for request reason (요청사유) if product wants it in the grid; otherwise show in detail modal only.

**Design docs**

- **search-fields-by-screen.md**: Extend §4 (검색 이력) with a new table or rows for 요청일시(시작/종료), 복호화 승인 여부, 요청사유: fieldId, label, controlType, block (e.g. extra or date-period + extra), width/height/padding per definition-items, placeholder/emptyOption for 승인 여부 “모두선택”. Toolbar structure: requester block + (요청일시 범위, 승인 여부, 요청사유) + 검색 + 초기화.

**Implementation note for Frontend (pattern §2.4 — search/filter UI consistency)**

When implementing the new Search History search fields (요청일시, 복호화 승인 여부, 요청사유), apply the following:

Before changing form/filter CSS or component layout, read `docs/design/search-field-definition-items.md` (§1 definition items, §4 cross-field rules), `docs/design/search-fields-by-screen.md` (per-screen tables for the affected screen, including the new §4 definitions for search-history), and `docs/design/forms-and-filters.md` (§ Single row for non-date, § Form per mode, § Width by role, § Compact variant). Apply width, height, padding, gap, and layout/structural rules from those docs; verify requirement §2 numeric excerpts against the docs. If any required standard for layout, field sizing, spacing, or control semantics is not defined or is ambiguous in the design docs, the implementer must not infer or hardcode a solution; the implementer must inform the user of the undefined standard items, explain why each is needed, propose a recommended standard draft, and request feedback before implementation proceeds.

### Affected scopes and change targets (verification)

Per `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md`:

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|-----------|-----------------------------------------------|
| Backend | Yes | Yes |
| Frontend (config UI + view screen) | Yes (view: main + search-history) | Yes |
| DB | Yes | Yes |
| Contract / Spec | Yes | Yes |
| Cursor tools (skills, specs) | Yes (skills if domain model changes) | Yes |

Pattern §2.4 (search/filter UI consistency) applies: new search fields on Search History must follow design docs. Implementation note for Frontend is included above. Design doc references are in §1 and §2. Change file list below includes design docs and frontend components.

### Planned change file list (expected change targets)

**(Planned at authoring. Implementing agent (Step 4) confirms or amends this list when implementation is complete.)**

#### Frontend

- `frontend/src/components/LogGrid.js`
  - Add UI to collect 요청 사유 before “복호화 승인 요청”; call createSearchHistory with requestReason.
- `frontend/src/services/searchHistoryService.js`
  - createSearchHistory(logType, searchParams, requestReason); body include requestReason. getSearchHistoryList: add params requestedAtFrom, requestedAtTo, approvalStatuses, requestReason; send as query params.
- `frontend/src/components/SearchHistory/SearchHistoryList.js`
  - Add state and UI for 요청일시 (시작/종료), 복호화 승인 여부 (multi-select + 모두선택), 요청사유 (text). Apply design doc sizing/layout. Pass new params to getSearchHistoryList; reset page on search/reset. Optionally add 요청사유 column to grid or show in detail.
- `frontend/src/components/SearchHistory/SearchHistory.css`
  - Styles for new filter block if needed; prefer `search-filter-standard.css` and compact panel classes.
- `frontend/src/components/SearchHistory/SearchHistoryList.test.js`
  - Add or extend tests for new filters (params sent, reset, optional column).
- `frontend/src/components/LogGrid.css`
  - Styles for 요청 사유 block (`.log-grid-request-reason`).

**Frontend implementation confirmed (2026-03-17).** Actual files changed: LogGrid.js, LogGrid.css, searchHistoryService.js, SearchHistoryList.js, SearchHistory.css, SearchHistoryList.test.js. Design doc: search-fields-by-screen.md §4.2 added.

#### Backend

- `backend/src/main/java/com/logmng/dto/request/SearchHistoryCreateRequest.java`
  - Add field requestReason (String); getter/setter.
- `backend/src/main/java/com/logmng/dto/request/SearchHistoryListRequest.java`
  - Add requestedAtFrom, requestedAtTo (String or suitable type), approvalStatuses (List<String>), requestReason (String).
- `backend/src/main/java/com/logmng/controller/SearchHistoryController.java`
  - list(): parse and pass requestedAtFrom, requestedAtTo, approvalStatus (multi), requestReason into SearchHistoryListRequest.
- `backend/src/main/java/com/logmng/service/SearchHistoryService.java`
  - create(): persist request_reason; list SELECT and buildListQuerySpec: add requested_at range, approval_status IN, request_reason ILIKE; include requestReason in row map. getDetail: include request_reason in SELECT and response.
- `backend/src/main/resources/db/schema.sql`
  - Add request_reason column to search_history (TEXT NULL or VARCHAR per product).
- `backend/src/main/resources/db/` (new migration script)
  - Idempotent migration: ADD COLUMN IF NOT EXISTS request_reason TEXT NULL to search_history.
- `backend/src/test/java/com/logmng/controller/SearchHistoryControllerTest.java`
  - Create with requestReason; list with requestedAtFrom/To, approvalStatuses, requestReason; verify request/response shape.
- `backend/src/test/java/com/logmng/service/SearchHistoryServiceTest.java`
  - create() stores request_reason; list() filters by requested_at range, approval_status IN, request_reason like.

**Backend implementation confirmed (2026-03-17).** Actual files changed: SearchHistoryCreateRequest.java, SearchHistoryListRequest.java, SearchHistoryController.java, SearchHistoryService.java, SearchHistoryControllerTest.java, SearchHistoryServiceTest.java. schema.sql and migration are DB scope (not modified by Backend). contract.md and api-definition.md already reflect requestReason/list params per Contract.

#### DB

- `backend/src/main/resources/db/schema.sql`
  - search_history: add request_reason TEXT NULL (or VARCHAR(n)).
- `backend/src/main/resources/db/` (migration)
  - New file e.g. migrate-search-history-request-reason.sql: ADD COLUMN IF NOT EXISTS request_reason TEXT NULL.

#### Contract / Spec

- `docs/contract.md`
  - POST /api/search-history body: requestReason (optional or required). GET /api/search-history query: requestedAtFrom, requestedAtTo, approvalStatus, requestReason. List/detail response: requestReason.
- `docs/api-definition.md`
  - §6.1.1 Request body table: requestReason. §6.1.2 Query: requestedAtFrom, requestedAtTo, approvalStatus, requestReason; response item: requestReason. §6.1.4 detail: requestReason.

#### Design docs

- `docs/design/search-fields-by-screen.md`
  - §4 검색 이력: add field definitions for 요청일시(시작/종료), 복호화 승인 여부, 요청사유 (label, controlType, block, width, height, padding, dataSource for 승인 여부, placeholder). Toolbar structure: requester + new block + 검색 + 초기화.

#### Cursor tool update targets

- `.cursor/skills/search-history-decrypt-domain/SKILL.md`
  - Mention that search_history stores request_reason (요청 사유); list/detail and list API filters (requested_at range, approval_status, request_reason like) when this requirement is implemented.

## 3. Test approach

### Test case list (required)

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|----|-------|------|------------------------------|-----------------|--------------------------------------------|
| TC-01 | Backend | Normal | POST /api/search-history with body { logType, searchParams, requestReason } | 201; response includes id, requestedAt, approvalStatus; DB row has request_reason set | Unit (SearchHistoryControllerTest / ServiceTest) |
| TC-02 | Backend | Normal | GET /api/search-history?requestedAtFrom=…&requestedAtTo=… | 200; list only includes rows where requested_at in range | Unit |
| TC-03 | Backend | Normal | GET /api/search-history?approvalStatus=PENDING&approvalStatus=APPROVED | 200; list only includes rows with approval_status IN (PENDING, APPROVED) | Unit |
| TC-04 | Backend | Normal | GET /api/search-history?requestReason=검색 | 200; list only includes rows where request_reason ILIKE %검색% | Unit |
| TC-05 | Backend | Edge | POST /api/search-history without requestReason (if optional) | 201; request_reason NULL in DB | Unit |
| TC-06 | Frontend | Normal | Main: enter request reason, click 복호화 승인 요청 | Request reason sent in POST body; success message; list shows reason in detail or grid | Unit (SearchHistoryList.test or LogGrid) / Manual |
| TC-07 | Frontend | Normal | Search History: set 요청일시 범위, 검색 | List filtered by requested_at in range; params in API call | Unit / Integration |
| TC-08 | Frontend | Normal | Search History: select 승인, 반려 only, 검색 | List filtered by approval_status IN (APPROVED, REJECTED) | Unit / Integration |
| TC-09 | Frontend | Normal | Search History: enter 요청사유 text, 검색 | List filtered by request reason like; params in API call | Unit / Integration |
| TC-10 | Frontend | Normal | Search History: “모두선택” for 승인 여부 then 검색 | All four statuses sent or no filter applied per product | Unit / Manual |
| TC-11 | Frontend | Normal | Search History: 초기화 clears 요청일시, 승인 여부, 요청사유 | All new filters reset; list reloads without those params | Unit |
| TC-12 | Integration | Normal | End-to-end: request approval with reason → open Search History → filter by request reason | Stored reason visible; filter returns the created row | Integration / Manual |
| TC-13 | DB | Normal | Migration adds request_reason column; existing rows have NULL | Column exists; SELECT returns request_reason | Unit or manual DB check |
| TC-14 | Frontend | Normal | Search History new filter fields (요청일시, 승인 여부, 요청사유) use control sizing per design docs (date min 140px max 220px, text min 100px max 200px, 34px height) | Layout/sizing matches search-field-definition-items.md and search-fields-by-screen.md §4 | Manual or unit (layout/snapshot) |

### Test scenarios

#### Scenario 1: Request reason storage and display

1. Log in as user with main decrypt permission. Open 검색하기, set criteria, enter 요청 사유 in the new input, click 복호화 승인 요청.
2. Verify 201 response and success message.
3. Open 검색 이력; find the new row; verify 요청사유 appears in detail or grid.
4. Use 요청사유 filter with the same text; verify list contains the row.

#### Scenario 2: Search History filters

1. Open 검색 이력. Set 요청일시 시작/종료, select one or more 승인 여부, enter 요청사유 (optional).
2. Click 검색. Verify GET request has correct query params and list is filtered.
3. Click 초기화. Verify all filters (requester + new) are cleared and list reloads.

### Test data

- At least one search_history row with request_reason populated for like-search tests.
- Rows with different requested_at and approval_status for range and IN filter tests.
- When derivation rules or defaults apply, provide executable SQL (INSERT/UPDATE) so QA can set up test data.

### Test environment

- Frontend: http://localhost:3001 (or per contract)
- Backend: http://localhost:9200
- Database: PostgreSQL (per contract)

### 3.5 Browser automation verification (optional)

- Applicable TCs: TC-06, TC-07, TC-08, TC-09, TC-11, TC-12 (manual or manual-browser).
- Procedure: browser_navigate → login → main: fill search, enter request reason, click 복호화 승인 요청 → search-history: set filters, 검색, 초기화; confirm list and params.
- Reference: `docs/workflow/BROWSER-AUTOMATION-VERIFICATION-POLICY.md`.

## 4. Checklist

### Frontend verification

- [ ] API parameters validated (requestReason in create; requestedAtFrom, requestedAtTo, approvalStatus, requestReason in list)
- [ ] UI behavior confirmed (request reason input; new filters and 모두선택; reset)
- [ ] Error handling verified
- [ ] New fields follow design doc sizing (width, height, padding)

### Backend verification

- [ ] API test cases written and run (create with requestReason; list with new params)
- [ ] Logs checked
- [ ] Performance checked (index on request_reason if needed for like)

### Integration

- [ ] End-to-end flow tested (request with reason → list/detail → filter by reason)
- [ ] Edge cases tested (empty approvalStatus, empty requestReason, date range)

### Documentation

- [ ] Requirement doc completed
- [ ] Design docs (search-fields-by-screen.md) updated with new field definitions
- [ ] docs/contract.md and docs/api-definition.md updated

## 5. Test results

### Test run date

- [Date and time]

### Test results

#### Frontend

[Pass / Fail]

- [Result description]

#### Backend

[Pass / Fail]

- [Result description]

**Commands:**

```bash
# Example: backend unit tests
cd backend && mvn test -Dtest=SearchHistoryControllerTest,SearchHistoryServiceTest

# Example: frontend unit tests
cd frontend && npm test -- --testPathPattern=SearchHistoryList --watchAll=false
```

**Outcome:**

- [Item 1]
- [Item 2]

### Issues found and resolution

(Record any issues and resolutions.)

### Next steps

1. Implement per §2 and planned change file list.
2. Run tests per §3; update §5.
3. Update TOPIC-INDEX.md when requirement is completed.

## 6. Error remedy result (cause and action) — for error/bug fix requirements only

(Not applicable for this feature requirement.)

---

## 7. Final version (Korean) — add after all verification is complete

(Add Korean summary after QA verification and before or with final commit.)

### Final Korean summary

- **Requirement description**: (1) 복호화 승인 요청 시 요청 사유 입력·저장·표시 및 검색 이력 화면에서 요청사유 like 검색. (2) 검색 이력 화면에 요청일시(시작·종료) 범위 검색, 복호화 승인 여부(승인/반려/대기/만료) 개별 선택 및 모두선택, 요청사유 like 검색 필드 추가.
- **Expected outcome**: 요청 사유 필드 추가; 검색 이력 툴바에 요청일시·승인 여부·요청사유 필터 추가, 공통 디자인 규칙 적용.
- **Verification result**: [§5 요약]

---

**Author**: Requirements subagent  
**Date**: 2026-03-17  
**Status**: In progress
