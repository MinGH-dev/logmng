# 20260408 - My page (modal), local default password, read-only profile fields

**Language**: §1 includes the **user-provided Korean source text** unchanged. §2 and §3 are authored in **English**. **§7 (Final Korean)**: add after QA completes verification per `docs/workflow/DOCUMENT-LANGUAGE-POLICY.md`.

**Commit**: Commits closing this requirement must reference this document (e.g. `req 20260408-my-page-local-password-and-profile` or `docs/requirements/20260408-my-page-local-password-and-profile.md`).

---

## 1. User requirement

### Original request (Korean) — preserve verbatim

1) 사용자 등록 시 AD 연동이 아닌 경우 패스워드 초기값은 user123으로 통일  
2) 사용자가 자신의 패스워드를 바꿀 수 있도록 마이페이지 제공  
3) 마이페이지에서 부서정보·이름은 보여주되 수정 불가  
4) 마이페이지 진입: 우측 상단 이름 옆 사용자 아이콘, 클릭 시 모달로 마이페이지  

### Requirement description

When **user registration / provisioning** occurs in a deployment where **directory (AD) authentication is not used** for that user’s login path (i.e. **local / table-backed** identity per `docs/contract.md` `auth.login.mode=local` and provisioning flows that set `password_hash`), the **initial password** for newly created application users must be **uniformly `user123`** (product-defined default for this requirement).

End users must be able to **change their own password** via a **“My page”** experience. On My page, **department** and **display name** (name) must be **visible but not editable**. Entry: **top-right**, next to the **user name**, a **user icon**; **clicking** it opens **My page in a modal** (not a separate routed screen unless product later extends).

Deployments in **`auth.login.mode=ad`** must **not** apply the `user123` initial-password rule to directory-authenticated users; **AD users do not use `app_user` stored passwords** for login per existing contract (no persisting AD passwords in `password_hash`). **TODO:** Confirm whether any **hybrid** user rows (local hash + external link) exist and how My page / password change should behave for them — see §2 **Open points**.

### User scenario

1. An administrator registers a new user in a **local-mode** environment; the system sets the initial credential so the user can first log in with password **`user123`** (subject to §2 password-hashing rules).
2. The user logs in, sees the main shell, and notices their **name** in the **top-right**; beside it, a **user icon** is shown.
3. The user **clicks the icon**; a **modal** opens (**My page**) showing **department**, **name**, and a **change password** section (fields TBD in Contract: current password, new password, confirm).
4. **Department** and **name** are **read-only**; the user enters **current** and **new** password, submits, and receives **clear success or validation errors** (no silent failure).
5. In **AD mode**, a normal user **signs in with directory credentials**; **My page** either **hides password change** or shows a **read-only notice** that password is managed by the organization — **TODO:** product decision (§2).

### Expected outcome

- **Local / non-AD provisioning**: initial password for new users is **`user123`** (single agreed default); stored only as a **password hash** (see §2.1), never logged in cleartext.
- **Self-service password change** is available from **My page** for users who **authenticate with local `password_hash`** (at minimum).
- **My page** displays **department** and **name** as **read-only**; users cannot modify those fields through this UI.
- **Entry UX**: **top-right name + user icon** → **click** → **modal** My page; focus trap and a11y consistent with existing modal patterns.
- **Contract / API** documents the password-change endpoint, validation rules, and error codes; **AD vs local** behavior is explicit.
- **Regression**: automated tests cover default password for local create paths, modal open/close, read-only profile fields, and password validation paths (§3).

**Note:** Numeric/layout standards for the top bar follow existing app shell / UX docs; this requirement does not introduce new search-filter §2.4 alignment unless product ties My page to those screens.

---

## 2. Design

### 2.1 Security review (PII / passwords / AD vs local)

- [ ] Security review performed (check when Step 3 Security completes)

| Topic | Design expectation |
|--------|-------------------|
| **Password at rest** | **`password_hash` must use a strong one-way hash (e.g. BCrypt)**; verify algorithm matches existing `AuthService` / user-create paths or **migrate** consistently. **Never** store `user123` or any cleartext password in DB columns or logs. |
| **AD mode** | Do **not** write end-user AD passwords to `app_user`. Password self-service in AD mode is **out of scope** unless product explicitly requires a directory-password-change integration **TODO**. |
| **Transport** | Password change only over **HTTPS** in production (deployment assumption). |
| **Session** | After successful password change, define whether **session remains valid** or **forces re-login** — **TODO:** product preference; document in Contract (both are acceptable if consistent). |
| **Rate limiting / lockout** | **TODO:** whether optional brute-force limits apply to password-change API (recommend align with login policy if any). |
| **Activity audit** | **TODO:** whether `user_activity_log` gets a new action type for “password changed (self)” — recommend for security reviews; confirm with activity-type contract. |
| **Authorization** | Password change endpoint: **authenticated user only**, scoped to **self** (`app_user.id` from session); **no** admin-on-behalf via this API unless a separate requirement. |

