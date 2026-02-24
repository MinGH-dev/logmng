-- 로그 관리 시스템 데이터베이스 스키마
-- PostgreSQL 16

-- 데이터베이스 생성 (수동 실행 필요)
-- CREATE DATABASE logmng;
-- \c logmng;

-- 사용자 생성 (수동 실행 필요)
-- CREATE USER logmng WITH PASSWORD 'logmng123';
-- GRANT ALL PRIVILEGES ON DATABASE logmng TO logmng;

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

-- 업데이트 시간 자동 갱신 함수
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

-- 검색 이력 (복호화 승인 부가 기능)
CREATE TABLE IF NOT EXISTS search_history (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL,
    log_type VARCHAR(50) NOT NULL,
    search_params TEXT NOT NULL,
    requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    approval_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_search_history_user_id ON search_history(user_id);
CREATE INDEX IF NOT EXISTS idx_search_history_requested_at ON search_history(requested_at);
CREATE INDEX IF NOT EXISTS idx_search_history_user_requested ON search_history(user_id, requested_at DESC);

-- 권한 부여
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO logmng;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO logmng;





