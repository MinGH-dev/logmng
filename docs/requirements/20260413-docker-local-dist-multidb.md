# 20260413 - Docker local runtime from dist bundle with three PostgreSQL databases

## 1. User requirement

### Requirement description

The project must support **running locally via Docker** so developers and operators can bring up the application stack without relying on ad-hoc dev builds from raw `backend/` and `frontend/` source trees. Container images and runtime artifacts must be based on the **packaged output under `dist/`** produced by the existing release pipeline (offline bundle), not on ephemeral developer builds. The stack must provision **three separate PostgreSQL databases** named **`logmng`**, **`pbfep`**, and **`imagelog`** (three distinct `DATABASE` objects on the same PostgreSQL server instance unless otherwise documented), aligned with Spring Boot multi-datasource configuration in `DataSourceConfig` and `application.yml`: primary JDBC targets **`logmng`**; **`APP_DATASOURCE_PB_URL`** targets **`pbfep`**; **`APP_DATASOURCE_IMAGELOG_URL`** targets **`imagelog`**.

A **standardized automated test execution environment** must be defined that matches **Linux 9.6** (RHEL-compatible, e.g. EL 9.6), **JDK 17**, and **PostgreSQL 16**, so that `mvn test` (or an equivalent documented command) runs in a container or CI stage that reflects this matrix. The requirement **does not** authorize adding extra OS/JDK/PostgreSQL combinations unless explicitly decided by the product owner (see **Interview / open questions**).

### User scenario

1. A developer clones the repository and wants a **repeatable local deployment** using `docker compose` (or the documented equivalent).
2. They build or obtain the **offline bundle** artifacts: run `./scripts/build-offline-bundle.sh` on an online build machine (or unpack an existing `dist/logmng-offline-*.tar.gz`), so that `dist/logmng-offline-<VERSION>/` contains `bin/backend`, `bin/frontend`, `db/`, and related bundle files per `bin/README.md` and `scripts/build-offline-bundle.sh`.
3. They start PostgreSQL 16 with **three databases** created and schema/data initialized per project DB guides, then start application containers that load **JARs and static assets from the dist bundle tree**, with environment variables matching `docs/contract.md` and `application.yml`.
4. They verify backend health, DB connectivity, and minimal UI/API smoke; separately, they run **backend unit tests** inside a **Linux 9.6 + JDK 17** environment with PostgreSQL 16 available as required by tests.

### Expected outcome

- **Dist-based Docker source of truth** is documented unambiguously: implementers and operators know whether Dockerfiles `COPY` from an **extracted** `dist/logmng-offline-<VERSION>/` directory, from a **tarball** path, or from a build-arg; and how to **populate `dist/`** before `docker compose` (e.g. run `./scripts/build-offline-bundle.sh`, optional `NO_TAR=1` for directory-only output).
- **`docker compose` (or documented stack)** brings up PostgreSQL 16 with databases **`logmng`**, **`pbfep`**, and **`imagelog`**, and applies DDL/initialization consistent with `backend/src/main/resources/db/setup.sh` / `backend/DB_SETUP_GUIDE.md` for split-PB and ImageLog separation.
- **Required environment variables** for Spring Boot in compose are listed (including `SPRING_DATASOURCE_*`, `APP_DATASOURCE_PB_*`, `APP_DATASOURCE_IMAGELOG_*`, `APP_DB_SCHEMA_*`, `CORS_ALLOWED_ORIGINS`, `ENCRYPTION_KEY`, and other keys required for non-interactive startup per `docs/contract.md`), with **example-only** values in `.env.example`-style files (no committed secrets).
- A **test runner image or CI job** is defined: **Linux 9.6**, **JDK 17**, **PostgreSQL 16** client/server as needed for `mvn test`, without silently expanding the test matrix to other OS or DB versions.
- **Interview / open questions** captures any optional matrices (browser E2E, Windows hosts, other PostgreSQL versions) for explicit user/product decisions.

### Interview / open questions (product / operator decisions)

The following items are **not** auto-selected by this requirement. Record decisions here or in linked runbooks before expanding scope:

| Topic | Question |
|-------|----------|
| Browser E2E | Should Playwright/Cypress or Browser MCP scenarios be part of the default Docker verification, or remain manual/optional? |
| Host OS | Is Docker on Windows/macOS a supported path for this compose file, or Linux-only? |
| PostgreSQL versions | Is any version other than **16** in scope for local Docker (e.g. 15 for compatibility testing)? |
| Auth / LDAP | Should local Docker default to `AUTH_LOGIN_MODE=local` only, or include optional AD/LDAP sidecar documentation? |
| Image registry | Are images built only locally, or pushed to a corporate registry (naming, tagging)? |
| Resource limits | CPU/memory limits for Postgres and app containers in dev? |

