# 20260306 - Approval scope fixed to department (read-only in UI)

## 1. User requirement

### Requirement description

For **approval** (승인) on permission-group–configurable screens (search-history, pending-approvals), the **permission scope** must be **fixed to department** (부서; API value: `team`) and **not configurable**. The scope selection in the permission config UI must **show "부서" when "승인" is selected** and must be **unchangeable** (read-only or disabled). Only **list/read scope** (조회 범위) remains selectable (본인 | 부서 | 전체) when the user has selected "조회" (view-only) for that screen.

This implements the product rule already stated in `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md` §2.1 and in contract/spec: "조회(목록) 범위만 선택 가능(본인/부서/전체). **승인 범위는 부서로 고정·변경 불가**."

### User scenario

1. An administrator opens **권한 그룹 관리** (permission group management) and edits a permission group (create or update).
2. For **검색 이력** (search-history) or **승인 대기** (pending-approvals), the admin selects the screen and chooses **"승인"** (approve) via the radio group.
3. **Current problem**: The scope dropdown (조회 범위) remains editable; the admin can set scope to "본인" or "전체". This contradicts the rule that approval scope is department-only and not configurable.
4. **Expected**: When "승인" is selected for that screen, the scope is displayed as **"부서"** and the scope control is **not changeable** (disabled or read-only). When "조회" is selected, the scope dropdown remains selectable (본인 | 부서 | 전체) as today.

### Expected outcome

- **Configuration UI**: For screens that support approve (search-history, pending-approvals), when the admin selects "승인", the scope is shown as "부서" and the scope dropdown is disabled (or replaced by read-only text). When "조회" is selected, scope remains configurable.
- **Data**: When a permission group has approve=true for a scope-supporting screen, the stored and sent scope for that screen is always `team`; the backend persists and returns `team` so that list/approval behavior is consistent (department-scoped).
- **Backend**: When saving `allowedScreens`, if a screen has `approve=true` and supports scope (search-history, pending-approvals), the backend coerces scope to `team` before persisting, so that even an older or incorrect client cannot store self/all for approval screens.
- **Contract/Spec**: Already state that approval scope is fixed to department; no API shape change. Optional: document that the permission config UI must display scope as "부서" and read-only when approve is selected.

---

## 2. Design

### 2.1 Security review (optional; when PII / decryption / access control)

- This requirement restricts **configuration** of approval scope to department only and makes it non-editable in the UI. It does not introduce new data exposure. Approval eligibility remains enforced by `canApproveForRequester` and `is_system_admin` on the backend.
- [ ] Security review performed (check if applicable)

### Technical design

#### Codebase summary

- **Frontend — configuration**
  - **ScreenSelectionTree.js**: Renders one scope dropdown per scope-supporting screen (activity-log, statistics, search-history, pending-approvals). The dropdown is always enabled; `scopeValue` and `changeScope` allow any of self/team/all regardless of the "조회" vs "승인" radio selection. `SCREENS_WITH_APPROVE` = ['search-history', 'pending-approvals']. The comment already says "approval scope is fixed to department (부서)" but the UI does not enforce it.
  - **PermissionGroupPanel.js**: `normalizeAllowedScreens` uses `scopeScreens` and default scope (e.g. 'self' in one path); `toAllowedScreensPayload` sends each item's `scope` as-is. No coercion of scope to `team` when approve is true.
- **Backend**
  - **PermissionGroupService**: `validateAllowedScreens` validates scope values (self/team/all) for scope-supporting screens; `saveAllowedScreens` persists `item.getScope()` for scope-supporting screens. There is no logic that forces scope to `team` when `approve=true` for search-history or pending-approvals. `ScreenConstants.supportsApprove(screenId)` exists and is used for validation.
- **Contract / Spec**
  - `specs/permission-group-hierarchy.spec.yaml` §1.1 "Scope values": "Approval scope (승인 범위) — fixed, not configurable. For screens with approve (search-history, pending-approvals), who can approve is always department-scoped. The scope dropdown in permission config affects only **list/read**."
  - `docs/contract.md`: "승인 범위: 승인(approve) 가능 범위는 부서로 고정되어 있으며 권한 설정에서 변경할 수 없음."

#### Problem analysis

1. The permission config UI allows changing scope (본인/부서/전체) even when "승인" is selected, so the stored scope can be self or all for approval screens, contradicting the product rule.
2. Backend does not coerce scope to `team` when `approve=true` for search-history or pending-approvals, so inconsistent data could be stored if the UI or another client sends a different scope.
3. When loading a permission group that has approve=true and scope=self (or all) from an older run, the UI would still show an editable scope; we need to normalize to team and make the control read-only when approve is selected.

#### Solution approach

