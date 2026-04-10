-- HR_SAMPLE 시드: ext_employee.employee_number 를 짧은 코드(10001 등)에서
-- 앱 사용자 ID와 동일한 8자리 숫자 문자열(2026xxxx)로 정렬.
-- 재실행 안전: 기존 값이 구 패턴일 때만 UPDATE.
-- 적용: SET search_path TO SCHEMA_SYS, SCHEMA_PB, public;

UPDATE ext_employee SET employee_number = '20261001'
WHERE source_system = 'HR_SAMPLE' AND external_employee_id = 'E-10001' AND employee_number = '10001';

UPDATE ext_employee SET employee_number = '20261002'
WHERE source_system = 'HR_SAMPLE' AND external_employee_id = 'E-10002' AND employee_number = '10002';

UPDATE ext_employee SET employee_number = '20261003'
WHERE source_system = 'HR_SAMPLE' AND external_employee_id = 'E-10003' AND employee_number = '10003';

UPDATE ext_employee SET employee_number = '20261999'
WHERE source_system = 'HR_SAMPLE' AND external_employee_id = 'E-UNPROV-1' AND employee_number = '99999';
