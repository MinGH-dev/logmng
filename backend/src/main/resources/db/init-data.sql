-- 초기 샘플 데이터 삽입
-- 실행 순서: department → app_user → decrypt_approver → permission_group → app_user_permission_group (FK 의존성 유지)

-- 부서 계층 (요건: 20250227-dept-hierarchy-daol-structure). parent_code NULL = 루트
-- 4-level 다올투자증권 구조: DAOL → 부문(DIV_*) → 본부(HQ_*) → 팀(TEAM_*)
-- 기존 DB에 구 코드(HQ, DEPT01 등)가 남아 있으면 dev reset 시: TRUNCATE department CASCADE 후 init-data 재실행.
INSERT INTO department (code, parent_code, name, sort_order)
VALUES
    ('DAOL', NULL, '다올투자증권', 0),
    ('DIV_SALES', 'DAOL', '영업부문', 1),
    ('DIV_RESEARCH', 'DAOL', '리서치부문', 2),
    ('HQ_SALES_A', 'DIV_SALES', '영업1본부', 3),
    ('HQ_RESEARCH', 'DIV_RESEARCH', '리서치본부', 4),
    ('TEAM_SALES_A1', 'HQ_SALES_A', '영업1팀', 5),
    ('TEAM_RESEARCH_1', 'HQ_RESEARCH', '리서치1팀', 6)
ON CONFLICT (code) DO NOTHING;

-- 외부 조직 복제 샘플 (요건: 20260407-external-dept-employee-ad-login). 프로비저닝·검색 테스트; PII 최소(가명).
-- 권한 그룹 연결보다 앞에 둠: 레거시 init-data 재실행 시 하단 UNIQUE 충돌이 있어도 ext_* 시드가 먼저 적재되도록.
INSERT INTO ext_department (source_system, external_department_id, name, parent_external_department_id, imported_at)
VALUES
    ('HR_SAMPLE', 'D-SALES-001', 'Sample Sales Division', NULL, CURRENT_TIMESTAMP),
    ('HR_SAMPLE', 'D-RD-001', 'Sample R&D Division', NULL, CURRENT_TIMESTAMP)
ON CONFLICT (source_system, external_department_id) DO NOTHING;

-- 복제 조직 부서키 → 내부 팀 코드 (프로비저닝·권한 트리 조회용)
INSERT INTO department_org_link (source_system, external_department_id, department_code)
VALUES
    ('HR_SAMPLE', 'D-SALES-001', 'TEAM_SALES_A1'),
    ('HR_SAMPLE', 'D-RD-001', 'TEAM_RESEARCH_1')
ON CONFLICT (source_system, external_department_id) DO NOTHING;

INSERT INTO ext_employee (source_system, external_employee_id, employee_number, display_name, job_title, external_department_id, is_active, imported_at, snapshot_id)
VALUES
    ('HR_SAMPLE', 'E-10001', '20261001', 'Sample Alpha', 'Developer', 'D-SALES-001', true, CURRENT_TIMESTAMP, 'poc-snap-20260408-A'),
    ('HR_SAMPLE', 'E-10002', '20261002', 'Sample Beta', 'Analyst', 'D-SALES-001', true, CURRENT_TIMESTAMP, 'poc-snap-20260408-A'),
    ('HR_SAMPLE', 'E-10003', '20261003', 'Sample Gamma', 'Manager', 'D-RD-001', true, CURRENT_TIMESTAMP, 'poc-snap-20260408-B'),
    ('HR_SAMPLE', 'E-UNPROV-1', '20261999', 'Unprovisioned User', 'Tester', 'D-RD-001', true, CURRENT_TIMESTAMP, 'poc-snap-20260408-B')
ON CONFLICT (source_system, external_employee_id) DO NOTHING;