---

## 2. Design

### 2.1 Security review (optional)

- [ ] Security review performed (check if applicable)

When implementing compose and examples:

- **Secrets**: Passwords and keys must come from **environment** or **Docker secrets**, not committed files. Document alignment with `docs/security-guide.md` and `docs/contract.md`.
- **CORS**: Document that browser origin for the static UI must appear in `CORS_ALLOWED_ORIGINS` when UI and API use different hostnames/ports inside Docker.

### Technical design

#### Codebase summary

- **Packaging**: `scripts/package-airgap-bin.sh` builds backend fat JAR, frontend static assets, and static-server JAR into `bin/backend` and `bin/frontend` (or `AIRGAP_BIN_ROOT`). `scripts/build-offline-bundle.sh` assembles **`dist/logmng-offline-<VERSION>/`** including that `bin/` tree **plus** `db/` (full copy of `backend/src/main/resources/db/`), `install-offline.sh`, `README-OFFLINE.md`, and metadata; optionally produces **`dist/logmng-offline-<VERSION>.tar.gz`** (same content as the directory). See `bin/README.md`.
- **Multi-datasource**: `com.logmng.config.DataSourceConfig` creates primary, optional dedicated PB, and optional ImageLog pools when `app.datasource.pb.url` and `app.datasource.imagelog.url` differ from the primary URL. Environment variables map via `application.yml`: `APP_DATASOURCE_PB_URL`, `APP_DATASOURCE_PB_USERNAME`, `APP_DATASOURCE_PB_PASSWORD`, … and `APP_DATASOURCE_IMAGELOG_*`.
- **DB provisioning**: `backend/src/main/resources/db/setup.sh` supports **`DB_A_NAME`**, **`DB_PB_NAME`** (split PB database), **`DB_B_NAME`** (ImageLog database), and modes such as `full`, `sys_only`, `pb_only`. `backend/DB_SETUP_GUIDE.md` describes three-way split operations.

#### Problem analysis

1. There is no single documented **Docker** path that binds **dist-bundle artifacts**, **three database names** (`logmng` / `pbfep` / `imagelog`), and **contract-compliant env**, so local onboarding is inconsistent.
2. Developers may confuse **`package-airgap-bin.sh` output** (repo `bin/` only) with the **full offline tree** under `dist/logmng-offline-*` (includes `db/`, installer, docs). Docker must state which tree is authoritative for images.
3. Automated tests must be runnable on a **pinned** platform (**Linux 9.6**, **JDK 17**, **PostgreSQL 16**) without implying support for additional combinations unless explicitly approved.

#### Solution approach

Structure by scope. **Frontend application source** is not required to change for this requirement if UI is served from prebuilt static assets in the bundle; verification is smoke-level unless a separate decision adds E2E to scope.

**Backend:**

- **Must verify** that running the **fat JAR from `dist/.../bin/backend/`** with three JDBC URLs pointing to **`logmng`**, **`pbfep`**, and **`imagelog`** matches `DataSourceConfig` behavior (dedicated PB and ImageLog pools when URLs differ from primary).
- **Must** document all **Spring-related** environment variables required for compose-based startup, consistent with `backend/src/main/resources/application.yml` and `docs/contract.md`.
- **Source code changes** are in scope only if bind-mount or container startup reveals a **defect**; otherwise implementation focuses on packaging, compose, and documentation.

**DB:**

- **Must** provision **three separate PostgreSQL databases** on the compose PostgreSQL service: `logmng` (system / primary), `pbfep` (PB FEP DDL per split-PB), `imagelog` (ImageLog DDL on DB B).
- **Must align** initialization with existing **`setup.sh`** variables: e.g. `DB_A_NAME=logmng`, `DB_PB_NAME=pbfep`, `DB_B_NAME=imagelog`, same host/port for typical single-instance compose; run **`full`** / **`sys_only`** / **`pb_only`** (or equivalent documented sequence) as required by `DB_SETUP_GUIDE.md` so DDL and grants match multi-datasource runtime.
- **May** use init scripts, a one-shot migration container, or documented `docker compose run` invoking `setup.sh` from the mounted `db/` directory from the dist tree.

