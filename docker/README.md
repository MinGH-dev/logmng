# Docker local stack (dist-based)

Requirement: `docs/requirements/20260413-docker-local-dist-multidb.md`.

Use **`docker-compose`** (standalone) or **`docker compose`** (Compose V2 plugin) — examples below use `docker-compose` for environments where the plugin is not installed (macOS Homebrew `docker` often has **no** `compose` subcommand; the helper script tries both).

## Agent / CI checklist (validate build + deploy)

Run from the **repository root** (`/path/to/logmng`).

1. **Host tools**: Node.js + npm (for frontend build), **Apache Maven 3.x** (`mvn` on `PATH`), Docker Engine with **`docker-compose`** or Compose V2 plugin. If `mvn` is missing: e.g. macOS `brew install maven`.
2. **Frontend deps**: `cd frontend && npm ci` (use if `npm run build` fails with missing modules or permission errors on `node_modules/.bin`).
3. **Offline bundle**: `./scripts/build-offline-bundle.sh` (or `VERSION=1.0.1 NO_TAR=1 ./scripts/build-offline-bundle.sh`). Confirm `dist/logmng-offline-<VERSION>/bin/backend/*.jar` and `.../db/` exist.
4. **Env file**: ensure `.env.docker` exists (`cp .env.docker.example .env.docker` if needed). For production-like secrets, replace placeholders; JDBC hosts inside containers must stay **`postgres`**.
5. **Compose interpolation**: use `./scripts/docker-local-manual-test.sh` **or** pass `--env-file .env.docker` on every `docker-compose` / `docker compose` invocation so `POSTGRES_PUBLISH_PORT`, `OFFLINE_ROOT`, and build args resolve consistently.
6. **Bring up**: `./scripts/docker-local-manual-test.sh up`, or `SKIP_BUNDLE_BUILD=1` when `dist/` already matches `VERSION`. Rebuild images without re-running DB init: `SKIP_DB_INIT=1 SKIP_BUNDLE_BUILD=1 ./scripts/docker-local-manual-test.sh up`.
7. **Smoke**: `./scripts/docker-local-manual-test.sh smoke` or `curl` TC-04–TC-06 per table below.
8. **Teardown**: `./scripts/docker-local-manual-test.sh down`. Reset DB data: remove the `pgdata` volume (see script output / `docker volume ls`).

## One-shot: build and run for manual testing

LDAP·브라우저 E2E 자동화는 포함하지 않습니다. 로컬에서 번들 빌드 → Postgres → DB 초기화 → 백엔드·프론트 기동까지 한 번에:

```bash
# 최초: .env.docker 없으면 .env.docker.example 이 자동 복사되고 그대로 진행(로컬 플레이스홀더). 비밀·키는 필요 시 수정.
./scripts/docker-local-manual-test.sh up
```

이미 `dist/logmng-offline-<VERSION>/` 가 있으면:

```bash
VERSION=1.0.1 SKIP_BUNDLE_BUILD=1 ./scripts/docker-local-manual-test.sh up
```

중지: `./scripts/docker-local-manual-test.sh down` — 헬스 확인: `./scripts/docker-local-manual-test.sh smoke`

**DB 초기화 재실패**: 이미 초기화된 볼륨에서 `db-init`이 다시 돌면 `setup.sh`가 실패할 수 있습니다. 그때는 Postgres 볼륨을 지우고 다시 `up` 하거나, 수동으로 `docker compose ... --profile init run --rm db-init` 만 생략하고 백엔드만 재기동하세요. 깨끗한 재시도: `./scripts/docker-local-manual-test.sh down` 후 `docker-compose --env-file .env.docker -f docker/docker-compose.yml --project-directory . down -v` (볼륨 삭제).

**시드 데이터**: compose 기본값은 `INIT_DATA_FILE=init-data-closed-network-admin-only.sql` + `CLOSED_NETWORK_MINIMAL=1` 입니다. (`app_user_permission_group` 스키마가 사용자당 한 행만 허용하므로, 리포지토리의 풀 `init-data.sql`은 동일 트랜잭션에서 실패할 수 있습니다.) 로컬 로그인 스모크는 시드의 `admin` 계정을 사용하세요.

**Host port 5432 busy**: 기본 **호스트** Postgres 포트는 **5433**(`POSTGRES_PUBLISH_PORT`)입니다. 로컬에 이미 PostgreSQL이 5432를 쓰는 경우를 피합니다. 필요 시 `.env.docker`에서 변경하세요.

## Prerequisites

### Build prerequisites (host)

