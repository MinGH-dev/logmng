# 20260414 - User Management v2: create top-level department in tree toolbar

## 1. User requirement

### Requirement description

On **User Management v2** (`user-management-v2`), operators with **write** access must be able to **create the first (top-level) department** when the manual department tree has **no departments at all**. Today, department creation is only exposed as **“하위 부서 추가”** on a **selected** tree node; when the tree is empty there is **no node to select**, so **no path** opens the create flow. The product must expose **explicit top-level department creation** without requiring a pre-selected parent.

The new control must live in the **same functional row** as **모두 펼치기 / 모두 접기** (expand/collapse all), **aligned to the right** of that row. It must be **enabled only when no department is selected** (selection state **미선택**); when any department is selected, the control must be **disabled** so operators do not confuse root creation with subtree operations.

**Scope note**: **Department Approvers** and other screens use different flows; this requirement targets **User Management v2** manual tree editing only unless product extends the same pattern elsewhere.

### User scenario

1. A **system administrator** (or authorized user with **write** on `user-management-v2`) opens User Management v2 on a fresh environment where **no departments exist**.

2. The tree area shows the empty state message (e.g. 등록된 부서가 없습니다). The **bulk** row still shows **모두 펼치기** and **모두 접기**, and on the **right** side of that row a **new** action appears (e.g. **최상위 부서 추가** or equivalent product label).

3. **Selection** reads **미선택**; the new action is **enabled** (subject to write permission).

4. The user invokes the action, completes the **same class of inputs** as other department-create flows in this screen (**name**, **code** where required, **changeReason** per `specs/user-management-v2.spec.yaml`), and saves.

5. The first root department is created via **`POST /api/user-management-v2/departments/root`** (client: `createRootDepartmentV2`); the tree reloads and the new node appears.

6. The user **selects** a department in the tree. The **top-level create** control becomes **disabled**; **“하위 부서 추가”** (or equivalent) remains the way to add children under the selected node.

7. **Problem**: Without this control, **step 4 is impossible** when the tree is empty because there is no node to attach **“하위 부서 추가”** to and `openDepartmentModal` only proceeds for **child** mode.

### Expected outcome

- A **visible, discoverable** control for **creating a root (top-level) department** exists on **User Management v2**, in the **tree bulk-actions row**, **right-aligned** beside expand/collapse.
- The control is **enabled** only when **`canWrite`** and **no department is currently selected** (`selectedDepartment` is cleared / 미선택); **disabled** when a department is selected or the user lacks write access (align with existing node-level department actions).
- Submitting the flow calls **`createRootDepartmentV2`** with a request body **consistent with** `specs/user-management-v2.spec.yaml` §4.1 (`name`, `code`, optional `sortOrder`, `changeReason`).
- After success, the hierarchy **refreshes** and the new root is visible; errors surface with existing UM v2 error handling patterns.
- **Automated tests** cover visibility, enable/disable rules, and API invocation for the root path (see §3).

---

## 2. Design

### 2.1 Security review (optional; when PII / decryption / access control)

- [ ] Security review performed (check if applicable)

This change is **UI wiring** to an **existing** authenticated mutation (`POST .../departments/root`). No new data class is introduced. **Backend authorization** for root create remains authoritative; frontend must not expose the action without **`canWrite`** consistent with other department mutations on this screen.

- Risks: Misconfiguration could still allow **unauthorized** use if server checks are missing — **verify** server-side write gates match child create/delete on UM v2.
- Acceptance: Same **screen + write** rules as existing v2 department mutations; no broadening of access via UI alone.

### Technical design

#### Codebase summary (investigation)

- **`frontend/src/components/UserManagement/UserManagement.js`**
  - Tree **bulk** controls use **`user-management-v2-tree-bulk-actions`** with **모두 펼치기** / **모두 접기** (`handleExpandAll`, `handleCollapseAll`).
  - When **`tree.length === 0`**, the UI shows an empty message and **does not render** `HierarchyTree`; **node-level** actions (including **하위 부서 추가**) are unavailable.
  - **`openDepartmentModal(departmentCode, isChild)`** returns immediately if **`!isChild`**, so **root** open is blocked at the handler level.
  - **`handleSubmitDepartmentModal`** supports **edit** and **child** paths; **child** calls **`createChildDepartmentV2`** with a resolved parent code. There is **no** branch for **root**.
