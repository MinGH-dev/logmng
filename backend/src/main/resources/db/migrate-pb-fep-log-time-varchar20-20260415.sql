-- Forward migration: PB FEP pb_send / pb_recv canonical log_time → VARCHAR(20), lexical yyyyMMddHHmmssSSSSSS.
-- Requirement: docs/requirements/20260415-pb-fep-log-time-20-char-microseconds.md
--
-- Operator runbook (summary):
--   1. Quiesce or pause ingest if needed; take backup before structural changes.
--   2. Apply this script with search_path including SCHEMA_PB (same as setup.sh / psql -c "SET search_path TO …").
--   3. Order: widen column (ALTER … TYPE VARCHAR(20)), then UPDATE backfill (14-digit → append 000000; 8-digit date-only → append 000000000000).
--   4. PostgreSQL 11+ partitioned tables: ALTER COLUMN on the parent propagates to partitions (confirm on your version).
--   5. Existing deployments with daily children created under the old 15-char bound pattern
--      (FOR VALUES FROM ('YYYYMMDD0000000') TO ('nextday0000000')) must recreate those partitions so bounds use 20 characters:
--        FROM (YYYYMMDD || '000000000000')  TO ((next calendar day YYYYMMDD) || '000000000000')
--      Pattern (one day D — replace names/schema as needed):
--        -- Inspect: SELECT c.relname, pg_get_expr(c.relpartbound, c.oid) FROM pg_inherits i JOIN pg_class c ON …
--        ALTER TABLE pb_send DETACH PARTITION pb_send_YYYYMMDD;
--        -- Save data: CREATE TABLE pb_send_YYYYMMDD_backup AS TABLE pb_send_YYYYMMDD;  (or INSERT into temp)
--        DROP TABLE pb_send_YYYYMMDD;
--        CREATE TABLE pb_send_YYYYMMDD PARTITION OF pb_send
--          FOR VALUES FROM ('YYYYMMDD000000000000') TO ('<next_YYYYMMDD>000000000000');
--        INSERT INTO pb_send SELECT * FROM pb_send_YYYYMMDD_backup;
--      Repeat for pb_recv. Re-attach triggers if your process recreated them (see create-pb-send-recv-daily-partitions-only.sql).
--   6. Re-run check-db.sh (section 6k) and PB FEP ingest smoke tests.
--
-- Idempotency:
--   - ALTER runs only when information_schema.character_maximum_length < 20.
--   - UPDATE uses CASE so rows already matching ^[0-9]{20}$ are unchanged on re-run.

DO $$
DECLARE
    k  TEXT;
    ml INT;
BEGIN
    FOREACH k IN ARRAY ARRAY['pb_send', 'pb_recv']
    LOOP
        IF to_regclass(k) IS NULL THEN
            RAISE NOTICE 'migrate-pb-fep-log-time-varchar20-20260415: % not found, skip.', k;
            CONTINUE;
        END IF;

        SELECT c.character_maximum_length
          INTO ml
          FROM information_schema.columns c
         WHERE c.table_schema = current_schema()
           AND c.table_name = k
           AND c.column_name = 'log_time';

        IF ml IS NULL THEN
            RAISE NOTICE 'migrate-pb-fep-log-time-varchar20-20260415: %.log_time column missing, skip.', k;
            CONTINUE;
        END IF;

        IF ml < 20 THEN
            EXECUTE format('ALTER TABLE %I ALTER COLUMN log_time TYPE VARCHAR(20)', k);
            RAISE NOTICE 'migrate-pb-fep-log-time-varchar20-20260415: ALTER %I.log_time → VARCHAR(20).', k;
        ELSE
            RAISE NOTICE 'migrate-pb-fep-log-time-varchar20-20260415: %.log_time already VARCHAR(%) — skip ALTER.', k, ml;
        END IF;

        EXECUTE format(
            $fmt$
            UPDATE %I SET log_time = (
                CASE
                    WHEN log_time IS NULL THEN NULL
                    WHEN btrim(log_time::text) ~ '^[0-9]{20}$' THEN substring(btrim(log_time::text) FROM 1 FOR 20)
                    WHEN btrim(log_time::text) ~ '^[0-9]{14}$' THEN btrim(log_time::text) || '000000'
                    WHEN btrim(log_time::text) ~ '^[0-9]{8}$' THEN btrim(log_time::text) || '000000000000'
                    ELSE btrim(log_time::text)
                END
            )
            WHERE log_time IS NOT NULL
            $fmt$,
            k
        );

        RAISE NOTICE 'migrate-pb-fep-log-time-varchar20-20260415: normalized log_time values for % (idempotent UPDATE).', k;
    END LOOP;
END $$;