-- 앱 사용자 (department_code는 department.code FK; 부서 삽입 후 실행)
-- 복호화 결재자 (요건: 20260224-decryption-approver-designation)
-- position: 요건 20250227-department-approver-position (팀장 지정 테스트용)
-- rank: 요건 20250227-remove-department-approver-screen-user-mgmt-improvements (직급)
-- is_system_admin: 요건 20250303-permission-group-delete-system-admin-protection (시스템 관리자 보호)
-- Dev only: password_hash에 평문 저장. 운영 환경에서는 BCrypt 등 해시 사용.
-- 테스트 비밀번호: admin=admin123, user1/user2/user3=user123
-- id: admin=20269999, 나머지 사용자=20260001부터 1씩 증가 (user1=20260001, user2=20260002, user3=20260003)
-- name: 사용자명(표시명). 요건 20260316-login-id-user-name-display. NULL이면 username으로 fallback.
INSERT INTO app_user (id, username, password_hash, role, department_code, position, rank, name, is_system_admin)
VALUES
    (20269999, 'admin', 'admin123', 'ADMIN', NULL, NULL, NULL, NULL, true),
    (20260001, 'user1', 'user123', 'USER', 'TEAM_SALES_A1', '팀장', '부장', '홍길동', false),
    (20260002, 'user2', 'user123', 'USER', 'TEAM_SALES_A1', '대리', '대리', NULL, false),
    (20260003, 'user3', 'user123', 'USER', 'TEAM_RESEARCH_1', NULL, '사원', NULL, false)
ON CONFLICT (username) DO NOTHING;
-- Sync sequence so next INSERT gets 20260004+
SELECT setval(pg_get_serial_sequence('app_user', 'id'), (SELECT COALESCE(MAX(id), 20260001) FROM app_user));

-- Ensure admin is system admin (idempotent; for re-run or migration backfill)
UPDATE app_user SET is_system_admin = true WHERE username = 'admin';

-- 기존 사용자 department_code 동기화 (TRUNCATE department CASCADE 후 재실행 시)
UPDATE app_user SET department_code = 'TEAM_SALES_A1' WHERE username IN ('user1','user2');
UPDATE app_user SET department_code = 'TEAM_RESEARCH_1' WHERE username = 'user3';

-- 기존 사용자 position 샘플 (요건 20250227-department-approver-position 테스트용)
UPDATE app_user SET position = '팀장' WHERE username = 'user1';
UPDATE app_user SET position = '대리' WHERE username = 'user2';

-- 기존 사용자 rank 샘플 (요건 20250227-remove-department-approver-screen-user-mgmt-improvements)
UPDATE app_user SET rank = '부장' WHERE username = 'user1';
UPDATE app_user SET rank = '대리' WHERE username = 'user2';
UPDATE app_user SET rank = '사원' WHERE username = 'user3';

-- 기존 사용자 name 샘플 (요건 20260316-login-id-user-name-display; 검색 이력 그리드 부서/사용자명 표시용)
UPDATE app_user SET name = '홍길동' WHERE username = 'user1';
UPDATE app_user SET name = '김철수' WHERE username = 'user2';
UPDATE app_user SET name = '이영희' WHERE username = 'user3';

-- user3 ↔ 외부 직원 E-10003 매핑 (AD·로컬 모드 app_user 해석 테스트). app_user 행 이후.
INSERT INTO app_user_external_identity (app_user_id, source_system, external_employee_id, linked_at)
SELECT u.id, 'HR_SAMPLE', 'E-10003', CURRENT_TIMESTAMP
FROM app_user u
WHERE u.username = 'user3'
  AND NOT EXISTS (SELECT 1 FROM app_user_external_identity m WHERE m.app_user_id = u.id);

-- 결재자: user1 = 전역 결재자(department_code NULL). app_user 삽입 후 실행. 재실행 시 idempotent.
-- app_user_id: Req 20260316-decrypt-approval-use-user-id-everywhere. 신규 설치 시 여기서 설정; 기존 DB는 migrate-decrypt-approval-use-user-id.sql backfill.
-- Remove stale approvers not in init-data (req 20250227-user2-approver-display-bugfix)
DELETE FROM decrypt_approver WHERE user_id != 'user1' OR (user_id = 'user1' AND department_code IS NULL);
INSERT INTO decrypt_approver (user_id, department_code, app_user_id)
SELECT 'user1', NULL, u.id
FROM app_user u
WHERE u.username = 'user1' AND NOT EXISTS (SELECT 1 FROM decrypt_approver WHERE user_id = 'user1' AND department_code IS NULL);

-- 권한 그룹 (요건: 20250227-user-permission-hierarchy-group). permission_group 먼저, 그 다음 사용자–그룹 연결.
INSERT INTO permission_group (code, name, description, sort_order)
VALUES
    ('GENERAL_USER', '일반 사용자 그룹', '일반 사용자용 기본 화면 접근 허용 (로그 검색, 이력·승인, 통계)', 0),
    ('AUDIT', '감사 권한', '감사 담당자 권한 그룹', 1),
    ('REPORT', '리포트 권한', '리포트 조회 권한', 2),
    ('ADMIN_EXT', '관리 확장', NULL, 3)
