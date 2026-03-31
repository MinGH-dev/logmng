# 20260330 - Imagelog duplicate-GUID sample data (same guid, different status)

## Parent / related requirement

- **`docs/requirements/20260330-image-log-pretty-decrypt-row-key.md`** — Image log **Pretty** and **decrypt** keyed by `guid` + `status`. That requirement’s tests (e.g. TC-01, TC-02) and manual/integration verification need **at least two DB rows** with the **same `guid`** and **different `status`**.
- This requirement adds **reproducible seed data** so QA and developers can run those scenarios against a real database, not only mocked unit tests.

## 1. User requirement

### Requirement description

The `imagelog` table enforces uniqueness on **`(guid, normalized status)`** (unique index `uq_imagelog_guid_row_status`), so **duplicate `guid` with different `status` is valid**. Today, **SQL init** and **Java startup seeding** populate sample rows when the table is empty, but they do not guarantee a **stable, documented pair** of rows for “same guid, two statuses.” Manual and integration testers need that pair in **fresh installs** and, where possible, in **existing databases** that already contain the legacy ~100-row sample set.

### User scenario

1. A tester or developer opens the **Java FW Image Log** screen and searches with a date/time range that includes the seeded rows.
2. The result list shows **two rows** sharing one **business `guid`** (e.g. `GUID-DUP-PRETTY-20260330`) with **`input`** and **`output`** (or equivalent normalized status values aligned with existing sample conventions).
3. The user exercises **Pretty** and **decrypt** per row (per parent requirement) without hand-crafting INSERTs.
4. **Problem**: Without seed data, reproducing “same guid, different status” requires ad hoc SQL or mocks; integration and manual tests are inconsistent.

### Expected outcome

- **Fresh DB / empty `imagelog`**: Initial sample load (SQL and/or Java seed) includes **exactly two rows** with shared `guid` `GUID-DUP-PRETTY-20260330`, statuses **`input`** and **`output`**, with **plain JSON** in `datastring` / `headerstring` so **Pretty** is visibly testable (e.g. minimal JSON objects).
- **Existing DB** that already has the historical bulk sample (COUNT ≠ 0): An **idempotent** migration can insert the same two rows **only if** that `(guid, status)` pair is not already present, without breaking `uq_imagelog_guid_row_status`.
- **Java-only dev path**: When developers rely on `GenerateSampleDataScript` (no SQL init), the generated batch **must** include the same logical pair so row counts and behavior match SQL-init environments.
- **Automated tests** that assert total imagelog row count after seeding **must** be updated when the canonical total increases (e.g. `TARGET_TOTAL` and `GenerateSampleDataScriptTest` expectations).

## 2. Design

### 2.1 Security review (optional)

- [ ] Security review performed (check if applicable)
- **Scope**: Sample rows with **plain JSON** only; align with existing `init-data-imagelog.sql` patterns (no new secret material).
- **Risks**: Low — same class of data as current plain sample rows.
- **Recommendations**: Do not put real PII in `datastring` / `headerstring`; use synthetic fields only.

### Technical design

#### Codebase summary

- **`backend/src/main/resources/db/init-data-imagelog.sql`**: Inserts ~100 imagelog rows **only when** `COUNT(*) = 0` (idempotent re-run; no duplicate bulk insert). Referenced by prior requirement `20260318-image-log-sample-data-preserve`.
- **`backend/src/main/resources/db/setup.sh`**: After `schema_imagelog.sql` and imagelog migrations, runs `init-data-imagelog.sql` against the ImageLog database (`DB_B_NAME`, `SCHEMA_IMAGELOG`).
- **`com.logmng.util.GenerateEncryptedSampleData`**: Builds the list of sample rows and **`TARGET_TOTAL`**; used by **`GenerateSampleDataScript`** (`CommandLineRunner`) to insert when the table is empty.
- **`GenerateSampleDataScriptTest`**: Asserts `imagelog` row count equals **`GenerateEncryptedSampleData.TARGET_TOTAL`** after the script runs (and on restart scenarios per existing tests).
- **`GenerateEncryptedSampleDataTest`**: Asserts generated sample list size and counts vs `TARGET_TOTAL` / `NON_ENCRYPTED_COUNT` where applicable.

#### Problem analysis

1. **Parent requirement** needs repeatable **DB-backed** duplicate-`guid` rows; unit tests alone are insufficient for integration/manual validation.
2. **`init-data-imagelog.sql` only runs when the table is empty**, so databases that were populated earlier never receive new rows unless a **separate idempotent migration** adds them.
3. **Java seed** must stay **aligned** with SQL init so developers who never run `init-data-imagelog.sql` still get the same pair and **count expectations** in tests remain correct.

