# 20260320 - Image log composite row identity (guid + status)

## 1. User requirement

### Requirement description

For **Java FW Image Log** (`java_fw_imglog`, table `imagelog`), a single **guid** value is **not** sufficient to identify a row: the business identity is the **pair (guid, status)**. The product must treat this pair as the **canonical row key** everywhere: search-result rows, detail fetch, decryption execution, decryption-approval snapshot, decryption-allowed authorization store, search-history payloads, and all related APIs and UI.

Today, much of the stack still keys imagelog rows **by guid only** (e.g. `search_history_approved_row.row_id` for `java_fw_imglog`, `user_decryption_allowed` primary key, GET decrypt-allowed list, and `LogDbService.getJavaFwImglogDetail` / `getApplicationServiceGroupByGuids`). The decrypt path already accepts **optional** `status` in SQL for `decryptRow`, but **authorization** (`DecryptionAllowedService.isAllowed`) and **snapshot** extraction use guid alone, which is incorrect when multiple rows share a guid with different `status` values.

### User scenario

1. An operator searches **Java FW Image Log** and sees two result rows with the **same guid** but different **status** (e.g. `input` vs `output`).
2. The operator requests decryption approval; an approver approves the search history.
3. The operator attempts to decrypt **one** of the two rows (the one matching the visible row’s status).
4. **Problem**: With guid-only identity, approval stores, allowed lists, and UI “allowed” checks can collapse the two rows, bind decrypt to the wrong physical row, or show incorrect application/service-group resolution in search-history detail.

### Expected outcome

- **Composite identity** `(guid, status)` is the **only** authoritative row key for `java_fw_imglog` across: approval snapshot persistence, decryption-allowed store, decrypt API validation, GET decrypt-allowed response, search-history detail `decryptionRequestedRows`, and imagelog detail/decrypt DB access (no guid-only fallbacks when multiple rows can exist).
- **POST** decrypt for `java_fw_imglog` requires **status** (non-empty, validated); server does **not** infer or default status from guid alone.
- **GET** `/api/decrypt/allowed` returns data sufficient for the UI to test **(guid, status)** membership (structured list of pairs or documented encoding — see §2 / Contract).
- **Search history** approval snapshot and audit rows store the composite consistently; `decryptionRequestedRows` includes **status** per item (or equivalent structured row key).
- **Imagelog detail** API: guid-only path is insufficient when uniqueness is composite — contract must specify **required query parameter** `status` (or path encoding) for `java_fw_imglog` detail/decrypt GET routes where applicable.
- **Regression**: `pb_feplog` and non-imagelog flows remain unchanged.
- **Audit / activity logging**: decrypt-related server logs and activity parameters should include **status** (and log type where not implicit) when referencing an imagelog row, without logging decrypted payload (see §2.1).

**Note**: This requirement does **not** trigger search/filter UI pattern §2.4 (forms-and-filters alignment). No §2.4 Implementation note for Frontend applies.

## 2. Design

### 2.1 Security review (optional; when PII / decryption / access control)

- [x] Security review performed (advisory input for §2; formal Security step may follow full doc per workflow)

**Risks**

- **Wrong-row decrypt**: Guid-only keys can map multiple logical rows to one stored identity; decrypt may target the wrong row or an unintended variant.
- **Snapshot mismatch**: Approval and `ROW_NOT_IN_APPROVED_SNAPSHOT` semantics must match the **exact** row the user saw; composite `(guid, status)` must be part of the approved set and eligibility checks.
- **Audit ambiguity**: Trails that log only `guid` cannot prove which row was decrypted.
- **Parameter tampering**: Treating `status` as optional or a hint allows clients to select a row not in the approved UI context.

**Acceptance / recommendations**