ON CONFLICT (code) DO NOTHING;

-- 권한 그룹별 접근 화면 (요건: 20250227-permission-group-screen-menu-access). GENERAL_USER 기본 화면.
-- New-install policy (req 20260318): grant pb-feplog and java-fw-imagelog instead of main for 로그 검색.
INSERT INTO permission_group_screen (permission_group_id, screen_id)
SELECT id, unnest(ARRAY['pb-feplog','java-fw-imagelog','search-history','activity-log','statistics','pending-approvals'])
FROM permission_group WHERE code = 'GENERAL_USER'
ON CONFLICT (permission_group_id, screen_id) DO NOTHING;

-- ADMIN_EXT: 사용자 관리 화면 접근 (요건: 20250303-user-management-permission-group-access). user3 테스트용.
-- PoC UM v2 클론 화면 (req 20260408-poc-user-management-v2-isolated-clone); hr-sync-poc PoC 메뉴와 병행 부여.
INSERT INTO permission_group_screen (permission_group_id, screen_id)
SELECT id, unnest(ARRAY[
    'user-management',
    'user-permission-hierarchy',
    'user-management-v2',
    'hr-sync-poc',
    'user-management-v2-poc'])
FROM permission_group WHERE code = 'ADMIN_EXT'
ON CONFLICT (permission_group_id, screen_id) DO NOTHING;

-- 승인 전용 권한 그룹 (팀장용): 로그 검색 불가, 복호화 결재 승인만 가능
-- Business rule: 팀장은 로그 조회를 할 수 없으므로, pending-approvals만 부여
-- (요건: 20260304-approve-only-permission-group)
INSERT INTO permission_group (code, name, description, sort_order)
VALUES ('APPROVE_USER', '승인 전용 (팀장)', '팀장 전용 — 로그 검색 불가, 복호화 승인만 가능', 10)
ON CONFLICT (code) DO NOTHING;

INSERT INTO permission_group_screen (permission_group_id, screen_id, approve)
SELECT id, 'pending-approvals', true
FROM permission_group WHERE code = 'APPROVE_USER'
ON CONFLICT (permission_group_id, screen_id) DO NOTHING;

-- 사용자–권한 그룹 연결: user1 → AUDIT, REPORT, GENERAL_USER; user2 → AUDIT, GENERAL_USER; user3 → GENERAL_USER, ADMIN_EXT (사용자 관리 권한).
INSERT INTO app_user_permission_group (user_id, permission_group_id)
SELECT 'user1', id FROM permission_group WHERE code = 'AUDIT'
ON CONFLICT (user_id, permission_group_id) DO NOTHING;
INSERT INTO app_user_permission_group (user_id, permission_group_id)
SELECT 'user1', id FROM permission_group WHERE code = 'REPORT'
ON CONFLICT (user_id, permission_group_id) DO NOTHING;
INSERT INTO app_user_permission_group (user_id, permission_group_id)
SELECT 'user2', id FROM permission_group WHERE code = 'AUDIT'
ON CONFLICT (user_id, permission_group_id) DO NOTHING;
INSERT INTO app_user_permission_group (user_id, permission_group_id)
SELECT 'user1', id FROM permission_group WHERE code = 'GENERAL_USER'
ON CONFLICT (user_id, permission_group_id) DO NOTHING;
INSERT INTO app_user_permission_group (user_id, permission_group_id)
SELECT 'user2', id FROM permission_group WHERE code = 'GENERAL_USER'
ON CONFLICT (user_id, permission_group_id) DO NOTHING;
INSERT INTO app_user_permission_group (user_id, permission_group_id)
SELECT 'user3', id FROM permission_group WHERE code = 'GENERAL_USER'
ON CONFLICT (user_id, permission_group_id) DO NOTHING;
INSERT INTO app_user_permission_group (user_id, permission_group_id)
SELECT 'user3', id FROM permission_group WHERE code = 'ADMIN_EXT'
ON CONFLICT (user_id, permission_group_id) DO NOTHING;

