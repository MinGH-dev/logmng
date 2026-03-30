-- 사용자 활동 이력 테이블
-- PostgreSQL 16

-- 사용자 활동 이력 테이블 생성
CREATE TABLE IF NOT EXISTS user_activity_log (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL,
    username VARCHAR(100),
    -- Longest canonical code in specs/activity-action-types.spec.yaml §2: UNASSIGN_USER_FROM_PERMISSION_GROUP (35). OP-04 20260330: VARCHAR(50) sufficient; extend via migration if future codes exceed 50.
    action_type VARCHAR(50) NOT NULL,
    -- JSON payload (e.g. permissionGroupAuditV1 in specs/activity-permission-group-audit.spec.yaml).
    -- TEXT: no PostgreSQL length limit for audit-sized documents; full before/after allowedScreens[]
    -- (allowed screen list ~12 items per specs/permission-group-hierarchy.spec.yaml §4.1) stays well below practical limits.
    -- Use JSONB + migration only if Backend requires server-side JSON operators or expression indexes on this column.
    action_detail TEXT,
    ip_address VARCHAR(45),            -- IPv6 지원
    user_agent TEXT,
    request_method VARCHAR(10),         -- GET, POST, PUT, DELETE
    request_path VARCHAR(500),
    request_params TEXT,                -- JSON 형태
    response_status INTEGER,            -- HTTP 상태 코드
    response_time_ms INTEGER,           -- 응답 시간 (밀리초)
    success BOOLEAN DEFAULT true,
    error_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 인덱스 생성
CREATE INDEX IF NOT EXISTS idx_user_activity_log_user_id ON user_activity_log(user_id);
CREATE INDEX IF NOT EXISTS idx_user_activity_log_action_type ON user_activity_log(action_type);
CREATE INDEX IF NOT EXISTS idx_user_activity_log_created_at ON user_activity_log(created_at);
CREATE INDEX IF NOT EXISTS idx_user_activity_log_ip_address ON user_activity_log(ip_address);
CREATE INDEX IF NOT EXISTS idx_user_activity_log_user_action_date ON user_activity_log(user_id, action_type, created_at);

-- 복합 인덱스 (검색 성능 향상)
CREATE INDEX IF NOT EXISTS idx_user_activity_log_search ON user_activity_log(created_at, user_id, action_type);

-- 업데이트 시간 자동 갱신 트리거
DROP TRIGGER IF EXISTS update_user_activity_log_updated_at ON user_activity_log;
CREATE TRIGGER update_user_activity_log_updated_at
    BEFORE UPDATE ON user_activity_log
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- 권한 부여
GRANT ALL PRIVILEGES ON TABLE user_activity_log TO logmng;
GRANT ALL PRIVILEGES ON SEQUENCE user_activity_log_id_seq TO logmng;





