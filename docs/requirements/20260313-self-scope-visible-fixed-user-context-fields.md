# 20260313 - Self scope visible fixed user-context fields

## 1. User requirement

### Requirement description
Redefine the current `scope=self` search/filter standard for applicable user-context or requester-context screens. The existing standard says the user/requester block must be hidden when the effective screen scope is `self`, but the user now requests the opposite usability rule: keep the relevant identity fields visible so the user can see why the screen is locked to the authenticated user context.

This change does **not** widen the access-control boundary. `scope=self` must remain current-user-only at the backend. The requested change is a reusable presentation and contract standard: when effective `scope=self`, the screen must show fixed self-context values instead of hiding the fields, and the same rule must be documented in design docs, contract/spec wording, and Cursor tool guidance so future screens follow the same pattern consistently.

The authoritative source for those locked self display values must be the auth current-user response contract (currently the authenticated payload family exposed by `/api/auth/check` and `/api/auth/me`), not the shared filter-options endpoint. This requirement therefore introduces an explicit self-context contract in auth responses for self-scoped locked values.

Known baseline scope for this requirement:
- The requirement applies to all current user-context or requester-context screens that use the shared user/requester filter-block pattern, not only to the initially discussed examples.
- `activity-log`, `statistics`, and `search-history` remain the confirmed baseline examples because those screens already share user-axis design rules and the shared department filter-options contract.

This requirement intentionally replaces the older guidance that said `scope=self` hides the user/requester block on those screens.

### User scenario
1. A non-admin user opens a user-context search screen whose effective scope is `self`.
2. The screen still shows the user/requester block instead of hiding it.
3. The user can see that Department, Username, and User ID are each fixed to the current authenticated user's own values.
4. The user runs search or reset and expects the result set to remain current-user-only.
5. **Problem**: current docs, shared design definitions, shared UI behavior, and shared filter-options wording still encode `scope=self => hide the block`, which harms usability and makes the locking rule invisible to the user.
6. The user wants one reusable standard, not a one-screen exception, so future screens and future agent-generated changes do not revert to the old hidden-field pattern.

### Expected outcome
- For the applicable self-scoped user-context/requester-context screens, the user/requester block must remain visible instead of being hidden.
- Department must be fixed to the current authenticated user's own department from the auth current-user self-context contract and must not be editable in effective `scope=self`.
- Username must be visible, fixed to the auth current-user self-context contract, and must not be editable in effective `scope=self`. If the system does not yet expose a distinct profile/display-name field, the authenticated username string must be used as the locked `username` display until a separate canonical display-name model exists.
- User ID must be shown only as the current authenticated user's own canonical `userId`, where this requirement defines `userId` as `app_user.username` (the authenticated login/user identifier used by the project today), and it must not be editable or switchable in effective `scope=self`.
- The backend must continue to treat client-provided identity filters as non-authoritative in effective `scope=self`, so the result set remains current-user-only regardless of what the client sends.
- The self-scoped visible fields must use the same user-block order and the same width/size rules across aligned screens; read-only self presentation must not shrink, squeeze, or visually redefine the user block compared with the shared editable standard.
- The auth current-user contract, shared department option contract, per-screen design docs, contract/spec wording, and Cursor domain guidance must all describe the same `visible but fixed to current user` standard instead of the old `hidden on self` standard.
- The common fixed-self presentation for the shared user/requester block must be `department -> username -> userId`, with all three fields visible and non-editable under effective `scope=self`.
- The rollout scope for this requirement is all current relevant screens that already use the shared user-context/requester-context pattern; implementation must align every such current screen in the same delivery, not only the initial three examples.

## 2. Design

### 2.1 Security review (optional; when PII / decryption / access control)
- [x] Security review performed
- Risks:
  - If the fields become visible but the backend stops being the source of truth, `scope=self` can regress into horizontal privilege escalation through direct API calls or tampered client state.
  - If visible self-context values and authoritative backend values drift, the UI can show one identity while the query executes with another interpretation.
  - If shared screen standards are updated only in some places, different self-scoped screens can diverge again and reintroduce insecure widening paths.
- Acceptance / recommendations:
  - `scope=self` must remain a backend-enforced current-user-only boundary; visibility of fields must never be treated as permission.
  - Department, Username, and User ID values shown under `scope=self` must be fixed to the authenticated user context and must not accept widening input from the client.
  - Any self-scoped display source used by the frontend must not expose third-party identity candidates or department candidates.
  - Logging, error handling, and normalization paths must avoid preserving attacker-supplied third-party identity values as authoritative data.