**Documentation:**

- **Must** add or update developer-facing docs (e.g. root `README.md`, `docs/` quick start, or dedicated `docs/docker/` page) describing: how to build `dist/`, how to start compose, env file template, ports, health URLs, and test-container usage.
- **Must** state **dist layout source of truth** (see below).

**Documentation — dist layout source of truth (mandatory statement)**

| Artifact | Role |
|----------|------|
| **`dist/logmng-offline-<VERSION>/` directory** | **Canonical deployable tree** for Docker images: use this path as the **build context subset** or `COPY` root for backend/frontend runtime. It is produced by `./scripts/build-offline-bundle.sh` (same content whether used as a directory or after extracting **`dist/logmng-offline-<VERSION>.tar.gz`**). |
| **`dist/logmng-offline-<VERSION>.tar.gz`** | **Archive** of the same directory; bit-for-bit equivalent file set to the directory for Docker purposes. |
| **`./scripts/package-airgap-bin.sh` alone** | Populates **`bin/`** at the repo root (or `AIRGAP_BIN_ROOT`); **does not** by itself produce the full offline tree (`db/`, installer). For Docker, prefer **`build-offline-bundle.sh`** so **`dist/.../bin`** and **`dist/.../db`** stay in sync with a single version label. |
| **Developer prerequisite** | Before `docker compose build` / `up`, run **`./scripts/build-offline-bundle.sh`** (set `VERSION` if needed) **or** extract an existing tarball into `dist/` so **`dist/logmng-offline-<VERSION>/`** exists. |

### Affected scopes and change targets (verification)

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|------------|-----------------------------------------------|
| Backend | Yes (verify runtime / optional fix) | Yes |
| Frontend (config UI + view screen) | No (prebuilt static assets from dist) | N/A — smoke only |
| DB | Yes (compose init + alignment with setup.sh) | Yes |
| Contract / Spec | Yes (reference / optional short cross-link) | Yes |
| Cursor tools (skills, specs) | Optional | List if db-domain narrative updated |

### Planned change file list (actual deliverables — Step 4 complete)

**(Updated by implementing agent.)**

#### Documentation

- `docs/contract.md` — Short cross-link to `.env.docker.example` and `docker/README.md` under Environment / DB table.
- `docker/README.md` — Technical runbook: `dist/` prerequisite, compose start order (`postgres` → `db-init` profile → `backend`/`frontend`), TC-08 `mvn test` image (`Dockerfile.mvn-test`), build args, validation command.

#### Docker / compose

- `docker/docker-compose.yml` — PostgreSQL 16 (`postgres:16`), services `backend` / `frontend` (build from `dist/logmng-offline-<VERSION>/` via Dockerfiles), ports **9200** / **3001**, profile `init` service `db-init` (runs `setup.sh` via `db-init-entrypoint.sh`), profile `mvn-test` for TC-08, volume `pgdata`, network `logmng-local-net`.
- `docker/Dockerfile.backend` — `eclipse-temurin:17-jre-alpine`, `COPY` fat JAR from offline bundle only (no Maven in image).
- `docker/Dockerfile.frontend` — Same base, `COPY` static-server JAR + `www/` from bundle only.
- `docker/Dockerfile.mvn-test` — `rockylinux/rockylinux:9.6`, JDK 17 + Maven for TC-08.
- `docker/db-init-entrypoint.sh` — Wrapper for non-interactive `setup.sh` with `DB_*` defaults for Compose (`postgres` host, three DB names).
- `.env.docker.example` (repo root) — Spring, `APP_DATASOURCE_*`, `APP_DB_SCHEMA_*`, `CORS_*`, `ENCRYPTION_KEY`, auth/decryption/HR PoC placeholders, `setup.sh` / bundle metadata (`DIST_VERSION`, `OFFLINE_ROOT`).

#### DB

- DB initialization: `db-init` service mounts `${OFFLINE_ROOT}/db` (offline bundle or repo `dist/.../db`) read-only and executes bundled `setup.sh` with `DB_A_NAME=logmng`, `DB_PB_NAME=pbfep`, `DB_B_NAME=imagelog`.

#### Repository hygiene

- `.gitignore` — Ignore `.env.docker` (secrets).

#### Backend (source)

- No Java source changes for this delivery.

#### Cursor tool update targets (optional)

- Not changed (db-domain skill optional per requirement).

---

## 3. Test approach

