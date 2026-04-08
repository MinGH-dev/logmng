-- app_user.employee_number 백필: 사용자 관리 트리(GET /api/departments/user-permission-hierarchy) 사번 표시
-- 원인: 초기 INSERT가 employee_number 컬럼을 채우지 않아 NULL인 경우가 많음.
-- 우선순위: app_user_external_identity → ext_employee.employee_number; 이후 로컬 시드 사용자명 기본값.
-- Idempotent; deleted_at IS NULL 행만 갱신.

UPDATE app_user u
SET employee_number = TRIM(BOTH FROM e.employee_number)
FROM app_user_external_identity m
JOIN ext_employee e ON e.source_system = m.source_system AND e.external_employee_id = m.external_employee_id
WHERE u.id = m.app_user_id
  AND u.deleted_at IS NULL
  AND e.employee_number IS NOT NULL
  AND TRIM(BOTH FROM e.employee_number) <> '';

UPDATE app_user SET employee_number = '20261001' WHERE username = 'user1' AND deleted_at IS NULL AND (employee_number IS NULL OR TRIM(BOTH FROM employee_number) = '');
UPDATE app_user SET employee_number = '20261002' WHERE username = 'user2' AND deleted_at IS NULL AND (employee_number IS NULL OR TRIM(BOTH FROM employee_number) = '');
UPDATE app_user SET employee_number = '20261003' WHERE username = 'user3' AND deleted_at IS NULL AND (employee_number IS NULL OR TRIM(BOTH FROM employee_number) = '');
UPDATE app_user SET employee_number = '20269999' WHERE username = 'admin' AND deleted_at IS NULL AND (employee_number IS NULL OR TRIM(BOTH FROM employee_number) = '');
