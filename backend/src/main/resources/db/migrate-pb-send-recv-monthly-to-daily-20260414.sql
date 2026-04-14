-- One-time: monthly RANGE partitions (pb_*_YYYYMM) -> daily (pb_*_YYYYMMDD) on log_timestamp.
-- Prerequisites: migrate-pb-send-recv-partitioning-20260408.sql (legacy monthly) already applied.
--
-- Idempotency:
--   - Safe no-op if neither pb_send nor pb_recv has a child named like *_YYYYMM (6 digits).
--   - Already-daily DBs (only *_YYYYMMDD children) exit with NOTICE only.
--   - Re-run after partial failure may require restore from backup (see DB_SETUP_GUIDE.md).
--
-- Does not log row contents (NOTICE names partitions only).

DO $$
DECLARE
    has_monthly_send BOOLEAN;
    has_monthly_recv BOOLEAN;
    r                RECORD;
    ym               TEXT;
    y                INT;
    mo               INT;
    month_start      DATE;
    month_end        DATE;
    d                DATE;
    pnm              TEXT;
    detached         TEXT;
BEGIN
    SELECT EXISTS (
        SELECT 1
        FROM pg_inherits i
        JOIN pg_class c ON c.oid = i.inhrelid
        JOIN pg_class p ON p.oid = i.inhparent
        WHERE p.oid = to_regclass('pb_send')
          AND c.relname ~ '^pb_send_[0-9]{6}$'
    ) INTO has_monthly_send;

    SELECT EXISTS (
        SELECT 1
        FROM pg_inherits i
        JOIN pg_class c ON c.oid = i.inhrelid
        JOIN pg_class p ON p.oid = i.inhparent
        WHERE p.oid = to_regclass('pb_recv')
          AND c.relname ~ '^pb_recv_[0-9]{6}$'
    ) INTO has_monthly_recv;

    IF NOT has_monthly_send AND NOT has_monthly_recv THEN
        RAISE NOTICE 'migrate-pb-send-recv-monthly-to-daily-20260414: no pb_*_YYYYMM partitions; skip.';
        RETURN;
    END IF;

    -- pb_send
    IF to_regclass('pb_send') IS NOT NULL
       AND EXISTS (SELECT 1 FROM pg_class WHERE oid = to_regclass('pb_send') AND relkind = 'p')
       AND has_monthly_send
    THEN
        FOR r IN
            SELECT c.relname::TEXT AS relname
            FROM pg_inherits i
            JOIN pg_class c ON c.oid = i.inhrelid
            JOIN pg_class p ON p.oid = i.inhparent
            WHERE p.oid = 'pb_send'::regclass
              AND c.relname ~ '^pb_send_[0-9]{6}$'
            ORDER BY c.relname
        LOOP
            detached := r.relname;
            EXECUTE format('ALTER TABLE pb_send DETACH PARTITION %I', detached);

            ym := replace(detached, 'pb_send_', '');
            y := substring(ym FROM 1 FOR 4)::INT;
            mo := substring(ym FROM 5 FOR 2)::INT;
            month_start := make_date(y, mo, 1);
            month_end := (month_start + INTERVAL '1 month')::DATE;

            d := month_start;
            WHILE d < month_end LOOP
                pnm := format('pb_send_%s', to_char(d, 'YYYYMMDD'));
                IF to_regclass(pnm) IS NULL THEN
                    EXECUTE format(
                        'CREATE TABLE %I PARTITION OF pb_send FOR VALUES FROM (%L) TO (%L)',
                        pnm,
                        d::TEXT,
                        (d + 1)::TEXT
                    );
                END IF;
                EXECUTE format(
                    'CREATE TRIGGER update_pb_send_updated_at BEFORE UPDATE ON %I FOR EACH ROW EXECUTE FUNCTION update_updated_at_column()',
                    pnm
                );
                d := d + 1;
            END LOOP;

            EXECUTE format('INSERT INTO pb_send SELECT * FROM %I', detached);
            EXECUTE format('DROP TABLE %I', detached);
            RAISE NOTICE 'migrate-pb-send-recv-monthly-to-daily-20260414: pb_send detached % finished.', detached;
        END LOOP;
    END IF;

    -- pb_recv
    IF to_regclass('pb_recv') IS NOT NULL
       AND EXISTS (SELECT 1 FROM pg_class WHERE oid = to_regclass('pb_recv') AND relkind = 'p')
       AND has_monthly_recv
    THEN
        FOR r IN
            SELECT c.relname::TEXT AS relname
            FROM pg_inherits i
            JOIN pg_class c ON c.oid = i.inhrelid
            JOIN pg_class p ON p.oid = i.inhparent
            WHERE p.oid = 'pb_recv'::regclass
              AND c.relname ~ '^pb_recv_[0-9]{6}$'
            ORDER BY c.relname
        LOOP
            detached := r.relname;
            EXECUTE format('ALTER TABLE pb_recv DETACH PARTITION %I', detached);

            ym := replace(detached, 'pb_recv_', '');
            y := substring(ym FROM 1 FOR 4)::INT;
            mo := substring(ym FROM 5 FOR 2)::INT;
            month_start := make_date(y, mo, 1);
            month_end := (month_start + INTERVAL '1 month')::DATE;

            d := month_start;
            WHILE d < month_end LOOP
                pnm := format('pb_recv_%s', to_char(d, 'YYYYMMDD'));
                IF to_regclass(pnm) IS NULL THEN
                    EXECUTE format(
                        'CREATE TABLE %I PARTITION OF pb_recv FOR VALUES FROM (%L) TO (%L)',
                        pnm,
                        d::TEXT,
                        (d + 1)::TEXT
                    );
                END IF;
                EXECUTE format(
                    'CREATE TRIGGER update_pb_recv_updated_at BEFORE UPDATE ON %I FOR EACH ROW EXECUTE FUNCTION update_updated_at_column()',
                    pnm
                );
                d := d + 1;
            END LOOP;

            EXECUTE format('INSERT INTO pb_recv SELECT * FROM %I', detached);
            EXECUTE format('DROP TABLE %I', detached);
            RAISE NOTICE 'migrate-pb-send-recv-monthly-to-daily-20260414: pb_recv detached % finished.', detached;
        END LOOP;
    END IF;
END $$;
