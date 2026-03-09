# Documentation–Code–Cursor Tools Sync

This doc states how documentation relates to the **running system** and how to keep docs, code, and Cursor tools (rules, skills, agents) aligned. **Language**: English per `docs/workflow/DOCUMENT-LANGUAGE-POLICY.md`.

---

## 1. Principle: Documentation follows the code

For this project the **system is in production and stable**. Therefore:

- **Code is the source of truth** for current behavior (API paths, request/response shape, error codes, constants, validation, config).
- **Documentation** describes the current implementation. When code and doc disagree, **update the doc** to match the code (unless a deliberate change is being implemented).
- **Cursor tools** (rules, skills, agents) reference **docs** as the single place to read API contract, error codes, screen IDs, etc. So keeping docs aligned with code keeps Cursor behavior correct.

Summary:

| Layer        | Role |
|-------------|------|
| **Code**    | Source of truth for behavior. |
| **Docs**    | Describe code; updated when code or intent changes. |
| **Cursor**  | Reads docs; no need to re-scan code if docs are accurate. |

---

## 2. One-time alignment (after drift)

Use this when bringing docs up to date with the current codebase:

1. **API paths and usage**
   - Extract actual paths from backend controllers (e.g. `@RequestMapping`, `@GetMapping`, `@PostMapping`).
   - Extract frontend API base paths and endpoints from `frontend/src/services/*.js` and components that call APIs.
   - Update `docs/api-definition.md` so path tables match. Remove or fix any path in docs that no backend implements (e.g. legacy `/api/logs/...` if only `/api/logs/db-refactored/...` exists).

2. **Constants and enums**
   - **Screen IDs / functions**: Compare `backend/.../constants/ScreenConstants.java` with `frontend/src/constants/menuTree.js`, `screenFunctionDescriptions.js`, `ScreenSelectionTree.js`. Document the canonical list in `docs/contract.md` and/or `specs/permission-group-hierarchy.spec.yaml` §4.1; note “implemented in BE/FE as …”.
   - **Log types**: Document actual values (e.g. `pb_feplog`, `java_fw_imglog`) in api-definition or a small “Constants” section; reference from both BE and FE.
   - **Error codes**: Grep backend for `CustomException` and `code:` strings; grep frontend for `errorMessage.js` and any `code === '...'`. Ensure `docs/api-definition.md` §11 lists all codes and that FE message map includes every code the backend can return (e.g. `FUNCTION_NOT_ALLOWED`).

3. **Validation and config**
   - **Pagination**: Document default/min/max page size per API (or globally) in `docs/contract.md` or api-definition; align with backend DTOs and frontend defaults (e.g. 20 default, 1–100 clamp).
   - **Validation rules**: Where BE uses `@Valid`, `@Size`, `@Pattern`, document in api-definition or spec so FE can mirror.

4. **Cursor references**
   - Ensure `.cursor/rules`, `.cursor/skills`, `.cursor/agents` that mention “contract”, “api-definition”, “specs” point to the **same** doc paths (e.g. `docs/contract.md`, `docs/api-definition.md`, `specs/*.spec.yaml`). No duplicate “source of truth” locations.

After this one-time pass, **ongoing** sync is maintained by the process below.

---

## 3. Keeping docs and code aligned (ongoing)

### 3.1 Same-PR rule

When you change **code** in a way that affects any of the following, **update the corresponding doc in the same PR** (or same commit):

- **API**: New or changed path, method, request/response shape, or query params  
  → Update `docs/api-definition.md` (and `specs/*.spec.yaml` if that feature has a spec).
- **Error codes**: New or removed `code` in backend or FE handling  
  → Update `docs/api-definition.md` §11 (and FE `errorMessage.js` if applicable).
- **Constants**: Screen IDs, log types, permission-related enums  
  → Update `docs/contract.md` and/or `specs/permission-group-hierarchy.spec.yaml` (and the other if they duplicate).
- **Config**: Default/min/max page size, timeouts, limits  
  → Update `docs/contract.md` or api-definition.

So: **code change that affects contract or behavior → doc update in the same change.** That way Cursor tools (which read docs) stay correct without extra steps.

### 3.2 Checklist in workflow

- In **handoff** (e.g. Backend/Frontend handoff):  
  “If your change adds or changes API paths, error codes, or shared constants, update `docs/api-definition.md` and/or `docs/contract.md` (and specs) in this same work.”
- In **review**:  
  “If API/constants/errors were changed, confirm that docs (api-definition, contract, specs) were updated in the same PR.”
- In **commit**:  
  Already covered by “include docs/ and specs/ changes in the commit” (see `.cursor/commands/commit-on-complete.md`).

### 3.3 Single reference for Cursor

- **Rules/skills/agents** should reference:
  - API paths, request/response, error codes → `docs/api-definition.md` (and §11 for error codes).
  - Environment, ports, DB, screen-based access → `docs/contract.md`.
  - Screen IDs, scope, permission model → `specs/permission-group-hierarchy.spec.yaml` and contract.
- Avoid creating **new** “source of truth” files for the same content. When adding a new skill or rule that needs API or contract info, point it to these same docs so one doc update keeps all Cursor behavior in sync.

### 3.4 Optional lightweight checks

- **Manual**: Before release or when touching API/contract, skim api-definition and contract against the main controllers and one FE service file.
- **Script (optional)**: A small script could grep backend for `@RequestMapping`/`@GetMapping`/`@PostMapping` and list paths, then compare to a section in api-definition or a generated “current paths” snippet. No need to block CI; use as a helper.

---

## 4. Summary

| Question | Answer |
|----------|--------|
| What is source of truth for behavior? | **Code.** |
| What should docs do? | **Describe the current code.** Fix doc when it and code disagree (unless you are implementing a planned change). |
| How to avoid doc–code drift? | **Same-PR rule**: when you change API/constants/errors/config, update the relevant doc in the same change. |
| How do Cursor tools stay correct? | They read **docs** only; keeping docs aligned with code keeps Cursor aligned. Use a **single** set of references (contract, api-definition, specs). |
| One-time cleanup? | Follow §2 to align api-definition, contract, and specs with current backend and frontend. |

---

**See also**: `docs/workflow/CURSOR-AND-TOOLS-INTEGRATION.md` (where rules/commands/skills point to which docs), `docs/contract.md`, `docs/api-definition.md`, `.cursor/rules/contract-first.mdc`.
