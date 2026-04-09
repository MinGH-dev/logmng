# 20260409 - Employee number uniqueness for external provisioning

## 1. User requirement

### Requirement description

`app_user.employee_number` must not be duplicated across **active** users (`deleted_at IS NULL`) when the value is **non-null**, using the same **trimmed** string semantics as elsewhere. User Management V2 **direct user create** already enforces this via `UserManagementV2Service.ensureEmployeeNumberAvailable` before insert. The **HR / external provisioning** path (`ProvisioningService.provisionFromExternalEmployee`) currently inserts `app_user` **without** an equivalent check, so a new user can be provisioned with the same trimmed `employee_number` as an existing active user. The database schema only has a **non-unique** index on `employee_number` (`schema_sys.sql`, migration `migrate-app-user-employee-number-20260407.sql` documents that legacy duplicate rows prevented a UNIQUE constraint). This requirement closes the **application-layer** gap and aligns API error behavior with direct create.

### User scenario

1. An operator provisions a user from `ext_employee` via `POST /api/provisioning/users/from-external-employee`.
2. Another active user already exists with the **same** trimmed `employee_number` (e.g. created earlier via User Management V2 direct create, or via a previous data issue).
3. **Problem**: Provisioning today can still succeed and create a second active row with the duplicate employee number, breaking the intended business rule and confusing operators and downstream HR/UI consistency.

### Expected outcome

- Provisioning **must** enforce the same rule as direct create: **no two active** `app_user` rows may share the same **non-null** `employee_number` after the same **trim** rules used when persisting (see `ProvisioningService`: `employeeNumberTrimmed` from `ext_employee.employee_number`; align with `UserManagementV2Service` trimming for direct create).
- On conflict, the API **must** return **409** with error code **`USER_EMPLOYEE_NUMBER_DUPLICATED`** (same as `UserManagementV2Service` / `specs/user-management-v2.spec.yaml`) so operators immediately understand the failure. If product ever needs a distinct code, document it in contract and this requirement; default is **reuse** the existing code.
- **Documentation**: Update `docs/contract.md`, `docs/api-definition.md` (error catalog / provisioning section as applicable), and **`specs/external-identity-auth.spec.yaml`** §4.3 errors so provisioning documents this 409. Add an **ops/DBA note**: environments with **legacy duplicate** active rows may need **data cleanup** before a future **partial UNIQUE** index at the DB layer; app enforcement does not remove the need for cleanup if migrating to DB uniqueness.
- **Optional design note (§2)**: Evaluate **partial unique index** `(employee_number) WHERE deleted_at IS NULL AND employee_number IS NOT NULL` vs **app-only** enforcement; if duplicates exist, any DB migration must be documented as **manual cleanup first**, then migration.

**Note**: Numeric layout and field labels for UI are unchanged; this requirement is backend rule + contract alignment.

## 2. Design

### 2.1 Security review (optional; when PII / decryption / access control)

- [ ] Security review performed (check if applicable)
- **Risks**: `employee_number` is identifier-like PII in HR context; error responses must not leak unnecessary cross-user detail beyond what direct create already exposes (align behavior and message style with `USER_EMPLOYEE_NUMBER_DUPLICATED` for V2).
- **Acceptance / recommendations**: Reuse existing conflict pattern; optional `details` keys only if consistent with `CustomException` usage for V2 duplicate (implementer to align).

### Technical design

#### Codebase summary

- **`UserManagementV2Service.createDirectUser`**: Reads `employeeNumber` via `requireTrimmed` (non-empty after trim, max length), then **`ensureEmployeeNumberAvailable(conn, employeeNumber)`** which runs  
  `SELECT 1 FROM app_user WHERE employee_number = ? AND deleted_at IS NULL LIMIT 1`  
  and throws **`CustomException.conflict(..., "USER_EMPLOYEE_NUMBER_DUPLICATED")`** if a row exists.
