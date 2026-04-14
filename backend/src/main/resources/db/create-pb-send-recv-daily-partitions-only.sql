-- PB FEP: partitioned parents (pb_send / pb_recv) + daily RANGE children
--
-- Time policy (finalized):
--   - Product-facing canonical time field: `log_time`.
--   - `log_timestamp` is physically removed (ingest bugfix requirement).
--
-- **No DEFAULT partition.** If `log_time` is not covered by a pre-created daily
-- child, INSERT fails (no catch-all). Operators must extend the daily window (re-run
-- this script with adjusted `back_days`/`fwd_days`, or `CREATE TABLE … PARTITION OF …`
-- for additional days) before loading data outside the covered dates.
--
-- Covers:
--   (A) No table yet (greenfield): sequence → partitioned parent (no DEFAULT) →
--       indexes on parent → daily partitions in [CURRENT_DATE − back_days, CURRENT_DATE + fwd_days]
--       + per-partition updated_at triggers.
--   (B) Ordinary table (relkind 'r'): rename to *_migr_tmp → create empty partitioned
--       parent → daily partitions for combined date range (see below) + triggers →
--       INSERT … SELECT → DROP migr_tmp → parent indexes (same as today).
--   (C) Already partitioned parent (relkind 'p'): only ensure daily partitions in window
--       (no DEFAULT; never creates a DEFAULT).
--
-- (B) Date range for daily children (per table, on *_migr_tmp):
--   Let win_min = CURRENT_DATE − back_days, win_max = CURRENT_DATE + fwd_days (inclusive days).
--   Let data_min = MIN(to_date(substr(log_time,1,8),'YYYYMMDD')), data_max = MAX(...) from the
--   temp table (NULL if empty).
--   Partition days run from d_start = LEAST(COALESCE(data_min, win_min), win_min) through
--   d_end = GREATEST(COALESCE(data_max, win_max), win_max) inclusive, so every existing row
--   has a home partition and the same rolling window as (A)/(C) is also covered.
--
-- Does NOT migrate monthly→daily; use migrate-pb-send-recv-monthly-to-daily-20260414.sql for that.
--
-- Canonical source: backend/src/main/resources/db/
-- Parent column layout: wire-aligned pb_send / pb_recv (requirement 20260414-pb-fep-wire-schema-alignment).
--
-- Prerequisites: none if you use this file alone — the block below matches
-- schema_sys.sql / schema_pb_fep.sql (idempotent CREATE OR REPLACE).
-- Run with search_path including the PB schema, e.g. SET search_path TO public;
-- Use a role that can CREATE TABLE / TRIGGER on the target database (owner or superuser).

CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DO $$
DECLARE
    back_days INT := 30;
    fwd_days  INT := 7;
    d         DATE;
    d_start   DATE;
    d_end     DATE;
    data_min  DATE;
    data_max  DATE;
    win_min   DATE;
    win_max   DATE;
    part_name TEXT;
    k         TEXT;
