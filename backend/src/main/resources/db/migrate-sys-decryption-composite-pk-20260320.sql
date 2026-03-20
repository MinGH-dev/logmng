-- Req: docs/requirements/20260320-imagelog-guid-status-composite-key.md
-- System DB / SCHEMA_SYS (and PB in search_path): search_history_approved_row, user_decryption_allowed.
-- Apply with same search_path as other sys migrations (e.g. SCHEMA_SYS, SCHEMA_PB, public).
--
-- Pre-flight: imagelog duplicate check runs on ImageLog DB (see migrate-imagelog-guid-status-unique-20260320.sql).
-- Legacy rows get row_status ''; PK collision only if duplicate (search_history_id, log_type, row_id) existed (should not).
--
-- Note: idx_user_decryption_allowed_get (user_id, screen, valid_until) already exists from
-- migrate-user-decryption-allowed.sql — no duplicate index added here.

ALTER TABLE search_history_approved_row
    ADD COLUMN IF NOT EXISTS row_status VARCHAR(256) NOT NULL DEFAULT '';

ALTER TABLE search_history_approved_row
    DROP CONSTRAINT IF EXISTS search_history_approved_row_pkey;

ALTER TABLE search_history_approved_row
    ADD PRIMARY KEY (search_history_id, log_type, row_id, row_status);

ALTER TABLE user_decryption_allowed
    ADD COLUMN IF NOT EXISTS row_status VARCHAR(256) NOT NULL DEFAULT '';

ALTER TABLE user_decryption_allowed
    DROP CONSTRAINT IF EXISTS user_decryption_allowed_pkey;

ALTER TABLE user_decryption_allowed
    ADD PRIMARY KEY (user_id, screen, guid, row_status);
