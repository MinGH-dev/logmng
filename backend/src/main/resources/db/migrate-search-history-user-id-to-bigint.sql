-- Migration: search_history.user_id VARCHAR -> BIGINT NOT NULL REFERENCES app_user(id).
-- Req: docs/requirements/20260316-search-history-user-id-query-and-naming.md.
-- DBA review: detection, backfill (username->id, id::text->id), orphan handling (Option A: delete), cutover, FK, indexes.
--
-- Prerequisite: Backup search_history (and optionally app_user) before running.
-- Run: psql -U postgres -h localhost -p 5432 -d logmng -f backend/src/main/resources/db/migrate-search-history-user-id-to-bigint.sql
-- Idempotent: skips entirely if user_id is already BIGINT (TC-11, TC-13, TC-14).

BEGIN;

DO $$
DECLARE
  col_type text;
  cnt_digit bigint;
  cnt_non_digit bigint;
  cnt_match_id bigint;
  cnt_match_username bigint;
  orphan_cnt bigint;
BEGIN
  SELECT data_type INTO col_type
  FROM information_schema.columns
  WHERE table_schema = 'public' AND table_name = 'search_history' AND column_name = 'user_id';

  IF col_type = 'bigint' THEN
    RAISE NOTICE 'search_history.user_id already BIGINT; migration skipped.';
    RETURN;
  END IF;

  -- Step 1: Detection (document semantics for corrective action / TC-13, TC-14)
  SELECT COUNT(*) INTO cnt_digit FROM search_history WHERE user_id ~ '^\d+$';
  SELECT COUNT(*) INTO cnt_non_digit FROM search_history WHERE user_id IS NULL OR user_id !~ '^\d+$';
  SELECT COUNT(*) INTO cnt_match_id FROM search_history sh INNER JOIN app_user u ON u.id::text = sh.user_id;
  SELECT COUNT(*) INTO cnt_match_username FROM search_history sh INNER JOIN app_user u ON u.username = sh.user_id;
  RAISE NOTICE 'Detection: digit-only rows=%, non-digit (or null)=%, match by id::text=%, match by username=%', cnt_digit, cnt_non_digit, cnt_match_id, cnt_match_username;

  -- Step 2: Add staging column
  ALTER TABLE search_history ADD COLUMN IF NOT EXISTS user_id_new BIGINT NULL;

  -- Step 3a: Backfill where user_id is username
  UPDATE search_history sh SET user_id_new = u.id FROM app_user u WHERE u.username = sh.user_id AND sh.user_id_new IS NULL;

  -- Step 3b: Backfill where user_id is id::text (digit-only and exists in app_user)
  UPDATE search_history sh SET user_id_new = sh.user_id::bigint FROM app_user u WHERE u.id = sh.user_id::bigint AND sh.user_id ~ '^\d+$' AND sh.user_id_new IS NULL;

  -- Step 4: Orphan handling (Option A: delete and document)
  SELECT COUNT(*) INTO orphan_cnt FROM search_history WHERE user_id_new IS NULL;
  IF orphan_cnt > 0 THEN
    RAISE NOTICE 'Orphan rows (no matching app_user): % deleted.', orphan_cnt;
    DELETE FROM search_history WHERE user_id_new IS NULL;
  END IF;

  -- Step 5: Cutover
  DROP INDEX IF EXISTS idx_search_history_user_requested;
  DROP INDEX IF EXISTS idx_search_history_user_id;
  ALTER TABLE search_history DROP COLUMN user_id;
  ALTER TABLE search_history RENAME COLUMN user_id_new TO user_id;
  ALTER TABLE search_history ALTER COLUMN user_id SET NOT NULL;
  ALTER TABLE search_history ADD CONSTRAINT fk_search_history_app_user FOREIGN KEY (user_id) REFERENCES app_user(id);
  CREATE INDEX IF NOT EXISTS idx_search_history_user_id ON search_history(user_id);
  CREATE INDEX IF NOT EXISTS idx_search_history_user_requested ON search_history(user_id, requested_at DESC);

  RAISE NOTICE 'Migration completed: search_history.user_id is BIGINT NOT NULL REFERENCES app_user(id).';
END $$;

COMMIT;
