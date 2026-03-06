# 20260306 - Permission terminology and scope guidelines (doc alignment)

## 1. User requirement

### Requirement description

Align **permission management UI and documentation** with the agreed terminology and scope rules:

1. **Terminology**: Scope labels must be **"본인"** | **"부서"** | **"전체"** (API values remain `self` | `team` | `all`). The old terms **"본인만"** and **"팀"** must not appear in user-facing copy or in authoritative docs that define terminology.
2. **Scope rules**: (1) **List/read scope (조회(목록) 범위)** — configurable per screen in permission group (본인 / 부서 / 전체). (2) **Approval scope (승인 범위)** — fixed to department (부서), not configurable. The permission config scope dropdown affects only list/read; who can approve is always department-scoped (canApproveForRequester).

The codebase (Frontend: ScreenSelectionTree.js, PendingApprovals.js; Spec: permission-group-hierarchy.spec.yaml §1.1; CONSISTENCY-STANDARDS.md §7; auth-permission-domain SKILL) already uses the correct terminology. This requirement **documents** the rule and **fixes remaining inconsistencies** in docs/specs so that future changes and AI tooling stay consistent.

### User scenario

1. An administrator or developer opens permission group management and configures scope for a scope-supporting screen (activity-log, statistics, search-history, pending-approvals).
2. They read API or contract documentation to understand what `screenScopes` and scope values mean.
3. **Problem**: Some documentation (e.g. `docs/api-definition.md`) still says "scope=self → 본인만", which contradicts the standard "본인" (no "만"). Past requirement docs may still describe the old labels "본인만" or "팀" in their design sections.
4. **Expected**: All terminology-defining and user-facing documentation use "본인" | "부서" | "전체" and clearly state that only **조회(목록) 범위** is selectable and **승인 범위** is fixed to department.

### Expected outcome

- **Single source of truth**: `specs/permission-group-hierarchy.spec.yaml` §1.1 and `docs/workflow/CONSISTENCY-STANDARDS.md` §7 already define the rule. No change there.
- **API definition**: `docs/api-definition.md` — in the sentence describing `screenScopes`, replace "scope=self → 본인만" with "scope=self → 본인".
- **Consistency check**: No remaining occurrences of "본인만" or "팀" (as scope label) in docs that define or expose terminology to users or implementers; historical requirement docs may keep old wording but should have a one-line note that current UI uses "조회" | "승인" and "본인" | "부서" | "전체" per CONTRACT/SPEC.
- **No application code change**: Frontend and backend already comply; this requirement is documentation-only.

**Korean summary (요약)**: 권한관리에서 범위 표기는 "본인"|"부서"|"전체"로 통일하고, "본인만"/"팀" 사용 금지. 조회(목록) 범위만 선택 가능, 승인 범위는 부서 고정. 문서만 수정하며 코드 변경 없음.

---

## 2. Design

### 2.1 Security review (optional; when PII / decryption / access control)

- Documentation and terminology only. No change to access control logic or data exposure.
- [ ] Security review performed (not applicable)

### Technical design

#### Problem analysis

1. **docs/api-definition.md** (around line 45): The `user.screenScopes` description says "scope=self → 본인만". Per CONSISTENCY-STANDARDS §7 and permission-group-hierarchy.spec.yaml §1.1, the label must be "본인" (no "만").
2. **Past requirement docs**: Some docs (e.g. 20250304, 20260305, 20250303) describe scope options as "본인만", "팀", or "조회만" in their design or change-file sections. Those are historical; the **current** UI and contract use "본인" | "부서" | "전체" and "조회" | "승인". Adding a short note in those docs (or in a central place) avoids confusion when reading old requirements.

#### Solution approach

**Contract / Spec / Docs (documentation-only):**

