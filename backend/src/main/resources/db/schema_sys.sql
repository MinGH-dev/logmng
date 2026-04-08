-- 애플리케이션 시스템 스키마 (검색 이력, 사용자, 권한 등)
-- PostgreSQL 16
-- 적용 시 search_path는 SCHEMA_SYS, SCHEMA_PB, public 순이어야 합니다.
--
-- 공통 updated_at 트리거 함수: schema_pb_fep.sql에도 동일 정의가 있음. SETUP_MODE=sys_only 등으로 PB DDL이
-- 생략되거나 schema_sys만 단독 적용될 때 트리거가 실패하지 않도록 여기서 CREATE OR REPLACE로 보장함.
-- (search_path의 첫 스키마에 생성·갱신; SYS·PB가 둘 다 public이면 PB와 동일 객체 1개로 합쳐짐.)
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- DDL 순서(참조 무결성):
--   department → ext_department, ext_employee (조직 복제; 앱 FK 없음) → department_org_link (복제 부서키 → department.code) → permission_group → app_user → app_user_external_identity
--   → search_history(FK는 나중에) → search_history_approved_row
--   → DO 블록으로 search_history FK 부착 → decrypt_approver → user_decryption_allowed → screen_display_label
--   → app_user_permission_group → permission_group_screen → 마지막에 트리거만 부착
-- 권한(GRANT)은 setup.sh에서 스키마별로 수행합니다.

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

-- 조직 복제 테이블 (ETL/레플리카). 앱 런타임 역할은 SELECT-only (setup.sh 그랜트). 요건: 20260407-external-dept-employee-ad-login
-- 자연키: (source_system, external_department_id) / (source_system, external_employee_id). app_user와 FK 없음 — app_user_external_identity로 논리 연결.
CREATE TABLE IF NOT EXISTS ext_department (
    id                      BIGSERIAL PRIMARY KEY,
    source_system           VARCHAR(64) NOT NULL,
    external_department_id  VARCHAR(256) NOT NULL,
    name                    VARCHAR(500) NULL,
    parent_external_department_id VARCHAR(256) NULL,
    imported_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ext_department_source_ext UNIQUE (source_system, external_department_id)
);
CREATE INDEX IF NOT EXISTS idx_ext_department_name ON ext_department (source_system, name);

CREATE TABLE IF NOT EXISTS ext_employee (
    id                      BIGSERIAL PRIMARY KEY,
    source_system           VARCHAR(64) NOT NULL,
    external_employee_id    VARCHAR(256) NOT NULL,
    employee_number         VARCHAR(100) NULL,
    display_name            VARCHAR(500) NULL,
    job_title               VARCHAR(200) NULL,
    external_department_id  VARCHAR(256) NULL,
    email                   VARCHAR(320) NULL,
    is_active               BOOLEAN NOT NULL DEFAULT true,
    imported_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- HR Sync PoC snapshot grouping (req 20260408-hr-sync-poc-snapshot-list-and-sample-data). Nullable for legacy rows.
    snapshot_id             VARCHAR(128) NULL,
    CONSTRAINT uq_ext_employee_source_ext UNIQUE (source_system, external_employee_id)
);
CREATE INDEX IF NOT EXISTS idx_ext_employee_source_empnum ON ext_employee (source_system, employee_number);
CREATE INDEX IF NOT EXISTS idx_ext_employee_display_name ON ext_employee (display_name);
CREATE INDEX IF NOT EXISTS idx_ext_employee_source_extdept ON ext_employee (source_system, external_department_id);
CREATE INDEX IF NOT EXISTS idx_ext_employee_source_snapshot ON ext_employee (source_system, snapshot_id);

-- 복제 조직의 부서 식별자 → 내부 부서 코드(department.code) 매핑. 프로비저닝 시 department_code 보강용.
CREATE TABLE IF NOT EXISTS department_org_link (
    source_system           VARCHAR(64) NOT NULL,
    external_department_id  VARCHAR(256) NOT NULL,
    department_code         VARCHAR(50) NOT NULL,
    CONSTRAINT pk_department_org_link PRIMARY KEY (source_system, external_department_id),
    CONSTRAINT fk_department_org_link_department FOREIGN KEY (department_code) REFERENCES department(code) ON DELETE RESTRICT
);
CREATE INDEX IF NOT EXISTS idx_department_org_link_department ON department_org_link (department_code);

-- 권한 그룹 (요건: 20250227-user-permission-hierarchy-group). app_user보다 먼저 — app_user_permission_group이 참조
CREATE TABLE IF NOT EXISTS permission_group (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    description TEXT NULL,
    sort_order INT DEFAULT 0
);

