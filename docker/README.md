# Docker local stack (dist-based)

Requirement: `docs/requirements/20260413-docker-local-dist-multidb.md`.

**Three Postgres services:** Compose runs **`postgres`** (Primary / `logmng`), **`postgres-pb`** (PB / `pbfep`), and **`postgres-imagelog`** (ImageLog / `imagelog`) on separate volumes to simulate three hosts. Default published ports: **5433**, **5434**, **5435** (override with `POSTGRES_*_PUBLISH_PORT` in `.env.docker`). `db-init` and `backend` wait until all three are healthy. JDBC inside the network uses those service names (see `.env.docker.example`).

Use **`docker-compose`** (standalone) or **`docker compose`** (Compose V2 plugin) — examples below use `docker-compose` for environments where the plugin is not installed (macOS Homebrew `docker` often has **no** `compose` subcommand; the helper script tries both).

## Dev sync (rebuild dist + Docker)

**Running containers do not auto-update when you edit source.** Backend and frontend images are built from **`dist/logmng-offline-<VERSION>/`** (JAR + static `www/`), not bind-mounted source trees. After **`frontend/` or `backend/`** changes, you must rebuild that bundle and recreate images:

```bash
./scripts/docker-dev-sync.sh
# same as:
./scripts/docker-local-manual-test.sh sync
```

**`./scripts/docker-local-manual-test.sh restart`** only rebuilds Docker images from the **existing** `dist/` tree. If you skipped a bundle rebuild, you will still run **old** JAR/static files — use **`sync`** (or `docker-dev-sync.sh`) after code changes.

Optional: `VERSION=1.0.2` (default), `NO_TAR=0` to also produce the gzip tarball (slower). See `docs/workflow/DOCKER-LOCAL-AGENTS.md` items 11–12.

## Agent / CI checklist (validate build + deploy)

Run from the **repository root** (`/path/to/logmng`).