BEGIN
    win_min := CURRENT_DATE - back_days;
    win_max := CURRENT_DATE + fwd_days;

    FOREACH k IN ARRAY ARRAY['pb_send', 'pb_recv']
    LOOP
        -- ---------- (B) ordinary table → partitioned (no DEFAULT; key=log_time) ----------
        IF to_regclass(k) IS NOT NULL
           AND EXISTS (
               SELECT 1 FROM pg_class WHERE oid = to_regclass(k::TEXT) AND relkind = 'r'
           )
        THEN
            EXECUTE format('ALTER TABLE %I RENAME TO %I', k, k || '_migr_tmp');

            EXECUTE format(
                'CREATE TABLE %I (
                id BIGINT NOT NULL DEFAULT nextval(%L::regclass),
                log_time VARCHAR(15),
                log_ch_cd VARCHAR(6),
                log_io_cd VARCHAR(1),
                log_len VARCHAR(6),
                len VARCHAR(6),
                tr_gb VARCHAR(1),
                comp_gb VARCHAR(1),
                enct_gb VARCHAR(1),
                data_off VARCHAR(3),
                tr_code VARCHAR(8),
                comp_no VARCHAR(3),
                brodid VARCHAR(16),
                media_gb VARCHAR(2),
                channel_no VARCHAR(3),
                tr_seq VARCHAR(9),
                trid_sign VARCHAR(1),
                trid_media_gb VARCHAR(2),
                trid_term_no VARCHAR(3),
                trid_svr_no VARCHAR(3),
                trid_svr_seq VARCHAR(7),
                pub_ip VARCHAR(12),
                prt_ip VARCHAR(12),
                prc_brno VARCHAR(3),
                brno VARCHAR(3),
                term_no VARCHAR(8),
                lan_gb VARCHAR(1),
                prc_time VARCHAR(9),
                msg_code VARCHAR(4),
                msg_gb VARCHAR(1),
                compress_re VARCHAR(1),
                fnc_key VARCHAR(4),
                rec_cnt VARCHAR(4),
                exp_prc VARCHAR(2),
                reserve VARCHAR(4),
                con_gb VARCHAR(1),
                con_key VARCHAR(18),
                vlen_len VARCHAR(2),
                vhd_len VARCHAR(2),
                bmsg_len VARCHAR(2),
                vlen TEXT,
                vhd TEXT,
                bmsg TEXT,
                data TEXT,
                wire_ts VARCHAR(19),
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            ) PARTITION BY RANGE (log_time)',
                k,
                k || '_id_seq'
            );

            EXECUTE format(
                'SELECT MIN(to_date(substr(log_time, 1, 8), ''YYYYMMDD'')), MAX(to_date(substr(log_time, 1, 8), ''YYYYMMDD'')) FROM %I WHERE log_time ~ ''^[0-9]{8,}$''',
                k || '_migr_tmp'
            )
            INTO data_min, data_max;

            d_start := LEAST(COALESCE(data_min, win_min), win_min);
            d_end := GREATEST(COALESCE(data_max, win_max), win_max);

            d := d_start;
            WHILE d <= d_end LOOP
                part_name := format('%s_%s', k, to_char(d, 'YYYYMMDD'));
                IF to_regclass(part_name) IS NULL THEN
                    EXECUTE format(
                        'CREATE TABLE %I PARTITION OF %I FOR VALUES FROM (%L) TO (%L)',
                        part_name,
                        k,
                        to_char(d, 'YYYYMMDD') || '0000000',
                        to_char(d + 1, 'YYYYMMDD') || '0000000'
                    );
                END IF;
                EXECUTE format(
                    'DROP TRIGGER IF EXISTS update_%s_updated_at ON %I',
                    k,
                    part_name
                );
                EXECUTE format(
                    'CREATE TRIGGER update_%s_updated_at BEFORE UPDATE ON %I FOR EACH ROW EXECUTE FUNCTION update_updated_at_column()',
                    k,
                    part_name
                );
                d := d + 1;
            END LOOP;

            EXECUTE format('INSERT INTO %I SELECT * FROM %I', k, k || '_migr_tmp');
            EXECUTE format('DROP TABLE %I', k || '_migr_tmp');

            EXECUTE format(
                'CREATE INDEX IF NOT EXISTS idx_%s_p_log_time ON %I(log_time)',
                k,
                k
            );
            EXECUTE format(
                'CREATE INDEX IF NOT EXISTS idx_%s_p_brodid ON %I(brodid)',
                k,
                k
            );
            EXECUTE format(
                'CREATE INDEX IF NOT EXISTS idx_%s_p_media_gb ON %I(media_gb)',
                k,
                k
            );
            EXECUTE format(
                'CREATE INDEX IF NOT EXISTS idx_%s_p_tr_code ON %I(tr_code)',
                k,
                k
            );
            EXECUTE format(
                'CREATE INDEX IF NOT EXISTS idx_%s_p_search ON %I(log_time, media_gb, tr_code)',
                k,
                k
            );
            EXECUTE format(
                'CREATE INDEX IF NOT EXISTS idx_%s_p_con_key ON %I(con_key)',
                k,
                k
            );

            RAISE NOTICE 'create-pb-send-recv-daily-partitions-only: converted ordinary % to partitioned parent (no DEFAULT; key=log_time).', k;
        END IF;

        -- ---------- (A) missing table: greenfield partitioned stack (no DEFAULT; key=log_time) ----------
        IF to_regclass(k) IS NULL THEN
            EXECUTE format('CREATE SEQUENCE IF NOT EXISTS %I AS BIGINT', k || '_id_seq');

            EXECUTE format(
                'CREATE TABLE %I (
                id BIGINT NOT NULL DEFAULT nextval(%L::regclass),
                log_time VARCHAR(15),
                log_ch_cd VARCHAR(6),
                log_io_cd VARCHAR(1),
                log_len VARCHAR(6),
                len VARCHAR(6),
                tr_gb VARCHAR(1),
                comp_gb VARCHAR(1),
                enct_gb VARCHAR(1),
                data_off VARCHAR(3),
                tr_code VARCHAR(8),
                comp_no VARCHAR(3),
                brodid VARCHAR(16),
                media_gb VARCHAR(2),
                channel_no VARCHAR(3),
                tr_seq VARCHAR(9),
                trid_sign VARCHAR(1),
                trid_media_gb VARCHAR(2),
                trid_term_no VARCHAR(3),
                trid_svr_no VARCHAR(3),
                trid_svr_seq VARCHAR(7),
                pub_ip VARCHAR(12),
                prt_ip VARCHAR(12),
                prc_brno VARCHAR(3),
                brno VARCHAR(3),
                term_no VARCHAR(8),
                lan_gb VARCHAR(1),
                prc_time VARCHAR(9),
                msg_code VARCHAR(4),
                msg_gb VARCHAR(1),
                compress_re VARCHAR(1),
                fnc_key VARCHAR(4),
                rec_cnt VARCHAR(4),
                exp_prc VARCHAR(2),
                reserve VARCHAR(4),
                con_gb VARCHAR(1),
                con_key VARCHAR(18),
                vlen_len VARCHAR(2),
                vhd_len VARCHAR(2),
                bmsg_len VARCHAR(2),
                vlen TEXT,
                vhd TEXT,
                bmsg TEXT,
                data TEXT,
                wire_ts VARCHAR(19),
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            ) PARTITION BY RANGE (log_time)',
                k,
                k || '_id_seq'
            );

            EXECUTE format(
                'CREATE INDEX IF NOT EXISTS idx_%s_p_log_time ON %I(log_time)',
                k,
                k
            );
            EXECUTE format(
                'CREATE INDEX IF NOT EXISTS idx_%s_p_brodid ON %I(brodid)',
                k,
                k
            );
            EXECUTE format(
                'CREATE INDEX IF NOT EXISTS idx_%s_p_media_gb ON %I(media_gb)',
                k,
                k
            );
            EXECUTE format(
                'CREATE INDEX IF NOT EXISTS idx_%s_p_tr_code ON %I(tr_code)',
                k,
                k
            );
            EXECUTE format(
                'CREATE INDEX IF NOT EXISTS idx_%s_p_search ON %I(log_time, media_gb, tr_code)',
                k,
                k
            );
            EXECUTE format(
                'CREATE INDEX IF NOT EXISTS idx_%s_p_con_key ON %I(con_key)',
                k,
                k
            );

            RAISE NOTICE 'create-pb-send-recv-daily-partitions-only: created greenfield partitioned % (no DEFAULT; key=log_time).', k;
        END IF;

        -- ---------- (C) already partitioned parent: ensure daily partitions + triggers only (no DEFAULT) ----------
        IF to_regclass(k) IS NULL THEN
            RAISE EXCEPTION 'create-pb-send-recv-daily-partitions-only: internal error, % missing after setup', k;
        END IF;

        IF NOT EXISTS (
            SELECT 1 FROM pg_class WHERE oid = to_regclass(k::TEXT) AND relkind = 'p'
        ) THEN
            RAISE NOTICE 'create-pb-send-recv-daily-partitions-only: % exists but is not a partitioned parent — skip daily parts.', k;
        ELSE
            d := win_min;
            WHILE d <= win_max LOOP
                part_name := format('%s_%s', k, to_char(d, 'YYYYMMDD'));
                IF to_regclass(part_name) IS NULL THEN
                    EXECUTE format(
                        'CREATE TABLE %I PARTITION OF %I FOR VALUES FROM (%L) TO (%L)',
                        part_name,
                        k,
                        to_char(d, 'YYYYMMDD') || '0000000',
                        to_char(d + 1, 'YYYYMMDD') || '0000000'
                    );
                END IF;
                EXECUTE format(
                    'DROP TRIGGER IF EXISTS update_%s_updated_at ON %I',
                    k,
                    part_name
                );
                EXECUTE format(
                    'CREATE TRIGGER update_%s_updated_at BEFORE UPDATE ON %I FOR EACH ROW EXECUTE FUNCTION update_updated_at_column()',
                    k,
                    part_name
                );
                d := d + 1;
            END LOOP;
        END IF;
    END LOOP;
END $$;
