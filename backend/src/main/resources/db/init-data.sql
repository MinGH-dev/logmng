-- 초기 샘플 데이터 삽입
-- 실행 순서: department → app_user → decrypt_approver → permission_group → app_user_permission_group (FK 의존성 유지)

-- 부서 계층 (요건: 20260225-department-approver-hierarchy). parent_code NULL = 루트
INSERT INTO department (code, parent_code, name, sort_order)
VALUES
    ('HQ', NULL, '본부', 0),
    ('DEPT01', 'HQ', '팀1', 1),
    ('DEPT02', 'HQ', '팀2', 2)
ON CONFLICT (code) DO NOTHING;

-- 앱 사용자 (department_code는 department.code FK; 부서 삽입 후 실행)
-- 복호화 결재자 (요건: 20260224-decryption-approver-designation)
-- Dev only: password_hash에 평문 저장. 운영 환경에서는 BCrypt 등 해시 사용.
-- 테스트 비밀번호: admin=admin123, user1/user2=user123
INSERT INTO app_user (username, password_hash, role, department_code)
VALUES
    ('admin', 'admin123', 'ADMIN', NULL),
    ('user1', 'user123', 'USER', 'DEPT01'),
    ('user2', 'user123', 'USER', 'DEPT01')
ON CONFLICT (username) DO NOTHING;

-- 결재자: user1 = 전역 결재자(department_code NULL). app_user 삽입 후 실행. 재실행 시 idempotent.
INSERT INTO decrypt_approver (user_id, department_code)
SELECT 'user1', NULL
WHERE NOT EXISTS (SELECT 1 FROM decrypt_approver WHERE user_id = 'user1' AND department_code IS NULL);

-- 권한 그룹 (요건: 20250227-user-permission-hierarchy-group). permission_group 먼저, 그 다음 사용자–그룹 연결.
INSERT INTO permission_group (code, name, description, sort_order)
VALUES
    ('GENERAL_USER', '일반 사용자 그룹', '일반 사용자용 기본 화면 접근 허용 (로그 검색, 이력·승인, 통계)', 0),
    ('AUDIT', '감사 권한', '감사 담당자 권한 그룹', 1),
    ('REPORT', '리포트 권한', '리포트 조회 권한', 2),
    ('ADMIN_EXT', '관리 확장', NULL, 3)
ON CONFLICT (code) DO NOTHING;

-- 권한 그룹별 접근 화면 (요건: 20250227-permission-group-screen-menu-access). GENERAL_USER 기본 화면.
INSERT INTO permission_group_screen (permission_group_id, screen_id)
SELECT id, unnest(ARRAY['main','search-history','activity-log','statistics','pending-approvals'])
FROM permission_group WHERE code = 'GENERAL_USER'
ON CONFLICT (permission_group_id, screen_id) DO NOTHING;

-- 사용자–권한 그룹 연결: user1 → AUDIT, REPORT, GENERAL_USER; user2 → AUDIT, GENERAL_USER (기존 app_user username 기준).
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

-- 송신 로그 샘플 데이터
INSERT INTO pb_send (log_timestamp, media_code, tr_code, user_id, ip_address, user_agent, request_data, response_data, status_code, response_time, session_id, device_type)
VALUES
    ('2025-10-10 10:00:00', 'A', 'SAAAA100', 'user001', '192.168.1.100', 'Mozilla/5.0', 'encrypted_request_data_1', 'encrypted_response_data_1', 200, 150, 'session001', 'PC'),
    ('2025-10-10 10:05:00', 'B', 'SBBBB100', 'user002', '192.168.1.101', 'Mozilla/5.0', 'encrypted_request_data_2', 'encrypted_response_data_2', 200, 120, 'session002', 'Mobile'),
    ('2025-10-10 10:10:00', 'C', 'SCCCC100', 'user003', '192.168.1.102', 'Mozilla/5.0', 'encrypted_request_data_3', 'encrypted_response_data_3', 200, 180, 'session003', 'PC');

-- 수신 로그 샘플 데이터
INSERT INTO pb_recv (log_timestamp, media_code, tr_code, user_id, ip_address, user_agent, request_data, response_data, status_code, response_time, session_id, device_type)
VALUES
    ('2025-10-10 10:01:00', 'A', 'RAAAA100', 'user001', '192.168.1.100', 'Mozilla/5.0', 'encrypted_request_data_1', 'encrypted_response_data_1', 200, 100, 'session001', 'PC'),
    ('2025-10-10 10:06:00', 'B', 'RBBBB100', 'user002', '192.168.1.101', 'Mozilla/5.0', 'encrypted_request_data_2', 'encrypted_response_data_2', 200, 110, 'session002', 'Mobile'),
    ('2025-10-10 10:11:00', 'C', 'RCCCC100', 'user003', '192.168.1.102', 'Mozilla/5.0', 'encrypted_request_data_3', 'encrypted_response_data_3', 200, 130, 'session003', 'PC');





