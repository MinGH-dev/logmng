# 20260408 - HR Sync PoC "Run preview" visible, meaningful results

## 1. User requirement

### Requirement description

The HR Sync Proof-of-Concept **Run preview** flow must surface **meaningful, trustworthy** preview results to operators. Today, users often perceive **no useful output**: backend preview may report **misleading aggregates** (e.g. entire `ext_employee` table counted as one stub bucket) or **all-zero counts** when the underlying read fails; the frontend surfaces the response only as **raw JSON in a `<pre>` block** at the bottom of the screen, which is easy to **miss or dismiss** as empty. The PoC remains **read-only** (`POST .../preview` must not mutate `app_user`, permission tables, or production Tree authority — consistent with `specs/hr-sync-poc.spec.yaml` and `docs/requirements/20260408-external-hr-user-sync-security-db-design.md` §2.6).

This requirement **closes the gap** between operator expectation (“I ran preview and see what would happen for **this** snapshot”) and current behavior, and **aligns** preview counts with **`snapshotId`** when the client sends it (per `specs/hr-sync-poc.spec.yaml` §4.2 request body). It **references** snapshot list / sample data work in `docs/requirements/20260408-hr-sync-poc-snapshot-list-and-sample-data.md`. Any intentional **API or response-shape** change must follow **DOC-CODE-SYNC** (`specs/hr-sync-poc.spec.yaml`, `docs/contract.md`, `docs/api-definition.md`).

### User scenario

1. An authorized operator opens the HR Sync PoC UI with PoC enabled; a **snapshot** is selected (or the UI sends a `snapshotId` when invoking preview).
2. The operator clicks **Run preview** (or equivalent).
3. **Problem**: The operator sees **no clear summary** (counts buried in raw JSON, or all zeros), or counts that **do not match** the selected snapshot scope, so the demo looks **broken or empty**.

### Expected outcome

- **Backend**: When the preview request body includes **`snapshotId`**, **classificationCounts** (and any related aggregates in the §4.2 `data` object) are **scoped to that snapshot** (e.g. `WHERE snapshot_id = :snapshotId` on replica `ext_employee` / agreed join path), not `COUNT(*)` over the full replica table unless product explicitly documents global preview. On **query/replica access failure**, the API must **not** silently return success with all zeros without a distinguishable signal — implementers must align with spec (e.g. appropriate **HTTP + `code`**, or a **non-success `messageCode`** / documented placeholder policy per §4.2) after **diagnostic confirmation** (see §2).
- **Frontend**: A **prominent summary panel** (or equivalent) shows **key preview fields**: at minimum **`classificationCounts`** (all spec keys or documented subset), **`snapshotId`** echo, **`riskTier`**, **`upstreamGateStatus`**, and **`messageCode`** when present — **without** requiring the user to scroll to raw JSON. Raw JSON may remain as **optional detail** (collapse, secondary tab, or dev-only) if product agrees.
- **Diagnostics**: Implementers **verify** root cause (network response vs DB scope vs UI placement) before changing logic; production must not retain noisy diagnostic logging (see §2 diagnostic phase).
- **PoC**: **Read-only** guarantee for `POST .../preview` is preserved.

**Note**: Personnel table and `GET .../snapshots` / `GET .../employees` remain as defined in the snapshot-list requirement; this requirement focuses on **preview visibility and snapshot-scoped counts**.

## 2. Design

### 2.1 Security review (minimal)

- **Scope**: Preview response may include **aggregate counts only** (no expansion of per-row PII beyond existing §4.2 contract). UI summary panel displays **the same DTO fields** already authorized for PoC preview callers.
- **Risks**: Mis-scoped counts could **leak operational scale** of replica data if interpreted outside intended audience — **mitigation**: same **authentication + PoC screen/admin** enforcement as existing `HrSyncPoc` routes; no new public routes.
- **Acceptance**: **No new secrets** in JSON; **no** broaden-preview to raw replica dumps; **403** / `POC_DISABLED` / `FORBIDDEN` behavior unchanged for unauthorized callers. Align with `specs/hr-sync-poc.spec.yaml` §3 and prior PoC §2.1 data minimization.

### Technical design

#### Problem analysis

1. **Backend stub behavior**: `HrSyncPocService` (or equivalent) **`buildPreview`** may use **`loadClassificationStubCounts()`** that effectively maps **whole-table** `COUNT(*)` on `ext_employee` into a **single classification** (e.g. all as `UNCHANGED` stub), which is **not meaningful** for multi-snapshot PoC and **ignores** `snapshotId` in the request body.
2. **Silent failure → zeros**: If the counting query **fails** (permissions, missing column, wrong datasource), the implementation may **degrade to all-zero** counts with **HTTP 200**, so the UI looks like “nothing to sync” instead of an **error or gate** state.
3. **UX gap**: Even when the payload is correct, showing preview only as **raw JSON in `<pre>`** at the bottom creates a **false “no results”** experience vs a **zero-count** legitimate preview — users do not distinguish **“I didn’t see it”** from **“counts are zero.”**

