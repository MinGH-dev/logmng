---
name: search-history-decrypt-domain
description: Search history, decryption approval, approver rules, and approval-state error behavior.
---

# Search history / decryption domain

Use this skill for questions about search history, decryption approval, pending approvals, approver behavior, `DECRYPTION_NOT_APPROVED`, and `ROW_NOT_IN_APPROVED_SNAPSHOT`.

## Core points

- Decryption requires both the appropriate permission and an approved search-history context when the flow requires approval.
- `DECRYPTION_NOT_APPROVED` means the request is not backed by a valid approved search-history record.
- `ROW_NOT_IN_APPROVED_SNAPSHOT` means the row was not part of the approved snapshot.
- Approval capability and pending-approval visibility must follow the approver and scope rules documented in the project contract and related requirement docs.
- **Approval path uses numeric app_user.id** (req 20260316): Permission checks and storage use **Long** (app_user.id). `decrypt_approver` is queried by `app_user_id`; `search_history` stores approver in `approved_by_user_id`. Display (e.g. `approvedBy` in list/detail) may still show username resolved from `approved_by_user_id` or fallback `approved_by`.
- **Decrypt execution path (req 20260317, 20260320)**: POST /api/logs/decrypt uses **numeric app_user.id**; **java_fw_imglog** requires body **`status`** and checks **decryption-allowed store** for **(guid, status)**. Snapshot APIs use `isRowInApprovedSnapshot(searchHistoryId, logType, rowId, rowStatus)` for java_fw_imglog. Ownership and approval checks use Long/BIGINT; username is not used for permission or validation on decrypt execution.
- **Encrypted rows only in snapshot and allowed (req 20260318, 20260320)**: The approval snapshot (`search_history_approved_row`: **row_id + row_status**) and decryption-allowed (`user_decryption_allowed`: **guid + row_status**) contain **only encrypted rows**, per log-type “has encrypted data” rules. See `docs/requirements/20260318-decryption-approval-guids-encrypted-only.md`, `docs/requirements/20260320-imagelog-guid-status-composite-key.md`.
- Search-history requester filters remain non-authoritative under `scope=self`; the visible locked requester values come from auth/current-user `selfContext`, while backend requester enforcement stays authoritative.
- Search history **list** response rows include requester fields for the grid: `requesterDepartmentName` (when available from department table), `requesterDepartmentCode`, `requesterDisplayName`, `requesterUsername`. The grid shows **three columns** for requester, in order: **부서** (department), **사용자ID** (username), **사용자명** (display name).
- **Search screen decrypt UI (req 20260317-search-decrypt-permission-ui)**: On the main (검색하기) screen, the decrypt approval request button and per-row decrypt button are gated by `screenFunctions.main.decrypt` (or `isSystemAdmin`). When the user lacks decrypt permission, the UI disables or hides these actions and shows "복호화 권한이 없습니다." Request reason for approval is collected in a modal that opens on "복호화 승인 요청" click, not as an inline field on the main form.
- **Search history detail modal (자세히 보기, req 20260318, 20260320)**: When APPROVED, the modal shows "복호화 요청 대상 (총 n건)" with columns **애플리케이션, 서비스 그룹, GUID, status**. Data: `decryptionRequestedRows`, `decryptionRequestedCount` from `GET /api/search-history/{id}`. PENDING/REJECTED/EXPIRED → "복호화 요청 대상: 해당 없음".

## References

- `docs/api-definition.md`
- `docs/contract.md`
- `docs/requirements/20260224-decryption-approval-snapshot-guide.md`
- `docs/requirements/20260316-decrypt-approval-use-user-id-everywhere.md`
