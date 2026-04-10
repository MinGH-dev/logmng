# 20260330 - Image log Pretty and decrypt per-row key (guid + status)

## 1. User requirement

### Requirement description

On the **image log** search results table, **Pretty** formatting and **decrypt** actions must apply to **at most one logical row at a time** when multiple rows share the same `guid` but differ by `status`. Today, decrypt-related state already uses a composite key `guid` + `status` (via `getLogKey(guid, status)`). **Pretty** state is keyed by **`guid` only**, so toggling Pretty on one row incorrectly affects every row that shares that `guid`. The product must align **Pretty** row identity with **decrypt** row identity so each table row is independent.

### User scenario

1. The user runs an image log search and the result set contains **two or more rows with the same `guid` but different `status`** (nested or duplicate GUID scenarios).
2. The user enables **Pretty** on one row only.
3. **Problem**: Other rows with the same `guid` also switch to Pretty mode (or share the same Pretty state), which is incorrect.
4. The user expects the same **per-row** behavior they already get for **decrypt**: only the row identified by **`guid` + `status`** is affected.

### Expected outcome

- **Pretty** on/off state is stored and evaluated using the **same composite key** as decrypt: `getLogKey(guid, status)` (or an equivalent `guid` + `status` contract), not `guid` alone.
- Toggling Pretty on row A **does not** change Pretty state for row B when A and B share a `guid` but differ in `status`.
- **Decrypt** behavior remains **per** `guid` + `status`; existing tests that assert independent decrypt per row **must** continue to pass (extend or add tests only if gaps are found).
- **Detail panel** (when it displays a selected row): Pretty display for that panel **must** use the **selected row’s** `guid` and `status` for consistency with the table row identity (same composite key as the inline Pretty toggle for that row). If the modal currently uses a separate global `prettyPrint` flag, it **must** be aligned so it does not conflate rows that share a `guid`.

**Note**: Numeric/layout values are unchanged; this requirement is **state keying** only.

## 2. Design

### 2.1 Security review (optional)

- [ ] Security review performed (check if applicable)
- **Scope**: Frontend UI state keying only; **no** change to decrypt API payload shape, approval rules, or server-side scope.
- **Risks**: None material beyond ensuring decrypt and Pretty remain consistent so users do not misread which row is “active.”
- **Recommendations**: None beyond aligning keys with existing `getLogKey` behavior.

### Technical design

#### Codebase summary

- **File**: `frontend/src/components/ImageLogTable.js`
- **Decrypt**: Uses `getLogKey(guid, status) = \`${guid}::${status || ''}\`` for `decryptedLogs`, `decryptingLogs`, `isDecrypted`, `getDecryptedData`, `handleDecrypt`, `handleDecryptCancel`, `isDecrypting`.
- **Pretty**: `prettyLogs` is a `Set` keyed by **`guid` only**; `togglePretty(guid)` and `isPretty(guid)` do not receive `status`. Row render uses `logGuid` only for Pretty (`isPrettyMode = isPretty(logGuid)`, `togglePretty(logGuid)`).
- **Detail modal**: Uses `getDecryptedData(selectedLogGuid, selectedLogStatus)` for decrypted content (composite-aware). **Pretty Print** checkboxes use a **single** `prettyPrint` state shared for both Data and Header sections—not keyed by `guid` + `status`—so behavior must be **verified** when aligning with per-row Pretty keys (e.g. sync modal Pretty to the selected row’s composite key or derive from `prettyLogs` for that key).

#### Problem analysis

1. **Shared `guid` across rows**: Multiple rows can legitimately share `guid` with different `status`; Pretty must not be a global-per-guid flag.
2. **Inconsistency with decrypt**: Users perceive Pretty and decrypt as **row-level** actions; decrypt is already per-row composite key; Pretty must match.

#### Diagnostic phase (mandatory for error/bug fix only)

*Not applicable — this is a feature/behavior alignment requirement, not an error-fix requirement.*

#### Solution approach

**Frontend:**

- Change **Pretty** storage from `Set` of `guid` to `Set` of **composite keys** using `getLogKey(guid, status)` (or the same string construction as decrypt for consistency).
- Update **`togglePretty`** and **`isPretty`** to accept **`(guid, status)`** and use `getLogKey` internally for membership in `prettyLogs`.
- In the **table body** `map`, pass **`logGuid` and `logStatus`** (or equivalent) into `isPretty` / `togglePretty` so each `<tr>` is independent.
- **Detail panel**: Ensure Pretty formatting for the selected row uses the **selected row’s** `guid` + `status`:
  - Either drive modal Pretty Print from the same composite-keyed set (e.g. read/write `prettyLogs` for `getLogKey(selectedLog.guid, selectedLog.status)`), or
  - Replace the global `prettyPrint` boolean with behavior that is **scoped** to the selected row’s composite key so opening another row does not reuse the wrong Pretty state.
- **Verify** `key` on `<tr>`: still unique per row index + guid; no requirement to change row keys unless tests show duplicate-key React issues.

**Backend:**

- None.

**DB:**

- None.

### Affected scopes and change targets (verification)

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|------------|-----------------------------------------------|
| Backend | No | N/A |
| Frontend (config UI + view screen) | Yes — view screen only | Yes |
| DB | No | N/A |
| Contract / Spec | No | N/A |
| Cursor tools (skills, specs) | Optional — only if a skill explicitly documents Pretty keying as `guid`-only | TBD by implementer |

### Planned change file list (expected change targets)

#### Frontend

- `frontend/src/components/ImageLogTable.js`
  - Key `prettyLogs` by `getLogKey(guid, status)`; update `togglePretty` / `isPretty` signatures and call sites; align detail panel Pretty behavior with selected row’s `guid` + `status`.
