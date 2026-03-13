# Consistency Standards

**Owner**: Consistency subagent. This document defines project-wide standards for naming, structure, logging, and common UI conventions. The **Review** subagent applies this document during change review.

## 1. API and error responses

- Follow `docs/api-definition.md` and `docs/contract.md` for shared response shape.
- Success responses use `success: true` with `data`.
- Failure responses use `success: false` with `error` and `code`.
- Register new error codes in `docs/api-definition.md`.
- Use uppercase snake case for error codes, for example `DECRYPTION_NOT_APPROVED`.
- Use HTTP status codes according to meaning: 400 client input error, 403 permission denial, 404 missing resource, 500 server error.

## 2. Naming

- API paths: lowercase, kebab case, or resource hierarchy, for example `/api/search-history/{id}/approve`.
- Database tables and columns: snake case.
- Frontend components: PascalCase. Supporting symbols such as local state and API call helpers use camelCase.

## 3. Logging

- Use `ERROR` for failures and exceptions.
- Use `WARN` for recoverable problems.
- Use `INFO` for major flow milestones.
- Use `DEBUG` for detailed trace output.
- Do not log passwords or direct PII values. See `docs/security-guide.md`.

## 4. Files and directories

- Requirement documents: `docs/requirements/yyyyMMdd-name.md`
- Backend packages remain under `backend/src/main/java/com/logmng/`
- DB resources remain under `backend/src/main/resources/db/`
- List or table-style frontend screens should use one unified grid pattern. See `docs/design/grid-and-table.md` and §6 below.

## 5. Ownership

- Only the Consistency subagent updates this standards document.
- Review reads and applies this document; it does not redefine the standards.

## 6. Data tables (unified grid)

- Use a single shared pattern for list and table UIs such as logs, activity logs, and search history.
- Prefer a shared component or a wrapper that follows the same contract.
- Common class structure should remain consistent across screens.
- Sorting is required for all grids and tables. Keep the same interaction model, indicators, and `aria-sort` behavior.
- Default rows per page is 20. If page-size controls exist, keep the same interaction pattern across screens.
- Shared footer metadata belongs to an always-visible footer region, even for one-page datasets.
- If a defect appears first on one screen but may belong to a shared table primitive, investigate shared ownership first.

## 7. Permission-management labels and scope

- Use the label **View** instead of "view only" where the UI needs a short action name.
- Scope labels should be expressed consistently and mapped to API values `self`, `team`, and `all`.
- Scope configuration changes the **list/view range** only.
- Approval range remains fixed to the department-based approval rule where that business rule applies.

## 8. Self-scope user/requester blocks

- For applicable user-context or requester-context search/filter blocks, do not use old wording such as "hide-on-self" or "hidden on self".
- The standard wording is: **visible, fixed to current user, not editable**.
- Keep the shared field order as `department -> username -> userId`.
- The authoritative source for locked self values is the authenticated current-user payload or equivalent auth/current-user context, not arbitrary client-entered filter values.
- Visible locked self fields are presentation-only. Backend self-scope enforcement remains authoritative and must ignore or normalize client-provided identity values that would widen scope.
- If a screen intentionally hides the user/requester block in `scope=self`, that exception must be explicitly stated by requirement and aligned in contract, design docs, and handoff wording. Do not infer the exception from older guidance.

## References

- Contract: `docs/contract.md`
- API definition: `docs/api-definition.md`
- Search/filter requirement checklist: `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md`
- Grid and table standard: `docs/design/grid-and-table.md`