- **`frontend/src/services/userService.js`** exports **`createRootDepartmentV2`** (`POST .../user-management-v2/departments/root`), but **`UserManagement.js`** does **not** import or call it today.
- **`specs/user-management-v2.spec.yaml`** §4.1 documents **`POST /api/user-management-v2/departments/root`** request/response fields.

**Out of scope (unless product requests)**: **User Management PoC** (`UserManagementPoc.js`), read-only **User Permission Hierarchy** tree, legacy **UserManagementLegacy**.

#### Problem analysis

1. **Empty tree bootstrap**: With zero departments, the UI never renders tree nodes, so **child-only** create cannot start.
2. **Handler guard**: **`openDepartmentModal`** rejects non-child opens, preventing reuse without a **new entry path** (dedicated button + modal mode or equivalent).
3. **Unused client API**: Root create is implemented in **`userService`** but **not** connected to the v2 screen.

#### Solution approach

**Frontend:**

- Add a **primary or outlined** button (match existing **bulk** button styling via **`user-management-v2-tree-bulk-btn`** / `UserManagement.css`) in the **same row** as expand/collapse:
  - **Layout**: One row container: **left** — expand/collapse; **right** — root-create (e.g. flex with `justify-content: space-between` or equivalent existing pattern).
  - **Label**: Product-facing copy for **top-level / root** creation (e.g. **최상위 부서 추가**); implementer must expose stable **`aria-label`** / **`title`** for tests and a11y.
- **Enabled when**: `canWrite && selectedDepartment == null` (and not in a loading/error state that should disable actions — **align** with other toolbar buttons).
- **Disabled when**: any department is **selected**, or **`!canWrite`**, or other global disables already applied to bulk actions (e.g. **`treeFilterDisabled`** if that flag must also gate this control — **verify** consistency with expand/collapse).
- **Behavior**: Opens the existing department dialog in a **new mode** (e.g. **`root`**) or extends **`departmentModalMode`** so that submit calls **`createRootDepartmentV2({ name, code, changeReason, sortOrder? })`** per spec; on success, close modal, clear errors, **`loadHierarchy()`**.
- **Imports**: Add **`createRootDepartmentV2`** to **`UserManagement.js`** from **`userService`**.
- **Tests**: Extend **`UserManagement.test.js`** — mock **`createRootDepartmentV2`**, assert enable/disable, layout presence, and successful POST payload shape.

**Backend:**

- **No change required** if **`POST /api/user-management-v2/departments/root`** is already implemented and authorized per contract; implementer **must verify** behavior matches **`specs/user-management-v2.spec.yaml`** §4.1.

**DB:**

- None for UI-only wiring; root row creation uses existing persistence.

### Affected scopes and change targets (verification)

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|------------|-----------------------------------------------|
| Backend | No (verify only) | [x] |
| Frontend (config UI + view screen) | Yes — **view**: User Management v2 | [x] |
| DB | No | [x] |
| Contract / Spec | No change expected — **verify** `docs/api-definition.md` / `specs/user-management-v2.spec.yaml` already describe root create; update only if discovery shows doc drift | [x] |
| Cursor tools (skills, specs) | No domain model change required for toolbar wiring; optional **ui-ux** cross-reference if menu copy changes | [x] |

### Planned change file list (expected change targets)

**(Planned at authoring. Implementing agent (Step 4) confirms or amends this list when implementation is complete.)**

#### Frontend

- `frontend/src/components/UserManagement/UserManagement.js`
  - Add root-create control to **bulk** row; enable/disable per selection + `canWrite`; wire **`createRootDepartmentV2`** and modal submit path for **root** mode.
- `frontend/src/components/UserManagement/UserManagement.css`
  - **Must** place root action **on the right** of the expand/collapse row (spacing aligned with existing hierarchy layout).
- `frontend/src/components/UserManagement/UserManagement.test.js`
  - **Must** add or extend tests for root-create visibility, gating, and API call.

#### Backend

- None unless verification finds missing or non-contract **`POST .../departments/root`** behavior (then track as a separate backend requirement).

#### DB

- None.

---

## 3. Test approach