- **`ProvisioningService.provisionFromExternalEmployee`**: Loads `ext_employee` row, sets `employeeNumberTrimmed = hasText(employee_number) ? trim : null`, then **INSERT** into `app_user` with that value — **no** pre-insert duplicate check for `employee_number` among active users.
- **DB**: `app_user.employee_number` indexed with **`idx_app_user_employee_number`** (non-unique). Migration comment: partial UNIQUE not applied due to possible legacy duplicates.
- **Tests**: `UserManagementV2ServiceTest` covers `USER_EMPLOYEE_NUMBER_DUPLICATED` on direct create. `ProvisioningServiceProvisionFromExternalEmployeeTest` covers trim/null storage and `EXTERNAL_IDENTITY_CONFLICT`, **not** employee-number duplicate vs another `app_user`.

#### Problem analysis

1. **Inconsistent enforcement**: Two entry points create `app_user` with `employee_number`; only one validates active-uniqueness.
2. **No DB guard**: Non-unique index allows duplicates at the storage layer; correctness relies on application rules.
3. **Operator clarity**: Provisioning contract today lists **409** `EXTERNAL_IDENTITY_CONFLICT` for external-key collision but does not document employee-number collision; operators need the **same** error code as V2 where possible.

#### Diagnostic phase (mandatory for error/bug fix only)

*Not applicable — this requirement is a **consistency / gap closure** for provisioning behavior, not a production error-message-driven bugfix. No diagnostic logging phase.*

#### Solution approach

Structure by scope so each implementing agent receives only its relevant section during handoff.

**Frontend:**

- **No change required** unless product adds UI mapping for a new `details` shape (implement only if Backend adds details and UX requests display). Default: show generic 409 conflict message from API `message` / existing error handler.

**Backend:**

- **Before** inserting `app_user` in `provisionFromExternalEmployee`, when `employeeNumberTrimmed` is **non-null**, enforce **active** uniqueness using the **same** predicate as `ensureEmployeeNumberAvailable` (same trimmed string as stored). Prefer **shared** extraction (e.g. package-private helper or small dedicated class) **or** a single duplicated query with a comment pointing to V2 — implementer chooses minimal-risk refactor; requirement is **behavioral identity** with V2.
- **Soft-delete**: Rows with `deleted_at NOT NULL` **must not** block reuse (match V2 query).
- **Null employee number**: Multiple active users with `employee_number IS NULL` remain allowed (no check when trimmed value is null).
- **HTTP**: **409** + **`USER_EMPLOYEE_NUMBER_DUPLICATED`**; user-visible message may match V2 Korean text for consistency (“이미 등록된 사번입니다.”) unless product requests provisioning-specific wording — default **same as V2**.

**DB:**

- **No mandatory migration** in this requirement. **Optional follow-up**: partial UNIQUE index as in §1 Expected outcome — only after duplicate cleanup in target environments; document in ops runbook / contract appendix.

**Contract / documentation:**

- Update **`specs/external-identity-auth.spec.yaml`** §4.3 error table: add **409** `USER_EMPLOYEE_NUMBER_DUPLICATED` when another **active** user already holds the same trimmed `employee_number`.
- Update **`docs/contract.md`** (provisioning / user identity subsection) and **`docs/api-definition.md`** (provisioning path and/or global error table) to match.
- **DBA/ops note**: State that adding a **partial UNIQUE** index in PostgreSQL requires **no duplicate active non-null** `employee_number` rows; cleanup may be manual SQL / one-off script per environment — **not** part of this requirement’s default deliverable unless a separate DB requirement is opened.

#### DBA-style tradeoff (optional §2 evaluation)

| Approach | Pros | Cons |
|----------|------|------|
| **App-only** (this requirement) | No migration risk; works on legacy DBs with duplicates; matches current V2 pattern | Duplicates still possible if another writer bypasses app or bug regresses |
| **Partial UNIQUE index** `WHERE deleted_at IS NULL AND employee_number IS NOT NULL` | Strong DB guarantee for active rows; aligns with business rule | **Fails** until legacy duplicates removed; requires coordinated migration and rollback plan |

**Recommendation**: Implement **app enforcement** now; plan DB constraint in a **separate** change after data audit and cleanup.

### Affected scopes and change targets (verification)

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|------------|-----------------------------------------------|
| Backend | Yes | Yes |
| Frontend (config UI + view screen) | No | N/A |
| DB | Optional note only | N/A for default delivery |
| Contract / Spec | Yes | Yes |
| Cursor tools (skills, specs) | Optional | Error-code doc in `error-codes-domain` skill if project lists this code — update only if skill mandates |