- **Server-authoritative identity**: Use **`(log_type, guid, status)`** (or equivalent composite fixed in contract) for decrypt validation, `user_decryption_allowed`, `search_history_approved_row` (or equivalent), and “row in snapshot” checks. Do **not** default `status` from `guid` when ambiguity exists.
- **Client role**: UI sends `status` for convenience; **all** security decisions re-validate; client values are untrusted input.
- **End-to-end consistency**: Migrate stored identifiers everywhere partial migration increases wrong-row risk.
- **Activity / decrypt logging**: Include **status** (and log type if needed) in structured audit fields for decrypt-related events; avoid logging decrypted content (align with existing PII/logging policy).
- **Failure mode**: Prefer **fail closed** if data has duplicate `(guid, status)` or ambiguous legacy rows until data is corrected.

### Technical design

#### Problem analysis

1. **DB (`imagelog`)**: Schema includes both `guid` and `status` (`schema_imagelog.sql`). Business uniqueness is **(guid, status)**, not `guid` alone; duplicate guids with different statuses are valid.
2. **Snapshot / approval (`SearchHistoryService`)**: `extractRowIdForSnapshot` for `java_fw_imglog` returns **guid only** (`row.get("guid")`). Approved rows and `addOrReplaceAllowed` therefore lose `status`.
3. **Decryption-allowed store (`DecryptionAllowedService`, `user_decryption_allowed`)**: Primary key is `(user_id, screen, guid)` — cannot represent two allowed rows with same guid and different status.
4. **Decrypt API (`DecryptController`)**: Calls `decryptionAllowedService.isAllowed(userId, screen, guid)` — **guid only**; `status` is not part of authorization even though `LogDbService.decryptRow` can filter by status.
5. **Imagelog detail (`LogDbService.getJavaFwImglogDetail`)**: `WHERE guid = ?` — ambiguous if multiple rows share guid.
6. **Resolution by guid (`getApplicationServiceGroupByGuids`)**: `WHERE guid IN (...)` — ambiguous for duplicate guids; search-history detail can attach wrong application/serviceGroup.
7. **Frontend (`ImageLogTable`, `LogGrid`)**: POST decrypt body already sends `{ guid, status }` and uses `guid::status` for local UI state, but **GET decrypt/allowed** uses `guids[]` and `isAllowedForGuid(guid)` only — cannot mark the correct row as allowed when guid repeats.

#### Diagnostic phase (mandatory for error/bug fix only)

*Not applicable — feature / design correction, not an error-fix requirement.*

#### Solution approach

Structure by scope for Step 4 handoff. **DB storage encoding** (single encoded `row_id` vs separate columns) is an implementation choice **provided** the contract defines a **canonical composite key** and round-trip uniqueness; DBA advisory prefers **separate columns** (`row_status` / `status` on sys tables) when integrity and clarity outweigh migration cost (see expert input in §2).

**Frontend:**

- **`LogGrid.js`**: Parse GET `/api/decrypt/allowed` per new contract (e.g. `allowedRows: [{ guid, status }]` or documented encoding); pass structured allowed set to `ImageLogTable`.
- **`ImageLogTable.js`**: Replace guid-only allowed check with **(guid, status)** membership; ensure decrypt button state, dimmed “not allowed” state, and error handling (`DECRYPTION_NOT_APPROVED`, `ROW_NOT_IN_APPROVED_SNAPSHOT`) align with composite semantics.
- **`SearchHistory` UI** (list/detail modal): Display **status** for each decryption-requested row when API provides it; update tests (`SearchHistoryList.test.js`, etc.).
- **Detail / navigation**: If app uses `GET .../db-refactored/java_fw_imglog/.../{identifier}` for row detail, align with contract (e.g. required `status` query for composite uniqueness).
- **Configuration UI**: No permission-group screen change required for this requirement; decrypt toggles remain per existing specs.

**Backend:**

