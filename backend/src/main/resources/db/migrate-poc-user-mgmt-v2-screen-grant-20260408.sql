-- PoC 사용자 관리 v2 · HR Sync PoC 화면 권한 (req 20260408-poc-user-management-v2-isolated-clone).
-- init-data.sql은 ADMIN_EXT에 user-management-v2-poc, hr-sync-poc를 함께 부여함. 전체 init-data를 재실행하지 않는 기존 DB용 idempotent 보정.
-- Mirrors: init-data.sql INSERT INTO permission_group_screen ... ADMIN_EXT ... unnest ARRAY including 'hr-sync-poc', 'user-management-v2-poc'.

INSERT INTO permission_group_screen (permission_group_id, screen_id)
SELECT id, unnest(ARRAY['hr-sync-poc', 'user-management-v2-poc'])
FROM permission_group WHERE code = 'ADMIN_EXT'
ON CONFLICT (permission_group_id, screen_id) DO NOTHING;