#### Diagnostic phase (mandatory for error/bug fix)

Before changing classification or error-mapping logic, implementers **must** confirm root cause:

- **Network / API**: In browser **DevTools → Network**, inspect **`POST /api/hr-sync/poc/preview`** with body **`{ "snapshotId": "<chosen-id>" }`**. Record **HTTP status**, `success`, `code`, full `data.classificationCounts`, and `messageCode`.
- **Replica / DB**: Confirm the app role can **`SELECT`** from `ext_employee` for PoC and that **rows exist** for the chosen `snapshot_id` (e.g. `SELECT count(*) FROM ext_employee WHERE snapshot_id = :id` — exact column per schema). Distinguish **empty snapshot** (legitimate zeros) from **query exception** (must not masquerade as success+zeros without product-approved signaling).
- **UX**: Confirm whether the issue is **data** (zeros/wrong scope) vs **presentation** (user never sees JSON). **Hypothesis-only** code changes are **not** allowed until logs/network/DB evidence is aligned.

**Production safety**: Any temporary diagnostic logging must be **DEBUG**, **dev-flagged**, or **removed** after verification — not emitted in production.

#### Solution approach

**Backend:**

- **`buildPreview`** (and helpers): When **`snapshotId` is non-null/non-blank**, compute **`classificationCounts`** over **only** `ext_employee` rows belonging to that snapshot (and agreed `source_system` filter if spec/requirements require). When **`snapshotId` is absent**, document behavior in spec (e.g. **global** stub vs **400 VALIDATION_ERROR** if product mandates snapshot for PoC) — **DOC-CODE-SYNC**.
- **Error handling**: If counting fails, **do not** return arbitrary all-zero success; return behavior **consistent with** `specs/hr-sync-poc.spec.yaml` §4.2 Errors (e.g. `SYNC_SOURCE_NOT_READY`, `503`, or `500` with safe `code` — **choose one and sync docs**).
- **Tests**: Unit/service tests assert **scoped counts** for a given `snapshotId` vs unscoped data in DB fixtures.

**Frontend:**

- Add a **summary panel** above or beside the action area: readable labels for each **classification count** (reuse i18n/labels pattern if present), plus **`snapshotId`**, **`riskTier`**, **`upstreamGateStatus`**, **`messageCode`**.
- On **API error** or **`success: false`**, show **inline error** in the summary area (reuse existing `errorMessage` / toast patterns).
- Keep **raw JSON** optional (collapsed “Technical detail”); default **must not** be the only visible preview output.

**DB:**

- **None** required for this requirement if `ext_employee.snapshot_id` already exists per `20260408-hr-sync-poc-snapshot-list-and-sample-data.md`; otherwise DB agent follows that requirement first.

### Affected scopes and change targets (verification)

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|------------|-----------------------------------------------|
| Backend | Yes | Yes |
| Frontend | Yes | Yes |
| DB | No (unless schema gap) | N/A |
| Contract / Spec | Yes (if error codes or request rules clarified) | Yes |
| Cursor tools (skills, specs) | Optional | If PoC preview semantics change |

### Planned change file list (expected change targets)

**(Planned at authoring. Implementing agent (Step 4) confirms or amends this list when implementation is complete.)**

#### Frontend

- `frontend/src/components/UserManagement/HrSyncPocPreview.js` (or equivalent)
  - Summary panel for preview `data`; optional collapsible raw JSON.
- `frontend/src/components/UserManagement/HrSyncPocPreview.css` (if needed)
  - Layout/styling for summary panel.

#### Backend

- `backend/src/main/java/com/logmng/service/HrSyncPocService.java`
  - Snapshot-scoped counts; error handling vs silent zeros.
- `backend/src/test/java/com/logmng/service/HrSyncPocServiceTest.java` (and/or controller test)
  - Assertions for `snapshotId` scoping and failure paths.

#### Contract / spec (same PR as code when behavior or errors change)

- `specs/hr-sync-poc.spec.yaml` §4.2 — clarify scoped counts, optional vs required `snapshotId`, error mapping.
- `docs/contract.md`, `docs/api-definition.md` — narrative alignment.

## 3. Test approach

### Test case list (required)