- **`SearchHistoryService`**: Extend snapshot extraction for `java_fw_imglog` to persist **(guid, status)** per approved encrypted row; align `loadDecryptionRequestedRows` / detail DTO builders with composite; ensure `addOrReplaceAllowed` receives composite keys.
- **`DecryptionAllowedService`**: Extend persistence and queries so authorization is **(user_id, screen, guid, status)** (or equivalent single stored key with documented encoding); update `isAllowed`, list, and replace semantics.
- **`DecryptController`**: Require `status` for `java_fw_imglog`; pass composite into `isAllowed`; return clear **400** validation error when `status` missing (e.g. `MISSING_STATUS` — exact code per Contract/error-codes skill).
- **`DecryptAllowedController`**: Response shape must expose composite allowed set per Contract.
- **`LogDbService`**: `getJavaFwImglogDetail` / `getDecryptedData` paths must use **guid + status**; `getApplicationServiceGroupByGuids` must be replaced or supplemented with **composite-aware** resolution for imagelog; `decryptRow` must **require** status for `java_fw_imglog` (no optional ambiguity).
- **Tests**: Update `SearchHistoryServiceTest`, `DecryptionAllowedServiceTest`, stubs (`StubLogDbService`, `RecordingDecryptionAllowedService`), and controller/service tests; add cases with **duplicate guid, different status**.

**DB:**

- **`imagelog` (ImageLog DB)**: Add **`UNIQUE (guid, status)`** (or equivalent) if business rule requires; adjust indexes (composite btree for lookup by `(guid, status)` per DBA note); update `schema_imagelog.sql` and migrations for deployed DBs.
- **Sys DB**: Migrate `search_history_approved_row` and `user_decryption_allowed` to carry composite identity (Option A: encoded `row_id` with delimiter rules and length checks; Option B: add `status`/`row_status` columns and new PK — **preferred** by DBA when feasible). Provide migration script(s), backfill rules, and **data discovery** query for ambiguous legacy guid-only rows.
- **`init-data-imagelog.sql`**: Include at least one scenario with **same guid, different status** if product confirms test data policy.

**Contract / spec:**

- Update **`docs/contract.md`**, **`docs/api-definition.md`**, and **`specs/permission-group-hierarchy.spec.yaml`** (decrypt-allowed, decrypt POST, search-history detail, imagelog detail) for composite identity and breaking-change notes.

### Affected scopes and change targets (verification)

**Change-target verification** was run against `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md` §1 (scope table) and §3.3 (API change pattern). Pattern §3.4 (search/filter UI consistency) **does not** apply.

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|------------|-----------------------------------------------|
| Backend | Yes | Yes |
| Frontend (config UI + view screen) | Yes (view + shared `LogGrid` / `ImageLogTable`; no new permission config) | Yes |
| DB | Yes (sys + imagelog) | Yes |
| Contract / Spec | Yes | Yes |
| Cursor tools (skills, specs) | Yes (domain model for imagelog row key) | Yes |

### Planned change file list (expected change targets)

**(Planned at authoring. Implementing agent (Step 4) confirms or amends this list when implementation is complete.)**

#### Frontend

- `frontend/src/components/LogGrid.js` — Parse extended decrypt-allowed response; state shape for composite allowed rows.
- `frontend/src/components/ImageLogTable.js` — Allowed-row check and any messaging using `(guid, status)`; align with API.
- `frontend/src/components/SearchHistory/SearchHistoryList.js` (and related detail modal components if split) — Show `status` in decryption-requested table; consume API field.
- `frontend/src/components/ImageLogTable.test.js` — Tests: duplicate guid, different status; allowed list membership.
- `frontend/src/components/SearchHistory/SearchHistoryList.test.js` — Assertions on `decryptionRequestedRows` including `status`.
- *Verify* any other consumer of `decryptionRequestedRows` or decrypt-allowed JSON (grep `guids`, `decryptionRequestedRows`).

#### Backend