#### Diagnostic phase (mandatory for error/bug fix only)

*Not applicable — seed-data feature, not an error-fix requirement.*

#### Solution approach

**DB:**

- Extend **`init-data-imagelog.sql`**: Inside the existing `IF COUNT(*) = 0` block, add **two** `VALUES` rows sharing `guid` = `GUID-DUP-PRETTY-20260330`, with statuses **`input`** and **`output`**, and plain JSON in `datastring` / `headerstring`. Use `insert_time` consistent with nearby rows (e.g. `EXTRACT(EPOCH FROM NOW() - INTERVAL '…') * 1000`) so a typical search window includes them.
- Add **`migrate-imagelog-dup-guid-sample-20260330.sql`** (filename may be adjusted to match repo naming): **Idempotent** `INSERT … SELECT … WHERE NOT EXISTS (SELECT 1 FROM imagelog … matching guid and normalized status)** so existing databases gain the pair without duplicate-key errors.
- **`setup.sh`**: **Recommended**: After **`init-data-imagelog.sql`**, invoke the new migration on the ImageLog DB/search_path (same pattern as `migrate-imagelog-guid-status-unique-20260320.sql` and other `run_sql_file_sp` calls). Order: migration runs after init so **fresh** DBs get rows from init **and** migration remains a no-op when both rows already exist; **existing** DBs with data get rows from migration only.

**Backend (Java):**

- **`GenerateEncryptedSampleData`**: Include the same two logical rows in the generated sample list; increment **`TARGET_TOTAL`** (and any derived constants such as plain vs encrypted counts) so totals stay internally consistent.
- **`GenerateSampleDataScript`**: No behavioral change beyond consuming the updated generator list unless insert loop needs ordering guarantees.
- **Tests**: Update **`GenerateSampleDataScriptTest`** and **`GenerateEncryptedSampleDataTest`** (and any test that hard-codes the old total) so assertions match the new **`TARGET_TOTAL`** and content expectations.

**Frontend:**

- None for seed data (parent requirement covers UI).

**Contract:**

- None unless documentation explicitly lists sample GUIDs (optional).

### Affected scopes and change targets (verification)

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|------------|-----------------------------------------------|
| Backend | Yes — Java generators + tests | Yes |
| Frontend | No | N/A |
| DB | Yes — init SQL, migration, setup.sh | Yes |
| Contract / Spec | Optional | TBD |
| Cursor tools (skills, specs) | Optional — `log-search-domain` if sample GUID is documented | TBD |

### Planned change file list (expected change targets)

#### Frontend

- None.

#### Backend

- `backend/src/main/resources/db/init-data-imagelog.sql`
  - Add two rows (same `guid`, `input` / `output`, plain JSON) inside the existing empty-table insert.
- `backend/src/main/resources/db/migrate-imagelog-dup-guid-sample-20260330.sql` (or name aligned with repo convention)
  - Idempotent inserts for the pair when missing.
- `backend/src/main/resources/db/setup.sh`
  - Run the new migration after `init-data-imagelog.sql` on the ImageLog DB (follow existing `run_sql_file_sp` patterns).
- `backend/src/main/java/com/logmng/util/GenerateEncryptedSampleData.java`
  - Add the duplicate-`guid` pair to generated samples; update `TARGET_TOTAL` (and related constants if present).
- `backend/src/main/java/com/logmng/util/GenerateSampleDataScript.java`
  - Verify compatibility with updated sample list (no duplicate inserts beyond empty-table guard).
- `backend/src/test/java/com/logmng/util/GenerateSampleDataScriptTest.java`
  - Update row-count expectations to match new `TARGET_TOTAL`.
- `backend/src/test/java/com/logmng/util/GenerateEncryptedSampleDataTest.java`
  - Update expectations tied to `TARGET_TOTAL` / list size / counts.

#### DB

- Same as Backend file list for SQL and `setup.sh`.

## 3. Test approach

### Test case list (required)

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|----|-------|------|------------------------------|-----------------|---------------------------------------------|
| TC-01 | DB | Normal | Fresh DB: `setup.sh` (or equivalent) runs init + migration. | Exactly two rows with `guid` = `GUID-DUP-PRETTY-20260330` and statuses `input` and `output` exist; no duplicate `(guid, normalized status)`. | Integration: SQL `SELECT` / `COUNT` on `imagelog` filtered by `guid`; or grep migration/init for the guid string |
| TC-02 | Backend | Normal | Empty `imagelog`; run `GenerateSampleDataScript` (or test harness). | Row count equals updated `TARGET_TOTAL`; duplicate-`guid` pair present. | Unit / integration (`GenerateSampleDataScriptTest`, `GenerateEncryptedSampleDataTest`) |
| TC-03 | Integration | Normal | Image Log search UI: date range covering seeded `insert_time`; optional filter by application/service if needed. | At least two result rows show the same GUID with different status; Pretty/decrypt behave per parent doc. | Manual or browser automation |
| TC-04 | DB | Regression | DB already had ~100 rows; migration run once. | Two new rows inserted if absent; second run is no-op. | Integration: run migration twice; row count stable; no unique violation | 

### Test scenarios

#### Scenario 1: SQL verification

1. After seeding, run: count rows where `guid = 'GUID-DUP-PRETTY-20260330'` → expect **2**; distinct statuses include **input** and **output**.

#### Scenario 2: Manual image log search

1. Log in; open Java FW Image Log; set search dates to include seeded timestamps.
2. Confirm two rows with the same displayed GUID and different status; exercise Pretty per parent requirement.

### Test data

- Canonical `guid`: **`GUID-DUP-PRETTY-20260330`**.
- Statuses: **`input`**, **`output`** (normalize per `schema_imagelog.sql` / application conventions if different from literal case).

### Test environment

- Database: PostgreSQL per project setup (`backend/src/main/resources/db/setup.sh`).
- Backend: port per `docs/contract.md` (e.g. 9200).
- Frontend: port per contract (e.g. 3001) for TC-03.

### 3.5 Browser automation verification (optional)

- **Applicable TCs**: TC-03 if Image Log flow is automated elsewhere.
- **Reference**: `docs/workflow/BROWSER-AUTOMATION-VERIFICATION-POLICY.md`.

## 4. Checklist

### Frontend verification

- [ ] N/A (unless a screen labels sample GUIDs — then align help text)

### Backend verification

- [ ] Init SQL + migration idempotent and aligned on guid/status
- [ ] Java `TARGET_TOTAL` and tests updated; `mvn test` passes

### Integration

- [ ] `setup.sh` path loads duplicate-`guid` rows on fresh DB
- [ ] Existing DB migration path verified (optional staging DB)

### Documentation

- [ ] Requirement doc completed
- [ ] After verification: add **§7 Final version (Korean)** per `docs/workflow/DOCUMENT-LANGUAGE-POLICY.md`
- [ ] Add this doc to `docs/requirements/TOPIC-INDEX.md` (image-log section); run `./scripts/generate-requirements-index.sh` if used

## 5. Test results

### Test run date

- 2026-03-30 (DB scope: `init-data-imagelog.sql`, `migrate-imagelog-dup-guid-sample-20260330.sql`, `setup.sh`)

### Test results

#### Frontend

- N/A

#### Backend

- DB scope only this change: SQL files reviewed; full `mvn test` deferred to Backend agent (Java `TARGET_TOTAL` / seed alignment).

**Commands:**

```bash
cd backend && mvn test -Dtest=GenerateSampleDataScriptTest,GenerateEncryptedSampleDataTest
cd backend && mvn test
```

**Outcome:**

- DB: `init-data-imagelog.sql` adds two rows (shared `guid` `GUID-DUP-PRETTY-20260330`, `input` / `output`); migration is idempotent via `WHERE NOT EXISTS` on `guid` + normalized `status`; `setup.sh` runs migration after `init-data-imagelog.sql` on DB B.

### Issues found and resolution

- None for DB scope.

### Next steps

1. Backend/DB implement per §2; run tests; optional QA manual TC-03.
2. Update parent requirement §3 or cross-link if test data GUID is referenced from frontend tests.

## 6. Error remedy result (cause and action) — for error/bug fix requirements only

N/A.

---

## 7. Final version (Korean) — add after all verification is complete

After QA completes verification, add a Korean summary per `docs/workflow/DOCUMENT-LANGUAGE-POLICY.md`.

### Final Korean summary (placeholder)

- **요구사항 요약**: (한국어 요약은 검증 완료 후 작성)
- **기대 결과**: 동일 `guid`·상이한 `status` 두 행이 샘플 데이터에 포함되어 Pretty/복호화 행 단위 테스트를 재현 가능하게 함
- **검증 결과**: §5 기록 후 반영

---

**Author**: Requirements (subagent)  
**Date**: 2026-03-30  
**Status**: In progress  
