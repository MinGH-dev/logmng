# 20260408 - User management v2 manual department tree and quick user entry

## 1. User requirement

### Requirement description

User Management v2 must support **in-tree department management** and **modal-based data entry**.

Department creation, update, and deletion must be executable only from the department tree interaction surface. Operators must not need to navigate to a separate management screen to mutate the tree.

User registration must also start from the department tree by selecting a department node and triggering "Add user", then completing required registration fields in a modal dialog.

The department tree label must display **department name only** (department code hidden from tree node label).

### User scenario

1. An authorized operator opens **User Management v2**.
2. In the tree panel, the operator selects a parent node and clicks the **Add department icon button**.
3. A department modal opens; the operator enters required fields (including mandatory department code) and confirms.
4. In the same tree panel, the operator selects a department node and clicks the **Edit department icon button**.
5. A department edit modal opens; the operator updates editable fields and confirms.
6. In the same tree panel, the operator selects a department node and clicks the **Add user icon button**.
7. A user modal opens; the operator enters/selects registration information and confirms.
8. The operator removes an unnecessary department via the **Delete department icon button** when policy allows deletion.
9. The operator hovers each icon button and confirms tooltip text for the corresponding action.
10. **Problem addressed**: operators currently need fragmented flows and cannot complete department/user creation/editing entirely from the tree context with clear icon affordances.

### Expected outcome

- Authorized users can add/edit/delete department nodes directly inside the tree interaction context.
- Department creation uses a modal flow with required field validation (department code mandatory) and explicit success/error feedback.
- Department edit flow is available and supports updating allowed department fields with validation.
- User creation starts from tree node action and uses a modal flow with required input/select fields.
- Department tree displays department name only; department code is not shown in tree node labels.
- Tree actions (`Add department`, `Edit department`, `Add user`, `Delete department`) are icon buttons with accessible name (`aria-label` and/or `title`) and hover tooltip text.
- Unauthorized users cannot execute add/delete department or add user actions; denial behavior follows contract error conventions.
- Agent delegation workflow must be preserved: the main agent does not directly implement changes and continues subagent handoff.
- `TODO`: confirm final department-delete policy (hard delete, soft delete, or blocked when child/users exist).
- `TODO`: confirm final editable department fields in update flow (name only vs name/order/status/parent move).
- `TODO`: confirm whether department code is immutable after creation.
- `TODO`: confirm mandatory user modal fields and enum/reference sources (rank, permission group, status defaults).

## 2. Design

### 2.1 Security review (optional; when PII / decryption / access control)

- [ ] Security review performed (required before implementation completion)
- Risks: User creation and department tree mutation are admin-sensitive actions; unauthorized edits can impact access scopes.
- Acceptance / recommendations: restrict v2 write operations to explicit screen-function write permission; require audit reason for mutation APIs if contract policy requires it.

### Technical design

#### Codebase summary

- Frontend currently has `UserManagement` with department tree rendering and `ExternalProvisioning` for HR-based employee search and registration.
- Backend currently exposes `/api/users` and `/api/provisioning/*` endpoints, with admin access checks.
- DB already includes `department`, `app_user`, `permission_group`, and external identity tables (`ext_department`, `ext_employee`, `app_user_external_identity`).

#### Problem analysis

1. Existing v2 behavior does not guarantee that department add/delete is completed fully within the tree context.
2. Department and user creation flows are not consistently modal-driven from node actions, causing context switching and higher operator friction.
3. Current requirement scope does not explicitly require department update flow from the same tree context.
4. Tree action controls are text/button-mixed and do not enforce icon-only action affordance with consistent tooltip/a11y semantics.
5. Tree label output can expose department code where only department name should be displayed for operator readability.
6. Permission/error handling for tree mutation and modal submission paths is not explicitly consolidated in current requirement scope.
7. Workflow governance requires subagent delegation continuity; requirement must preserve this operating model.

#### Solution approach

**Frontend:**
- In the tree UI, provide icon-button node actions for `Add department`, `Edit department`, `Delete department`, and `Add user`.
- For all action icons, provide accessible names (`aria-label` and/or `title`) and visible tooltip text on hover.
- Tree node label rendering must display department name only; department code must be hidden from the tree label.
- Use a **department create modal** launched from tree actions; validate required fields before submit and show inline validation messages, with department code as mandatory.
- Use a **department edit modal** launched from tree actions; support editable field updates with validation and success/error feedback.
- Use a **user create modal** launched from tree actions; provide required input/select controls and submit feedback.
- Keep all mutation buttons disabled/hidden for non-authorized users and display denial/error feedback consistently.
- Keep v2 flow independent from HR provisioning as default entry path.

**Backend:**
- Add/extend APIs for department add/update/delete and direct user creation tied to selected department context.
- Validate modal submission payloads (required fields, parent/department relation, permission group existence), including mandatory department code on create.
- Enforce authorization checks on every mutation endpoint with stable error code mapping.
- Return validation/authorization errors in a format frontend can bind to modal error states.
- `TODO`: confirm backend policy for department-code update attempts (reject/ignore/allow with migration impact).