- `backend/src/main/java/com/logmng/service/LogDbService.java` — Composite detail/decrypt/resolution queries; require status for imagelog decrypt.
- `backend/src/main/java/com/logmng/service/SearchHistoryService.java` — Snapshot `row_id` / composite extraction; detail row building; calls to decryption-allowed refresh.
- `backend/src/main/java/com/logmng/service/DecryptionAllowedService.java` — Composite key persistence and `isAllowed`.
- `backend/src/main/java/com/logmng/controller/DecryptController.java` — Validate status; composite `isAllowed`.
- `backend/src/main/java/com/logmng/controller/DecryptAllowedController.java` — Response mapping for allowed composite set.
- `backend/src/main/java/com/logmng/controller/LogDbController.java` — Imagelog detail/decrypt GET: document and implement `status` requirement (query or path per contract).
- `backend/src/test/java/com/logmng/service/SearchHistoryServiceTest.java` — Composite snapshot / detail tests.
- `backend/src/test/java/com/logmng/service/DecryptionAllowedServiceTest.java` — Composite PK / replace / isAllowed.
- `backend/src/test/java/com/logmng/service/StubLogDbService.java` — Resolution helpers for tests.
- `backend/src/test/java/com/logmng/service/RecordingDecryptionAllowedService.java` — Signature if list payload changes.
- *Add or update* controller tests if present for decrypt / allowed endpoints.

#### DB

- `backend/src/main/resources/db/schema_imagelog.sql` — Unique constraint / indexes on `(guid, status)`.
- `backend/src/main/resources/db/schema_sys.sql` — `user_decryption_allowed` / `search_history_approved_row` shape (or new migration-only delta per project convention).
- `backend/src/main/resources/db/migrate-imagelog-guid-status-unique-20260320.sql` — ImageLog `(guid, status)` 유니크.
- `backend/src/main/resources/db/migrate-sys-decryption-composite-pk-20260320.sql` — sys 테이블 복합 PK.
- `backend/src/main/resources/db/migrate-imagelog-composite-decrypt-20260320.sql` — 단일 DB용 `\ir` 래퍼(선택).
- `backend/src/main/resources/db/init-data-imagelog.sql` — Optional duplicate-guid scenario for QA.
- `backend/src/test/java/com/logmng/util/GenerateSampleDataScriptTest.java` (or related) — If schema constraints affect sample generation.

#### Contract / spec

- `docs/contract.md` — Composite identity narrative; decrypt-allowed; decrypt POST; search-history detail; imagelog detail.
- `docs/api-definition.md` — Endpoint tables, examples, error codes (`MISSING_STATUS`, etc., per final naming).
- `specs/permission-group-hierarchy.spec.yaml` — Decrypt-allowed / decrypt API bullets referencing composite key where applicable.

#### Cursor tool update targets

Implementing agents **must** update skills after behavior change (per `REQUIREMENTS-AUTHORING-WORKFLOW.md` §1.4):

- `.cursor/skills/log-search-domain/SKILL.md` — State imagelog row key = `(guid, status)`.
- `.cursor/skills/search-history-decrypt-domain/SKILL.md` — Snapshot, `decryptionRequestedRows`, decrypt-allowed: composite key.
- `.cursor/skills/api-permission-map/SKILL.md` — Map updated decrypt / decrypt-allowed request shapes for permission tests.
- `.cursor/skills/db-domain/SKILL.md` — Schema notes for `imagelog` and sys tables if present.

## 3. Test approach

### Test case list (required)

**Domain-specific completeness**

- **`api-permission-map`**: Include TCs that assert **403 `FUNCTION_NOT_ALLOWED`** still applies when decrypt permission is off; **403 `DECRYPTION_NOT_APPROVED`** when composite pair not in allowed store; logType↔screen unchanged for `java_fw_imglog` → `java-fw-imagelog`.
- **`search-history-decrypt-domain`**: Include TCs for **approval snapshot** containing correct `(guid, status)` pairs; **search-history detail** `decryptionRequestedRows` includes status; decrypt path uses **numeric user id** and decryption-allowed store only (unchanged rule); no guid-only fallback for imagelog.
- **`log-search-domain`**: Include TC that **search result rows** for `java_fw_imglog` expose both `guid` and `status` used for downstream decrypt (field presence / mapping).