-- 검색 이력 샘플 (dev only). user_id = app_user.id (numeric); join app_user.id = search_history.user_id. Req: 20260316-search-history-user-id-query-and-naming.
-- Omit this block if search_history should be runtime-only (no seed rows). Idempotent: inserts only when table is empty.
INSERT INTO search_history (user_id, log_type, search_params, requested_at, expires_at, approval_status)
SELECT u.id, v.log_type, v.search_params, v.requested_at, v.expires_at, v.approval_status
FROM (VALUES
    ('admin'::VARCHAR(100), 'pb_send'::VARCHAR(50), '{}'::TEXT, CURRENT_TIMESTAMP - interval '2 hours', CURRENT_TIMESTAMP + interval '30 days', 'PENDING'::VARCHAR(20)),
    ('user1'::VARCHAR(100), 'pb_send'::VARCHAR(50), '{"media_code":"A"}'::TEXT, CURRENT_TIMESTAMP - interval '1 hour', CURRENT_TIMESTAMP + interval '30 days', 'APPROVED'::VARCHAR(20)),
    ('user2'::VARCHAR(100), 'pb_recv'::VARCHAR(50), '{}'::TEXT, CURRENT_TIMESTAMP - interval '30 minutes', CURRENT_TIMESTAMP + interval '30 days', 'PENDING'::VARCHAR(20))
) AS v(username, log_type, search_params, requested_at, expires_at, approval_status)
JOIN app_user u ON u.username = v.username
WHERE NOT EXISTS (SELECT 1 FROM search_history LIMIT 1);

-- PB FEP: Rows below populate the same tables the backend queries for PB FEP log search —
-- LogDbService uses unqualified SELECT ... FROM pb_send UNION ALL SELECT ... FROM pb_recv
-- (shared by legacy POST .../search and wireframe POST .../pb-fep-log-search).
-- Installers should apply init-data.sql through setup.sh so session search_path matches
-- SCHEMA_PB, aligned with app.db.schema and JDBC pool init SQL (DataSourceConfig / PgSchemaSupport).

-- 송신 로그 샘플 데이터 (와이어 컬럼; loginId 필터는 brodid 기준 — 요구사항 20260414-pb-fep-wire-schema-alignment)
INSERT INTO pb_send (
    log_time, media_gb, tr_code, brodid, pub_ip, prt_ip, log_ch_cd,
    prc_time, msg_code, data, bmsg, con_key, vhd, wire_ts
)
VALUES
    ('20251010100000000000', '01', 'SAAAA100', 'user001', '192.168.1.10', '192.168.1.10', 'PC    ',
     '100000000', '0200', 'encrypted_request_data_1', 'encrypted_response_data_1', rpad('session001', 18), 'Mozilla/5.0', '2025-10-10 10:00:00'),
    ('20251010100500000000', '02', 'SBBBB100', 'user002', '192.168.1.11', '192.168.1.11', 'MOB   ',
     '100500000', '0200', 'encrypted_request_data_2', 'encrypted_response_data_2', rpad('session002', 18), 'Mozilla/5.0', '2025-10-10 10:05:00'),
    ('20251010101000000000', '03', 'SCCCC100', 'user003', '192.168.1.12', '192.168.1.12', 'PC    ',
     '101000000', '0200', 'encrypted_request_data_3', 'encrypted_response_data_3', rpad('session003', 18), 'Mozilla/5.0', '2025-10-10 10:10:00');

-- 수신 로그 샘플 데이터
INSERT INTO pb_recv (
    log_time, media_gb, tr_code, brodid, pub_ip, prt_ip, log_ch_cd,
    prc_time, msg_code, data, bmsg, con_key, vhd, wire_ts
)
VALUES
    ('20251010100100000000', '01', 'RAAAA100', 'user001', '192.168.1.10', '192.168.1.10', 'PC    ',
     '100100000', '0200', 'encrypted_request_data_1', 'encrypted_response_data_1', rpad('session001', 18), 'Mozilla/5.0', '2025-10-10 10:01:00'),
    ('20251010100600000000', '02', 'RBBBB100', 'user002', '192.168.1.11', '192.168.1.11', 'MOB   ',
     '100600000', '0200', 'encrypted_request_data_2', 'encrypted_response_data_2', rpad('session002', 18), 'Mozilla/5.0', '2025-10-10 10:06:00'),
    ('20251010101100000000', '03', 'RCCCC100', 'user003', '192.168.1.12', '192.168.1.12', 'PC    ',
     '101100000', '0200', 'encrypted_request_data_3', 'encrypted_response_data_3', rpad('session003', 18), 'Mozilla/5.0', '2025-10-10 10:11:00');