Structure by scope for handoff.

**Frontend:**

- **ScreenSelectionTree.js**
  - When `supportsScope(view) && supportsApprove(view) && approveChecked` (i.e. "승인" is selected for search-history or pending-approvals): display scope as **"부서"** (team) and **disable** the scope dropdown (or show read-only text "부서"). Do not call `changeScope` when approve is selected.
  - When building or normalizing selected items: for any item where `approve === true` and the screen is in scope-supporting + approve list (search-history, pending-approvals), set `scope` to `'team'` so that the stored/sent value is always team when approve is on.
  - When "조회" is selected (`approveChecked === false`), keep current behavior: scope dropdown enabled, value from item or default `DEFAULT_SCOPE` ('team').
- **PermissionGroupPanel.js**
  - In `normalizeAllowedScreens`: when an item has `approve === true` and is in the scope-supporting list that also has approve (search-history, pending-approvals), set `scope` to `'team'` so that on load the UI shows "부서" and disabled.
  - In `toAllowedScreensPayload` (or equivalent): when building the payload, for items where `approve === true` and the screen is scope-supporting with approve, set `scope` to `'team'` so the API always receives team for approval screens.

**Backend:**

- **PermissionGroupService** (or equivalent save path): In `saveAllowedScreens`, when processing an `AllowedScreenItem` where `ScreenConstants.supportsApprove(screenId)` and `Boolean.TRUE.equals(item.getApprove())`, **coerce scope to `"team"`** before persisting, regardless of `item.getScope()`. This ensures the DB always has scope=team for approval screens and defends against any client sending self/all.

**Contract / Spec:**

- No API or payload shape change. Optional: in `specs/permission-group-hierarchy.spec.yaml` §1.1 or in a UI note, state that the permission config UI must display scope as "부서" and make it read-only when approve is selected for that screen.

**DB:**

- No schema change. Stored scope for approval screens becomes consistently `team` after this change.

### Affected scopes and change targets (verification)

Per `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md`, the following was verified.

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|-----------|-----------------------------------------------|
| Backend | Yes | Yes — PermissionGroupService save coercion |
| Frontend (config UI + view screen) | Yes (config UI only) | Yes — ScreenSelectionTree, PermissionGroupPanel |
| DB | No | N/A |
| Contract / Spec | Optional (clarification only) | Yes — optional spec/contract note |
| Cursor tools (skills, specs) | Optional | Yes — auth-permission-domain can note UI rule |

**Domain pattern (§2.1)**: This requirement **changes** scope behavior for existing scope-supporting screens (approve case). Configuration UI (ScreenSelectionTree, PermissionGroupPanel) and backend save path are covered. View screens (PendingApprovals, SearchHistory list) do not need code changes; they already use scope from API, which will be team when approve is true.

### Change file list

**(Tentative. Implementing agent (Step 4) confirms or updates with actual files changed.)**

#### Frontend

- `frontend/src/components/PermissionGroupManagement/ScreenSelectionTree.js`
  - When approve is selected for search-history or pending-approvals: show scope as "부서" (team) and disable the scope dropdown (or render read-only "부서"). In normalizer / onChange, when approve=true for such screens, set scope to 'team'.
- `frontend/src/components/PermissionGroupManagement/PermissionGroupPanel.js`
  - In `normalizeAllowedScreens`: for items with approve=true and screen in ['search-history','pending-approvals'], set scope to 'team'. In payload builder: for such items, send scope as 'team'.
- `frontend/src/components/PermissionGroupManagement/ScreenSelectionTree.test.js`
  - Add test(s): when "승인" is selected for search-history or pending-approvals, scope dropdown is disabled (or not present) and displayed value is "부서"; onChange does not allow changing scope for that screen when approve is selected.

#### Backend

- `backend/src/main/java/com/logmng/service/PermissionGroupService.java`
  - In `saveAllowedScreens`: for each item where `ScreenConstants.supportsApprove(screenId)` and `Boolean.TRUE.equals(item.getApprove())`, set scope to `"team"` before `ps.setString(3, scope)` (or equivalent), so stored scope is always team for approval screens.
- `backend/src/test/java/com/logmng/service/PermissionGroupServiceTest.java`
  - Add or extend test: when creating/updating a permission group with allowedScreens containing search-history or pending-approvals with approve=true and scope=self (or all), stored row has scope=team.

#### Contract / Spec (optional)

- `specs/permission-group-hierarchy.spec.yaml`
  - Optional: under §1.1 Scope values or a "UI behavior" note, state that when approve is selected for a scope-supporting screen, the config UI must display scope as "부서" and make it read-only.

#### Cursor tools (optional)