### Test case list (required)

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification |
|----|-------|------|------------------------------|-----------------|----------------|
| TC-01 | Integration | Normal | Run `./scripts/build-offline-bundle.sh`; confirm `dist/logmng-offline-<VERSION>/` contains `bin/backend/*.jar`, `bin/frontend/www/`, and `db/` mirroring bundle script output | Layout matches documented source of truth; `MANIFEST.txt` or `BUNDLE-VERSION.txt` present | Manual / script |
| TC-02 | DB | Normal | PostgreSQL 16 up; three databases `logmng`, `pbfep`, `imagelog` exist; roles can connect | `\l` shows three DBs; app role has CONNECT | Manual (`psql`) or scripted |
| TC-03 | Integration | Normal | `docker compose up` (or documented stack) with env from template; backend container running | Backend listens on published port; no startup failure from datasource pools | Manual / compose logs |
| TC-04 | Integration | Normal | `curl -s http://localhost:9200/api/health` | HTTP 200 and healthy JSON | Manual (`curl`) |
| TC-05 | Integration | Normal | `curl -s http://localhost:9200/api/db/test` | `data.connected === true` (or contract-equivalent) | Manual (`curl`) |
| TC-06 | Integration | Normal | Frontend static server responds (e.g. port 3001) | HTTP 2xx from root or documented path | Manual (`curl`) |
| TC-07 | Integration | Smoke | Open UI in browser (if in scope); login page or shell loads | Visible app shell or login | Manual / optional Browser MCP per policy |
| TC-08 | Backend | Normal | Run `mvn test` inside **Linux 9.6 + JDK 17** test container/environment with **PostgreSQL 16** available per documented procedure | Exit code 0; tests pass | CI or `docker run` per docs |
| TC-09 | Documentation | Normal | Follow only the new Docker doc from a clean clone | Developer can populate `dist/` and start stack without undisclosed steps | Peer / QA walkthrough |

### Test scenarios

#### Scenario 1: Compose up and health

1. Build offline bundle to populate `dist/logmng-offline-<VERSION>/`.
2. Copy or reference `.env.docker.example`; set passwords and keys (local dev only).
3. Run `docker compose up -d` (exact command per final doc).
4. Verify TC-04, TC-05, TC-06.

#### Scenario 2: Test container (`mvn test`)

1. Build or pull the documented **Linux 9.6 / JDK 17** test image.
2. Start or link **PostgreSQL 16** per documentation.
3. Run `mvn test` from `backend/` with the same env contract as local tests require.
4. Verify TC-08.

### Test data

- Use `setup.sh`-applied init data where applicable; document **non-interactive** env for ETL users if `full` mode requires `DB_ETL_USER` / `DB_ETL_PASSWORD` per `DB_SETUP_GUIDE.md`.

### Test environment

- **Application (Docker)**: Backend `http://localhost:9200` (or mapped host port); frontend static server `http://localhost:3001` (or mapped).
- **PostgreSQL**: Version **16**; databases **`logmng`**, **`pbfep`**, **`imagelog`**.
- **Unit/integration test runner**: **Linux 9.6**-compatible image, **JDK 17**, DB **PostgreSQL 16**.

### 3.5 Browser automation verification

- **Optional** for this requirement unless product selects browser E2E in **Interview / open questions**. If optional, TC-07 remains manual smoke.

---

## 4. Checklist

### Frontend verification

- [ ] N/A or smoke: static UI loads from dist-based deployment *(not exercised this run — no `docker compose up` without offline bundle)*

### Backend verification

- [ ] Health and DB test endpoints pass in Docker stack *(not exercised this run)*
- [ ] Logs checked for datasource pool initialization (no full JDBC URLs at INFO) *(not exercised this run)*

### Integration

- [ ] End-to-end: compose up → health → db test → UI HTTP 2xx *(not exercised this run)*

### Documentation

- [x] Requirement doc completed (see §5 when implementation done)
- [x] Docker and env template documented *(compose + `docker/README.md`, `.env.docker.example`, root README / QUICK_START present)*

---

## 5. Test results

### Test run date

- 2026-04-13 (QA verification pass — compose file validation and doc update)

### Test results

#### Summary (TC status)

