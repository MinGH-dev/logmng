-- Migration: add user_decryption_allowed table (decryption authorization store).
-- Req: docs/requirements/20260318-decryption-allowed-store-and-decrypt-ui.md.
-- Does NOT alter or drop search_history_approved_row (audit/history only).
--
-- Apply: from project root:
--   psql -U postgres -h localhost -p 5432 -d logmng -f backend/src/main/resources/db/migrate-user-decryption-allowed.sql
-- Or with logmng user:
--   psql -U logmng -h localhost -p 5432 -d logmng -f backend/src/main/resources/db/migrate-user-decryption-allowed.sql
-- Idempotent: CREATE TABLE IF NOT EXISTS, CREATE INDEX IF NOT EXISTS; backfill is safe to run once or re-run.

-- 1) Table and indexes
CREATE TABLE IF NOT EXISTS user_decryption_allowed (
    user_id    BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    screen     VARCHAR(50) NOT NULL,
    guid       VARCHAR(512) NOT NULL,
    valid_until TIMESTAMP NOT NULL,
    PRIMARY KEY (user_id, screen, guid)
);

CREATE INDEX IF NOT EXISTS idx_user_decryption_allowed_get ON user_decryption_allowed(user_id, screen, valid_until);
CREATE INDEX IF NOT EXISTS idx_user_decryption_allowed_cleanup ON user_decryption_allowed(user_id, valid_until);

-- 2) Optional one-time backfill: copy current APPROVED, non-expired (search_history + search_history_approved_row)
--    into user_decryption_allowed so existing approved users can decrypt without re-approval.
--    Screen set to 'main' (search screen). On conflict, keep the later valid_until.
INSERT INTO user_decryption_allowed (user_id, screen, guid, valid_until)
SELECT sh.user_id, 'main'::VARCHAR(50), ar.row_id, MAX(sh.expires_at)
FROM search_history sh
JOIN search_history_approved_row ar ON sh.id = ar.search_history_id
WHERE sh.approval_status = 'APPROVED' AND sh.expires_at > CURRENT_TIMESTAMP
GROUP BY sh.user_id, ar.row_id
ON CONFLICT (user_id, screen, guid) DO UPDATE SET valid_until = GREATEST(user_decryption_allowed.valid_until, EXCLUDED.valid_until);
