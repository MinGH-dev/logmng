-- app_user: HR employee_number (인사정보 사번); 프로비저닝 시 ext_employee 와 동일 값
-- Idempotent; safe for legacy DBs. Non-unique index: partial UNIQUE would fail on duplicate non-null legacy rows.

ALTER TABLE app_user ADD COLUMN IF NOT EXISTS employee_number VARCHAR(100) NULL;

CREATE INDEX IF NOT EXISTS idx_app_user_employee_number ON app_user (employee_number);
