# 20250227-user-permission-hierarchy-group-bugfix-1 — DB schema and init-data not applied

**Parent requirement ID**: `20250227-user-permission-hierarchy-group`  
**Bugfix sequence**: 1

## 1. Discovery

- **When**: During QA verification (restart and health/behavior check) after build and restart were reported done.
- **What failed**:
  - **GET /api/departments/user-permission-hierarchy** (admin session) → 500 Internal Server Error. Backend log: `relation "department" does not exist`.
  - **GET /api/permission-groups** (admin session) → 500 Internal Server Error. Backend log: `relation "permission_group" does not exist`.
  - **DB setup**: Running `backend/src/main/resources/db/setup.sh` failed with `FATAL: role "postgres" does not exist`.

## 2. Error scope

- **Failure scope**: **db**
- **Layer**: db
- **Symptom**: New tables (`department`, `permission_group`, `app_user_permission_group`) and seed data from schema.sql and init-data.sql are not present in the target database.
- **Impact**: All new APIs for user-permission hierarchy and permission group management return 500. §3 test cases TC-01–TC-09 cannot be completed; browser verification (step 3.5) skipped until APIs succeed.

## 3. Cause (estimated)

- Schema and init-data have not been applied to the database used by the backend (localhost:5432/logmng).
- `setup.sh` uses `psql -U postgres`; on this environment the role "postgres" does not exist (e.g. Homebrew PostgreSQL default user may be the OS user), so the script fails and schema/init-data are never run.

## 4. Action

- **DB subagent (or responsible expert)**:
  1. Apply `backend/src/main/resources/db/schema.sql` and `backend/src/main/resources/db/init-data.sql` to the target database (logmng on localhost:5432) using a DB user that has sufficient privileges (e.g. superuser or the `logmng` user if it can create tables). Use `psql -U <user> -h localhost -p 5432 -d logmng -f schema.sql` (and similarly for init-data.sql), or equivalent.
  2. Optionally: update `setup.sh` or document an alternative (e.g. use `$USER` or configurable DB_SUPERUSER) for environments where the "postgres" role does not exist, so that future runs can apply schema/init-data without manual steps.
- After schema and init-data are applied, **restart backend** (or confirm backend is restarted) and hand off to **QA** for re-verification.

**Done (main agent):** `setup.sh` updated to use `DB_SUPERUSER` (default `postgres`). Run with `DB_SUPERUSER=$USER ./setup.sh` when the "postgres" role does not exist. Comment at top documents schema/init-data-only apply.

## 5. Verification

- **Re-verification date**: 2026-02-27 (QA, after user confirmed schema/init-data applied and backend restarted).
- **Health check**: Pass (backend 9200 → 200, frontend 3001 → 200, DB test → `connected: true`).
- **§3 test cases**: TC-07 (non-admin 403) **Pass**. TC-01, TC-02, TC-08 **Fail** (GET user-permission-hierarchy and GET permission-groups return 500 with admin session). TC-03–TC-06, TC-09 blocked.
- **Action items**: Schema/init-data applied per user confirmation; `setup.sh` updated (DB_SUPERUSER). **Outcome**: APIs still return 500 for admin hierarchy and permission-groups; existing GET /api/departments?format=tree also 500 → indicates `department` (and likely `permission_group`) tables are not present in the database the backend connects to.
- **Resolved**: Setup script improvement (DB_SUPERUSER) — done. **Not resolved**: Verification did not pass; backend either (1) uses a different DB than the one schema was applied to, or (2) was not restarted after apply, or (3) another runtime issue. **Failure scope** for handoff: **db** (confirm schema applied to correct DB and backend restarted) or **backend** (if DB confirmed applied).
- **Commit**: Not performed (verification failed). QA will re-run verification after responsible expert closes the issue.

### 5.2 Re-verification (bugfix-1 resolved) — 2026-02-27

- **Context**: Main agent applied schema.sql and init-data.sql to localhost:5432/logmng (using `$USER`), restarted backend. Smoke test: hierarchy and permission-groups → 200.
- **Health check**: Pass (backend 9200, frontend 3001, DB connected).
- **§3 test cases**: TC-01–TC-09 **all Pass** (API-level verification).
- **Browser (step 3.5)**: cursor-ide-browser; app loads (title "로그 관리 시스템"); API verification covers critical path.
- **Resolution**: Schema and init-data applied to correct DB; backend restarted. All APIs return 200 for admin. **Bugfix-1 closed.** QA commit performed.

### 5.1 사용자 확인용: 백엔드가 쓰는 DB에 테이블 있는지 확인

백엔드 설정: `application.yml` → `jdbc:postgresql://localhost:5432/logmng`, 사용자 `logmng`.

**1) 같은 DB에 접속해 테이블 목록 확인**
```bash
# PostgreSQL 슈퍼유저(또는 logmng)로 접속
psql -U "$USER" -h localhost -p 5432 -d logmng -c "\dt"
# 또는
psql -U logmng -h localhost -p 5432 -d logmng -c "\dt"
```
`department`, `permission_group`, `app_user_permission_group`가 보여야 함. 없으면 아래 2) 실행.

**2) 스키마·init-data를 이 DB에 적용**
```bash
DIR=/Volumes/T7/dev/logmng_frontend/dev/backend/src/main/resources/db
# 슈퍼유저로 테이블 생성 (logmng 사용자로는 CREATE 권한이 없을 수 있음)
psql -U "$USER" -h localhost -p 5432 -d logmng -f "$DIR/schema.sql"
psql -U "$USER" -h localhost -p 5432 -d logmng -f "$DIR/schema_user_activity_log.sql"
psql -U "$USER" -h localhost -p 5432 -d logmng -f "$DIR/init-data.sql"
```
적용 후 백엔드 재시작 → QA 재검증 요청.