### 2.2 Codebase summary
- **Frontend**
  - `frontend/src/App.js` and `frontend/src/utils/security.js` currently persist authenticated user context from `/api/auth/check`, but they do not yet carry an explicit self-context payload for locked `department -> username -> userId` display.
  - `frontend/src/components/common/UserContextFilterBlock.js` is the current shared primitive for `department -> username -> userId`, and it still uses a binary `hideUserFilters` contract that returns nothing for `scope=self`.
  - `frontend/src/components/UserActivityLog/UserActivityLogSearchForm.js` and `frontend/src/components/UserActivityLog/UserActivityLogList.js` currently clear and hide the user block when effective `scope=self`.
  - `frontend/src/components/StatisticsFilters.js` and `frontend/src/components/ActivityStatistics.js` follow the same hide-on-self pattern for statistics filters.
  - `frontend/src/components/SearchHistory/SearchHistoryList.js` hides the requester block entirely when effective `scope=self`.
  - `frontend/src/services/filterOptionsService.js` currently serves the shared department option contract for `activity-log`, `statistics`, and `search-history`; after this requirement it must remain an editable-scope option source and must not be treated as the authoritative source for locked self display values.
- **Backend**
  - `backend/src/main/java/com/logmng/controller/AuthController.java`, `backend/src/main/java/com/logmng/service/AuthService.java`, and `backend/src/main/java/com/logmng/dto/response/LoginResponse.java` currently expose authenticated user/session data through `/api/auth/check` and `/api/auth/me`, but they do not yet define an explicit self-context contract for locked Department / Username / User ID display.
  - `backend/src/main/java/com/logmng/controller/FilterOptionsController.java` and `backend/src/main/java/com/logmng/service/FilterOptionsService.java` own the shared department filter-options endpoint that is currently documented with `scope=self => []`.
  - `backend/src/main/java/com/logmng/controller/UserActivityLogController.java` and `backend/src/main/java/com/logmng/service/UserActivityLogService.java` already enforce current-user-only behavior for `activity-log` self scope.
  - `backend/src/main/java/com/logmng/controller/ActivityStatisticsController.java` and `backend/src/main/java/com/logmng/service/ActivityStatisticsService.java` already participate in scope-sensitive statistics filtering.
  - `backend/src/main/java/com/logmng/controller/SearchHistoryController.java` and `backend/src/main/java/com/logmng/service/SearchHistoryService.java` already treat requester filters as non-widening and ignore them for effective `scope=self`.
- **Docs / design / Cursor tools**
  - `docs/design/search-fields-by-screen.md`, `docs/design/search-field-definition-items.md`, and `docs/design/forms-and-filters.md` currently encode or imply `scope=self` hiding for the aligned user-context screens.
  - `docs/contract.md`, `docs/api-definition.md`, and `specs/permission-group-hierarchy.spec.yaml` currently describe the shared department options endpoint as returning `[]` for `scope=self` and do not yet define auth self-context fields for locked self display, so the shared contract still reflects the old hide-on-self model.
  - `.cursor/skills/search-consistency-domain/SKILL.md` still states the old hide-on-self standard directly.
- **Resolved design decisions**
  - Resolved: the locked self display values (`department`, `username`, `userId`) must come from the auth current-user response contract, not from the shared filter-options endpoint.
  - Resolved: auth current-user responses must expose explicit self-context fields for locked `department -> username -> userId` display under effective `scope=self`.
  - Resolved: the visible self-scoped `userId` uses the canonical `app_user.username` meaning.
  - Resolved: until the system exposes a separate canonical display-name field, the authenticated username string is the source for the locked `username` display value.

### 2.3 Technical design

#### Problem analysis
1. The current standard is binary: effective `scope=self` hides the shared user/requester block. That behavior is implemented in shared UI logic, per-screen components, design docs, contract wording, and Cursor skills.
2. The requested usability rule changes the standard at the shared-pattern level, not only at one screen. If the requirement is written as a one-screen exception, the existing shared primitive and shared documentation will continue to recreate the old hide-on-self behavior elsewhere.
3. The current auth current-user payload does not expose an explicit self-context contract for locked `department -> username -> userId` display, while the shared filter-options endpoint is an option-source API rather than an identity-authoritative API. Without an explicit separation, the frontend can drift between display values, editable options, and enforcement semantics.
4. The current field-definition schema expresses `scopeWhenSelf` mainly as hidden vs visible, which is not rich enough for a reusable `visible-readonly-fixed-to-current-user` behavior.
5. The project currently exposes `username` but not a separate canonical display-name field in auth responses. Without an explicit rule for `username` display and canonical `userId` meaning, aligned screens and docs can label the same authenticated value differently and reintroduce drift.

#### Solution approach

