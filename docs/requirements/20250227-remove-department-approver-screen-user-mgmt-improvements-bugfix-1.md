# 20250227-remove-department-approver-screen-user-mgmt-improvements-bugfix-1 — POST /api/users/approvers returns 500 instead of 404

**Parent requirement ID**: `20250227-remove-department-approver-screen-user-mgmt-improvements`  
**Bugfix sequence**: 1

## 1. Discovery

- **When**: During verification (API integration test TC-06)
- **What failed**: `POST /api/users/approvers` returns HTTP 500 with `INTERNAL_SERVER_ERROR`; requirement expects 404 or 410 (endpoint removed).

## 2. Error scope

- **Failure scope**: backend
- **Layer**: backend
- **Symptom**: Removed endpoint returns 500 instead of 404/410
- **Impact**: API consistency; clients expecting 404 for removed endpoints may misinterpret 500

## 3. Cause (estimated)

- `UserController` has no `@PostMapping("/approvers")` — endpoint was removed per requirement.
- When Spring receives `POST /api/users/approvers` with no matching handler, it may route to a catch-all or global exception handler that returns 500.
- Spring's default "no handler found" behavior may return 404, but some interceptor or filter could be catching the request first and returning 500.

## 4. Action

- Add explicit handling for removed endpoints: either a `@RequestMapping` that returns 404/410 for `POST /api/users/approvers` and `DELETE /api/users/approvers`, or ensure Spring's default 404 is returned when no handler matches.
- Verify `GET /api/departments/{code}/approvers` returns 404 (already passes).

## 5. Verification

- **Resolved**: Added explicit handlers in UserController returning 410 Gone for POST and DELETE /api/users/approvers.
- `curl -X POST http://localhost:9200/api/users/approvers` (with valid session) → 410.
- `curl -X DELETE http://localhost:9200/api/users/approvers` (with valid session) → 410.
