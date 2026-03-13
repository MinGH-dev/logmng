# Export: Design Reference Pack

This directory contains **design-reference documents** intended for use by external tools or other AI models when authoring **UI/UX design standards**. The documents are derived from the current codebase and specs so that:

- **Search/filter field sizing** can be aligned with **DB column sizes** and **API request/response shapes**.
- **Screen-to-API** and **API-to-DB** mappings are explicit for consistent behavior and layout rules across screens.
- **User actions per screen** are documented so that design standards can cover all interactions (read, search, export, approve, etc.).

## Documents

| Document | Purpose |
|----------|---------|
| [db-definition.md](db-definition.md) | DB schema summary: tables, columns, types, and sizes. Use when defining input maxLength, select option length, or display width from data source. |
| [api-db-mapping.md](api-db-mapping.md) | API endpoints mapped to DB tables/entities. Use when designing request/response field sizes and validation rules. |
| [screen-api-mapping.md](screen-api-mapping.md) | Each screen (view) and the APIs it uses. Use when designing per-screen layouts and ensuring field–API consistency. |
| [screen-user-actions.md](screen-user-actions.md) | User actions per screen (search, export, approve, re-request, etc.). Use when designing buttons, permissions, and interaction flows. |
| [permission-by-screen.md](permission-by-screen.md) | **화면별 권한 부여** — 권한관리 화면에서 화면마다 설정 가능한 항목(scope, read/write/approve/decrypt)과 UI 동작, API 형식. 다른 팀/도구에 권한관리 규칙 전달 시 참고. |

## How to use (for design-standard authors)

1. **Field width / maxLength**: Prefer values that match DB column sizes and API constraints in `db-definition.md` and `api-db-mapping.md`. When a field is backed by a VARCHAR(n), consider n when setting maxLength and min/max width.
2. **Which APIs a screen uses**: See `screen-api-mapping.md` so that search/filter fields and result columns align with the request/response of those APIs.
3. **What users can do on each screen**: See `screen-user-actions.md` so that design standards cover all actions (including conditional ones like approve when scope/role allows).
4. **Scope and permissions**: Screens with scope (self/team/all) or function (read/write/approve/decrypt) are noted in both screen documents; design standards should account for hidden filters when scope=self and disabled actions when function is not granted.
5. **Permission management screen (권한관리)**: The options shown per screen (scope dropdown, approve radio, write/decrypt toggles) differ by screen. Use `permission-by-screen.md` to avoid confusion: it lists per-screen configurable items, the “조회 범위 vs 승인 범위” distinction, and the `allowedScreens` API shape.

## Source of truth

- **DB**: `backend/src/main/resources/db/schema.sql`, `schema_imagelog.sql`, `schema_user_activity_log.sql` (and migrations).
- **API**: `docs/api-definition.md`, `docs/contract.md`, `specs/permission-group-hierarchy.spec.yaml`.
- **Screens**: `frontend/src/App.js`, `frontend/src/constants/menuTree.js`, and component-level API calls in `frontend/src/`.
- **Design refs**: `docs/design/search-fields-by-screen.md`, `docs/design/search-field-definition-items.md`, `docs/design/forms-and-filters.md`.

These export documents are **snapshots** for design reference. When schema, API, or screens change, regenerate or update the export files so design standards stay aligned.