**Frontend:**
- Replace the current binary self-scope presentation rule with a shared self-presentation standard for the user/requester block: editable for `team/all`, fixed-self for `self`, and hidden only where a screen explicitly has no user/requester block by design.
- Update the shared user-context primitive first (`UserContextFilterBlock` and its styling/consumers) so the standard is defined once and then consumed by every current relevant screen that uses the shared user/requester pattern, including `activity-log`, `statistics`, and `search-history`.
- In effective `scope=self`, render Department, Username, and User ID as visible locked values rather than editable inputs or hidden fields. Those locked values must come from the auth current-user self-context contract, not from `filter-options`. Do not show `"All / 전체"` or any other widening affordance for those fields.
- Keep the aligned user-block width, order, spacing, and block-level width rules the same across the applicable screens. The fixed-self presentation must not collapse or squeeze the user block relative to the editable mode.
- Preserve reset/search behavior so fixed self-context values remain visible after reset and are not silently converted back into the old hidden-state pattern.
- Treat locked `userId` as the authenticated `app_user.username` value, and treat locked `username` display as the auth current-user display string; if no separate profile/display-name field exists, reuse the authenticated username string for both roles according to the finalized contract wording.
- Require Step 4 screen inventory confirmation so all current screens that use the shared user/requester pattern receive the same fixed-self presentation in the same delivery.

**Backend:**
- Keep `scope=self` enforcement current-user-only for all affected current endpoints, including `activity-log`, `statistics`, and `search-history`; visible fields must not weaken or bypass current backend behavior.
- Standardize self-scope normalization so Department, Username, and User ID values shown in the UI are non-authoritative from the client perspective and are always resolved against the authenticated user context.
- Extend the auth current-user response contract (the authenticated payload family exposed by `/api/auth/check` and `/api/auth/me`) with explicit self-context fields for locked `department -> username -> userId` display under effective `scope=self`.
- Keep the shared department filter-options endpoint limited to editable option delivery; it must not be treated as the authoritative source of locked self display values under `scope=self`.
- Verify that statistics and search-history remain narrowing-only or current-user-only where already defined; this requirement changes visible UX and shared standards, not scope breadth.
- Preserve `scope=team` and `scope=all` semantics exactly as they are today.

**Contract / Spec / design docs:**
- Redefine effective `scope=self` wording from `hide user/requester filters` to `show fixed self-context values and treat client identity inputs as non-authoritative`.
- Define auth current-user payload as the authoritative source of locked self display values, introduce the explicit self-context contract, and fix the visible `userId` meaning to `app_user.username`.
- Document that the current authenticated username string is the locked `username` display source until a separate canonical display-name model exists.
- Update the shared department filter-options contract so it no longer conflicts with a visible locked Department field in `scope=self` and is documented only as an editable-options source.
- Expand the field-definition/design standard so self presentation can express at least one explicit fixed-readonly state instead of only hidden/visible semantics.
- Update per-screen design docs for all current screens that use the shared user/requester pattern, with `activity-log`, `statistics`, and `search-history` as the baseline confirmed examples that must remain aligned.

**Cursor tool update targets:**
- `.cursor/skills/search-consistency-domain/SKILL.md`
  - Must replace the old hide-on-self rule with the new visible-but-fixed standard.
- `.cursor/skills/auth-permission-domain/SKILL.md`
  - Must continue to state that backend self scope is authoritative while aligning the visible field behavior with the new standard.
- `.cursor/skills/api-permission-map/SKILL.md`
  - Must align required regression coverage with visible-but-fixed identity fields rather than hidden fields.
- `.cursor/skills/activity-statistics-domain/SKILL.md`
  - Must stay aligned if statistics self-scope filters adopt the same visible fixed rule.
- `.cursor/skills/search-history-decrypt-domain/SKILL.md`
  - Must stay aligned if search-history requester filters adopt the same visible fixed rule.
- `.cursor/rules/search-filter-form-design.mdc`
  - Must remain aligned with the revised self-scope presentation standard for shared user/requester filter blocks.
- `specs/permission-group-hierarchy.spec.yaml`
  - Must remain the contract-level source for scope semantics and shared filter-options behavior after the standard changes.

**Implementation note for Frontend (pattern 3.4):**
- The implementing agent must read and apply `docs/design/search-field-definition-items.md`, `docs/design/search-fields-by-screen.md`, `docs/design/forms-and-filters.md`, and `docs/design/css-standard-and-exceptions.md`, and must use `frontend/src/styles/search-filter-standard.css` as the single sizing source when changing the self-scope user/requester block presentation on aligned screens. Requirement §2 defines the shared self-scope behavior and affected screens, but the actual field sizes, width-by-role, compact spacing, panel width, and CSS exception handling must come from those design docs and the shared standard CSS. If any required standard for field metadata, self-presentation state naming, layout, spacing, label placement, or display-value source is undefined or ambiguous, the implementer must not infer it silently; the implementer must list the undefined items, explain why each is needed, propose a recommended standard draft, and request user feedback before implementation.

