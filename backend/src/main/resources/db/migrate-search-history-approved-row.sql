-- Approval snapshot table: rows allowed for decryption per approved search_history.
-- Ref: docs/requirements/20260224-decryption-snapshot-final-design-en.md §6.2

CREATE TABLE IF NOT EXISTS search_history_approved_row (
    search_history_id BIGINT NOT NULL REFERENCES search_history(id) ON DELETE CASCADE,
    log_type         VARCHAR(50) NOT NULL,
    row_id           VARCHAR(512) NOT NULL,
    PRIMARY KEY (search_history_id, log_type, row_id)
);
CREATE INDEX IF NOT EXISTS idx_search_history_approved_row_history ON search_history_approved_row(search_history_id);