- **docs/api-definition.md**: In the paragraph that describes `user.screenScopes` (login response §2.1 and GET /api/auth/me if duplicated), change the phrase "scope=self → 본인만" to "scope=self → 본인". Keep "scope=team → 동일 부서" and "scope=all → 전체"; the sentence already states that approval scope is fixed to department.
- **Other docs/specs/skills**: Grep shows that `specs/permission-group-hierarchy.spec.yaml`, `docs/workflow/CONSISTENCY-STANDARDS.md`, and `.cursor/skills/auth-permission-domain/SKILL.md` (and search-history-decrypt-domain) already use the correct terminology. No change required there.
- **Past requirement docs (improvement)**: In §2 or in an "Improvements" note, recommend adding a one-line note to any past requirement doc whose **current** design section still says "조회만" or "팀" as the scope label: "Current UI uses 조회 | 승인 and 본인 | 부서 | 전체 per CONTRACT/SPEC." This can be done in the doc’s §2 or in a short "Terminology alignment" note at the end of §2.

**Backend / Frontend / DB:** No code change. Already compliant (ScreenConstants, ScreenSelectionTree SCOPE_OPTIONS, PendingApprovals, spec §1.1).

### Affected scopes and change targets (verification)

**Checklist run** (per `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md` §1 and §2.1):

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|-----------|-----------------------------------------------|
| Backend | No | No code change; terminology is doc-only. |
| Frontend (config UI + view screen) | No | No code change; already uses 본인 \| 부서 \| 전체 and 조회 \| 승인. |
| DB | No | No change. |
| Contract / Spec | Yes | docs/api-definition.md — fix "본인만" → "본인" in screenScopes description. Specs already correct. |
| Cursor tools (skills, specs) | No | Skills and permission-group-hierarchy.spec.yaml already correct. |

**Pattern §2.1 (scope-supporting / terminology):** This requirement is about **terminology and scope guidelines**, not adding a new scope-supporting screen. Touchpoints for "terminology alignment" are: Contract/Spec (api-definition.md), and optionally a note in past requirement docs. All listed in §2 and in the change file list below.

### Change file list

**(Tentative. Implementing agent confirms or updates. This requirement is doc-only; no implementing agent for code.)**

#### Contract / Spec / Docs

| File | Change |
|------|--------|
| `docs/api-definition.md` | In the `user.screenScopes` description (login response §2.1, ~line 45), replace "scope=self → 본인만" with "scope=self → 본인". |

#### Backend

- No change.

#### Frontend

- No change.

#### DB

- No change.

#### Cursor skills / specs

- No change (already aligned).

### Improvements (recommendations)

1. **Replace "본인만" with "본인" in docs/api-definition.md**  
   In the sentence that describes `screenScopes` (목록/조회에만 적용; scope=self → …), use "scope=self → 본인" so it matches CONSISTENCY-STANDARDS and the spec.

2. **Past requirement docs**  
   If any of the following still describe scope labels as "본인만" or "팀" in their **current** design or expected-outcome sections (not only in §5 historical results), add a one-line note:  
   *"Current UI and contract use scope labels 본인 | 부서 | 전체 and function labels 조회 | 승인 (not 조회만, not 팀) per specs/permission-group-hierarchy.spec.yaml §1.1 and docs/workflow/CONSISTENCY-STANDARDS.md §7."*  
   Candidates (from grep): `docs/requirements/20250304-permission-scope-team-and-approval-pending.md`, `docs/requirements/20250304-team-scope-default-and-approval.md`, `docs/requirements/20260305-pending-approvals-scope-same-as-search-history.md`, `docs/requirements/20250303-activity-statistics-self-only-scope.md`, `docs/requirements/20250303-screen-function-availability.md`, `docs/workflow/ANALYSIS-pending-approvals-scope-frontend-incomplete.md`. Optional: `docs/SKILL-DOCUMENT-IMPROVEMENT-DESIGN.md` (table "self=본인만" → "self=본인" for consistency).

---

## 3. Test approach