### Affected scopes and change targets (verification)

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|-----------|-----------------------------------------------|
| Backend | Yes | Yes |
| Frontend (config UI + view screen) | Yes (view screens and shared primitive; no permission-config UI change is planned) | Yes |
| DB | No | N/A |
| Contract / Spec | Yes | Yes |
| Cursor tools (skills, specs) | Yes | Yes |

**Applied pattern checks from `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md`:**
- **3.1 Scope-supporting screen**: covered for frontend scope consumption, backend self-scope enforcement, shared filter-options behavior, and contract/spec wording.
- **3.4 Search/filter UI consistency**: covered for shared user-block visibility, width/size consistency, design-doc references, frontend implementation note, and screen verification across aligned screens.

**§3.4 verification (mandatory when pattern applies):**
- §1 Expected outcome explicitly requires the self-scoped user block to keep the same width/size rules across aligned screens.
- §2 and the planned change file list include shared user-block ownership, block-level width preservation, and shared CSS/design-doc alignment so the user block is not squeezed.
- §3 includes explicit cross-screen TCs for self-scope visible/fixed behavior and user-block size consistency.

**Change target verification:** completed against `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md` before finalizing §2.

### Planned change file list (expected change targets)

**(Planned at authoring. Implementing agent (Step 4) confirms or amends this list when implementation is complete.)**

#### Frontend
- `frontend/src/App.js`
  - Must retain the auth current-user self-context payload from `/api/auth/check` (and any equivalent auth current-user response consumed by the app) so aligned screens can render locked self values without deriving them from option lists.
- `frontend/src/utils/security.js`
  - Must preserve any required minimal current-user self-context fields needed for locked `department -> username -> userId` display under effective `scope=self`.
- `frontend/src/components/common/UserContextFilterBlock.js`
  - Must replace the binary hide-on-self rendering rule with a shared fixed-self presentation rule.
- `frontend/src/components/common/UserContextFilterBlock.css`
  - Must preserve shared user-block width and styling while supporting locked self presentation.
- `frontend/src/components/common/UserContextFilterBlock.test.js`
  - Must cover the common fixed-self presentation for `department -> username -> userId` under effective `scope=self`.
- `frontend/src/components/UserActivityLog/UserActivityLogSearchForm.js`
  - Must adopt the shared fixed-self rule instead of hiding the user block for effective `scope=self`.
- `frontend/src/components/UserActivityLog/UserActivityLogList.js`
  - Must align request/reset behavior with visible fixed self-context values and the revised shared rule.
- `frontend/src/components/StatisticsFilters.js`
  - Must adopt the shared fixed-self rule for statistics filters and preserve the aligned user block.
- `frontend/src/components/ActivityStatistics.js`
  - Must align statistics request/export behavior with visible fixed self-context values.
- `frontend/src/components/SearchHistory/SearchHistoryList.js`
  - Must adopt the shared fixed-self requester presentation instead of hiding the requester block for effective `scope=self`.
- `frontend/src/services/filterOptionsService.js`
  - Must remain aligned with the revised contract by serving editable-scope department options only and by not acting as the source of locked self display values.
- `frontend/src/styles/search-filter-standard.css`
  - Must continue to be the single source for aligned field sizing if any self-presentation state needs standard styling support.
- `frontend/src/components/UserActivityLog/UserActivityLogList.test.js`
  - Must add or update regression coverage for visible fixed self behavior on activity-log.
- `frontend/src/components/ActivityStatistics.test.js`
  - Must add or update regression coverage for visible fixed self behavior on statistics.
- `frontend/src/components/SearchHistory/SearchHistoryList.test.js`
  - Must add or update regression coverage for visible fixed self behavior on search-history.
- `frontend/src/components/**/*`
  - Any additional current screen component that already uses the shared user-context/requester-context pattern must be aligned in the same delivery once Step 4 confirms the current inventory.

#### Step 4 frontend actual change confirmation
- `frontend/src/App.js`
  - Persists auth-owned `selfContext` from authenticated responses so shared-pattern screens can render locked self values after reload and login.
- `frontend/src/utils/security.js`
  - Normalizes and stores minimal `selfContext` (`department`, `username`, `userId`) with the cached user payload.
- `frontend/src/components/common/UserContextFilterBlock.js`
  - Replaced the old hide-on-self contract with shared `editable` / `locked` presentation modes while preserving the shared `department -> username -> userId` order.
- `frontend/src/components/common/UserContextFilterBlock.css`
  - Kept shared block sizing intact and added readonly control styling without redefining standard control sizing.
- `frontend/src/components/common/UserContextFilterBlock.test.js`
  - Added regression coverage for the shared locked-self presentation and editable mode behavior.