| ID | Result | Notes |
|----|--------|-------|
| TC-01 | **Blocked (prerequisite)** | No `dist/logmng-offline-*` directory in workspace. Full image build / stack run requires `./scripts/build-offline-bundle.sh` or extracting a tarball per §2. Does **not** invalidate compose syntax validation. |
| TC-02 | Not executed | Requires running PostgreSQL service (e.g. `docker compose up`). |
| TC-03 | Not executed | Requires dist bundle + compose up. |
| TC-04 | Not executed | Requires backend container up. |
| TC-05 | Not executed | Requires backend container up. |
| TC-06 | Not executed | Requires frontend container up. |
| TC-07 | Not executed | Optional smoke; no browser MCP run this pass. |
| TC-08 | **Skipped** | `mvn` not available in QA environment PATH (`command not found`, exit 127). Re-run where Maven is installed or use `Dockerfile.mvn-test` per `docker/README.md`. |
| TC-09 | **Partial** | Compose + documentation artifacts reviewed; full clean-clone walkthrough not repeated in this run. |

#### Frontend

- Not run (no containers started).

#### Backend

- Not run (no containers started).

**Commands:**

| Command | Exit code | Outcome |
|---------|-----------|---------|
| `docker compose -f docker/docker-compose.yml --project-directory . config` (from repo root) | **125** | **Failed**: this environment’s `docker` CLI does not accept `compose` as a subcommand (reports `unknown shorthand flag: 'f'`). Use standalone **`docker-compose`** if the Compose V2 plugin is not installed. |
| `docker-compose -f docker/docker-compose.yml --project-directory . config` (from repo root) | **0** | **Pass**: merged config emitted; project name `logmng-local`; `postgres` uses `postgres:16`; backend/frontend build contexts and ports **9200** / **3001** present. |
| `mvn -q -DskipTests package` (in `backend/`) | **127** | **Skipped**: `mvn` not on `PATH` in this environment. |

**Outcome:**

- **Compose configuration validation: PASS** via `docker-compose … config` (exit 0).
- **TC-01 bundle layout:** not verified — `dist/logmng-offline-*` absent; treat as prerequisite before `docker compose build` / `up`.
- **Optional Maven package:** not run (Maven unavailable).
- **Runtime / health / DB / UI (TC-02–TC-07):** not executed in this pass; recommend after populating `dist/` and starting the stack per `docker/README.md`.

### Issues found and resolution

- **Compose V1 vs V2 CLI**: Document or operator may need `docker-compose` (standalone) when Docker Compose V2 plugin is not installed. No code change required for valid `docker-compose.yml`.
- **Maven on host**: Optional `mvn package` could not run locally; use documented container path for TC-08.

### Next steps

- Build or extract offline bundle so `dist/logmng-offline-<VERSION>/` exists; then run `docker compose up` / `docker-compose up` per `docker/README.md` and execute TC-02–TC-07.
- Run TC-08 in Rocky 9.6 + JDK 17 image or an environment with Maven 3.x on `PATH`.

---

## 6. Error remedy result (cause and action) — for error/bug fix requirements only

_Not applicable (feature/requirements document)._

---

**Author**: Requirements (subagent)  
**Date**: 2026-04-13  
**Status**: Verification recorded (compose config); full stack TCs deferred pending `dist/` bundle  

---

## Change-target verification (REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md)

Completed against `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md` for this requirement:

| Check | Covered |
|-------|---------|
| **Backend** — API/config/runtime | §2 Backend: Spring env, JAR from dist, optional code fix only if needed; planned list includes conditional backend paths. |
| **Frontend** — UI/source | Explicitly **not** in scope for source changes; dist static assets; §4 Frontend N/A/smoke. |
| **DB** — schema/init | §2 DB: three databases, `setup.sh` alignment, planned init/wrapper files. |
| **Contract / Spec** — env documentation | §2 Documentation + optional `docs/contract.md` cross-link; env vars enumerated. |
| **Cursor tools** — skills | Optional `.cursor/skills/db-domain/SKILL.md` if narrative update needed. |
| **Docker + dist** | §2 dist source-of-truth table; TC-01; planned Dockerfiles/compose/env example. |
| **Three DBs** | §1, §2 DB, TC-02, test environment; JDBC URLs to `logmng` / `pbfep` / `imagelog`. |
| **Test image (Linux 9.6, JDK 17, PG 16)** | §1, §2 Documentation + Dockerfile/test stage; TC-08. |
| **Pattern §2.4 (search/filter UI)** | **Does not apply** — no search/filter field alignment. |
| **Interview / open questions** | §1 subsection — no extra test matrix auto-selected. |

---

**Checklist line (for workflow automation — leave unchecked until requirement workflow complete):**

- [x] Requirement doc completed