- `.cursor/skills/auth-permission-domain/SKILL.md`
  - Optional: under Quick reference or "Adding a new scope-supporting screen", note that in the permission config UI, when approve is selected for search-history or pending-approvals, the scope control must show "부서" and be read-only.

---

## 3. Test approach

### Test case list (required)

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|----|-------|------|------------------------------|-----------------|--------------------------------------------|
| TC-01 | Frontend | Normal | Permission group edit: select search-history, select "승인" radio. | Scope is displayed as "부서" and the scope dropdown is disabled (or read-only). | Unit (ScreenSelectionTree.test.js) |
| TC-02 | Frontend | Normal | Permission group edit: select pending-approvals, select "승인" radio. | Scope is displayed as "부서" and the scope dropdown is disabled (or read-only). | Unit (ScreenSelectionTree.test.js) |
| TC-03 | Frontend | Normal | Permission group edit: select search-history, select "조회" radio. | Scope dropdown is enabled with options 본인/부서/전체. | Unit (ScreenSelectionTree.test.js) |
| TC-04 | Frontend | Normal | Save permission group with search-history approve=true. | Payload sent to API includes scope=team for that screen (and dropdown was disabled). | Unit (PermissionGroupPanel or integration) |
| TC-05 | Backend | Normal | Create or update permission group with allowedScreens containing search-history, approve=true, scope=self. | Stored row in permission_group_screen has scope='team'. | Unit (PermissionGroupServiceTest) |
| TC-06 | Backend | Normal | Create or update permission group with allowedScreens containing pending-approvals, approve=true, scope=all. | Stored row has scope='team'. | Unit (PermissionGroupServiceTest) |
| TC-07 | Integration | Normal | Load permission group that has search-history with approve=true; open edit. | UI shows "승인" selected and scope "부서" (read-only/disabled). | Manual / browser |
| TC-08 | Frontend | Edge | Switch from "승인" to "조회" for search-history. | Scope dropdown becomes enabled; value can be 본인/부서/전체. | Unit or manual |

### Test scenarios

#### Scenario 1: Config UI — approve selected, scope fixed and read-only

1. Log in as system admin, open 권한 그룹 관리, edit a group (or create).
2. Select "검색 이력", choose "승인" radio.
3. Verify scope shows "부서" and dropdown is disabled (or read-only text).
4. Select "승인 대기", choose "승인" radio; verify same.
5. Save; reload group; verify again that approve + "부서" read-only.

#### Scenario 2: Backend persistence

1. Via API or UI, create/update a permission group with search-history approve=true, scope=self (or all).
2. Query DB or GET permission group: allowedScreens entry for search-history has scope=team.

### Test data

- Permission group with search-history and pending-approvals, both with approve=true; existing groups with approve and scope=self or all (for regression).

### Test environment

- Frontend: http://localhost:3001 (or per contract)
- Backend: http://localhost:9200
- Database: PostgreSQL (per project)

### 3.5 Browser automation verification (optional)

- **Applicable TCs**: TC-07 (load group with approve=true, confirm UI shows 부서 read-only).
- **Procedure**: Login as admin → permission group management → edit group that has search-history approve=true → confirm "승인" selected and scope "부서" and dropdown disabled or read-only. Policy: `docs/workflow/BROWSER-AUTOMATION-VERIFICATION-POLICY.md`.

---

## 4. Checklist

### Frontend verification

- [ ] Scope dropdown disabled (or read-only) when approve selected
- [ ] Payload sends scope=team for approval screens
- [ ] Normalize load: approve=true → scope=team in UI

### Backend verification

- [ ] saveAllowedScreens coerces scope to team when approve=true for search-history, pending-approvals
- [ ] Unit test for create/update with approve=true and scope=self or all → stored scope=team

### Integration

- [ ] Edit and save round-trip: approve + 부서 persists and displays correctly

### Documentation

- [ ] Requirement doc completed
- [ ] Optional: spec/skill note on UI behavior

---

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
# Frontend unit
cd frontend && npm test -- --watchAll=false --testPathPattern="ScreenSelectionTree"

# Backend unit
cd backend && mvn test -Dtest=PermissionGroupServiceTest
```

**Outcome:**

- [Item 1]
- [Item 2]

### Issues found and resolution

(To be filled when tests or verification are run.)

### Next steps

1. Implement Frontend (ScreenSelectionTree, PermissionGroupPanel) and tests.
2. Implement Backend (PermissionGroupService save coercion) and test.
3. Run verification; update §5; optional spec/skill update.

---

## 6. Error remedy result (cause and action) — for error/bug fix requirements only

N/A — this is a new product behavior requirement.

---

**Author**: Requirements subagent  
**Date**: 2026-03-06  
**Status**: In progress