- `frontend/src/components/UserActivityLog/UserActivityLogSearchForm.js`
  - Keeps the user block visible in `scope=self`, shows auth-owned locked self values, and preserves them across reset.
- `frontend/src/components/UserActivityLog/UserActivityLogList.js`
  - Passes normalized auth `selfContext` into the shared user block and keeps self-scope request sanitization unchanged on the API side.
- `frontend/src/components/UserActivityLog/UserActivityLogList.test.js`
  - Updated regression coverage so `scope=self` verifies visible locked self values plus request sanitization.
- `frontend/src/components/StatisticsFilters.js`
  - Uses the shared locked-self presentation for the statistics user block while keeping team/all editable.
- `frontend/src/components/ActivityStatistics.js`
  - Uses auth-owned locked self values for statistics filters/reset and skips editable option loading in `scope=self`.
- `frontend/src/components/ActivityStatistics.test.js`
  - Added regression coverage for auth-owned locked self values and the absence of editable option loading in `scope=self`.
- `frontend/src/components/SearchHistory/SearchHistoryList.js`
  - Keeps the requester block visible in `scope=self`, sources locked values from auth `selfContext`, and continues omitting requester params from self-scope requests.
- `frontend/src/components/SearchHistory/SearchHistoryList.test.js`
  - Updated regression coverage for visible locked requester values and request omission in `scope=self`.
- Frontend inventory confirmation
  - Current shared user/requester block consumers confirmed in this delivery: `activity-log`, `statistics`, `search-history`. No additional current frontend consumer of the same shared pattern was found.

#### Step 4 cursor actual change confirmation
- No additional frontend-owned Cursor skill update was required during Step 4 because the relevant search-consistency skill text was already aligned with the visible locked self standard before implementation.

#### Backend
- `backend/src/main/java/com/logmng/controller/AuthController.java`
  - Must expose the finalized auth current-user self-context contract consistently in the authenticated response payloads used by the frontend.
- `backend/src/main/java/com/logmng/dto/response/LoginResponse.java`
  - Must define the explicit self-context fields needed for locked `department -> username -> userId` display.
- `backend/src/main/java/com/logmng/controller/FilterOptionsController.java`
  - Must keep the shared department options contract aligned with the new auth self-context contract without becoming the source of locked self values.
- `backend/src/main/java/com/logmng/service/FilterOptionsService.java`
  - Must preserve the finalized `filter-options` role as an editable option-source service and not as the self-display source.
- `backend/src/main/java/com/logmng/service/AuthService.java`
  - Must populate the authoritative self-context data used for visible locked self values in auth current-user responses.
- `backend/src/main/java/com/logmng/controller/UserActivityLogController.java`
  - Must continue to enforce current-user-only behavior regardless of visible self-scoped field values.
- `backend/src/main/java/com/logmng/service/UserActivityLogService.java`
  - Must preserve self-scope enforcement and regression coverage when visible self fields are introduced.
- `backend/src/main/java/com/logmng/controller/ActivityStatisticsController.java`
  - Must preserve self-scope enforcement and align documented self-field semantics for statistics.
- `backend/src/main/java/com/logmng/service/ActivityStatisticsService.java`
  - Must preserve narrowing/current-user-only semantics under the revised visible self standard.
- `backend/src/main/java/com/logmng/controller/SearchHistoryController.java`
  - Must preserve requester self-scope enforcement while the requester block becomes visible and fixed.
- `backend/src/main/java/com/logmng/service/SearchHistoryService.java`
  - Must preserve narrowing/current-user-only semantics under the revised visible self standard.
- `backend/src/test/java/com/logmng/controller/FilterOptionsControllerTest.java`
  - Must cover the revised shared department options behavior for effective `scope=self` without treating it as the locked self display source.
- `backend/src/test/java/com/logmng/service/FilterOptionsServiceTest.java`
  - Must cover the revised self-scope department option behavior after the display-source responsibility moves to auth current-user.
- `backend/src/test/java/com/logmng/controller/AuthControllerTest.java`
  - Must be added or updated to cover the finalized auth current-user self-context contract exposed to the frontend.
- `backend/src/test/java/com/logmng/service/AuthServiceTest.java`
  - Must be added or updated to cover authoritative self-context mapping for visible locked self values.
- `backend/src/test/java/com/logmng/controller/UserActivityLogControllerTest.java`
  - Must extend regression coverage so visible self fields cannot widen `activity-log`.
- `backend/src/test/java/com/logmng/controller/ActivityStatisticsControllerTest.java`
  - Must extend regression coverage so visible self fields cannot widen statistics queries.
- `backend/src/test/java/com/logmng/controller/SearchHistoryControllerTest.java`
  - Must extend regression coverage so visible fixed requester fields cannot widen search-history scope.
