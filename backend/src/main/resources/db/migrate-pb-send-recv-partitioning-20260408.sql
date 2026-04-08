-- PB FEP 테이블 파티셔닝 전환 (데이터 보존형)
-- 대상: pb_send, pb_recv
-- 방식: 기존 테이블을 *_default로 rename 후 partitioned parent 생성 + DEFAULT partition attach
-- 주의: parent에는 PK를 두지 않음(기존 PK(id)는 default partition에 유지)

DO $$
DECLARE
    month_start DATE := date_trunc('month', CURRENT_DATE)::DATE;
    prev_month  DATE := (date_trunc('month', CURRENT_DATE) - INTERVAL '1 month')::DATE;
    next_month  DATE := (date_trunc('month', CURRENT_DATE) + INTERVAL '1 month')::DATE;
    next2_month DATE := (date_trunc('month', CURRENT_DATE) + INTERVAL '2 month')::DATE;
    part_name TEXT;
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
                media_code VARCHAR(10) NOT NULL,
                tr_code VARCHAR(20) NOT NULL,
                user_id VARCHAR(50),
                ip_address VARCHAR(45),
                user_agent TEXT,
                request_data TEXT,
                response_data TEXT,
                status_code INTEGER,
                response_time INTEGER,
                error_message TEXT,
                session_id VARCHAR(100),
                device_type VARCHAR(20),
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            ) PARTITION BY RANGE (log_timestamp)
        ';

        EXECUTE 'ALTER TABLE pb_send ATTACH PARTITION pb_send_default DEFAULT';

        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_pb_send_p_log_timestamp ON pb_send(log_timestamp)';
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_pb_send_p_media_code ON pb_send(media_code)';
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_pb_send_p_tr_code ON pb_send(tr_code)';
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_pb_send_p_user_id ON pb_send(user_id)';
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_pb_send_p_session_id ON pb_send(session_id)';
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_pb_send_p_search ON pb_send(log_timestamp, media_code, tr_code)';

        FOREACH part_name IN ARRAY ARRAY[
            format('pb_send_%s', to_char(prev_month, 'YYYYMM')),
            format('pb_send_%s', to_char(month_start, 'YYYYMM')),
            format('pb_send_%s', to_char(next_month, 'YYYYMM'))
        ]
        LOOP
            IF to_regclass(part_name) IS NULL THEN
                IF part_name = format('pb_send_%s', to_char(prev_month, 'YYYYMM')) THEN
                    EXECUTE format(
                        'CREATE TABLE %I PARTITION OF pb_send FOR VALUES FROM (%L) TO (%L)',
                        part_name, prev_month::TEXT, month_start::TEXT
                    );
                ELSIF part_name = format('pb_send_%s', to_char(month_start, 'YYYYMM')) THEN
                    EXECUTE format(
                        'CREATE TABLE %I PARTITION OF pb_send FOR VALUES FROM (%L) TO (%L)',
                        part_name, month_start::TEXT, next_month::TEXT
                    );
                ELSE
                    EXECUTE format(
                        'CREATE TABLE %I PARTITION OF pb_send FOR VALUES FROM (%L) TO (%L)',
                        part_name, next_month::TEXT, next2_month::TEXT
                    );
                END IF;
            END IF;
            EXECUTE format('DROP TRIGGER IF EXISTS update_pb_send_updated_at ON %I', part_name);
            EXECUTE format(
                'CREATE TRIGGER update_pb_send_updated_at BEFORE UPDATE ON %I FOR EACH ROW EXECUTE FUNCTION update_updated_at_column()',
                part_name
            );
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
                media_code VARCHAR(10) NOT NULL,
                tr_code VARCHAR(20) NOT NULL,
                user_id VARCHAR(50),
                ip_address VARCHAR(45),
                user_agent TEXT,
                request_data TEXT,
                response_data TEXT,
                status_code INTEGER,
                response_time INTEGER,
                error_message TEXT,
                session_id VARCHAR(100),
                device_type VARCHAR(20),
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            ) PARTITION BY RANGE (log_timestamp)
        ';

        EXECUTE 'ALTER TABLE pb_recv ATTACH PARTITION pb_recv_default DEFAULT';

        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_pb_recv_p_log_timestamp ON pb_recv(log_timestamp)';
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_pb_recv_p_media_code ON pb_recv(media_code)';
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_pb_recv_p_tr_code ON pb_recv(tr_code)';
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_pb_recv_p_user_id ON pb_recv(user_id)';
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_pb_recv_p_session_id ON pb_recv(session_id)';
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_pb_recv_p_search ON pb_recv(log_timestamp, media_code, tr_code)';

        FOREACH part_name IN ARRAY ARRAY[
            format('pb_recv_%s', to_char(prev_month, 'YYYYMM')),
            format('pb_recv_%s', to_char(month_start, 'YYYYMM')),
            format('pb_recv_%s', to_char(next_month, 'YYYYMM'))
        ]
        LOOP
            IF to_regclass(part_name) IS NULL THEN
                IF part_name = format('pb_recv_%s', to_char(prev_month, 'YYYYMM')) THEN
                    EXECUTE format(
                        'CREATE TABLE %I PARTITION OF pb_recv FOR VALUES FROM (%L) TO (%L)',
                        part_name, prev_month::TEXT, month_start::TEXT
                    );
                ELSIF part_name = format('pb_recv_%s', to_char(month_start, 'YYYYMM')) THEN
                    EXECUTE format(
                        'CREATE TABLE %I PARTITION OF pb_recv FOR VALUES FROM (%L) TO (%L)',
                        part_name, month_start::TEXT, next_month::TEXT
                    );
                ELSE
                    EXECUTE format(
                        'CREATE TABLE %I PARTITION OF pb_recv FOR VALUES FROM (%L) TO (%L)',
                        part_name, next_month::TEXT, next2_month::TEXT
                    );
                END IF;
            END IF;
            EXECUTE format('DROP TRIGGER IF EXISTS update_pb_recv_updated_at ON %I', part_name);
            EXECUTE format(
                'CREATE TRIGGER update_pb_recv_updated_at BEFORE UPDATE ON %I FOR EACH ROW EXECUTE FUNCTION update_updated_at_column()',
                part_name
            );
        END LOOP;
    END IF;
END $$;