-- 앱 사용자 (복호화 결재자 지정 요건: 20260224-decryption-approver-designation)
-- position: 요건 20250227-department-approver-position (직책, 팀장 지정 등)
-- rank: 요건 20250227-remove-department-approver-screen-user-mgmt-improvements (직급)
-- API/UI canonical user ID = app_user.id (numeric); req 20260316-user-id-numeric-userid-naming.
CREATE TABLE IF NOT EXISTS app_user (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    -- 인사정보 사번; 프로비저닝 시 ext_employee.employee_number 와 동일 값
    employee_number VARCHAR(100) NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    department_code VARCHAR(50),
    position VARCHAR(50) NULL,
    rank VARCHAR(50) NULL,
    name VARCHAR(200) NULL,
    is_system_admin BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    -- Soft delete: NULL = active; non-NULL = user soft-deleted at that instant (UTC). DBA rec. req 20260407 user-management.
    deleted_at TIMESTAMPTZ NULL,
    CONSTRAINT chk_app_user_role CHECK (role IN ('ADMIN', 'USER')),
    CONSTRAINT fk_app_user_department FOREIGN KEY (department_code) REFERENCES department(code) ON DELETE SET NULL
);
CREATE INDEX IF NOT EXISTS idx_app_user_username ON app_user(username);
CREATE INDEX IF NOT EXISTS idx_app_user_employee_number ON app_user (employee_number);

-- 프로비저닝/AD 로그인용 외부 직원 자연키 → app_user 매핑 (복제 테이블 volatile 시 FK 회피). 요건: 20260407-external-dept-employee-ad-login
CREATE TABLE IF NOT EXISTS app_user_external_identity (
    id                      BIGSERIAL PRIMARY KEY,
    app_user_id             BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    source_system           VARCHAR(64) NOT NULL,
    external_employee_id    VARCHAR(256) NOT NULL,
    linked_at               TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_app_user_external_identity_natural UNIQUE (source_system, external_employee_id),
    CONSTRAINT uq_app_user_external_identity_app_user UNIQUE (app_user_id)
);
CREATE INDEX IF NOT EXISTS idx_app_user_external_identity_app_user ON app_user_external_identity (app_user_id);

-- 검색 이력 (복호화 승인 부가 기능)
-- user_id: requester's user id (numeric). app_user.id = search_history.user_id. Do not store username. Req: 20260316-search-history-user-id-query-and-naming.
-- approved_by_user_id: approver's app_user.id (numeric). Req: 20260316-decrypt-approval-use-user-id-everywhere. FK는 아래 DO 블록에서 부착.
-- search_result_total_count / decryption_target_count: snapshot at request time (nullable); existing DBs: migrate-search-history-result-counts.sql.
CREATE TABLE IF NOT EXISTS search_history (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    log_type VARCHAR(50) NOT NULL,
    search_params TEXT NOT NULL,
    requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    approval_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    approved_by VARCHAR(100) NULL,
    approved_by_user_id BIGINT NULL,
    approved_at TIMESTAMP NULL,
    rejected_by VARCHAR(100) NULL,
    rejected_at TIMESTAMP NULL,
    rejection_reason TEXT NULL,
    request_reason TEXT NULL,
    search_result_total_count INTEGER NULL,
    decryption_target_count INTEGER NULL
);

CREATE INDEX IF NOT EXISTS idx_search_history_user_id ON search_history(user_id);
CREATE INDEX IF NOT EXISTS idx_search_history_requested_at ON search_history(requested_at);
CREATE INDEX IF NOT EXISTS idx_search_history_user_requested ON search_history(user_id, requested_at DESC);

-- Approval snapshot: rows allowed for decryption per approved search_history (20260224-decryption-snapshot-final-design-en)
CREATE TABLE IF NOT EXISTS search_history_approved_row (
    search_history_id BIGINT NOT NULL REFERENCES search_history(id) ON DELETE CASCADE,
    log_type         VARCHAR(50) NOT NULL,
    row_id           VARCHAR(512) NOT NULL,
    row_status       VARCHAR(256) NOT NULL DEFAULT '',
    PRIMARY KEY (search_history_id, log_type, row_id, row_status)
);
CREATE INDEX IF NOT EXISTS idx_search_history_approved_row_history ON search_history_approved_row(search_history_id);

-- search_history → app_user FK (신규 설치). 기존 DB·VARCHAR user_id 등은 migrate-search-history-user-id-to-bigint.sql 등 사용.
-- approved_by_user_id FK: migrate-decrypt-approval-use-user-id.sql 와 동일 제약명.
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = current_schema()::text AND table_name = 'search_history' AND column_name = 'user_id' AND data_type = 'bigint') THEN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_search_history_app_user') THEN
      ALTER TABLE search_history ADD CONSTRAINT fk_search_history_app_user FOREIGN KEY (user_id) REFERENCES app_user(id);
    END IF;
  END IF;
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = current_schema()::text AND table_name = 'search_history' AND column_name = 'approved_by_user_id') THEN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_search_history_approved_by_app_user') THEN
      ALTER TABLE search_history ADD CONSTRAINT fk_search_history_approved_by_app_user FOREIGN KEY (approved_by_user_id) REFERENCES app_user(id);
    END IF;
  END IF;
END $$;