- `backend/src/test/java/com/logmng/service/SearchHistoryServiceTest.java`
  - Must cover requester filtering behavior under the revised visible self standard.

#### Contract / Spec / docs
- `docs/contract.md`
  - Must redefine `scope=self` for the applicable screens as visible fixed self-context rather than hidden user/requester filters.
- `docs/api-definition.md`
  - Must redefine self-scope field semantics and the shared department options behavior consistently with the contract.
- `specs/permission-group-hierarchy.spec.yaml`
  - Must redefine the shared self-scope and filter-options contract consistently with the updated docs.
- `docs/design/search-fields-by-screen.md`
  - Must replace `scopeWhenSelf: hidden` with the revised self-presentation standard and define the common fixed-self field set as `department -> username -> userId` for every current relevant aligned screen.
- `docs/design/search-field-definition-items.md`
  - Must extend self-presentation metadata so fixed-readonly self behavior can be expressed explicitly.
- `docs/design/forms-and-filters.md`
  - Must define the common visible locked self presentation rule for user/requester blocks.
- `docs/workflow/CONSISTENCY-STANDARDS.md`
  - Must record the new shared wording so future requirements and reviews do not revert to hide-on-self.
- `docs/workflow/HANDOFF-CHECKLIST.md`
  - Must remain aligned so Step 4 handoffs describe the new self-scope standard consistently.

#### Cursor tools
- `.cursor/skills/search-consistency-domain/SKILL.md`
  - Must be updated to the new visible-but-fixed self standard.
- `.cursor/skills/auth-permission-domain/SKILL.md`
  - Must stay aligned with backend-enforced self scope and visible locked fields.
- `.cursor/skills/api-permission-map/SKILL.md`
  - Must stay aligned with the required regression coverage for visible locked self fields.
- `.cursor/skills/activity-statistics-domain/SKILL.md`
  - Must stay aligned if statistics adopts the shared fixed-self presentation.
- `.cursor/skills/search-history-decrypt-domain/SKILL.md`
  - Must stay aligned if search-history adopts the shared fixed-self requester presentation.
- `.cursor/rules/search-filter-form-design.mdc`
  - Must stay aligned with the shared self-presentation standard for search/filter forms.

#### Step 4 backend actual change confirmation
- `backend/src/main/java/com/logmng/dto/response/LoginResponse.java`
  - Added the explicit auth-owned `selfContext` contract for `department`, `username`, and canonical `userId`.
- `backend/src/main/java/com/logmng/service/AuthService.java`
  - Populates `selfContext` from authenticated `app_user` data for both login and current-user responses.
- `backend/src/main/java/com/logmng/controller/AuthController.java`
  - Exposes `selfContext` through `/api/auth/check` alongside the existing authenticated payload fields.
- `backend/src/test/java/com/logmng/service/AuthServiceTest.java`
  - Added regression coverage for authoritative self-context mapping from login and current-user flows.
- `backend/src/test/java/com/logmng/controller/AuthControllerTest.java`
  - Added controller-level JSON contract coverage for `/api/auth/login`, `/api/auth/check`, and `/api/auth/me`.
- `.cursor/skills/auth-permission-domain/SKILL.md`
  - Updated to state that auth/current-user payloads are the authoritative locked self-context source.
- `.cursor/skills/api-permission-map/SKILL.md`
  - Updated regression guidance to include auth `selfContext` contract checks for self-scoped screens.
- `.cursor/skills/activity-statistics-domain/SKILL.md`
  - Updated self-scope wording from hidden filters to visible fixed self-context.
- `.cursor/skills/search-history-decrypt-domain/SKILL.md`
  - Updated requester self-scope guidance so visible requester values remain auth-owned while backend enforcement stays authoritative.
- Verified without code change in this Step 4 slice:
  - Existing self-scope backend enforcement remains in `UserActivityLogController` / `UserActivityLogService`, `ActivityStatisticsController`, and `SearchHistoryController` / `SearchHistoryService`.
  - Shared department filter options remain non-authoritative for locked self display values in the current `FilterOptionsController` / `FilterOptionsService` implementation.

#### DB
- None.

## 3. Test approach

