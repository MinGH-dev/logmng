-- Set app_user.id: admin = 20269999, other users = 20260001, 20260002, ... (ordered by username).
-- Run after schema/init-data. Idempotent for re-run; syncs sequence to avoid future PK conflicts.

-- 1) Non-admin users: assign 20260001, 20260002, ... in username order
WITH ordered AS (
    SELECT id, username, ROW_NUMBER() OVER (ORDER BY username) AS rn
    FROM app_user
    WHERE username != 'admin'
),
new_ids AS (
    SELECT id, 20260000 + rn AS new_id
    FROM ordered
)
UPDATE app_user u
SET id = n.new_id
FROM new_ids n
WHERE u.id = n.id;

-- 2) Admin: id = 20269999
UPDATE app_user SET id = 20269999 WHERE username = 'admin';

-- 3) Sync sequence so next INSERT gets a valid id (max(id) + 1)
SELECT setval(
    pg_get_serial_sequence('app_user', 'id'),
    (SELECT COALESCE(MAX(id), 20260001) FROM app_user)
);
