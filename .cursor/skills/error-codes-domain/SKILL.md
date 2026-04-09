---
name: error-codes-domain
description: Common API error-code meaning and where error codes are documented.
---

# Error codes domain

Use this skill for questions about API error codes, 4xx/5xx meaning, and where new codes must be documented.

## Core points

- Keep the authoritative error-code list in `docs/api-definition.md`.
- **`USER_EMPLOYEE_NUMBER_DUPLICATED`** (409): another **active** `app_user` already has the same trimmed `employee_number`. Returned by User Management V2 direct user create and by **external provisioning** (`POST /api/provisioning/users/from-external-employee`) when the conflict is on employee number (not external identity key).
- Keep new error-code registration aligned with `docs/workflow/CONSISTENCY-STANDARDS.md`.
- Pay special attention to permission-related and decryption-related codes when they appear in the requirement.

## References

- `docs/api-definition.md`
- `docs/workflow/CONSISTENCY-STANDARDS.md`