### Test case list (required)

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|----|-------|------|------------------------------|-----------------|--------------------------------------------|
| TC-01 | Frontend | Regression | Open `activity-log` with effective `scope=self` after the app has loaded auth current-user data | The user block stays visible; Department, Username, and User ID are shown as locked self-context values sourced from the auth current-user contract; no `"All / 전체"` or editable widening control is available | Unit / manual / browser |
| TC-02 | Frontend | Regression | Open `statistics` with effective `scope=self` after the app has loaded auth current-user data | The user block stays visible; Department, Username, and User ID are shown as locked self-context values sourced from the auth current-user contract; aligned user-block width/size matches `activity-log` | Unit / manual / browser |
| TC-03 | Frontend | Regression | Open `search-history` with effective `scope=self` after the app has loaded auth current-user data | The requester block stays visible; Department, Username, and User ID are locked to the current authenticated user from the auth current-user contract and cannot widen requester scope | Unit / manual / browser |
| TC-04 | Frontend | Normal | Open `activity-log`, `statistics`, and `search-history` with effective `scope=team` or `scope=all` | The existing editable user/requester block remains available, and the self-only locked presentation is not incorrectly applied | Unit / manual / browser |
| TC-05 | Frontend | Consistency | Compare the self-scoped user/requester block on the aligned screens and any other current screens using the same shared pattern | Applicable user-block fields keep the same order, width, and visual size across all current aligned screens even when read-only | Manual / browser |
| TC-06 | Backend | Regression | Call the auth current-user responses used by the frontend (`/api/auth/check` authenticated payload and `/api/auth/me`) as a self-scoped user | The response exposes explicit self-context fields for locked `department -> username -> userId`; `userId` equals the authenticated `app_user.username`; if no separate display-name field exists, the locked `username` display equals the authenticated username string | Unit / integration |
| TC-07 | Backend | Regression | Send widened `department` input, `"all"` representations, or empty variants to the shared department-options path and to search APIs while effective scope is `self` | `filter-options` does not become the authoritative self-display source, and the self result remains fixed to the authenticated user's own context without exposing third-party department candidates | Unit / integration |
| TC-08 | Backend | Regression | Send tampered `username`, `userId`, and any allowed identity combination to `activity-log` while effective scope is `self` | Results remain current-user-only and client values do not widen scope | Unit / integration |
| TC-09 | Backend | Regression | Send tampered self-scope identity filters, including `username` and `userId`, to statistics APIs while effective scope is `self` | Results remain current-user-only and client values do not widen scope | Unit / integration |
| TC-10 | Backend | Regression | Send tampered requester filters, including `username` and `userId`, to `search-history` while effective scope is `self` | Results remain current-requester-only and requester filters do not widen scope | Unit / integration |
| TC-11 | Contract / Docs | Documentation | Review contract, API definition, spec, design docs, workflow docs, and listed Cursor tools after implementation | All sources describe `scope=self` as visible fixed self-context sourced from auth current-user, and they use the same `userId` / `username` meaning for all current relevant screens using the shared pattern | Manual review |
| TC-12 | Integration | Regression | Log in as a self-scoped user and perform search + reset on each current aligned screen using the shared pattern | Locked self-context values remain visible after reset, remain aligned with the auth current-user payload, and the result set stays fixed to the authenticated user | Integration / browser |

### Test scenarios

#### Scenario 1: Visible fixed self-context replaces hidden self block
1. Prepare a non-admin user whose effective screen scope is `self` for each current relevant aligned screen.
2. Confirm the auth current-user responses expose the finalized self-context payload for the authenticated user.
3. Open `activity-log`, `statistics`, and `search-history`.
4. Verify that the user/requester block is still visible and that Department/Username/User ID use the locked self-context presentation defined by the final standard.

#### Scenario 2: Visible self fields do not weaken backend enforcement
1. Prepare authenticated requests for self-scoped users on `activity-log`, statistics, and `search-history`.
2. Submit tampered identity values (`department`, `username`, `userId`, and any screen-specific identity fields) through the client or direct API calls.
3. Verify that the backend still returns only the current authenticated user's allowed rows.

#### Scenario 3: Shared sizing and UX remain aligned
1. Compare the aligned user/requester block on all current relevant screens using the shared pattern under self scope.
2. Verify block order, width-by-role, and visible locked presentation against the shared design docs.
3. Verify that the self presentation does not collapse the block or diverge screen-to-screen.

### Test data
- One authenticated non-admin user with effective `scope=self` on each confirmed aligned screen.
- One authenticated non-admin user with effective `scope=team` for regression comparison.
- One authenticated admin or `scope=all` user for regression comparison.
- Department and user data that make the current user's own department and own user ID clearly distinguishable from other values.
- User data that also makes the current authenticated user's own username clearly distinguishable from other usernames.

### Test environment
- Frontend: `http://localhost:3001`
- Backend: `http://localhost:9200`
- Database: project default database with seeded user, permission-group, and screen-scope data

### 3.5 Browser automation verification (optional — for frontend-heavy requirements)
- **Applicable TCs**: TC-01, TC-02, TC-03, TC-04, TC-05, TC-12
- **Procedure per TC**: login as the relevant user, open each confirmed aligned screen, inspect the visible user/requester block, verify locked-field behavior, run search and reset, and confirm both UI state and returned results stay within the expected self/team/all rules.