### Planned change file list (expected change targets)

**(Planned at authoring. Implementing agent (Step 4) confirms or amends this list when implementation is complete.)**

#### Frontend

- *(none for default scope)*

#### Backend (actual — Step 4 backend scope)

- `backend/src/main/java/com/logmng/service/AppUserEmployeeNumberUniqueness.java` — **new** shared helper: same SQL and `CustomException.conflict` as former `UserManagementV2Service.ensureEmployeeNumberAvailable` body; used by V2 and provisioning.
- `backend/src/main/java/com/logmng/service/UserManagementV2Service.java` — `ensureEmployeeNumberAvailable` delegates to `AppUserEmployeeNumberUniqueness.ensureAvailableForActiveUser`.
- `backend/src/main/java/com/logmng/service/ProvisioningService.java` — after computing `employeeNumberTrimmed`, if non-null, calls `AppUserEmployeeNumberUniqueness.ensureAvailableForActiveUser` before `INSERT` into `app_user`.
- `backend/src/test/java/com/logmng/service/ProvisioningServiceProvisionFromExternalEmployeeTest.java` — TC-02 (active duplicate employee number), TC-05 (soft-deleted holder does not block); existing trim/null test retained for TC-04.
- `backend/src/test/java/com/logmng/service/UserManagementV2ServiceTest.java` — not modified; regression covered by full `mvn test`.

#### DB

- No migration file required for default scope. Document cleanup + optional UNIQUE follow-up in contract/docs.

#### Contract / docs (actual — aligned with Contract agent pass)

- `docs/contract.md` — provisioning / error catalog: **409** `USER_EMPLOYEE_NUMBER_DUPLICATED` for active duplicate `employee_number`.
- `docs/api-definition.md` — provisioning path and/or global error table aligned with the above.
- `specs/external-identity-auth.spec.yaml` — §4.3 errors: documents **409** `USER_EMPLOYEE_NUMBER_DUPLICATED` when another active user holds the same trimmed `employee_number`.

#### Cursor tool update targets (actual)

- `.cursor/skills/error-codes-domain/SKILL.md` — added `USER_EMPLOYEE_NUMBER_DUPLICATED` note (V2 + provisioning path).

## 3. Test approach

### Test case list (required)

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|----|-------|------|------------------------------|-----------------|--------------------------------------------|
| TC-01 | Backend | Normal | Provisioning: `ext_employee.employee_number` trims to value **not** held by any active `app_user` | Insert succeeds; stored `employee_number` equals trimmed value | Unit (`mvn test`, `ProvisioningServiceProvisionFromExternalEmployeeTest` style) |
| TC-02 | Backend | Exception | Another **active** `app_user` already has **same** trimmed `employee_number`; provision new external employee with that number | **409**-class `CustomException`, error code **`USER_EMPLOYEE_NUMBER_DUPLICATED`**; **no** new duplicate active row | Unit |
| TC-03 | Backend | Normal / regression | User Management V2 **direct create** with duplicate `employeeNumber` vs existing active user | Still **`USER_EMPLOYEE_NUMBER_DUPLICATED`** (unchanged behavior) | Unit (`UserManagementV2ServiceTest`) |
| TC-04 | Backend | Edge | Provisioning with **null/blank** `ext_employee.employee_number` (stored as NULL) | No employee-number duplicate check failure; behavior unchanged vs today | Unit (existing trim/null test + ensure no false conflict) |
| TC-05 | Backend | Edge | Existing user with same `employee_number` but **`deleted_at` set** (soft-deleted); provision same number | Provisioning **allowed** (reuse allowed for soft-deleted holder) | Unit (seed `app_user` with `deleted_at` not null if H2 schema supports in test) |

### Test scenarios

#### Scenario 1: Provisioning blocked on duplicate active employee number

1. Seed active `app_user` with `employee_number = '20260001'` and `deleted_at` NULL.
2. Seed `ext_employee` with a **different** external key and `employee_number` trimming to `20260001`.
3. Call `provisionFromExternalEmployee`.
4. Verify conflict code `USER_EMPLOYEE_NUMBER_DUPLICATED` and no second active user with that number.

