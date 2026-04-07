-- 조직 복제 부서키 → department.code 매핑 (프로비저닝 시 앱 사용자 부서 정합).
-- 신규 설치: schema_sys.sql에 동일 객체 포함(본 파일 재실행 시 no-op).
-- 적용: SET search_path TO SCHEMA_SYS, SCHEMA_PB, public;

CREATE TABLE IF NOT EXISTS department_org_link (
    source_system           VARCHAR(64) NOT NULL,
    external_department_id  VARCHAR(256) NOT NULL,
    department_code         VARCHAR(50) NOT NULL,
    CONSTRAINT pk_department_org_link PRIMARY KEY (source_system, external_department_id),
    CONSTRAINT fk_department_org_link_department FOREIGN KEY (department_code) REFERENCES department(code) ON DELETE RESTRICT
);
CREATE INDEX IF NOT EXISTS idx_department_org_link_department ON department_org_link (department_code);