#### Risks (initial)

- **Uniform default password** (`user123`) increases **credential stuffing** risk if users are not forced to change on first login — **TODO:** product may require **“must change password on first login”** in a follow-up requirement; not mandated here unless confirmed.
- **User enumeration** via password-change error messages — responses should be **generic** where appropriate (e.g. invalid current password without revealing whether account exists).

### Technical design

#### Problem analysis

1. **AD vs local**: Contract already defines **`auth.login.mode`** `local` | `ad`. Initial password `user123` applies only to **local-mode** provisioning / direct user create, not to pure AD authentication flows.
2. **Provisioning touchpoints**: User creation may occur via **User Management**, **User Management v2**, or **provisioning APIs** — all paths that set **`password_hash`** for local users must **converge on the same default** (`user123`) when external AD is **not** the auth source for that user.
3. **UI shell**: Top-right user area must gain an **icon affordance** and **modal** without breaking existing layout (sidebar / top bar z-index per project patterns).
4. **API gap**: A **documented** authenticated endpoint (or extension of existing user API) is needed for **self password change** with validation and consistent error codes.

#### Diagnostic phase (mandatory for error/bug fix only)

*Not applicable — feature requirement.*

#### Solution approach

**Frontend:**

- Add **user icon** adjacent to **displayed user name** (top-right); **click** opens **My page modal**.
- Modal sections: **Profile (read-only)**: department, name (labels aligned with `GET /api/auth/me` or agreed contract fields); **Change password**: current password, new password, confirm; submit calls Contract-defined API; show errors inline or via existing error utility.
- When **`auth.login.mode` is `ad`** (or when server indicates **no local password**): **hide** password change **or** show read-only explanation — **TODO** product decision (§2 Open points).
- Unit tests: modal visibility, read-only fields, validation messaging, API error mapping.

**Backend:**

- Ensure **all local user creation paths** set initial `password_hash` from password **`user123`** (hashed) when not AD-backed — **single helper** preferred to avoid drift.
- Implement **`PUT` or `PATCH`** (Contract must fix method/path) **authenticated self password change**: verify **current password** against `password_hash`, validate **new password** policy, update hash, return success/errors per `docs/contract.md` / `docs/api-definition.md`.
- Do **not** apply default password logic to users created **only** for AD binding without local password — align with §2.1.

**DB:**

- **Likely no schema change** if `password_hash` already exists; **TODO:** confirm no new columns (e.g. `must_change_password`, `password_changed_at`) — optional product follow-up.

**Contract / specs:**

- Update **`docs/contract.md`**, **`docs/api-definition.md`**, and a **`specs/*.spec.yaml`** slice (e.g. extend **`specs/external-identity-auth.spec.yaml`** or add **`specs/my-page-password.spec.yaml`**) with: request/response, error codes (`INVALID_INPUT`, `UNAUTHORIZED`, **`FORBIDDEN`**, wrong current password code TBD), and **AD vs local** behavioral notes.

### Affected scopes and change targets (verification)

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|------------|-----------------------------------------------|
| Backend | Yes | [ ] |
| Frontend | Yes | [ ] |
| DB | Maybe (TODO) | [ ] |
| Contract / Spec | Yes | [ ] |
| Cursor tools (skills, specs) | Optional | [ ] |

### Open points (**TODO** — explicit)

| ID | Item | Owner |
|----|------|--------|
| TODO-01 | **Password policy** for new password (min length, complexity, history) — product / Security | Contract + Backend |
| TODO-02 | **AD mode**: hide password change vs informational message vs out-of-scope | Product + Frontend |
| TODO-03 | **Session handling** after successful change (stay logged in vs force re-login) | Product + Backend |
| TODO-04 | **Activity log** event for self password change — required or optional | Product + Backend + `activity-action-types` |
| TODO-05 | **First-login forced change** for `user123` — in scope or follow-up requirement | Product |
| TODO-06 | **Hybrid** users (external identity link + local hash) — if any, clarify My page rules | Product + Backend |
| TODO-07 | Exact **REST path** and HTTP method for password change API | Contract |
| TODO-08 | **Rate limiting** on password-change endpoint | Security / Backend |

### Planned change file list (expected change targets)

**(Planned at authoring. Implementing agent (Step 4) confirms or amends when implementation is complete.)**

#### Frontend

- `frontend/src/App.js` or top-bar/shell component (e.g. header next to user name)
  - User icon; open/close My page modal; wire auth mode if needed for conditional password form.
- `frontend/src/components/...` (new or existing)
  - `MyPageModal` (or equivalent): read-only department/name; password change form; tests.
- `frontend/src/services/*`
  - API client for password change.

#### Backend

- User create / provisioning services (e.g. `UserManagement*Service`, `UserManagementV2Service`, provisioning controllers)
  - Centralize default initial password `user123` → hash for **local** creates.
- `AuthService` or dedicated `UserProfileService` / controller
  - Self password change endpoint; validate current password; update `password_hash`.
