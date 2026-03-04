# 20250303-screen-function-checkbox-selection-bugfix-1 — read/write/approve not stored; INVALID_SCREEN_FUNCTION validation not enforced

**Parent requirement ID**: `20250303-screen-function-checkbox-selection`  
**Bugfix sequence**: 1

## 1. Discovery

- **When**: During Step 5 QA verification (integration tests TC-01, TC-05, TC-06, TC-07)
- **What failed**:
  - TC-01: Admin creates group with user-management write=false → DB stores read/write/approve as NULL; user2 screenFunctions shows write: true (derivation) instead of write: false
  - TC-05: POST with main write=true → 201 accepted instead of 400 INVALID_SCREEN_FUNCTION
  - TC-06: POST with user-management approve=true → 201 accepted instead of 400 INVALID_SCREEN_FUNCTION
  - TC-07: User with user-management write=false (from TC01 group) calls POST permission-groups/{id}/users → 200 (expected 403 FUNCTION_NOT_ALLOWED). Root cause: write=false not stored, so backend derives write=true

## 2. Error scope

- **Failure scope**: backend
- **Layer**: backend
- **Symptom**: read/write/approve from allowedScreens request body are not persisted to permission_group_screen; invalid combinations (main+write, user-management+approve) are not rejected with 400 INVALID_SCREEN_FUNCTION
- **Impact**: Explicit checkbox selection (read/write/approve) does not work; validation per spec §1.1.1 not enforced

## 3. Cause (estimated)

- **Cause 1**: AllowedScreenListDeserializer or request parsing path may not correctly populate read/write/approve on AllowedScreenItem when deserializing from JSON
- **Cause 2**: validateScreenFunctions may not be invoked, or items may be normalized/stripped before validation
- **Cause 3**: saveAllowedScreens may receive items with null read/write/approve (getRead/getWrite/getApprove return null)

## 4. Action

- **Investigate**: Trace request flow from PermissionGroupController.create → PermissionGroupCreateRequest → AllowedScreenListDeserializer → validateAllowedScreens → saveAllowedScreens. Add logging if needed to confirm where read/write/approve are lost.
- **Fix**: Ensure (1) AllowedScreenListDeserializer correctly sets read, write, approve from JSON; (2) validateScreenFunctions rejects main+write/approve and unsupported screen+write/approve with 400 INVALID_SCREEN_FUNCTION; (3) saveAllowedScreens persists non-null values to DB.
- **Files changed**: `backend/src/main/java/com/logmng/dto/request/AllowedScreenListDeserializer.java` — added `readBooleanOrNull()` helper to correctly parse read/write/approve from JSON (preserve explicit false and null; previously `asBoolean()` on null returned false). PermissionGroupService (validateAllowedScreens, validateScreenFunctions, saveAllowedScreens) unchanged — logic was correct; root cause was deserializer not populating values.

## 5. Verification

- Re-run TC-01, TC-05, TC-06, TC-07 after fix. All must pass.
- TC-01: DB must have read=true, write=false for user-management; user screenFunctions['user-management'].write must be false
- TC-05/TC-06: Must return 400 with code INVALID_SCREEN_FUNCTION
- TC-07: Must return 403 with code FUNCTION_NOT_ALLOWED

### 5.1 Re-verification results (2025-03-03)

| ID | Result | Note |
|----|--------|------|
| TC-01 | Pass | user_tc01 (group 50 only) → screenFunctions['user-management'] = { read: true, write: false }; DB read=true, write=false |
| TC-05 | Pass | POST main write=true → 400 INVALID_SCREEN_FUNCTION |
| TC-06 | Pass | POST user-management approve=true → 400 INVALID_SCREEN_FUNCTION |
| TC-07 | Pass | user_tc01 POST permission-groups/50/users → 403 FUNCTION_NOT_ALLOWED |

**Note**: Full rebuild (`mvn package`) and backend restart required for fix to take effect (JAR was stale on first re-run).

## 6. Error remedy result

- **Root cause**: AllowedScreenListDeserializer used `asBoolean()` on null/missing nodes, returning false instead of null. `readBooleanOrNull()` helper now correctly preserves explicit false and null.
- **Fix**: `AllowedScreenListDeserializer.java` — parse read/write/approve with `readBooleanOrNull()`; preserve explicit false for storage and validation.
- **Verification**: TC-01, TC-05, TC-06, TC-07 all pass. Full §3 integration suite (TC-01–TC-08) pass.