- **Maven**: `mvn` must be on `PATH` for `./scripts/build-offline-bundle.sh` / `package-airgap-bin.sh`. There is no `mvnw` in this repo.
- **Node/npm**: required for the frontend production build inside `package-airgap-bin.sh`.

### Runtime prerequisites

1. Build the offline bundle so `dist/logmng-offline-<VERSION>/` exists (includes `bin/backend/*.jar`, `bin/frontend/www/`, `db/`):

   ```bash
   ./scripts/build-offline-bundle.sh
   # optional: VERSION=x.y.z NO_TAR=1
   ```

2. Environment file:

   ```bash
   cp .env.docker.example .env.docker
   ```

   Edit passwords and `ENCRYPTION_KEY`. Keep `SPRING_DATASOURCE_*` / `APP_DATASOURCE_*` JDBC hosts as **`postgres`** (Compose service name). Align `DIST_VERSION` / `OFFLINE_ROOT` with your `dist/logmng-offline-*` directory.

3. Compose variable substitution: prefer `./scripts/docker-local-manual-test.sh` (loads `.env.docker`) **or** `docker-compose --env-file .env.docker -f docker/docker-compose.yml --project-directory . …`. A repo-root `.env` file is optional; it is **not** required if you use `.env.docker` as above.

## Start order

Use the same `--env-file .env.docker` (and optional `set -a && source .env.docker && set +a`) so port and path variables match the manual-test script.

1. PostgreSQL 16:

   ```bash
   docker-compose --env-file .env.docker -f docker/docker-compose.yml --project-directory . up -d postgres
   ```

2. One-shot DB init (`setup.sh` → databases `logmng`, `pbfep`, `imagelog`):

   ```bash
   docker-compose --env-file .env.docker -f docker/docker-compose.yml --project-directory . --profile init run --rm db-init
   ```

3. Backend + frontend (build context copies from `dist/logmng-offline-<VERSION>/`):

   ```bash
   docker-compose --env-file .env.docker -f docker/docker-compose.yml --project-directory . up -d --build backend frontend
   ```

Published ports: **9200** (API), **3001** (static UI), **5433** → container 5432 for Postgres by default (override with `POSTGRES_PUBLISH_PORT` in `.env.docker`).

## Build arguments

Runtime images accept:

| Build arg       | Default   | Role                                      |
|----------------|-----------|-------------------------------------------|
| `DIST_VERSION` | `1.0.1`   | Path segment `dist/logmng-offline-<ver>` |
| `BACKEND_JAR`  | `logmng-backend-1.0.1.jar` | Fat JAR filename under `bin/backend/` |
| `STATIC_SERVER_JAR` | `logmng-static-server-1.0.0.jar` | Under `bin/frontend/` |

Override in Compose via `.env.docker` / `.env` (`DIST_VERSION`, `BACKEND_JAR`, `STATIC_SERVER_JAR`).

## TC-08: `mvn test` on Linux 9.6 + JDK 17

Project tests use **H2** by default (`application-test.yml`); a live PostgreSQL container is **not** required for the standard suite.

```bash
docker build -f docker/Dockerfile.mvn-test -t logmng-mvn-test:rocky96 .
docker run --rm -v "$PWD/backend:/work/backend" -w /work/backend logmng-mvn-test:rocky96 mvn -q test
```

Or via Compose profile:

```bash
docker-compose -f docker/docker-compose.yml --project-directory . --profile mvn-test build mvn-test
docker-compose -f docker/docker-compose.yml --project-directory . --profile mvn-test run --rm mvn-test
```

To run tests against **PostgreSQL 16** on the same Compose network (e.g. future integration tests), start `postgres`, attach the test container with `--network logmng-local-net`, and point JDBC at `postgres:5432`.

## Validate compose file

```bash
cp .env.docker.example .env.docker   # if missing
docker-compose --env-file .env.docker -f docker/docker-compose.yml --project-directory . config
```

## Dist layout (source of truth)

| Path | Use |
|------|-----|
| `dist/logmng-offline-<VERSION>/` | Canonical tree for `COPY` in Dockerfiles |
| `dist/logmng-offline-<VERSION>/bin/backend/` | Fat JAR + `run.sh` |
| `dist/logmng-offline-<VERSION>/bin/frontend/` | Static server JAR + `www/` |
| `dist/logmng-offline-<VERSION>/db/` | `setup.sh` and SQL (mounted for `db-init`) |

JDBC URLs and env names follow `docs/contract.md` and `backend/src/main/resources/application.yml`.
