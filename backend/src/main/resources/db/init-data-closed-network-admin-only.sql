-- Closed-network / offline minimal seed for a **fresh** install.
--
-- Intended use: run full `setup.sh` through step 4h (DDL + migrations), then set step 5 inputs:
--   INIT_DATA_FILE=init-data-closed-network-admin-only.sql
--   CLOSED_NETWORK_MINIMAL=1
-- Example from this directory:
--   INIT_DATA_FILE=init-data-closed-network-admin-only.sql CLOSED_NETWORK_MINIMAL=1 ./setup.sh
-- If the bundle calls `db/setup.sh` or `install-offline.sh db`, export the same variables there
-- (passthrough from `scripts/offline-bundle/install-offline.sh` may need a follow-up if not wired).
--
-- Single `app_user`: admin, id 20269999, plaintext `password_hash` (dev style — same as `init-data.sql`).
-- No departments, no ext_*, no decrypt_approver, no search_history, no pb_send/pb_recv rows.
--
-- Permission model: `schema_sys` enforces one permission group row per user (`uq_user_permission_group_user`).
-- Admin is assigned **ADMIN_EXT** only; **GENERAL_USER** screen grants are duplicated onto **ADMIN_EXT**
-- via `permission_group_screen` so effective access matches GENERAL_USER + ADMIN_EXT (as in `init-data.sql`).

-- ---------------------------------------------------------------------------
-- permission_group (subset: GENERAL_USER + ADMIN_EXT only)
-- ---------------------------------------------------------------------------
INSERT INTO permission_group (code, name, description, sort_order)
VALUES
    ('GENERAL_USER', '일반 사용자 그룹', '일반 사용자용 기본 화면 접근 허용 (로그 검색, 이력·승인, 통계)', 0),
    ('ADMIN_EXT', '관리 확장', NULL, 3)
ON CONFLICT (code) DO NOTHING;

-- GENERAL_USER screens (same list as init-data.sql)
INSERT INTO permission_group_screen (permission_group_id, screen_id)
SELECT id, unnest(ARRAY['pb-feplog','java-fw-imagelog','search-history','activity-log','statistics','pending-approvals'])
FROM permission_group WHERE code = 'GENERAL_USER'
ON CONFLICT (permission_group_id, screen_id) DO NOTHING;

-- ADMIN_EXT screens (same list as init-data.sql; migrate-poc-user-mgmt-v2-screen-grant runs after setup step 5)
INSERT INTO permission_group_screen (permission_group_id, screen_id)
SELECT id, unnest(ARRAY[
    'user-management',
    'user-permission-hierarchy',
    'user-management-v2',
    'hr-sync-poc',
    'user-management-v2-poc'])
FROM permission_group WHERE code = 'ADMIN_EXT'
ON CONFLICT (permission_group_id, screen_id) DO NOTHING;

-- Merge GENERAL_USER screens onto ADMIN_EXT so one group membership yields both capability sets.
INSERT INTO permission_group_screen (permission_group_id, screen_id)
SELECT g_admin.id, pgs.screen_id
FROM permission_group g_general
JOIN permission_group g_admin ON g_admin.code = 'ADMIN_EXT'
JOIN permission_group_screen pgs ON pgs.permission_group_id = g_general.id
WHERE g_general.code = 'GENERAL_USER'
ON CONFLICT (permission_group_id, screen_id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- app_user (exactly one row for this mode)
-- Dev only: password_hash 평문. 운영에서는 BCrypt 등 사용.
-- ---------------------------------------------------------------------------
INSERT INTO app_user (id, username, password_hash, role, department_code, position, rank, name, is_system_admin, employee_number)
VALUES
    (20269999, 'admin', 'admin123', 'ADMIN', NULL, NULL, NULL, NULL, true, '20269999')
ON CONFLICT (username) DO NOTHING;

UPDATE app_user SET is_system_admin = true WHERE username = 'admin';

SELECT setval(
    pg_get_serial_sequence('app_user', 'id'),
    (SELECT COALESCE(MAX(id), 20260001) FROM app_user)
);

-- One group per user (schema): link admin → ADMIN_EXT only (screens already merged above).
INSERT INTO app_user_permission_group (user_id, permission_group_id)
SELECT 'admin', id FROM permission_group WHERE code = 'ADMIN_EXT'
ON CONFLICT (user_id, permission_group_id) DO NOTHING;
