-- Approval-history columns for search_history (idempotent; safe to run multiple times)
-- Requirement: docs/requirements/20260224-search-history-decryption-approval.md

ALTER TABLE search_history ADD COLUMN IF NOT EXISTS approved_by VARCHAR(100) NULL;
ALTER TABLE search_history ADD COLUMN IF NOT EXISTS approved_at TIMESTAMP NULL;
ALTER TABLE search_history ADD COLUMN IF NOT EXISTS rejected_by VARCHAR(100) NULL;
ALTER TABLE search_history ADD COLUMN IF NOT EXISTS rejected_at TIMESTAMP NULL;
ALTER TABLE search_history ADD COLUMN IF NOT EXISTS rejection_reason TEXT NULL;
