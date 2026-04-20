-- One-time bugfix migration:
--   - Physically remove log_timestamp from pb_send/pb_recv.
--   - Rebuild partitioned parents to RANGE(log_time) without DEFAULT partition.
--   - Keep daily partition window [CURRENT_DATE - 30, CURRENT_DATE + 7].
-- Idempotent:
--   - If log_timestamp is already absent on parent, skip that table.

DO $$
DECLARE
    k          TEXT;
    rel_kind   "char";
    has_col    BOOLEAN;
    part_key   TEXT;
    back_days  INT := 30;
    fwd_days   INT := 7;
    win_min    DATE := CURRENT_DATE - 30;
    win_max    DATE := CURRENT_DATE + 7;
    d          DATE;
    d_start    DATE;
    d_end      DATE;
    data_min   DATE;
    data_max   DATE;
    part_name  TEXT;
BEGIN
    FOREACH k IN ARRAY ARRAY['pb_send', 'pb_recv']
    LOOP
        IF to_regclass(k) IS NULL THEN
            RAISE NOTICE 'migrate-pb-send-recv-remove-log-timestamp-20260414: % not found, skip.', k;
            CONTINUE;
        END IF;

        SELECT c.relkind
          INTO rel_kind
          FROM pg_class c
         WHERE c.oid = to_regclass(k);

        SELECT EXISTS (
            SELECT 1
              FROM information_schema.columns
             WHERE table_schema = current_schema()
               AND table_name = k
               AND column_name = 'log_timestamp'
        )
        INTO has_col;

        IF NOT has_col THEN
            RAISE NOTICE 'migrate-pb-send-recv-remove-log-timestamp-20260414: %.log_timestamp already removed, skip.', k;
            CONTINUE;
        END IF;

        IF rel_kind = 'r' THEN
            EXECUTE format('ALTER TABLE %I DROP COLUMN IF EXISTS log_timestamp', k);
            EXECUTE format('DROP INDEX IF EXISTS idx_%s_log_timestamp', k);
            EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%s_log_time ON %I(log_time)', k, k);
            EXECUTE format('DROP INDEX IF EXISTS idx_%s_search', k);
            EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%s_search ON %I(log_time, media_gb, tr_code)', k, k);
            RAISE NOTICE 'migrate-pb-send-recv-remove-log-timestamp-20260414: dropped log_timestamp from ordinary table %.', k;
            CONTINUE;
        END IF;

        IF rel_kind <> 'p' THEN
            RAISE NOTICE 'migrate-pb-send-recv-remove-log-timestamp-20260414: % relkind % unsupported, skip.', k, rel_kind;
            CONTINUE;
        END IF;

        SELECT pg_get_partkeydef(to_regclass(k))
          INTO part_key;

        IF part_key IS NOT NULL AND position('log_timestamp' IN part_key) = 0 THEN
            EXECUTE format('ALTER TABLE %I DROP COLUMN IF EXISTS log_timestamp', k);
            EXECUTE format('DROP INDEX IF EXISTS idx_%s_log_timestamp', k);
            EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%s_p_log_time ON %I(log_time)', k, k);
            EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%s_p_brodid ON %I(brodid)', k, k);
            EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%s_p_media_gb ON %I(media_gb)', k, k);
            EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%s_p_tr_code ON %I(tr_code)', k, k);
            EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%s_p_search ON %I(log_time, media_gb, tr_code)', k, k);
            EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%s_p_con_key ON %I(con_key)', k, k);
            RAISE NOTICE 'migrate-pb-send-recv-remove-log-timestamp-20260414: % already partitioned by %, dropped log_timestamp only.', k, part_key;
            CONTINUE;
        END IF;

        EXECUTE format('ALTER TABLE %I RENAME TO %I', k, k || '_legacy_ts');

        EXECUTE format(
            'CREATE TABLE %I (
            id BIGINT NOT NULL DEFAULT nextval(%L::regclass),
            log_time VARCHAR(20),
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
            'SELECT MIN(
                        COALESCE(
                            CASE WHEN log_time ~ ''^[0-9]{8,}$'' THEN to_date(substr(log_time, 1, 8), ''YYYYMMDD'') END,
                            log_timestamp::date,
                            created_at::date
                        )
                    ),
                    MAX(
                        COALESCE(
                            CASE WHEN log_time ~ ''^[0-9]{8,}$'' THEN to_date(substr(log_time, 1, 8), ''YYYYMMDD'') END,
                            log_timestamp::date,
                            created_at::date
                        )
                    )
               FROM %I
              WHERE (log_time ~ ''^[0-9]{8,}$'')
                 OR log_timestamp IS NOT NULL
                 OR created_at IS NOT NULL',
            k || '_legacy_ts'
        ) INTO data_min, data_max;

        d_start := LEAST(COALESCE(data_min, win_min), win_min);
        d_end := GREATEST(COALESCE(data_max, win_max), win_max);
        d := d_start;
        WHILE d <= d_end LOOP
            -- Legacy children keep names like pb_send_YYYYMMDD under *_legacy_ts.
            -- Use a new stable prefix to avoid name collisions during rebuild.
            part_name := format('%s_logtime_%s', k, to_char(d, 'YYYYMMDD'));
            IF to_regclass(part_name) IS NULL THEN
                EXECUTE format(
                    'CREATE TABLE %I PARTITION OF %I FOR VALUES FROM (%L) TO (%L)',
                    part_name, k, to_char(d, 'YYYYMMDD') || '000000000000', to_char(d + 1, 'YYYYMMDD') || '000000000000'
                );
            END IF;
            EXECUTE format('DROP TRIGGER IF EXISTS update_%s_updated_at ON %I', k, part_name);
            EXECUTE format(
                'CREATE TRIGGER update_%s_updated_at BEFORE UPDATE ON %I FOR EACH ROW EXECUTE FUNCTION update_updated_at_column()',
                k, part_name
            );
            d := d + 1;
        END LOOP;

        EXECUTE format(
            'INSERT INTO %I (
                id, log_time, log_ch_cd, log_io_cd, log_len, len, tr_gb, comp_gb, enct_gb, data_off, tr_code, comp_no, brodid,
                media_gb, channel_no, tr_seq, trid_sign, trid_media_gb, trid_term_no, trid_svr_no, trid_svr_seq,
                pub_ip, prt_ip, prc_brno, brno, term_no, lan_gb, prc_time, msg_code, msg_gb, compress_re, fnc_key, rec_cnt,
                exp_prc, reserve, con_gb, con_key, vlen_len, vhd_len, bmsg_len, vlen, vhd, bmsg, data, wire_ts, created_at, updated_at
            )
            SELECT
                id,
                COALESCE(
                    CASE WHEN (NULLIF(TRIM(log_time), '''')) ~ ''^[0-9]{20}$'' THEN SUBSTRING(TRIM(log_time) FROM 1 FOR 20) END,
                    CASE WHEN (NULLIF(TRIM(log_time), '''')) ~ ''^[0-9]{14}$'' THEN TRIM(log_time) || ''000000'' END,
                    CASE WHEN (NULLIF(TRIM(log_time), '''')) ~ ''^[0-9]{8}$'' THEN TRIM(log_time) || ''000000000000'' END,
                    to_char(log_timestamp, ''YYYYMMDDHH24MISS'') || ''000000'',
                    to_char(created_at, ''YYYYMMDDHH24MISS'') || ''000000''
                ) AS log_time,
                log_ch_cd, log_io_cd, log_len, len, tr_gb, comp_gb, enct_gb, data_off, tr_code, comp_no, brodid,
                media_gb, channel_no, tr_seq, trid_sign, trid_media_gb, trid_term_no, trid_svr_no, trid_svr_seq,
                pub_ip, prt_ip, prc_brno, brno, term_no, lan_gb, prc_time, msg_code, msg_gb, compress_re, fnc_key, rec_cnt,
                exp_prc, reserve, con_gb, con_key, vlen_len, vhd_len, bmsg_len, vlen, vhd, bmsg, data, wire_ts, created_at, updated_at
              FROM %I',
            k,
            k || '_legacy_ts'
        );

        EXECUTE format('DROP TABLE %I CASCADE', k || '_legacy_ts');

        EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%s_p_log_time ON %I(log_time)', k, k);
        EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%s_p_brodid ON %I(brodid)', k, k);
        EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%s_p_media_gb ON %I(media_gb)', k, k);
        EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%s_p_tr_code ON %I(tr_code)', k, k);
        EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%s_p_search ON %I(log_time, media_gb, tr_code)', k, k);
        EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%s_p_con_key ON %I(con_key)', k, k);

        RAISE NOTICE 'migrate-pb-send-recv-remove-log-timestamp-20260414: rebuilt % to RANGE(log_time) and removed log_timestamp.', k;
    END LOOP;
END $$;
