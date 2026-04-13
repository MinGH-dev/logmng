# Docker local stack (dist-based)

Requirement: `docs/requirements/20260413-docker-local-dist-multidb.md`.

Use **`docker-compose`** (standalone) or **`docker compose`** (Compose V2 plugin) — examples below use `docker-compose` for environments where the plugin is not installed.

## Prerequisites

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

3. Optional: set `POSTGRES_PASSWORD` (and `OFFLINE_ROOT`, `DIST_VERSION`) in repo-root `.env` for Compose interpolation, or export them before `docker compose`.

## Start order

1. PostgreSQL 16:

   ```bash
   docker-compose -f docker/docker-compose.yml --project-directory . up -d postgres
   ```

2. One-shot DB init (`setup.sh` → databases `logmng`, `pbfep`, `imagelog`):

   ```bash
   docker-compose -f docker/docker-compose.yml --project-directory . --profile init run --rm db-init
   ```

3. Backend + frontend (build context copies from `dist/logmng-offline-<VERSION>/`):

   ```bash
   docker-compose -f docker/docker-compose.yml --project-directory . up -d --build backend frontend
   ```

Published ports: **9200** (API), **3001** (static UI), **5432** (Postgres; override with `POSTGRES_PUBLISH_PORT` if the host port is busy).

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
cp .env.docker.example .env.docker
docker-compose -f docker/docker-compose.yml --project-directory . config
```

## Dist layout (source of truth)

| Path | Use |
|------|-----|
| `dist/logmng-offline-<VERSION>/` | Canonical tree for `COPY` in Dockerfiles |
| `dist/logmng-offline-<VERSION>/bin/backend/` | Fat JAR + `run.sh` |
| `dist/logmng-offline-<VERSION>/bin/frontend/` | Static server JAR + `www/` |
| `dist/logmng-offline-<VERSION>/db/` | `setup.sh` and SQL (mounted for `db-init`) |

JDBC URLs and env names follow `docs/contract.md` and `backend/src/main/resources/application.yml`.