**Mandatory automated tests (Definition of Done)**: For each TC with Verification **Unit** or **Integration**, Step 4 **must** add or extend Jest/JUnit (or documented integration) so QA can run them.

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification |
|----|-------|------|------------------------------|-----------------|--------------|
| TC-01 | Backend | Normal | Approve search history for `java_fw_imglog` where result set has two rows: same `guid`, different `status`, both encrypted | Snapshot + `user_decryption_allowed` contain **two** distinct composite entries; neither collapses to guid-only | Unit / Integration (mvn test) |
| TC-02 | Backend | Normal | `DecryptionAllowedService.isAllowed(user, screen, guid, status)` (or equivalent) | True only when **pair** matches stored allowed row and `valid_until` not expired | Unit (mvn test) |
| TC-03 | Backend | Exception | POST `/api/logs/decrypt/java_fw_imglog` with `guid` set, `status` missing or blank | **400** with documented error code (e.g. `MISSING_STATUS`); no decrypt | Integration (curl or mvc test) |
| TC-04 | Backend | Normal | POST decrypt with composite in allowed store | **200** and decrypted payload for the **correct** physical row | Integration |
| TC-05 | Backend | Exception | POST decrypt with guid+status **not** in allowed store | **403** `DECRYPTION_NOT_APPROVED` | Integration |
| TC-06 | Backend | Normal | GET `/api/decrypt/allowed?screen=java-fw-imagelog` after approval with composite rows | Response includes structured pairs (or documented encoding) matching DB | Integration |
| TC-07 | Backend | Normal | GET search-history detail for APPROVED record with imagelog snapshot | Each `decryptionRequestedRows[]` item includes **status** (and guid); count matches snapshot | Unit / Integration |
| TC-08 | DB | Edge | Migration / schema: `imagelog` violates `UNIQUE (guid, status)` with duplicate pair | Constraint enforced; migration doc describes remediation for bad legacy data | Manual / DBA script review + test DB |
| TC-09 | Frontend | Normal | Render `ImageLogTable` with two rows same guid, different status; user has both in allowed set | Both rows show decrypt allowed; clicking each calls API with correct `status` | Unit (npm test) |
| TC-10 | Frontend | Normal | Allowed set contains only `(g1, input)`; row `(g1, output)` present | Output row shows not-allowed / dimmed state per UX rules | Unit (npm test) |
| TC-11 | Integration | Normal | End-to-end: search → request approval → approver approve → decrypt row B (not A) with same guid | Only row B decrypts; row A still ciphertext or prior state | Manual / browser |
| TC-12 | Backend | Regression | `pb_feplog` search / approve path (if any snapshot) unchanged; no new required `status` on non-imagelog APIs | No regression failures | mvn test |

### Test scenarios

#### Scenario 1: Duplicate guid, different status (happy path)

1. Seed imagelog with two encrypted rows sharing `guid`, different `status`.
2. Search and create search history; approver approves.
3. Verify DB snapshot and decryption-allowed store have two entries.
4. Decrypt each row; verify distinct decrypted content.

#### Scenario 2: Legacy / ambiguous data

1. If migration maps guid-only rows to composite: run discovery query from §2 DBA notes.
2. Document product decision for rows that cannot be disambiguated (fail closed vs default).

### Test data

- Provide **SQL or script** to insert two `imagelog` rows with **same `guid`**, different **`status`**, both with encrypted payloads suitable for decrypt tests.
- Sys DB: after migration, insert `user_decryption_allowed` / `search_history_approved_row` rows matching composite contract (exact shape depends on Option A/B).

### Test environment

- Frontend: `http://localhost:3001` (per project convention)
- Backend: `http://localhost:9200`
- Database: PostgreSQL (primary + ImageLog datasource per `docs/contract.md`)

### 3.5 Browser automation verification (optional — for frontend-heavy requirements)

- **Applicable TCs**: TC-11 (and manual verification of Search History modal columns).
- **Procedure**: Login → Java FW Image Log → search with seeded data → open search history detail → confirm **status** column/value for decryption-requested rows → decrypt from grid and confirm correct row decrypts.
- **Reference**: `docs/workflow/BROWSER-AUTOMATION-VERIFICATION-POLICY.md`