**Domain note**: Map **`POST /api/hr-sync/poc/preview`** to permission / `POC_DISABLED` per `api-permission-map` skill; include **403** cases unchanged from prior PoC.

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|----|-------|------|------------------------------|-----------------|--------------------------------------------|
| TC-01 | Backend | Normal | `POST .../preview` with `snapshotId` **A**; DB has rows only in A and B; stub/classification query counts **only A** | `classificationCounts` reflect **A** row set (not **A+B**); `snapshotId` in response echoes A | Unit (`mvn test`) |
| TC-02 | Backend | Normal | Same DB; `snapshotId` **B** | Counts reflect **B** only | Unit (`mvn test`) |
| TC-03 | Backend | Edge | `snapshotId` valid but **no** `ext_employee` rows | Legitimate **zeros** (or **404 NOT_FOUND** if spec chooses — **DOC-CODE-SYNC**); **not** same as query failure | Unit (`mvn test`) |
| TC-04 | Backend | Exception | Counting query **simulated failure** (e.g. SQLException in test double) | **No** `200` with all-zero masquerading as success unless spec explicitly defines placeholder; aligned **HTTP + code** | Unit (`mvn test`) |
| TC-05 | Backend | Exception | `HR_SYNC_POC_ENABLED` false | **403** `POC_DISABLED` (or route absent — document) | Unit (`mvn test`) |
| TC-06 | Frontend | Normal | Successful preview; `data.classificationCounts` populated | **Summary panel** shows all material keys; user need not open raw JSON | Unit (`npm test`) |
| TC-07 | Frontend | Normal | Preview returns **error** or `success: false` | Inline error visible in summary area (or established error UX) | Unit (`npm test`) |
| TC-08 | QA | Manual / browser | Login as allowed user → select snapshot **A** → **Run preview** → Network tab | **POST /preview** body contains `snapshotId`; response counts **consistent** with DB for A; summary panel **visible without scrolling to `<pre>`** | Browser / MCP per policy |
| TC-09 | QA | Manual | Optional: snapshot **B** | Same as TC-08 for B; counts differ from A when seed data differs | Browser |

### Test scenarios

#### Scenario 1: Snapshot-scoped preview (Backend + UI)

1. Seed or use existing **two** snapshot ids with non-zero `ext_employee` rows each.
2. Call `POST .../preview` with `snapshotId` = first id; verify counts match **first** cohort only.
3. Repeat for second id; verify counts differ when DB cohorts differ.
4. In UI, run preview after selecting each snapshot; verify summary panel matches network response.

#### Scenario 2: Diagnostic confirmation

1. Capture **Network** payload for failed or zero preview **before** fix.
2. Run DB **scoped** `count` for same `snapshotId`.
3. Confirm whether root cause is **scope**, **failure swallowed**, or **UI only**; only then apply code changes.

### Test data

- Use **`HR_SAMPLE`** PoC seed from `docs/requirements/20260408-hr-sync-poc-snapshot-list-and-sample-data.md` §5 / migration: snapshots **`poc-snap-20260408-A`**, **`poc-snap-20260408-B`** with distinct row sets.

### Test environment

- Frontend: `http://localhost:3001` (per contract)
- Backend: `http://localhost:9200`
- Database: PostgreSQL (project standard)

### 3.5 Browser automation verification

- **Applicable TCs**: TC-08, TC-09.
- **Procedure**: Navigate to HR Sync PoC → select snapshot → Run preview → `browser_snapshot` asserts summary panel region (e.g. labeled counts) is **visible**; Network tab or MCP network capture confirms **`POST .../preview`** includes `snapshotId`.
- **Reference**: `docs/workflow/BROWSER-AUTOMATION-VERIFICATION-POLICY.md`

## 4. Checklist

### Frontend verification

- [ ] Summary panel visible for successful preview
- [ ] Error path UX verified
- [ ] Raw JSON is optional / secondary

### Backend verification

- [ ] Snapshot-scoped counts tested
- [ ] Failure path does not silently return misleading zeros
- [ ] Logs: no production diagnostic noise

### Integration

- [ ] End-to-end: snapshot select → preview → counts match DB
- [ ] Read-only: no mutations to `app_user` / permissions from preview

### Documentation

- [ ] Requirement doc §5 completed after verification
- [ ] Spec/contract updated if preview semantics or errors change

## 5. Test results

### Test run date

- _(To be filled by QA after verification)_

### Test results

- _(Pending)_

**Commands:**

_(QA: one executable command per §3 TC after implementation.)_

### Issues found and resolution

- _(Pending)_

### Next steps

1. Security subagent full review if PII expansion is proposed (not expected for counts-only summary).
2. Implement Backend + Frontend per §2; DOC-CODE-SYNC if spec changes.

## 6. Error remedy result (cause and action) — for error/bug fix requirements only

- **Requirement ID**: `20260408-hr-sync-poc-run-preview-visible-results`
- **Root cause**: _(Record after diagnostic phase.)_
- **Actions taken**: _(Summary of changes.)_
- **Result**: _(Verification method and outcome.)_
- **Completed**: _(yyyy-MM-dd HH:mm)_

---

## 7. Final version (Korean) — add after all verification is complete

_(Deferred per `docs/workflow/DOCUMENT-LANGUAGE-POLICY.md`.)_

---

**Author**: Requirements (subagent)  
**Date**: 2026-04-08  
**Status**: In progress