## 4. Checklist

### Frontend verification
- [ ] Shared self-presentation mode implemented for the aligned user/requester block
- [ ] Department, Username, and User ID stay visible and locked for all current relevant self-scoped screens using the shared pattern
- [ ] Team/all editable behavior remains unchanged
- [ ] User-block width/size stays aligned across all current relevant screens using the shared pattern

### Backend verification
- [ ] Auth current-user responses expose the finalized self-context contract for locked Department / Username / User ID
- [ ] Self-scope enforcement remains current-user-only for every current affected endpoint
- [ ] Shared department-options behavior is aligned with the revised self standard
- [ ] Tampered client identity values cannot widen self scope

### Integration
- [ ] Search and reset flows verified on each current aligned screen using the shared pattern
- [ ] Browser/API verification confirms visible fixed self behavior and preserved enforcement

### Documentation
- [ ] Requirement doc completed
- [ ] Contract/spec/design/Cursor documents updated consistently

## 5. Test results

### Test run date
- 2026-03-13 - Step 5 QA evidence review and result recording

### Test results

#### Backend
Pass (automated tests + restart/health evidence)
- `cd backend && mvn test` -> `Tests run: 87, Failures: 0, Errors: 0, Skipped: 0`
- `./scripts/dev-services.sh backend restart` completed successfully.
- `curl -s http://localhost:9200/api/health` returned `status=OK`.
- Reviewed automated evidence for the auth-owned self-context contract: `AuthControllerTest` and `AuthServiceTest` verify `/api/auth/login`, `/api/auth/check`, and `/api/auth/me` expose `selfContext.department`, `selfContext.username`, and canonical `selfContext.userId`. This supports TC-06.
- Reviewed automated evidence for self-scope enforcement remaining authoritative: `UserActivityLogControllerTest`, `UserActivityLogServiceTest`, `ActivityStatisticsControllerTest`, `SearchHistoryControllerTest`, and existing `SearchHistoryServiceTest` verify tampered requester/user filters do not widen self/team scope. This supports TC-07, TC-08, TC-09, and TC-10.
- No failing backend evidence was found in this QA slice.

#### Frontend
Pass (targeted unit tests + build/restart/reachability evidence)
- `npm test -- --watchAll=false --runInBand src/components/common/UserContextFilterBlock.test.js src/components/ActivityStatistics.test.js src/components/SearchHistory/SearchHistoryList.test.js src/components/UserActivityLog/UserActivityLogList.test.js` -> `4 suites / 14 tests passed`
- `npm run build` passed.
- `./scripts/dev-services.sh frontend restart` completed successfully.
- `curl http://localhost:3001` returned HTTP `200`.
- Reviewed automated evidence from `UserContextFilterBlock.test.js`, `ActivityStatistics.test.js`, `SearchHistoryList.test.js`, and `UserActivityLogList.test.js`: visible locked self-context rendering, removal of editable widening controls in `scope=self`, and retention of editable controls in non-self flows are covered. This gives automated evidence for TC-01, TC-02, TC-03, and part of TC-04.
- `UserActivityLogList.test.js` and `SearchHistoryList.test.js` also verify self-scope request sanitization after scope changes, which supports the request-enforcement aspect of TC-12.
- Frontend implementation handoff also reported no linter errors in the changed frontend files.

#### Manual / browser / documentation verification status
Partial - remaining gaps are `not run`, not `failed`
- No browser automation evidence was provided for TC-01, TC-02, TC-03, TC-04, TC-05, or TC-12, so those browser/manual checks are recorded as `not run`.
- Known environment limitation: the currently available `user2` account has `activity-log=team`, not `self`, so the live browser verification for the `activity-log` self-scope scenario cannot be completed with that account alone. A dedicated self-scoped account or adjusted seed/scope data is still required.
- Because browser/manual verification was not completed, the following remain unverified in a live authenticated UI session: cross-screen width/size consistency (TC-05), reset persistence on each aligned screen (TC-12), and full end-to-end visual confirmation of self/team/all behavior in the browser (TC-01, TC-02, TC-03, TC-04).
- Full manual wording review for TC-11 was not completed in this QA slice. The worktree shows related contract/spec/design/Cursor files changed, but this item remains partial until those documents are checked line-by-line for final wording consistency.

## 6. Error remedy result (cause and action) — for error/bug fix requirements only

Not applicable. This document defines a new shared standard change request rather than recording a completed bug-fix remedy.

## 7. Final version (Korean) — add after all verification is complete

### Final Korean summary
- **Requirement description**: To be added after verification
- **Expected outcome**: To be added after verification
- **Verification result**: To be added after verification

---

**Author**: Requirements subagent
**Date**: 2026-03-13
**Status**: In progress