## 4. Checklist

### Frontend verification

- [x] API parameters validated (composite on client matches contract) — `LogGrid` parses `allowedRows`; `ImageLogTable` checks `(guid, status)`; POST body includes `status`.
- [x] UI behavior confirmed (duplicate guid rows) — `ImageLogTable.test.js` composite membership case.
- [x] Error handling verified (`MISSING_STATUS`, `DECRYPTION_NOT_APPROVED`) — `DecryptControllerTest` TC-07 + 기존 403 케이스; UI는 기존 메시지 경로 유지.

### Backend verification

- [x] API test cases written and run — `mvn test` (SearchHistory, DecryptionAllowed, DecryptController 등).
- [ ] Logs checked (status present in decrypt audit params where applicable) — 권장: 배포 전 `ActivityLogAspect` / decrypt 로그 샘플 확인.
- [ ] Performance checked (if applicable — index changes on imagelog) — 운영 DB 인덱스 적용 후 필요 시.

### Integration

- [ ] End-to-end flow tested (approval + decrypt + allowed list) — 브라우저/스테이징 권장 (§3.5).
- [x] Edge cases tested (TC-08 legacy ambiguity) — 단위/통합: 동일 guid·다른 status 스냅샷·허용 키; 레거시 API는 `guids`만 있을 때 guid-only 폴백.

### Documentation

- [x] Requirement doc completed — contract, api-definition, skills 갱신됨.
- [ ] Code comments added (if applicable) — 최소 주석만; 필요 시 Step 4 후속.

## 5. Test results

### Test run date

- 2026-03-20 (dev workspace — Step 4 자동화 테스트; 동일일 §7 한국어 최종 요약 반영)

### Test results

#### Frontend

- `CI=true npm test -- --watchAll=false` — **PASS** (77 tests, 15 suites).
- 관련 단위: `ImageLogTable.test.js`, `SearchHistoryList.test.js` 등.

#### Backend

- `cd backend && mvn test` — **PASS** (예: SearchHistoryServiceTest, DecryptionAllowedServiceTest, `com.logmng.webtest.DecryptControllerTest`).

**Commands:**

```bash
cd backend && mvn test
cd frontend && CI=true npm test -- --watchAll=false
```

### Issues found and resolution

- **Java erasure**: `DecryptionAllowedService`에서 `List<String>` / `List<DecryptionRowKey>` 오버로드 충돌 → deprecated guid-only 오버로드 제거.
- **H2(SearchHistoryServiceTest)**: `user_decryption_allowed` 테이블 누락으로 승인 테스트 오류 → 테스트 DDL·`clearAllTables`에 테이블 추가.

### Next steps

1. 운영/스테이징 DB에 마이그레이션 적용: **DB B** → `migrate-imagelog-guid-status-unique-20260320.sql`, **DB A** → `migrate-sys-decryption-composite-pk-20260320.sql` (단일 DB·public만이면 `migrate-imagelog-composite-decrypt-20260320.sql` 래퍼 또는 `setup.sh`와 동일 순서). `backend/DB_SETUP_GUIDE.md` § ImageLog·승인 스냅샷 복합 키 참고.
2. §3.5 브라우저 검증(선택) 및 §4 Integration E2E 체크.
3. QA 커밋 정책에 따라 브랜치에서 커밋·PR.

## 6. Error remedy result (cause and action) — for error/bug fix requirements only

*N/A*

---

## 7. Final version (Korean)

Stakeholder-facing summary aligned with §1–§3. Tool-facing handoffs remain English per `docs/workflow/DOCUMENT-LANGUAGE-POLICY.md`.

### Final Korean summary