- Tests: service + controller for default password on create, password change success/failure, AD/local branching if implemented.

#### DB

- **None** unless TODO-01/TODO-05 introduce columns — implement only after Contract approval.

#### Contract / documentation

- `docs/contract.md` — password change API summary; initial password rule for local provisioning.
- `docs/api-definition.md` — endpoint detail and error codes.
- `specs/*.spec.yaml` — normative request/response for My page password change and config flags if any.

---

## 3. Test approach

### Test case list (required)

**Domain-specific completeness**: Password change is **authenticated**; ensure **`api-permission-map`** (or auth skill) expectations are met: **session required**, **no elevation**, **self scope only**.

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification |
|----|-------|------|------------------------------|-----------------|--------------|
| TC-01 | Backend | Normal | **Local** admin/API creates a new user (non-AD path); read stored credentials via service or login | User can authenticate with initial password **`user123`** (after hash compare) | Unit / integration (`mvn test`) |
| TC-02 | Backend | Regression | Multiple create paths (if distinct: legacy user create, v2 direct create) all use **same** initial password | Same as TC-01 for each path | Unit (`mvn test`) |
| TC-03 | Backend | Exception | Password change: wrong **current** password | **4xx** with agreed error code; **no** hash update | Unit (`mvn test`) |
| TC-04 | Backend | Exception | Password change: new password fails policy (when TODO-01 defined) | **400** `INVALID_INPUT` (or agreed code); no update | Unit (`mvn test`) |
| TC-05 | Backend | Exception | Password change: **unauthenticated** request | **401** | Unit (`mvn test`) |
| TC-06 | Backend | Edge | **AD mode** (or user flagged **no local password**): password change behavior per TODO-02 | **403** / hidden feature / documented stub — align with Contract after decision | Unit (`mvn test`) |
| TC-07 | Frontend | Normal | Click **user icon** next to name | **My page modal** opens; focus manageable | Unit (`npm test`) |
| TC-08 | Frontend | Normal | My page shows **department** and **name** | Fields are **read-only** (no input or `readOnly` / disabled) | Unit (`npm test`) |
| TC-09 | Frontend | Normal | Submit password change with valid inputs | Success feedback; API called with expected payload | Unit (`npm test` mock) |
| TC-10 | Frontend | Exception | API returns validation error | User-visible message; no crash | Unit (`npm test`) |
| TC-11 | Integration | Normal | E2E: local user logs in → open modal → change password → login again with **new** password | New password works; old fails | Manual or scripted integration |
| TC-12 | Integration | Regression | Fresh **local** user from provision path: initial login **`user123`** | Documented in test data / setup SQL | Integration |

### Test scenarios

#### Scenario 1: My page modal and read-only profile

1. Log in as a **local** user with known department/name on `GET /api/auth/me`.
2. Click **user icon** (top-right).
3. Verify modal title/region, **department** and **name** visible and **not editable**.
4. Close modal (ESC or primary close); verify return to shell.

#### Scenario 2: Password change validation

1. Open My page; enter **wrong** current password → expect error.
2. Enter **valid** current + **invalid** new (per policy when defined) → expect error.
3. Enter valid **current + new + confirm** → success; verify login with new password (TC-11).

### Test data

- **SQL / fixtures**: At least one **`app_user`** row created via **local** provisioning with initial password verifiable as `user123` (via login test, not by reading hash).
- **TODO:** Document exact insert or use API-only setup per QA playbook.

### Test environment

- Frontend: `http://localhost:3001` (per contract)
- Backend: `http://localhost:9200`
- Database: PostgreSQL (project standard)

### 3.5 Browser automation verification (optional)

- **Applicable TCs**: TC-07, TC-08, TC-11 (and modal a11y spot-check).
- **Procedure**: Login → snapshot top bar → click user icon → snapshot modal → attempt read-only field interaction → submit password form.
- **Reference**: `docs/workflow/BROWSER-AUTOMATION-VERIFICATION-POLICY.md`

---

## 4. Checklist

### Frontend verification

- [ ] My page modal: open/close, icon placement, read-only profile fields
- [ ] Password form validation and API errors
- [ ] AD/local conditional UI per final TODO-02

### Backend verification

- [ ] Initial password `user123` for all local create paths
- [ ] Self password change tests and logging (no password in logs)
- [ ] Contract-aligned error codes

### Integration

- [ ] E2E password change (TC-11)
- [ ] Regression: new user initial login

### Documentation

- [ ] Requirement doc completed (when verified)
- [ ] Contract + api-definition + spec updated

---

## 5. Test results

### Test run date

- *Pending*

### Test results

*To be filled after QA.*

### Issues found and resolution

*To be filled when issues arise.*

---

## 6. Error remedy result (cause and action)

*Not applicable unless promoted from a bugfix.*

---

## 7. Final version (Korean) — add after all verification is complete

*Deferred per DOCUMENT-LANGUAGE-POLICY.md.*

---

**Author**: Requirements (subagent)  
**Date**: 2026-04-08  
**Status**: In progress