**DB:**
- Reuse existing `department` and `app_user` structures unless modal-required fields require schema extension.
- Ensure delete policy constraints are enforceable (e.g., reject delete when descendants/users exist) according to final policy.
- Add migration only when requirement finalization confirms additional fields/constraints.

**Contract / Spec:**
- Update v2 contract for tree action endpoints (`add/edit/delete department`, `add user`) and modal payload validation/error schema.
- Define delete failure codes and validation failure structures for modal UX integration.
- Define authoritative sources for selectable user modal fields (`rank`, `permission`).
- Define create/update department payload constraints, including mandatory code on create and update-field constraints.

### Affected scopes and change targets (verification)

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|------------|-----------------------------------------------|
| Backend | Yes | [x] |
| Frontend (config UI + view screen) | Yes | [x] |
| DB | Yes (conditional on persistence strategy) | [x] |
| Contract / Spec | Yes | [x] |
| Cursor tools (skills, specs) | Yes | [x] |

### Planned change file list (expected change targets)

**(Planned at authoring. Implementing agent (Step 4) confirms or amends this list when implementation is complete.)**

#### Frontend
- `frontend/src/components/UserManagement/UserManagement.js`
  - Must render department-name-only tree labels, icon-button actions, hover tooltips, and create/edit department + add user flows.
- `frontend/src/components/UserManagement/UserManagement.css`
  - Must style icon actions, tooltip behavior, and v2 editor/form layout.
- `frontend/src/components/UserManagement/ExternalProvisioning.js`
  - Must align visibility and wording so HR provisioning is not the default v2 registration path.
- `frontend/src/components/UserManagement/UserManagement.test.js`
  - Must cover v2 tree label rendering, icon action accessibility/tooltip, create/edit department flow, and quick-entry UX behavior.
- `frontend/src/services/userService.js`
  - Must add/align v2 API calls.
- `frontend/src/services/permissionGroupService.js`
  - Must align permission selection behavior if reused in v2 registration.

Step 4 implementation confirmation (Frontend, 2026-04-08):
- Changed: `frontend/src/components/UserManagement/UserManagement.js`
- Changed: `frontend/src/components/UserManagement/UserManagement.css`
- Changed: `frontend/src/components/UserManagement/UserManagement.test.js`
- Changed: `frontend/src/services/userService.js`
- Not changed in this step: `frontend/src/components/UserManagement/ExternalProvisioning.js`, `frontend/src/services/permissionGroupService.js`

#### Backend
- `backend/src/main/java/com/logmng/controller/UserController.java`
  - Must expose v2 user registration and department tree mutation endpoints (add/edit/delete department) or delegated controller with equivalent policy.
- `backend/src/main/java/com/logmng/service/UserPermissionHierarchyService.java`
  - Must support manual department tree mutation operations for v2.
- `backend/src/main/java/com/logmng/service/DecryptApproverService.java`
  - Must align user list/create behavior if shared with existing user management flow.
- `backend/src/main/java/com/logmng/service/AuthService.java`
  - Must verify v2 endpoint permission checks are consistent.
- `backend/src/main/java/com/logmng/dto/request/*`
  - Must add request DTOs for v2 tree and direct registration payloads, including required-field constraints.
- `backend/src/test/java/com/logmng/controller/*`
  - Must add/extend tests for v2 endpoints and authorization behavior.
- `backend/src/test/java/com/logmng/service/*`
  - Must add/extend tests for tree mutation and registration validation.

#### DB
- `backend/src/main/resources/db/schema_sys.sql`
  - Must verify existing department/app_user schema supports v2 operations.
- `backend/src/main/resources/db/migrate-*.sql`
  - Must add migration if new columns/tables are required (e.g. recent-value persistence metadata).
- `backend/src/main/resources/db/init-data.sql`
  - Must provide minimal sample data for v2 manual tree and user registration tests.

#### Contract / spec / docs
- `docs/api-definition.md`
  - Must define User Management v2 API contracts and validation/error behavior (including department create/update constraints).
- `docs/contract.md`
  - Must align user-management screen access and endpoint expectations.
- `specs/user-management-v2.spec.yaml` (new, if Contract step confirms)
  - Must define v2 request/response model and constraints.

#### Cursor tool update targets
- `.cursor/skills/ui-ux-domain/SKILL.md`
  - Must reflect user-management v2 tree-edit and registration UX behavior.
- `.cursor/skills/auth-permission-domain/SKILL.md`
  - Must reflect v2 permission requirements.
- `.cursor/skills/department-approver-domain/SKILL.md`
  - Must align with manual department tree management rules.

## 3. Test approach