### Test case list (required)

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|----|-------|------|------------------------------|-----------------|--------------------------------------------|
| TC-01 | — | Doc | Grep for "본인만" in docs/, specs/, .cursor/skills/ in terminology-defining or user-facing context. | After fix: zero hits in api-definition.md; allowed exceptions: historical requirement docs that keep old wording but have the one-line note. | Manual (grep) |
| TC-02 | — | Doc | Grep for "팀" as scope label (e.g. "scope … 팀", "옵션 … 팀") in docs/, specs/, .cursor/skills/. | No authoritative doc (api-definition, contract, spec §1.1, CONSISTENCY-STANDARDS) uses "팀" for scope; spec and skills use "부서". | Manual (grep) |
| TC-03 | Frontend | Manual | Open permission group management, select a scope-supporting screen (e.g. search-history or pending-approvals). | Scope dropdown shows "본인" | "부서" | "전체"; for approve screens, radio shows "조회" | "승인" (not "조회만"). | Manual / browser |

### Test scenarios

#### Scenario 1: Doc/spec consistency (TC-01, TC-02)

1. Run: `grep -r "본인만" docs/ specs/ .cursor/skills/ --include="*.md" --include="*.yaml"` (or equivalent).
2. Confirm `docs/api-definition.md` no longer contains "본인만" in the screenScopes description.
3. Run: `grep -r "팀" docs/ specs/ .cursor/skills/` and exclude false positives (e.g. "팀장", department names like "개발팀", "팀 선택"). Confirm no terminology-defining doc uses "팀" for scope label.

#### Scenario 2: Optional — UI labels (TC-03)

1. Log in as admin, open permission group management.
2. Create or edit a group; select "검색 이력" or "승인 대기".
3. Verify scope dropdown labels: "본인", "부서", "전체".
4. Verify approve screens show "조회" and "승인" (not "조회만").

### Test data

- None required (doc-only change).

### Test environment

- Docs: workspace `docs/`, `specs/`, `.cursor/skills/`.
- Optional UI: Frontend at contract URL (e.g. http://localhost:3001).

### 3.5 Browser automation verification (optional)

- **Applicable TCs**: TC-03 (manual UI check).
- **Procedure**: Login as admin → permission group management → select scope-supporting screen → snapshot to confirm "본인" | "부서" | "전체" and "조회" | "승인".
- **Reference**: `docs/workflow/BROWSER-AUTOMATION-VERIFICATION-POLICY.md`.

---

## 4. Checklist

### Frontend verification

- [ ] N/A (no code change).

### Backend verification

- [ ] N/A (no code change).

### Integration

- [ ] Doc grep (TC-01, TC-02) performed.

### Documentation

- [ ] Requirement doc completed.
- [ ] docs/api-definition.md updated (본인만 → 본인).

---

## 5. Test results

### Test run date

- [To be filled when verification is run]

### Test results

#### Docs

- [ ] TC-01: grep "본인만" — api-definition.md fixed; other hits reviewed.
- [ ] TC-02: grep "팀" (scope context) — no violation in authoritative docs.
- [ ] TC-03 (optional): UI labels confirmed.

**Commands (example):**

```bash
grep -rn "본인만" docs/ specs/ .cursor/skills/ --include="*.md" --include="*.yaml"
grep -rn "팀" docs/api-definition.md docs/contract.md specs/permission-group-hierarchy.spec.yaml docs/workflow/CONSISTENCY-STANDARDS.md .cursor/skills/auth-permission-domain/
```

**Outcome:**

- [To be filled]

### Issues found and resolution

- (None yet)

### Next steps

1. Apply the change to `docs/api-definition.md` (본인만 → 본인).
2. Optionally add the one-line terminology note to the listed past requirement docs.
3. Re-run grep (TC-01, TC-02) to confirm.

---

## 6. Error remedy result (cause and action) — for error/bug fix requirements only

- Not applicable (this is a documentation alignment requirement).

---

## 7. Final version (Korean) — add after all verification is complete

- **요건 설명**: 권한관리 UI·문서를 용어 "본인"|"부서"|"전체" 및 범위 지침(조회 범위만 선택 가능, 승인 범위 부서 고정)에 맞춤. "본인만", "팀" 사용 금지.
- **기대 결과**: api-definition.md에서 "본인만" → "본인" 수정; 필요 시 과거 요구사항 문서에 현재 용어 안내 문구 추가.
- **검증 결과**: (§5 완료 후 기입)

---

**Author**: Requirements subagent  
**Date**: 2026-03-06  
**Status**: In progress
