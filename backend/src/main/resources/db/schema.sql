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
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    approved_by VARCHAR(100) NULL,
    approved_at TIMESTAMP NULL,
    rejected_by VARCHAR(100) NULL,
    rejected_at TIMESTAMP NULL,
    rejection_reason TEXT NULL
);

CREATE INDEX IF NOT EXISTS idx_search_history_user_id ON search_history(user_id);
CREATE INDEX IF NOT EXISTS idx_search_history_requested_at ON search_history(requested_at);
CREATE INDEX IF NOT EXISTS idx_search_history_user_requested ON search_history(user_id, requested_at DESC);

-- Approval snapshot: rows allowed for decryption per approved search_history (20260224-decryption-snapshot-final-design-en)
CREATE TABLE IF NOT EXISTS search_history_approved_row (
    search_history_id BIGINT NOT NULL REFERENCES search_history(id) ON DELETE CASCADE,
    log_type         VARCHAR(50) NOT NULL,
    row_id           VARCHAR(512) NOT NULL,
    PRIMARY KEY (search_history_id, log_type, row_id)
);
CREATE INDEX IF NOT EXISTS idx_search_history_approved_row_history ON search_history_approved_row(search_history_id);

-- 부서 마스터 (계층: code, parent_code). 요건: 20260225-department-approver-hierarchy
CREATE TABLE IF NOT EXISTS department (
    code VARCHAR(50) PRIMARY KEY,
    parent_code VARCHAR(50),
    name VARCHAR(200),
    sort_order INT DEFAULT 0,
    CONSTRAINT fk_department_parent FOREIGN KEY (parent_code) REFERENCES department(code) ON DELETE SET NULL
);
CREATE INDEX IF NOT EXISTS idx_department_parent ON department(parent_code);
CREATE INDEX IF NOT EXISTS idx_department_parent_sort ON department(parent_code, sort_order);

-- 앱 사용자 (복호화 결재자 지정 요건: 20260224-decryption-approver-designation)
-- position: 요건 20250227-department-approver-position (직책, 팀장 지정 등)
-- rank: 요건 20250227-remove-department-approver-screen-user-mgmt-improvements (직급)
CREATE TABLE IF NOT EXISTS app_user (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    department_code VARCHAR(50),
    position VARCHAR(50) NULL,
    rank VARCHAR(50) NULL,
    is_system_admin BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_app_user_role CHECK (role IN ('ADMIN', 'USER')),
    CONSTRAINT fk_app_user_department FOREIGN KEY (department_code) REFERENCES department(code) ON DELETE SET NULL
);
CREATE INDEX IF NOT EXISTS idx_app_user_username ON app_user(username);

DROP TRIGGER IF EXISTS update_app_user_updated_at ON app_user;
CREATE TRIGGER update_app_user_updated_at
    BEFORE UPDATE ON app_user
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- 복호화 결재자 지정 (user_id = app_user.username). 부서별 지정: department_code NULL = 전역 결재자 (20260225)
-- 마이그레이션: 기존 단일 PK 구조에서 확장. 신규 설치 시 아래 CREATE만 실행됨.
-- 주의: DROP TABLE로 인해 기존 decrypt_approver 데이터가 삭제됨. 이미 데이터가 있는 배포 환경에서는
-- 대안: decrypt_approver 백업 후 ALTER TABLE로 id/department_code 추가, 기존 행 backfill, partial unique 인덱스 추가 후
-- 기존 PK 제거 방식의 마이그레이션 스크립트를 별도 적용하는 것을 권장. (참고: migration-20260225-decrypt-approver-note.md)
DROP TABLE IF EXISTS decrypt_approver;
CREATE TABLE decrypt_approver (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL,
    department_code VARCHAR(50) NULL,
    CONSTRAINT fk_decrypt_approver_department FOREIGN KEY (department_code) REFERENCES department(code) ON DELETE SET NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_decrypt_approver_global ON decrypt_approver (user_id) WHERE department_code IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS idx_decrypt_approver_dept ON decrypt_approver (user_id, department_code) WHERE department_code IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_decrypt_approver_user ON decrypt_approver(user_id);
CREATE INDEX IF NOT EXISTS idx_decrypt_approver_department ON decrypt_approver(department_code);

-- 권한 그룹 (요건: 20250227-user-permission-hierarchy-group). DBA 검토 반영.
CREATE TABLE IF NOT EXISTS permission_group (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    description TEXT NULL,
    sort_order INT DEFAULT 0
);

-- 사용자–권한 그룹 1:1 (user_id = app_user.username, UNIQUE). permission_group 삭제 시 CASCADE; 역방향 조회용 인덱스.
-- req 20250304-single-permission-group-per-user: 사용자당 최대 1개 권한 그룹.
CREATE TABLE IF NOT EXISTS app_user_permission_group (
    user_id VARCHAR(100) NOT NULL,
    permission_group_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, permission_group_id),
    CONSTRAINT uq_user_permission_group_user UNIQUE (user_id),
    CONSTRAINT fk_app_user_permission_group_user FOREIGN KEY (user_id) REFERENCES app_user(username) ON DELETE CASCADE,
    CONSTRAINT fk_app_user_permission_group_group FOREIGN KEY (permission_group_id) REFERENCES permission_group(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_app_user_permission_group_group ON app_user_permission_group(permission_group_id);

-- 권한 그룹별 접근 화면 (요건: 20250227-permission-group-screen-menu-access, 20250303-activity-statistics-self-only-scope, 20250303-screen-function-checkbox-selection)
CREATE TABLE IF NOT EXISTS permission_group_screen (
    permission_group_id BIGINT NOT NULL REFERENCES permission_group(id) ON DELETE CASCADE,
    screen_id VARCHAR(50) NOT NULL,
    scope VARCHAR(10) NULL,
    read BOOLEAN NULL,
    write BOOLEAN NULL,
    approve BOOLEAN NULL,
    decrypt BOOLEAN NULL,
    PRIMARY KEY (permission_group_id, screen_id),
    CONSTRAINT chk_permission_group_screen_scope CHECK (scope IS NULL OR scope IN ('self', 'all', 'team'))
);
CREATE INDEX IF NOT EXISTS idx_permission_group_screen_screen ON permission_group_screen(screen_id);

-- 권한 부여
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO logmng;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO logmng;





