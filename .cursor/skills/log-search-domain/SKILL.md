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
- **Imagelog row identity (req 20260320)**: `java_fw_imglog` rows are distinguished by **`(guid, status)`**. Decryption-allowed store, approval snapshot, `POST /api/logs/decrypt/java_fw_imglog`, and search-history detail `decryptionRequestedRows` all use this composite; `GET /api/decrypt/allowed` returns `allowedRows: [{ guid, status }]` (plus legacy `guids`).
- **Search vs decrypt (req 20260413 + highlight flags)**: For `java_fw_imglog`, **`POST /api/logs/db-refactored/search`** and **`advanced-search`** return row maps **without** `decrypted_*` keys or undocumented `_`-prefixed keys. **Optional display metadata** (no plaintext): `hasEncryptedMatchDatastring` / `hasEncryptedMatchHeaderstring` when a filter matched **inside** quoted bracket JSON ciphertext (decrypt-for-match); used for encrypted-region highlight (req 20260224, 20260206). The server decrypts in memory for **matching/filtering** only; **plaintext for display** uses **`POST /api/logs/decrypt/java_fw_imglog`** (and approval rules). See `docs/contract.md` — Java FW Image Log search match vs plaintext display.
- **PB FEP keyword search (`pb_feplog`, req 20260415)**: On **`POST /api/logs/db-refactored/search`** (default `logType=pb_feplog`) and **`POST /api/logs/db-refactored/pb-fep-log-search`**, **keywords** drive **row inclusion**: **plaintext** match on wire/JDBC strings (`request_data`, `response_data`, **`bmsg` / `error_message`**) plus **decrypt-for-match** in memory on PB FEP ciphertext in those surfaces (`CryptoUtil.decryptLogPayload`, `LogPayloadCryptoVariant.PB_FEP`) for **predicate only** — same search-vs-display split as imagelog. **`decryptData` does not gate** keyword matching. List/search responses **do not** expose **`decrypted_*`** (or other decrypted plaintext row keys) unless Contract explicitly allows; align with `docs/requirements/20260415-encrypted-field-search-no-client-plaintext.md`. Full behavior: `docs/requirements/20260415-pb-fep-keyword-decrypt-and-plaintext-search.md`.

## References

- `docs/api-definition.md`
- `docs/contract.md`
- `docs/requirements/20260415-pb-fep-keyword-decrypt-and-plaintext-search.md`
