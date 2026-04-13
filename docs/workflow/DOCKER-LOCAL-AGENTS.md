# Docker local stack — agent-facing pitfalls

Use this with `docker/README.md` (checklist and commands are authoritative).

1. **Compose CLI**: Some hosts only have `docker-compose` (standalone), not `docker compose`. Scripts try both; manual commands should use whichever works (`docker compose version` vs `docker-compose version`).
2. **Env interpolation**: Compose file variables such as `POSTGRES_PUBLISH_PORT` and `OFFLINE_ROOT` are **not** read from `env_file` on services alone. Pass `--env-file .env.docker` from the repo root, or `set -a && source .env.docker && set +a`, or use `./scripts/docker-local-manual-test.sh`.
3. **Host port 5432**: Default published Postgres port is **5433** to avoid clashing with a local PostgreSQL on 5432. Override in `.env.docker` if needed.
4. **Offline bundle scripts**: `scripts/build-offline-bundle.sh` and `scripts/package-airgap-bin.sh` must be executable (`chmod +x`); the repo stores the executable bit in git.
5. **Maven on PATH**: There is no `mvnw`. Host `mvn` is required for bundle build; see `docker/README.md` → Build prerequisites.
6. **Frontend**: Run `npm ci` in `frontend/` if the build fails (missing modules or `Permission denied` on `node_modules/.bin`).
7. **db-init idempotency**: Re-running `db-init` on an already-initialized volume may fail. Remove the Postgres volume and re-run `up`, or skip the init profile if databases already exist.
8. **Seed file**: Default compose uses `INIT_DATA_FILE=init-data-closed-network-admin-only.sql` because `app_user_permission_group` enforces one row per `user_id`. The full `init-data.sql` can fail under that constraint; use the closed-network seed for Docker smoke (e.g. `admin` user per that file).
9. **Empty env overrides**: Do not set `APP_DECRYPTION_ENABLED=` (empty) or similar `APP_*=` placeholders in `.env.docker` — Spring Boot may bind empty strings and fail boolean properties (e.g. `CryptoUtil`). Omit the key or set an explicit `true`/`false`.
