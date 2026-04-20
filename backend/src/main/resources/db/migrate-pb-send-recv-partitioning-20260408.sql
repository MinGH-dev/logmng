-- PB FEP 테이블 파티셔닝 전환 (데이터 보존형)
-- 대상: pb_send, pb_recv
-- 정책:
--   * log_timestamp는 물리적으로 제거한다(적재 실패 bugfix).
--   * 일 단위 RANGE 파티션(pb_*_YYYYMMDD)만 운영하고 DEFAULT 파티션은 사용하지 않음
--   * 과거 DEFAULT 자식이 남아 있으면 데이터 이관 후 detach/drop 하여 정책 정렬
--
-- 파티션 사전 생성 창: CURRENT_DATE 기준 이전 30일 ~ 이후 7일 (양 끝 포함)
-- 컬럼: 와이어 정렬 schema_pb_fep.sql 과 동일 (요구사항 20260414-pb-fep-wire-schema-alignment).

DO $$
DECLARE
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
    k          TEXT;
    default_part_name TEXT;
    has_log_timestamp_col BOOLEAN;
BEGIN
    FOREACH k IN ARRAY ARRAY['pb_send', 'pb_recv']
    LOOP
        -- ordinary table -> partitioned parent (no DEFAULT)
        IF to_regclass(k) IS NOT NULL
           AND EXISTS (SELECT 1 FROM pg_class WHERE oid = to_regclass(k::TEXT) AND relkind = 'r')
        THEN
            EXECUTE format('ALTER TABLE %I RENAME TO %I', k, k || '_migr_tmp');

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
                'SELECT MIN(to_date(substr(log_time, 1, 8), ''YYYYMMDD'')), MAX(to_date(substr(log_time, 1, 8), ''YYYYMMDD'')) FROM %I WHERE log_time ~ ''^[0-9]{8,}$''',
                k || '_migr_tmp'
            ) INTO data_min, data_max;

            d_start := LEAST(COALESCE(data_min, win_min), win_min);
            d_end := GREATEST(COALESCE(data_max, win_max), win_max);
            d := d_start;
            WHILE d <= d_end LOOP
                part_name := format('%s_%s', k, to_char(d, 'YYYYMMDD'));
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

            EXECUTE format('INSERT INTO %I SELECT * FROM %I', k, k || '_migr_tmp');
            EXECUTE format('DROP TABLE %I', k || '_migr_tmp');
        END IF;

        -- partitioned parent housekeeping: keep daily window and remove DEFAULT partition if present
        IF to_regclass(k) IS NOT NULL
           AND EXISTS (SELECT 1 FROM pg_class WHERE oid = to_regclass(k::TEXT) AND relkind = 'p')
        THEN
            SELECT EXISTS (
                SELECT 1
                  FROM information_schema.columns
                 WHERE table_schema = current_schema()
                   AND table_name = k
                   AND column_name = 'log_timestamp'
            )
            INTO has_log_timestamp_col;

            SELECT c.relname
              INTO default_part_name
              FROM pg_inherits i
              JOIN pg_class c ON c.oid = i.inhrelid
             WHERE i.inhparent = to_regclass(k::TEXT)
               AND pg_get_expr(c.relpartbound, c.oid) = 'DEFAULT'
             LIMIT 1;

            IF default_part_name IS NOT NULL THEN
                IF has_log_timestamp_col THEN
                    EXECUTE format(
                        'SELECT MIN(log_timestamp::date), MAX(log_timestamp::date) FROM %I',
                        default_part_name
                    ) INTO data_min, data_max;
                ELSE
                    EXECUTE format(
                        'SELECT MIN(to_date(substr(log_time, 1, 8), ''YYYYMMDD'')), MAX(to_date(substr(log_time, 1, 8), ''YYYYMMDD'')) FROM %I WHERE log_time ~ ''^[0-9]{8,}$''',
                        default_part_name
                    ) INTO data_min, data_max;
                END IF;

                d_start := LEAST(COALESCE(data_min, win_min), win_min);
                d_end := GREATEST(COALESCE(data_max, win_max), win_max);
                d := d_start;
                WHILE d <= d_end LOOP
                    part_name := format('%s_%s', k, to_char(d, 'YYYYMMDD'));
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

                EXECUTE format('INSERT INTO %I SELECT * FROM %I', k, default_part_name);
                EXECUTE format('ALTER TABLE %I DETACH PARTITION %I', k, default_part_name);
                EXECUTE format('DROP TABLE %I', default_part_name);
                RAISE NOTICE 'migrate-pb-send-recv-partitioning-20260408: removed DEFAULT partition % for %.', default_part_name, k;
            END IF;

            d := win_min;
            WHILE d <= win_max LOOP
                part_name := format('%s_%s', k, to_char(d, 'YYYYMMDD'));
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

            EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%s_p_log_time ON %I(log_time)', k, k);
            EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%s_p_brodid ON %I(brodid)', k, k);
            EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%s_p_media_gb ON %I(media_gb)', k, k);
            EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%s_p_tr_code ON %I(tr_code)', k, k);
            EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%s_p_search ON %I(log_time, media_gb, tr_code)', k, k);
            EXECUTE format('CREATE INDEX IF NOT EXISTS idx_%s_p_con_key ON %I(con_key)', k, k);
        END IF;
    END LOOP;
END $$;