-- 복호화 결재자 지정. user_id = app_user.username (legacy); app_user_id = app_user.id (canonical). 부서별 지정: department_code NULL = 전역 결재자 (20260225).
-- Req 20260316-decrypt-approval-use-user-id-everywhere: permission checks use app_user_id; user_id kept for backward compat. Backfill: migrate-decrypt-approval-use-user-id.sql.
-- 마이그레이션: 기존 단일 PK 구조에서 확장. 신규 설치 시 아래 CREATE만 실행됨.
-- 주의: DROP TABLE로 인해 기존 decrypt_approver 데이터가 삭제됨. 이미 데이터가 있는 배포 환경에서는
-- 대안: decrypt_approver 백업 후 ALTER TABLE로 id/department_code 추가, 기존 행 backfill, partial unique 인덱스 추가 후
-- 기존 PK 제거 방식의 마이그레이션 스크립트를 별도 적용하는 것을 권장. (참고: migration-20260225-decrypt-approver-note.md)
-- 신규 설치 시 NOTICE 방지: 없는 테이블에 대해 DROP IF EXISTS 하지 않고, 있을 때만 DROP.
DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM pg_catalog.pg_class c
    JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = current_schema()::name
      AND c.relname = 'decrypt_approver'
      AND c.relkind IN ('r', 'p')
  ) THEN
    EXECUTE format('DROP TABLE %I.decrypt_approver', current_schema());
  END IF;
END $$;
CREATE TABLE decrypt_approver (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(100) NOT NULL,
    app_user_id BIGINT NULL REFERENCES app_user(id),
    department_code VARCHAR(50) NULL,
    CONSTRAINT fk_decrypt_approver_department FOREIGN KEY (department_code) REFERENCES department(code) ON DELETE SET NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_decrypt_approver_global ON decrypt_approver (user_id) WHERE department_code IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS idx_decrypt_approver_dept ON decrypt_approver (user_id, department_code) WHERE department_code IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_decrypt_approver_user ON decrypt_approver(user_id);
CREATE INDEX IF NOT EXISTS idx_decrypt_approver_app_user ON decrypt_approver(app_user_id);
CREATE INDEX IF NOT EXISTS idx_decrypt_approver_department ON decrypt_approver(department_code);

-- 복호화 허용 저장소: 사용자·화면별 허용 GUID와 유효기간. 권한 판단만 사용; search_history_approved_row는 감사용 유지.
-- Req: docs/requirements/20260318-decryption-allowed-store-and-decrypt-ui.md. §2.1: user_id, screen, guid, valid_until only.
CREATE TABLE IF NOT EXISTS user_decryption_allowed (
    user_id     BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    screen      VARCHAR(50) NOT NULL,
    guid        VARCHAR(512) NOT NULL,
    row_status  VARCHAR(256) NOT NULL DEFAULT '',
    valid_until TIMESTAMP NOT NULL,
    PRIMARY KEY (user_id, screen, guid, row_status)
);
CREATE INDEX IF NOT EXISTS idx_user_decryption_allowed_get ON user_decryption_allowed(user_id, screen, valid_until);
CREATE INDEX IF NOT EXISTS idx_user_decryption_allowed_cleanup ON user_decryption_allowed(user_id, valid_until);

-- 화면/메뉴 표시 라벨 (관리자 설정). screen_id = currentView / permission screen_id (kebab-case). API·스펙: specs/menu-display-labels.spec.yaml.
-- Req: docs/requirements/20260406-menu-display-names-admin.md (20260406-menu-display-names-admin).
-- Sidebar parent/order: docs/requirements/20260407-screen-menu-parent-order.md — parent_group_id / sort_order (app allowlist & validation).
CREATE TABLE IF NOT EXISTS screen_display_label (
    screen_id        VARCHAR(128) PRIMARY KEY,
    label_user       VARCHAR(256) NOT NULL,
    label_admin      VARCHAR(256) NULL,
    parent_group_id  VARCHAR(64) NULL,
    sort_order       INT NULL,
    updated_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by       BIGINT NULL REFERENCES app_user(id) ON DELETE SET NULL
);
CREATE OR REPLACE TRIGGER update_screen_display_label_updated_at
    BEFORE UPDATE ON screen_display_label
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- 사용자–권한 그룹 1:1 (user_id = app_user.username, UNIQUE). permission_group 삭제 시 CASCADE; 역방향 조회용 인덱스.
-- req 20250304-single-permission-group-per-user: 사용자당 최대 1개 권한 그룹.
-- Legacy DBs may have user_id = app_user.id::text; migrate-app-user-permission-group-user-id-to-username-20260407.sql normalizes to username (FK).
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

-- 트리거: app_user.updated_at (함수는 본 파일 상단 + schema_pb_fep.sql 에서 동일 시그니처로 보장).
-- CREATE OR REPLACE: 신규 설치 시 DROP TRIGGER IF EXISTS 에 따른 NOTICE 없음 (PostgreSQL 14+).
CREATE OR REPLACE TRIGGER update_app_user_updated_at
    BEFORE UPDATE ON app_user
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
