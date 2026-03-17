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
- **Decrypt execution path (req 20260317)**: POST /api/logs/decrypt uses **numeric app_user.id only**. `isValidApprovalForUser(searchHistoryId, currentUserId)` checks `search_history.user_id = currentUserId` (BIGINT); no username in this path. `isRowInApprovedSnapshot` uses only search_history_id, log_type, row_id. Ownership and approval checks use Long/BIGINT; username is not used for permission or validation on decrypt execution.
- Search-history requester filters remain non-authoritative under `scope=self`; the visible locked requester values come from auth/current-user `selfContext`, while backend requester enforcement stays authoritative.
- Search history **list** response rows include requester fields for the grid: `requesterDepartmentName` (when available from department table), `requesterDepartmentCode`, `requesterDisplayName`, `requesterUsername`. The grid shows **three columns** for requester, in order: **부서** (department), **사용자ID** (username), **사용자명** (display name).

## References

- `docs/api-definition.md`
- `docs/contract.md`
- `docs/requirements/20260224-decryption-approval-snapshot-guide.md`
- `docs/requirements/20260316-decrypt-approval-use-user-id-everywhere.md`
