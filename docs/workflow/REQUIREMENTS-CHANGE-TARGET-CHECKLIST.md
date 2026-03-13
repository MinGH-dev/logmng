# Requirements authoring — change target checklist

Use this checklist before finalizing requirement-doc §2. Its goal is to ensure that the requirement covers every affected scope and every required touchpoint.

## 1. Scope verification (mandatory)

For each scope below, answer: **Does this requirement affect it?** If yes, ensure §2 contains a subsection and the planned change file list contains the relevant files.

| Scope | Question | If yes, ensure §2 and the change file list include |
|-------|----------|----------------------------------------------------|
| Backend | Does the requirement change API behavior, services, controllers, or backend config? | Controllers, services, DTOs, config, tests |
| Frontend | Does the requirement change UI, API calls, state, or shared UI primitives? | Configuration/setup UI, user-facing screens, shared components, shared CSS |
| DB | Does the requirement change schema, migrations, or init data? | Schema files, migrations, setup/apply scripts |
| Contract / Spec | Does the requirement change request/response shape, permission mapping, or documented API behavior? | `docs/contract.md`, `docs/api-definition.md`, `specs/*.spec.yaml` |
| Cursor tools | Does the requirement change the domain model that tools rely on? | Relevant `.cursor/skills/**`, related rules, and spec references |

## 2. Frontend sub-checks

When Frontend is affected, explicitly verify:

- **Configuration UI**: where the feature is configured or enabled
- **View screen**: where the feature is displayed or used
- **Shared primitive ownership**: whether the issue belongs to a shared component, shared stylesheet, or shared layout contract
- **Consumer verification**: which consumer screens must be rechecked after a shared fix

Do not reduce a shared UI issue to a single screen unless shared ownership has been ruled out.

## 3. Domain patterns

Apply the matching pattern checklist when the requirement fits one of the cases below.

### 3.1 Scope-supporting screen

If the requirement adds or changes a screen that supports `self | team | all` behavior:

- Backend: scope resolution, controller/service filtering, and any supporting constants
- Frontend configuration: scope-selection UI and save/normalize logic
- Frontend view: screen behavior and hints derived from `screenScopes`
- Contract/spec: scope mapping and affected endpoints
- Cursor tools: auth/permission-related skills and specs

### 3.2 Permission or screen-access change

If the requirement changes screen access, allowed screens, or permission-group behavior:

- Backend access checks and auth response shape
- Frontend menu/sidebar and permission configuration UI
- Contract/spec permission mapping
- Auth/permission-related skills

### 3.3 API or error-code change

If the requirement adds or changes an API or error code:

- Contract/spec docs
- Backend controller/service/DTO and error constants
- Frontend API client and error handling
- Error-code-related skills

### 3.4 Search/filter UI consistency

If the requirement aligns search/filter UI across screens, ensure §2 and the planned change file list cover:

- group-title placement
- block structure and same-row layout
- form-per-mode behavior when a mode switch exists
- form/panel width
- user-block field width (department, user name, user ID)
- spacing
- accessibility semantics
- explicit design-doc references
- frontend implementation note
- CSS standard / exception handling

## 4. Search/filter verification table

When pattern 3.4 applies, confirm the following:

| Check | Requirement doc expectation |
|-------|-----------------------------|
| User-block field size | §1 explicitly says department, user name, and user ID use the same width/size across aligned screens |
| Form/panel width | §1 or §2 explicitly says aligned screens use the same panel/container width |
| §2 implementation note | §2 tells Frontend to read and apply the relevant design docs rather than infer values |
| Change file list | Shared CSS, shared components, and affected screens are all listed |
| §3 test cases | At least one TC compares user-block field size across aligned screens |

## 5. Cursor tool update targets

When the requirement changes a domain model that tools rely on, add a subsection in §2 named **Cursor tool update targets** and list:

- relevant `.cursor/skills/**` files
- related rules or commands if the workflow changes
- specs that need updating

## 6. Completion rule

If any required touchpoint is missing, add it to §2 and to the planned change file list before finalizing the requirement doc.

## References

- `docs/workflow/REQUIREMENTS-AUTHORING-WORKFLOW.md`
- `docs/workflow/HANDOFF-CHECKLIST.md`
- `docs/design/forms-and-filters.md`
- `docs/design/search-fields-by-screen.md`
- `docs/design/search-field-definition-items.md`
- `docs/design/css-standard-and-exceptions.md`
