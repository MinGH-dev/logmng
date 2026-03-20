---
name: log-search-domain
description: Log search types, request models, imagelog behavior, and related API concepts.
---

# Log search domain

Use this skill for questions about log search, log type, `pb_feplog`, `java_fw_imglog`, imagelog behavior, and DB-backed log-search APIs.

## Core points

- Keep log-search request shape aligned with the contract and API definition.
- Distinguish search behavior by log type where the requirement depends on it.
- Track related advanced-search and imagelog behavior through the documented API models.
- **Data sources (req 20260320)**: PB FEP log (`pb_feplog`) uses the **primary** JDBC pool (DB A, schemas `app.db.schema.sys` / `app.db.schema.pb` via `search_path`). Java FW Image Log (`java_fw_imglog`, table `imagelog`) uses the **ImageLog** pool (`app.datasource.imagelog.*`); if `app.datasource.imagelog.url` is empty, the app reuses the primary pool (single-DB dev). See `docs/contract.md` § DB multi-datasource.

## References

- `docs/api-definition.md`
- `docs/contract.md`