### Test case list (required)

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|----|-------|------|------------------------------|-----------------|--------------------------------------------|
| TC-01 | Frontend | Normal | `canWrite === true`, `selectedDepartment === null`, tree empty | Root-create control is **visible** and **enabled** | Unit (npm test) |
| TC-02 | Frontend | Normal | `canWrite === true`, user has **selected** a department | Root-create control is **disabled** | Unit (npm test) |
| TC-03 | Frontend | Normal | `canWrite === false` (read-only) | Root-create control is **not** usable (hidden or disabled — **same pattern** as node department actions) | Unit (npm test) |
| TC-04 | Frontend | Normal | User clicks root-create, submits valid **name**, **code**, **changeReason** | **`createRootDepartmentV2`** called once with body matching spec (including `changeReason`); modal closes; hierarchy reload invoked | Unit (npm test) |
| TC-05 | Frontend | Edge | Submit root-create with validation errors (e.g. empty **changeReason**) | Error message shown; **no** successful API call | Unit (npm test) |
| TC-06 | Frontend | Normal | Tree **non-empty**, `selectedDepartment === null` | Control **enabled** if product keeps rule “only selection matters”; if product restricts to empty-tree-only, adjust TC — **confirm** with product | Unit (npm test) |
| TC-07 | Integration | Normal | Manual or browser: empty DB, write user | User can create first root from toolbar; tree shows new department | Manual / browser |
| TC-08 | Integration | Regression | After first root exists | **하위 부서 추가** from selected node still works; root-create disabled when selection active | Manual / browser |

**Note on TC-06**: §1 states enable when **no department selected**. If product additionally requires **empty tree only**, update §1 and this TC to **disable** when `tree.length > 0` even with 미선택 — implementer **must confirm** with product before coding.

### Test scenarios

#### Scenario 1: Bootstrap empty organization

1. Ensure no departments (or use isolated test data).
2. Open User Management v2 as write user.
3. Confirm bulk row shows expand/collapse **and** root-create on the **right**; root-create enabled.
4. Create root department; verify tree loads.

#### Scenario 2: Selection disables root-create

1. With at least one department, select a node.
2. Confirm root-create is **disabled**.
3. Deselect if the product adds deselection — if not applicable, rely on TC-02 state only.

### Test data

- Environment with **zero** `department` rows relevant to UM v2 hierarchy (or API returns empty tree). Provide **executable** INSERT/DELETE or admin SQL via QA playbook if needed.

### Test environment

- Frontend: `http://localhost:3001` (or per contract)
- Backend: `http://localhost:9200`
- Database: PostgreSQL (per project)

### 3.5 Browser automation verification (optional)

- **Applicable TCs**: TC-07, TC-08
- **Procedure**: Login → navigate to User Management v2 → snapshot toolbar → exercise root create → snapshot tree.
- **Reference**: `docs/workflow/BROWSER-AUTOMATION-VERIFICATION-POLICY.md`

---

## 4. Checklist

### Frontend verification

- [ ] API parameters validated (match `specs/user-management-v2.spec.yaml` §4.1)
- [ ] UI behavior confirmed (placement, enable/disable)
- [ ] Error handling verified

### Backend verification

- [ ] N/A for UI-only change (or run if backend fixes needed)

### Integration

- [ ] End-to-end flow tested (TC-07)
- [ ] Edge cases tested (TC-05, TC-08)

### Documentation

- [ ] Requirement doc completed
- [ ] Code comments added (if applicable)

---

## 5. Test results

### Test run date

- (Pending)

### Test results

#### Frontend

- (Pending)

#### Backend

- (Pending)

**Commands:**

```bash
# (To be filled by QA — e.g. cd frontend && npm test -- --watchAll=false)
```

**Outcome:**

- (Pending)

### Issues found and resolution

- (None yet)

### Next steps

1. Frontend implementation per §2.
2. QA runs §3 and updates §5.

---

## 6. Error remedy result (cause and action) — for error/bug fix requirements only

N/A (feature requirement).

---

## 7. Final version (Korean) — add after all verification is complete

Per `docs/workflow/DOCUMENT-LANGUAGE-POLICY.md`, add **§ Final version (Korean)** after QA verification passes.

### Final Korean summary

- (Pending)

---

**Author**: Requirements (subagent)  
**Date**: 2026-04-14  
**Status**: In progress
