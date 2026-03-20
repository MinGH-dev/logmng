-- PB FEP 로그 스키마 (송신/수신 테이블 및 공통 트리거 함수)
-- PostgreSQL 16
-- 적용 시 세션 search_path에 SCHEMA_PB(예: logmng)가 앞에 오도록 setup.sh 또는 psql에서 설정.
-- 단일 DB 개발: 기본 public과 동일 동작.

-- 송신 로그 테이블
CREATE TABLE IF NOT EXISTS pb_send (
    id BIGSERIAL PRIMARY KEY,
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
);

-- 수신 로그 테이블
CREATE TABLE IF NOT EXISTS pb_recv (
    id BIGSERIAL PRIMARY KEY,
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
);

-- 인덱스 생성
CREATE INDEX IF NOT EXISTS idx_pb_send_timestamp ON pb_send(log_timestamp);
CREATE INDEX IF NOT EXISTS idx_pb_send_media_code ON pb_send(media_code);
CREATE INDEX IF NOT EXISTS idx_pb_send_tr_code ON pb_send(tr_code);
CREATE INDEX IF NOT EXISTS idx_pb_send_user_id ON pb_send(user_id);
CREATE INDEX IF NOT EXISTS idx_pb_send_session_id ON pb_send(session_id);

CREATE INDEX IF NOT EXISTS idx_pb_recv_timestamp ON pb_recv(log_timestamp);
CREATE INDEX IF NOT EXISTS idx_pb_recv_media_code ON pb_recv(media_code);
CREATE INDEX IF NOT EXISTS idx_pb_recv_tr_code ON pb_recv(tr_code);
CREATE INDEX IF NOT EXISTS idx_pb_recv_user_id ON pb_recv(user_id);
CREATE INDEX IF NOT EXISTS idx_pb_recv_session_id ON pb_recv(session_id);

-- 복합 인덱스 (검색 성능 향상)
CREATE INDEX IF NOT EXISTS idx_pb_send_search ON pb_send(log_timestamp, media_code, tr_code);
CREATE INDEX IF NOT EXISTS idx_pb_recv_search ON pb_recv(log_timestamp, media_code, tr_code);

-- 업데이트 시간 자동 갱신 함수 (시스템 테이블·schema_user_activity_log에서도 사용)
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- 트리거 생성
DROP TRIGGER IF EXISTS update_pb_send_updated_at ON pb_send;
CREATE TRIGGER update_pb_send_updated_at
    BEFORE UPDATE ON pb_send
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

DROP TRIGGER IF EXISTS update_pb_recv_updated_at ON pb_recv;
CREATE TRIGGER update_pb_recv_updated_at
    BEFORE UPDATE ON pb_recv
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
