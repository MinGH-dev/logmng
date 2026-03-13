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
- Search-history requester filters remain non-authoritative under `scope=self`; the visible locked requester values come from auth/current-user `selfContext`, while backend requester enforcement stays authoritative.

## References

- `docs/api-definition.md`
- `docs/contract.md`
- `docs/requirements/20260224-decryption-approval-snapshot-guide.md`
