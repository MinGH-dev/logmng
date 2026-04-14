-- PB FEP 테이블 파티셔닝 전환 (데이터 보존형)
-- 대상: pb_send, pb_recv
-- 방식: 기존 테이블을 *_default로 rename 후 partitioned parent 생성 + DEFAULT partition attach
-- 주의: parent에는 PK를 두지 않음(기존 PK(id)는 default partition에 유지)
--
-- 파티션: log_timestamp 기준 **일 단위** RANGE. 이름 pb_send_YYYYMMDD / pb_recv_YYYYMMDD.
-- 사전 생성 범위: CURRENT_DATE 기준 **이전 30일 ~ 이후 7일** (양 끝 포함, 총 38일분).
-- 그 밖의 시각은 pb_*_default 로 적재(운영에서 일 파티션 추가 절차는 DB_SETUP_GUIDE.md 참고).
--
-- 컬럼: 와이어 정렬 schema_pb_fep.sql 과 동일 (요구사항 20260414-pb-fep-wire-schema-alignment).

DO $$
DECLARE
    back_days  INT := 30;
    fwd_days   INT := 7;
    d          DATE;
    part_name  TEXT;
BEGIN
    -- pb_send
    IF to_regclass('pb_send') IS NOT NULL
       AND EXISTS (SELECT 1 FROM pg_class WHERE oid = to_regclass('pb_send') AND relkind = 'r')
    THEN
        EXECUTE 'ALTER TABLE pb_send RENAME TO pb_send_default';

        EXECUTE '
            CREATE TABLE pb_send (
                id BIGINT NOT NULL DEFAULT nextval(''pb_send_id_seq''::regclass),
                log_timestamp TIMESTAMP NOT NULL,
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
            ) PARTITION BY RANGE (log_timestamp)
        ';

        EXECUTE 'ALTER TABLE pb_send ATTACH PARTITION pb_send_default DEFAULT';

        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_pb_send_p_log_timestamp ON pb_send(log_timestamp)';
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_pb_send_p_brodid ON pb_send(brodid)';
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_pb_send_p_media_gb ON pb_send(media_gb)';
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_pb_send_p_tr_code ON pb_send(tr_code)';
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_pb_send_p_search ON pb_send(log_timestamp, media_gb, tr_code)';
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_pb_send_p_con_key ON pb_send(con_key)';

        d := CURRENT_DATE - back_days;
        WHILE d <= CURRENT_DATE + fwd_days LOOP
            part_name := format('pb_send_%s', to_char(d, 'YYYYMMDD'));
            IF to_regclass(part_name) IS NULL THEN
                EXECUTE format(
                    'CREATE TABLE %I PARTITION OF pb_send FOR VALUES FROM (%L) TO (%L)',
                    part_name,
                    d::TEXT,
                    (d + 1)::TEXT
                );
            END IF;
            EXECUTE format('DROP TRIGGER IF EXISTS update_pb_send_updated_at ON %I', part_name);
            EXECUTE format(
                'CREATE TRIGGER update_pb_send_updated_at BEFORE UPDATE ON %I FOR EACH ROW EXECUTE FUNCTION update_updated_at_column()',
                part_name
            );
            d := d + 1;
        END LOOP;
    END IF;

    -- pb_recv
    IF to_regclass('pb_recv') IS NOT NULL
       AND EXISTS (SELECT 1 FROM pg_class WHERE oid = to_regclass('pb_recv') AND relkind = 'r')
    THEN
        EXECUTE 'ALTER TABLE pb_recv RENAME TO pb_recv_default';

        EXECUTE '
            CREATE TABLE pb_recv (
                id BIGINT NOT NULL DEFAULT nextval(''pb_recv_id_seq''::regclass),
                log_timestamp TIMESTAMP NOT NULL,
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
            ) PARTITION BY RANGE (log_timestamp)
        ';

        EXECUTE 'ALTER TABLE pb_recv ATTACH PARTITION pb_recv_default DEFAULT';

        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_pb_recv_p_log_timestamp ON pb_recv(log_timestamp)';
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_pb_recv_p_brodid ON pb_recv(brodid)';
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_pb_recv_p_media_gb ON pb_recv(media_gb)';
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_pb_recv_p_tr_code ON pb_recv(tr_code)';
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_pb_recv_p_search ON pb_recv(log_timestamp, media_gb, tr_code)';
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_pb_recv_p_con_key ON pb_recv(con_key)';

        d := CURRENT_DATE - back_days;
        WHILE d <= CURRENT_DATE + fwd_days LOOP
            part_name := format('pb_recv_%s', to_char(d, 'YYYYMMDD'));
            IF to_regclass(part_name) IS NULL THEN
                EXECUTE format(
                    'CREATE TABLE %I PARTITION OF pb_recv FOR VALUES FROM (%L) TO (%L)',
                    part_name,
                    d::TEXT,
                    (d + 1)::TEXT
                );
            END IF;
            EXECUTE format('DROP TRIGGER IF EXISTS update_pb_recv_updated_at ON %I', part_name);
            EXECUTE format(
                'CREATE TRIGGER update_pb_recv_updated_at BEFORE UPDATE ON %I FOR EACH ROW EXECUTE FUNCTION update_updated_at_column()',
                part_name
            );
            d := d + 1;
        END LOOP;
    END IF;
END $$;