### Test case list (required)

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|----|-------|------|------------------------------|-----------------|--------------------------------------------|
| TC-01 | Frontend | Normal | Open User Management v2 with write permission | Tree node label shows department name only (department code hidden) | Unit (npm test) |
| TC-02 | Frontend | Normal | Open User Management v2 with write permission | Icon action buttons (`Add department`, `Edit department`, `Delete department`, `Add user`) are visible in valid node context | Unit (npm test) |
| TC-03 | Frontend | Normal | Hover each tree action icon button | Correct tooltip text is shown for each action | Unit / manual |
| TC-04 | Frontend | Normal | Inspect action icon DOM attributes | Each icon exposes accessible naming (`aria-label` and/or `title`) aligned with action meaning | Unit |
| TC-05 | Frontend | Normal | Click `Add department` icon on a parent node | Department create modal opens with required inputs and confirm/cancel actions | Unit / manual |
| TC-06 | Frontend | Exception | Submit department create modal without department code | Required validation message shown; submit blocked | Unit |
| TC-07 | Backend | Normal | Submit valid department add payload with code | Department node is created with correct parent relationship | Unit (mvn test) |
| TC-08 | Frontend | Normal | Click `Edit department` icon on selected node and submit valid update | Department edit modal opens and updated values are reflected in tree/detail area | Unit / manual |
| TC-09 | Backend | Exception | Submit invalid department update payload or disallowed field update | API rejects with defined validation/business error code | Unit / integration |
| TC-10 | Backend | Exception | Delete department when policy violation exists (e.g., has child/users) | API rejects with defined business error code | Unit / integration |
| TC-11 | Frontend | Normal | Click `Add user` icon from selected department node | User modal opens; department context is prebound/visible | Unit / manual |
| TC-12 | Frontend | Exception | Submit user modal with invalid/missing data | Field-level error and/or modal-level error is displayed with retry path | Unit |
| TC-13 | Backend | Normal | Submit valid user create payload from modal flow | User is created and linked to selected department | Unit / integration |
| TC-14 | Security | Exception | User without write permission attempts add/edit/delete/add-user action | Buttons hidden/disabled and API returns authorization error if called directly | Integration / manual |
| TC-15 | Integration | Regression | End-to-end create department -> edit department -> create user in that node | Data appears correctly in tree and user list without leaving v2 context | Integration / manual |
| TC-16 | Workflow | Governance | Requirement implementation is requested | Main agent does not directly implement; handoff remains delegated to domain subagents | Manual (process audit) |

### Test scenarios

#### Scenario 1: In-tree department create/edit/delete with modal
1. Select a parent node and open department create modal.
2. Submit valid input and verify the new node appears in tree.
3. Open department edit modal from target node, update allowed fields, and verify updated rendering.
4. Attempt delete on node with restricted condition and verify defined error behavior.

#### Scenario 2: In-tree user add with modal
1. Select a department node and open user create modal.
2. Enter/select required registration values and submit.
3. Verify user is created under selected department and modal success feedback is shown.

### Test data
- Departments: one root and at least two nested children.
- Users: at least three registrations across two departments via modal flow.
- Permissions: at least two distinct permission groups for select validation.
- Accessibility: test ids or selectors for icon buttons and tooltip containers.
- `TODO`: finalize department delete policy test fixture matrix (with-child, with-user, empty-node).
- `TODO`: finalize tooltip wording source of truth (i18n key vs static text) for icon actions.
- `TODO`: finalize canonical source/type for `rank` and additional user modal selectable fields.

### Test environment
- Frontend: `http://localhost:3001`
- Backend: `http://localhost:9200`
- Database: PostgreSQL (project default schema)

### 3.5 Browser automation verification (optional)

- Applicable TCs: TC-03, TC-05, TC-06, TC-08, TC-11, TC-12, TC-14, TC-15
- Procedure: login as write-authorized user -> open User Management v2 -> verify name-only tree labels -> hover icon actions and verify tooltips -> run department create/edit and user create flows -> submit success/error cases including required department code -> verify non-authorized behavior with separate account.

## 4. Checklist

### Frontend verification
- [ ] API parameters validated
- [ ] UI behavior confirmed
- [ ] Error handling verified

### Backend verification
- [ ] API test cases written and run
- [ ] Logs checked
- [ ] Performance checked (if applicable)

### Integration
- [ ] End-to-end flow tested
- [ ] Edge cases tested

### Documentation
- [ ] Requirement doc completed
- [ ] Code comments added (if applicable)

## 5. Test results

### Test run date
- Not run (requirement-authoring step only)

### Test results

#### Frontend
Not run

#### Backend
Not run

**Outcome:**
- Requirement and test approach authored for handoff.
- Implementation and verification pending Step 4/Step 5.

### Issues found and resolution
- None during requirement authoring.

### Next steps
1. Frontend/Backend/DB implementation handoff based on §2 and §3.
2. QA verification and §5 update after implementation.

---

**Author**: Requirements (subagent)
**Date**: 2026-04-08
**Status**: In progress