1. **Host tools**: Node.js + npm (for frontend build), **Apache Maven 3.x** (`mvn` on `PATH`), Docker Engine with **`docker-compose`** or Compose V2 plugin. If `mvn` is missing: e.g. macOS `brew install maven`.
2. **Frontend deps**: `cd frontend && npm ci` (use if `npm run build` fails with missing modules or permission errors on `node_modules/.bin`).
3. **Offline bundle**: `./scripts/build-offline-bundle.sh` (or `VERSION=1.0.2 NO_TAR=1 ./scripts/build-offline-bundle.sh`). Confirm `dist/logmng-offline-<VERSION>/bin/backend/*.jar` and `.../db/` exist.
4. **Env file**: ensure `.env.docker` exists (`cp .env.docker.example .env.docker` if needed). For production-like secrets, replace placeholders; JDBC hosts inside containers must match Compose service names: **`postgres`**, **`postgres-pb`**, **`postgres-imagelog`**.
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
VERSION=1.0.2 SKIP_BUNDLE_BUILD=1 ./scripts/docker-local-manual-test.sh up
```

중지: `./scripts/docker-local-manual-test.sh down` — 헬스 확인: `./scripts/docker-local-manual-test.sh smoke`

**재시작(번들·이미지만 갱신, DB 유지)**: `dist/logmng-offline-<VERSION>/` 가 있을 때 `./scripts/docker-local-manual-test.sh restart` — 기본으로 db-init은 생략하고 backend·frontend만 `--build` 후 재기동합니다. Postgres를 내리지 않습니다. 스키마/시드를 다시 넣어야 하면 `RESTART_DB_INIT=1 ./scripts/docker-local-manual-test.sh restart`.

**컨테이너에서 테스트**: `./scripts/docker-local-manual-test.sh test-backend`(Compose 프로필 `mvn-test`, 이미지 `Dockerfile.mvn-test`) → `./scripts/docker-local-manual-test.sh test-frontend`(프로필 `npm-test`, `Dockerfile.npm-test`, `npm ci && npm test`). 한 번에: `./scripts/docker-local-manual-test.sh test-all`. 모두 `docker compose` / `docker-compose`로 기동하며 호스트의 `mvn`/`npm test`를 직접 호출하지 않습니다. 백엔드만 돌릴 때: `MVN_ARGS='-Dtest=LogDbServiceTest' ./scripts/docker-local-manual-test.sh test-backend`. `npm-test`는 `node_modules`를 명명 볼륨 `npm_test_node_modules`로 두어 호스트 `node_modules`와 `npm ci` 충돌(ENOTEMPTY)을 피합니다. `frontend` 외에 `scripts/`·`backend/` 소스는 읽기 전용 마운트(`verify-screen-access-consistency.js` 등).

**DB 초기화 재실패**: 이미 초기화된 볼륨에서 `db-init`이 다시 돌면 `setup.sh`가 실패할 수 있습니다. 그때는 Postgres 볼륨을 지우고 다시 `up` 하거나, 수동으로 `docker compose ... --profile init run --rm db-init` 만 생략하고 백엔드만 재기동하세요. 깨끗한 재시도: `./scripts/docker-local-manual-test.sh down` 후 `docker-compose --env-file .env.docker -f docker/docker-compose.yml --project-directory . down -v` (볼륨 삭제).

**시드 데이터**: compose 기본값은 `INIT_DATA_FILE=init-data-closed-network-admin-only.sql` + `CLOSED_NETWORK_MINIMAL=1` 입니다. (`app_user_permission_group` 스키마가 사용자당 한 행만 허용하므로, 리포지토리의 풀 `init-data.sql`은 동일 트랜잭션에서 실패할 수 있습니다.) 로컬 로그인 스모크는 시드의 `admin` 계정을 사용하세요. `CLOSED_NETWORK_MINIMAL=1`이면 대량 imagelog 샘플은 생략되지만, 기본으로 `LOAD_LOCAL_DECRYPT_TEST_DATA=1`(`.env.docker` / `docker-compose.yml`의 `db-init`)이 켜져 있어 `setup.sh` 6b 단계에서 복호화 연습용 소량 시드(`init-data-local-decrypt-test-*.sql`)가 적재됩니다. 끄려면 `.env.docker`에 `LOAD_LOCAL_DECRYPT_TEST_DATA=0`을 넣으세요. 폐쇄망 `install-offline.sh` 경로는 이 플래그를 켜지 않습니다(`setup.sh` 기본 0).

**Host port 5432 busy**: 기본 **호스트** Postgres 포트는 **5433**(`POSTGRES_PUBLISH_PORT`)입니다. 로컬에 이미 PostgreSQL이 5432를 쓰는 경우를 피합니다. 필요 시 `.env.docker`에서 변경하세요.

**로그인 시 "접근이 제한된 IP 주소"**: API는 `AuthService`가 `IpUtil`로 클라이언트 IP를 검사합니다. 호스트 브라우저→게시 포트로 들어오는 연결은 컨테이너에서 **127.0.0.1이 아니라 Docker 브리지(예: `172.17.0.1`)** 로 보일 수 있어 기본 허용 목록에 없으면 거절됩니다. `.env.docker`에 `APP_SECURITY_AUTHORIZED_IPS`를 설정하세요(`.env.docker.example` 참고 — 예: `172.*`, `192.168.*` 와일드카드). 변경 후 백엔드 컨테이너 재시작.

## Air-gapped hosts (폐쇄망)

Internet is required only on a **build/bundle machine**. The runtime stack does not call Maven/npm or public registries if images and `dist/` are already on the host.

1. **On a machine with Docker + internet** (or with base images already cached):
   - Produce `dist/logmng-offline-<VERSION>/`: `./scripts/build-offline-bundle.sh`
   - Copy `.env.docker.example` → `.env.docker` and set secrets.
   - Export images for transfer:
     ```bash
     ./scripts/docker-export-images-for-airgap.sh
     ```
     This pulls `postgres:16` and `eclipse-temurin:17-jre`, builds `backend` / `frontend` images, and writes `dist/logmng-docker-airgap-<VERSION>-YYYYMMDD.tar` (override with `OUT=...`).  
     `SKIP_PULL=1` — use only if images are already local. `SKIP_COMPOSE_BUILD=1` — save base images only (you must build app images separately before `docker save` if needed).

2. **On the air-gapped host**: copy the **repository tree** (or at least `docker/`, `dist/logmng-offline-<VERSION>/`, `.env.docker`) and the **tar** from step 1.
   ```bash
   docker load -i dist/logmng-docker-airgap-*.tar
   docker-compose --env-file .env.docker -f docker/docker-compose.yml --project-directory . up -d --pull never postgres postgres-pb postgres-imagelog
   docker-compose --env-file .env.docker -f docker/docker-compose.yml --project-directory . --profile init run --rm db-init
   docker-compose --env-file .env.docker -f docker/docker-compose.yml --project-directory . up -d --pull never --build backend frontend
   ```
   `--pull never` avoids any registry access. **First-time image build on an air-gapped host is not supported** (Dockerfiles run `apt-get`); prefer building images online and loading from the tar.

3. **Same three databases** as online compose: `logmng`, `pbfep`, `imagelog` on **three** Postgres services — see **Start order** below.

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

   Edit passwords and `ENCRYPTION_KEY`. Keep `SPRING_DATASOURCE_*` / `APP_DATASOURCE_*` JDBC hosts aligned with **three** Compose services (`postgres`, `postgres-pb`, `postgres-imagelog`). Align `DIST_VERSION` / `OFFLINE_ROOT` with your `dist/logmng-offline-*` directory.

3. Compose variable substitution: prefer `./scripts/docker-local-manual-test.sh` (loads `.env.docker`) **or** `docker-compose --env-file .env.docker -f docker/docker-compose.yml --project-directory . …`. A repo-root `.env` file is optional; it is **not** required if you use `.env.docker` as above.

## Start order

Use the same `--env-file .env.docker` (and optional `set -a && source .env.docker && set +a`) so port and path variables match the manual-test script.

1. PostgreSQL 16 (three services):

   ```bash
   docker-compose --env-file .env.docker -f docker/docker-compose.yml --project-directory . up -d postgres postgres-pb postgres-imagelog
   ```

2. One-shot DB init (`setup.sh` → databases `logmng`, `pbfep`, `imagelog`):

   ```bash
   docker-compose --env-file .env.docker -f docker/docker-compose.yml --project-directory . --profile init run --rm db-init
   ```

3. Backend + frontend (build context copies from `dist/logmng-offline-<VERSION>/`):

   ```bash
   docker-compose --env-file .env.docker -f docker/docker-compose.yml --project-directory . up -d --build backend frontend
   ```

Published ports: **9200** (API), **3001** (static UI), **5433** / **5434** / **5435** → container 5432 for each Postgres service by default (override with `POSTGRES_PUBLISH_PORT`, `POSTGRES_PB_PUBLISH_PORT`, `POSTGRES_IMAGELOG_PUBLISH_PORT` in `.env.docker`).

## Build arguments

Runtime images accept:

| Build arg       | Default   | Role                                      |
|----------------|-----------|-------------------------------------------|
| `DIST_VERSION` | `1.0.2`   | Path segment `dist/logmng-offline-<ver>` |
| `BACKEND_JAR`  | `logmng-backend-1.0.2.jar` | Fat JAR filename under `bin/backend/` |
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

To run tests against **PostgreSQL 16** on the same Compose network (e.g. future integration tests), start the Postgres services, attach the test container with `--network logmng-local-net`, and point JDBC at `postgres:5432` (and PB/ImageLog hosts if needed).

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