#### Scenario 2: Direct create unchanged

1. Run existing V2 tests for duplicate employee number.
2. Confirm no regression.

#### Scenario 3: Trim semantics unchanged

1. Keep / run `provisionTrimsEmployeeNumberAndStoresNullWhenBlank`-style assertions (leading/trailing spaces stripped before compare and insert).

### Test data

- H2 PostgreSQL-mode fixtures as in `ProvisioningServiceProvisionFromExternalEmployeeTest`: `app_user` with `deleted_at` column; seed duplicate scenarios with SQL `INSERT`.
- For TC-05, insert soft-deleted row with same `employee_number` if supported.

### Test environment

- Frontend: N/A for automated TCs above
- Backend: `http://localhost:9200` (integration optional)
- Database: H2 for unit tests; PostgreSQL for full env per project norms

### 3.5 Browser automation verification (optional — for frontend-heavy requirements)

- **Not applicable** (backend-only behavior change; no UI change required for default scope).

## 4. Checklist

### Frontend verification

- [x] N/A (no UI change for default scope)

### Backend verification

- [x] API test cases written and run (`mvn test`)
- [x] Logs checked — no new production diagnostic logging required
- [x] Performance checked (if applicable) — single indexed lookup; negligible

### Integration

- [ ] Optional: curl provisioning endpoint against DB with duplicate seed (not required for this closure)

### Documentation

- [x] Requirement doc completed (§5, §7)
- [x] Contract/spec updated (`docs/contract.md`, `docs/api-definition.md`, `specs/external-identity-auth.spec.yaml`)

## 5. Test results

### Test run date

- **2026-04-09** (QA verification pass)

### Test results

#### Frontend

- **N/A** (backend + contract/docs scope; no frontend change)

#### Backend

- **Pass**

**Commands:**

```bash
cd backend && mvn test
cd backend && mvn test -Dtest=ProvisioningServiceProvisionFromExternalEmployeeTest,UserManagementV2ServiceTest
```

**Outcome:**

- `mvn test`: **Pass** (full suite; exit 0).
- Focused: `ProvisioningServiceProvisionFromExternalEmployeeTest`, `UserManagementV2ServiceTest` — **Pass** (covers TC-01–TC-05 and V2 duplicate regression).

**Verify (restart + health):**

```bash
./scripts/dev-services.sh backend restart
# wait for listen (~15–25 s if cold start)
curl -s http://localhost:9200/api/health   # expect 200 JSON success
curl -s http://localhost:9200/api/db/test  # expect success; data.connected true
```

**Outcome:** **Pass** — `GET /api/health` → 200; `GET /api/db/test` → `connected: true` (PostgreSQL).

**Browser (step 3.5):** **Skipped** — backend-only requirement; §3.5 N/A.

### Issues found and resolution

- None for this pass.

### Next steps

- [ ] Optional: data-quality report for duplicate `employee_number` among active rows in each environment
- [ ] Optional future requirement: DB partial UNIQUE after cleanup

## 6. Error remedy result (cause and action) — for error/bug fix requirements only

- *Not used.*

---

## 7. Final version (Korean) — add after all verification is complete

*(Per `docs/workflow/DOCUMENT-LANGUAGE-POLICY.md`, add Korean summary after verification.)*

### Final Korean summary

- HR 외부 연동 **프로비저닝**(`POST /api/provisioning/users/from-external-employee`)에서도 사용자관리 V2 **직접 생성**과 동일하게, **삭제되지 않은(active) 사용자**끼리 **동일한 사번(트림 후)** 이 중복되지 않도록 애플리케이션에서 검사합니다.
- 이미 다른 활성 사용자가 같은 사번을 쓰는 경우 **409** 및 오류 코드 **`USER_EMPLOYEE_NUMBER_DUPLICATED`** 로 응답합니다(V2와 동일).
- 계약서·API 정의·`external-identity-auth` 스펙에 해당 409 및 코드가 반영되었고, 에러 코드 스킬에도 정리되어 있습니다. DB **부분 UNIQUE** 인덱스는 별도 데이터 정리 후 선택 과제로 남습니다.

---

**Author**: Requirements (subagent)  
**Date**: 2026-04-09  
**Status**: Completed

- [x] Requirement doc completed