- **요구 배경**: Java FW Image Log(`java_fw_imglog`, 테이블 `imagelog`)에서는 **guid 하나만으로는 행을 유일하게 식별할 수 없다.** 비즈니스 식별자는 **(guid, status)** 복합이며, 검색 결과 행·상세 조회·복호화 실행·복호화 승인 스냅샷·복호화 허용 저장·검색 이력 페이로드 및 관련 API·UI **전 구간**에서 이 복합키를 **표준 행 키**로 취급해야 한다. 동일 guid에 서로 다른 status가 있을 때 guid만 쓰면 승인·허용·UI가 두 행을 한 행으로 뭉개 잘못된 행 복호화나 스냅샷 불일치가 난다.

- **기대 결과(복합키)**: `java_fw_imglog`에 대해 **(guid, status)** 가 승인 스냅샷·`user_decryption_allowed`·복호화 API 검증·GET decrypt-allowed·검색 이력 상세 `decryptionRequestedRows`·imagelog 상세/복호화 DB 접근의 **유일한 권위 있는 식별자**이며, guid만으로의 폴백은 **허용하지 않는다**(여러 행이 존재할 수 있는 경우).

- **API·백엔드**: `java_fw_imglog` **POST 복호화**는 **status 필수**(비어 있지 않게 검증); 서버는 guid만으로 status를 추론·기본값 부여하지 않는다. **GET `/api/decrypt/allowed`** 는 UI가 **(guid, status)** 멤버십을 검사할 수 있도록 구조화된 쌍 또는 계약에 문서화된 인코딩을 반환한다. 검색 이력 승인 스냅샷·감사·`decryptionRequestedRows`는 항목마다 **status**(또는 동등한 구조화된 행 키)를 포함한다. Imagelog 상세·복호화 GET 경로는 복합 유일성이 필요한 경우 계약에 따라 **`status` 쿼리(또는 경로 인코딩)** 등이 필수다. `pb_feplog` 및 비-imagelog 흐름은 **변경 없음**(회귀). 복호화 관련 감사·활동 로그는 imagelog 행을 가리킬 때 **status**(필요 시 로그 유형)를 포함하고, 복호화된 페이로드는 로깅하지 않는다(§2.1·기존 PII 정책 정렬).

- **DB·마이그레이션**: `imagelog`에는 비즈니스 규칙에 맞게 **`UNIQUE (guid, status)`**(또는 동등) 및 조회용 **복합 인덱스**를 반영한다(`schema_imagelog.sql`, 배포 DB용 마이그레이션). Sys DB의 `search_history_approved_row`·`user_decryption_allowed`는 복합 신원을 저장하도록 **마이그레이션·백필**하고, 레거시 guid-only 데이터는 **발견 쿼리·모호 행 처리 정책**(가능하면 fail closed)을 따른다.

- **프론트엔드**: GET decrypt-allowed 응답을 계약에 맞게 파싱해 **`LogGrid` → `ImageLogTable`** 에 **(guid, status)** 허용 집합을 전달하고, 복호화 버튼·비허용 표시·`DECRYPTION_NOT_APPROVED` / `ROW_NOT_IN_APPROVED_SNAPSHOT` 등이 복합 의미와 일치한다. 검색 이력 UI는 복호화 요청 행에 **status**를 표시한다. 상세·네비게이션은 imagelog 복합 상세에 맞게 `status`를 포함한다.

- **§5 검증 요약**: 2026-03-20 dev 워크스페이스에서 프론트 `CI=true npm test -- --watchAll=false`·백엔드 `mvn test` **통과**로 Step 4 자동화 검증이 기록되어 있다. **운영/스테이징 DB에 복합 키 마이그레이션**(ImageLog 유니크 + sys PK 확장, `DB_SETUP_GUIDE.md` 참고) **적용**, §3.5 브라우저·§4 통합 E2E는 필요 시 추가 검증 대상으로 남는다.

---

**Author**: Requirements (subagent)  
**Date**: 2026-03-20  
**Status**: Step 4 구현·자동화 테스트 완료; §7 한국어 최종 요약 반영. 운영/스테이징 DB 마이그레이션 적용 및 선택 E2E·로그/성능 점검은 필요 시 진행.