- `frontend/src/components/ImageLogTable.test.js`
  - Add unit tests: two rows, same `guid`, different `status` — Pretty toggle on one row does not affect the other; decrypt regressions unchanged if already covered.

#### Backend

- None.

#### DB

- None.

## 3. Test approach

### Test case list (required)

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|----|-------|------|------------------------------|-----------------|--------------------------------------------|
| TC-01 | Frontend | Normal | Table has two rows: same `guid`, different `status`; both have JSON `datastring`/`headerstring` suitable for Pretty. | Clicking **Pretty** on row 1 toggles only row 1’s cells; row 2’s Pretty state (button label / cell class) is unchanged. | Unit (`npm test`, `ImageLogTable.test.js`) |
| TC-02 | Frontend | Normal | Same as TC-01; enable Pretty on row 2 after row 1 is Pretty ON. | Row 1 stays Pretty ON; row 2 toggles independently (both can be ON simultaneously). | Unit |
| TC-03 | Frontend | Regression | Existing decrypt tests: same `guid`, different `status` — decrypt one row only. | Decrypt state and buttons remain independent per row; no regression. | Unit (extend or rely on existing cases if already present) |
| TC-04 | Frontend | Edge | Empty or missing `status` treated like `''` for keying (matches `getLogKey`). | Pretty/decrypt keys remain consistent with `guid::` + normalized status. | Unit (optional if covered by implementation detail tests) |
| TC-05 | Frontend | Manual / optional | Open detail modal for a row (if wired in app or test harness). | Pretty Print in modal matches that row’s `guid` + `status` composite behavior (no cross-row bleed). | Manual or unit if modal is testable |

**Domain note**: Apply `log-search-domain` / `search-history-decrypt-domain` skills only for cross-screen consistency; this requirement is **scoped to `ImageLogTable`**.

### Test scenarios

#### Scenario 1: Duplicate guid, distinct status — Pretty independence

1. Render `ImageLogTable` with two logs: `{ guid: 'G', status: 'S1', ... }`, `{ guid: 'G', status: 'S2', ... }` with valid JSON in data/header fields.
2. Click Pretty on the first row.
3. Assert first row shows Pretty layout; second row does not; second row’s button still says **Pretty** (not OFF).

#### Scenario 2: Decrypt unchanged

1. Use mocks or existing tests ensuring decrypt only populates `decryptedLogs` for the clicked row’s `getLogKey`.
2. Re-run; expect no change in pass/fail except for intentional Pretty updates.

### Test data

- Minimal JSON strings in `datastring` / `headerstring` so Pretty toggle is visually/assertively testable (e.g. `{"a":1}`).

### Test environment

- Frontend unit: Jest + React Testing Library (`frontend`).

### 3.5 Browser automation verification (optional)

- Optional manual confirmation on image log screen with real duplicate-guid results if available in integration data.

## 4. Checklist

### Frontend verification

- [ ] Pretty keyed by `guid` + `status` matches decrypt keying
- [ ] UI behavior confirmed (table + detail if applicable)
- [ ] Error handling unchanged (N/A for this change)

### Backend verification

- [ ] N/A

### Integration

- [ ] End-to-end spot-check on image log screen if QA resources allow

### Documentation

- [ ] Requirement doc completed
- [ ] Code comments only if non-obvious key normalization

## 5. Test results

### Test run date

- 2026-03-30

### Test results

#### Frontend

- `ImageLogTable.test.js`: TC-01, TC-02 (Pretty per guid+status), TC-03 (decrypt regression with duplicate guid) — pass.
- Full suite: `cd frontend && npm test -- --watchAll=false` — 18 suites, 93 tests — pass.

#### Backend

- N/A

**Commands:**

```bash
cd frontend && npm test -- --watchAll=false --testPathPattern=ImageLogTable
cd frontend && npm test -- --watchAll=false
```

**Outcome:**

- Exit code 0 for ImageLogTable-focused run and full frontend test run (2026-03-30).

### Issues found and resolution

- TC-03 async: assertion after decrypt click required `waitFor` until row 1 shows "복호화 해제" (not while still "복호화 중").

### Next steps

1. Frontend implements per §2 and runs automated tests.
2. QA updates §5 and checklist; add Korean verification lines in §7 if needed.

## 6. Error remedy result (cause and action) — for error/bug fix requirements only

N/A — not classified as error-fix-only.

---

## 7. Final version (Korean)

**참고**: 구현·QA 검증 완료 후 검증 결과(§5)를 아래에 반영한다.

### 최종 요약 (한국어)

- **요구사항 요약**: 이미지 로그 검색 결과에서 같은 `guid`를 가진 행이 `status`만 다르게 여러 줄로 있을 때, **Pretty** 표시는 **복호화와 동일하게** `guid`와 `status`를 묶은 **행 단위**로만 적용되어야 한다. 현재 Pretty는 `guid`만으로 상태를 저장해 동일 `guid` 행이 함께 켜지는 문제가 있으므로, `getLogKey(guid, status)`(또는 동일 규칙)으로 **행 식별**을 맞춘다.
- **기대 결과**: 한 행에서만 Pretty를 켜도 다른 `status`의 동일 `guid` 행에는 영향이 없다. 복호화는 기존처럼 행(`guid`+`status`) 단위로 유지된다. 상세 패널이 있을 경우 선택된 행의 `guid`+`status`와 동일한 키로 Pretty 동작이 일치한다.
- **검증 결과**: 구현 및 테스트 완료 후 §5에 기록한다.

---

**Author**: Requirements (subagent)  
**Date**: 2026-03-30  
**Status**: In progress  
