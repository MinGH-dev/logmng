# 20260410 - Install and deploy: three databases, split DB init, service control, env-driven logs, non-interactive .env

## 1. User requirement

### Requirement description

Production and hardened deployments must support **three physically separate PostgreSQL databases** for:

1. **logmng** (application system domain: users, permissions, search history, activity metadata, decryption stores, and related DDL under the primary “A” model in existing design).
2. **pb_fep** (PB FEP log tables such as `pb_send` / `pb_recv` and PB-specific migrations).
3. **imagelog** (Java FW Image Log tables and related access).

Development may continue to use a **single shared database** when explicitly configured that way; production must allow **distinct hosts/instances/databases** per domain without requiring a monolithic “one DB only” assumption.

**Database initialization** for **logmng (system)** and **pb_fep** must be **independently runnable**: operators can run or re-run system DDL/init and PB DDL/init on separate schedules or clusters as needed (aligned with split setup modes already sketched in DB tooling, but the **primary operator path** must be documented and scriptable).

**Deployment and runtime operations** must expose **start**, **stop**, and **status** for **backend** and **frontend** processes separately—not only a single combined command that always treats the stack as one unit. (A combined “all” command may remain as an optional convenience.)

**Application and service log file locations** must be **configurable via environment variables** (or Spring Boot–equivalent externalized properties) and **honored at runtime** (not fixed to repository-relative paths only).

**PostgreSQL initialization UX** must support a **non-interactive** path: credentials and options are supplied from a **`.env` file (or equivalent)** so a full install/DB bootstrap can complete **in one pass** without repeated password prompts per step.

**Installation mode (primary path)** is **non-interactive / non-dialog**: the operator **edits `.env` (from a template) manually**, then runs install/bootstrap scripts; **interactive prompts must not be required** for the default production-oriented flow. A **`.env.example`** (or similarly named template at a documented path) must include **detailed comments in Korean** explaining each variable (purpose, typical values, security notes where relevant).

This requirement **builds on** multi-datasource configuration described in `docs/requirements/20260320-multi-datasource-schema-configuration.md` and focuses on **operational packaging**: install scripts, deploy entrypoints, documentation, and runtime configurability for logs and credentials.

### User scenario

1. An operator copies `.env.example` to `.env`, fills PostgreSQL superuser credentials, three database names/hosts, JDBC URLs, schema names, and optional log directory variables (comments in Korean guide each field).
2. They run a **single non-interactive** install command (e.g. `install_linux.sh` with a flag or a wrapper that sources `.env`) that applies DDL/init to the correct targets without `read -p` or repeated password entry, using `PGPASSWORD` / connection URIs as appropriate.
3. They initialize **system data** on the logmng DB and **PB structures** on the pb_fep DB **independently** when operations require (e.g. new PB cluster or replay of PB-only migration), without being forced to run a single indivisible “full only” step.
4. For deploy/runtime, they use **backend** `start` / `stop` / **status** and **frontend** `start` / `stop` / **status** from the same tooling family (scripts under `scripts/`, `bin/`, or documented systemd units that wrap them).
5. **Problem:** Today, `scripts/install_linux.sh` is **menu-driven and interactive** (multiple `read` prompts including superuser password). `scripts/dev-services.sh` supports `start`/`stop`/`restart` per service but **no `status`**. Backend logging uses a fixed `logging.file.name` default (`logs/application.log`) without a documented env-first override for deploy paths. Three-DB production layout is not consistently driven by a **single .env-first** documented path.

### Expected outcome

- **Three-DB production** is achievable via **configuration only** (env / `.env`), with JDBC URLs and credentials for primary (logmng/sys), PB FEP, and ImageLog documented in `docs/contract.md` (or successor env table) and mirrored in `.env.example`.
- **Independent DB init** for logmng (system) vs pb_fep is **documented and scriptable** (e.g. explicit `SETUP_MODE` or dedicated entrypoints calling `backend/src/main/resources/db/setup.sh` with the right env), without mandating one combined run for both.
- **Deploy/runtime tooling** provides **`start` / `stop` / `status`** for **backend** and **frontend** (and documents how Linux service managers map to these).
- **Log output paths** for the Spring Boot application (and any documented companion processes) are set via **environment variables** and verified to write under the configured directory.
- **Non-interactive install** is the **primary** documented path: sourcing `.env` + one command completes DB bootstrap and optional env file generation without interactive prompts.
- **`.env.example`** exists at a documented location with **Korean comments** per variable; real `.env` remains **untracked** per `docs/security-guide.md`.

**Note:** This requirement does **not** trigger search/filter UI pattern §2.4 (forms-and-filters alignment).

## 2. Design

### 2.1 Security review (optional; when PII / decryption / access control)

This requirement makes **`.env` the canonical production install input**, which concentrates **PostgreSQL superuser credentials, application DB passwords, and JDBC-related secrets** on disk and in process environment. That is **in scope** for Security Step 2 (operational confidentiality and least privilege), even though it does not change application decryption or screen-level access control.

- [x] **Security review performed** — 2026-04-10 (Security subagent)

#### Scope

- Operational files: `.env`, optional `.pgpass`, generated env fragments, backup/archive contents that may include these files.
- Scripts: `scripts/install_linux.sh` (and any non-interactive wrapper), `backend/src/main/resources/db/setup.sh`, offline / closed-network installers that share the same contract.
- Runtime: Spring Boot log path configuration (avoid writing logs where secrets could be copied into log files via misconfiguration or verbose startup diagnostics).

#### Threats and risks

| Risk | Description |
|------|-------------|
| **Filesystem disclosure** | `.env` world-readable or readable by service accounts that do not need install secrets → local escalation or backup/artifact leak. |
| **Version control leak** | Accidental commit of `.env` or real passwords pasted into `.env.example` / docs / tickets. |
| **Shell and CI logging** | `set -x`, `bash -x`, verbose CI steps, or `env` / `printenv` dumps print secrets to logs. |
| **`PGPASSWORD` in process environment** | On Linux, environment of the install process may be visible to same-UID tooling or briefly exposed; inherited by child processes; survives until unset in that shell. |
| **Over-privileged DB users** | Runtime JDBC users granted superuser or excessive DDL → larger blast radius on app compromise. |
| **Superuser in long-lived `.env`** | Operators keep bootstrap superuser credentials in the same file as day-to-day app config → unnecessary exposure and weak rotation story. |
| **Error messages** | “Validation failed” paths that print whole lines from `.env` or interpolated connection strings can leak passwords. |

#### Security acceptance criteria

1. **`.env` permissions and ownership** — Document that production operators should use **`chmod 600`** on `.env` (owner read/write only; no group/other). Prefer the **same OS user** that runs install/bootstrap to own the file; document if a dedicated deploy user is required.
2. **Git and templates** — Real `.env` remains **untracked** (project `.gitignore`); **`.env.example` must contain placeholders only** (no real hosts/passwords). §3 TC-09 stands; optional hardening: CI grep or review rule blocking high-entropy secrets in example files.
3. **No secret echo** — Install and `setup.sh` paths must **never** print password values, `PGPASSWORD`, full JDBC URLs with credentials, or raw `.env` lines to **stdout/stderr** in success or failure paths. Missing-variable errors list **variable names only** (as already required in §2 for validation).
4. **Debug / tracing** — Default non-interactive path must **not** enable `set -x` or equivalent. Any opt-in debug mode that traces commands must be **documented as unsafe for secrets** and off by default.
5. **CI/CD** — Pipelines that source `.env` or export DB secrets must **mask or redact** those variables in job logs; avoid publishing full environment on failure.
6. **`PGPASSWORD` vs `.pgpass`** — Document tradeoffs: `PGPASSWORD` is simple but lives in the process environment; **prefer `~/.pgpass` with mode `600`** where operators can use it, or **one-shot** `env PGPASSWORD='…' psql …` in a subshell so the parent session does not retain the variable. Never log the value. Document that restricting **who can run install** and **filesystem permissions** reduces exposure.
7. **Least-privilege database roles** — **Superuser (or bootstrap-equivalent) credentials are for install/DDL only**; runtime application connections use **per-database application roles** with the minimum privileges required (no superuser for normal app JDBC). Three-DB layout implies **separate role scope per database** where policy allows.
8. **Bootstrap vs runtime secrets** — Contract / `.env.example` should **label or group** variables as **install-time only** (e.g. superuser) vs **runtime** (app JDBC users), and ops docs should recommend **rotation** and, where feasible, **removing bootstrap-only secrets** from active `.env` after successful provisioning (optional organizational policy; document the recommendation).
9. **Backend logging** — Consistent with §2: adjusting startup diagnostics must **not** log full JDBC URLs, usernames, or passwords at INFO or above; multi-datasource validation failures must not echo secret material.

#### Security checklist (implementation verification)

- [x] `.env.example` and docs state **`chmod 600`** (and `.pgpass` `600` if documented). — `.env.example` L9–11; ops docs cross-ref in QA §5.
- [x] Non-interactive install and `setup.sh` never print secrets; missing-env errors are **key names only**. — TC-05 (2026-04-10).
- [x] No default `set -x` / secret-dumping debug on the primary install path. — Code review: `set -x` only when `SETUP_BASH_XTRACE=1`.
- [ ] Offline / closed-network install scripts follow the **same** stdout/stderr and env rules as `install_linux.sh`. — Not exercised in this QA pass.
- [ ] `.env.example` distinguishes **bootstrap** vs **runtime** credentials; superuser not presented as required for normal app runtime where avoidable. — Partial (comments); formal grouping optional follow-up.
- [ ] CI documentation or pipeline notes include **log redaction** for DB-related variables when applicable.

#### Relation to `docs/security-guide.md`

The repository `docs/security-guide.md` is **frontend-oriented**; it does not replace the above. Ops content should still **avoid committing secrets** and align with project **`.gitignore`** for `.env`. If backend/ops secret-handling policy is later centralized in a dedicated doc, add a **cross-link** from this requirement’s implementation notes (Documentation agent / Release).

### Technical design

#### Codebase summary

- **Backend config:** `backend/src/main/resources/application.yml` defines `spring.datasource.*`, `app.datasource.pb.*`, `app.datasource.imagelog.*`, and `app.db.schema.*` with env overrides. Multi-datasource behavior is specified in `docs/requirements/20260320-multi-datasource-schema-configuration.md`.
- **Logging:** `logging.file.name` is currently fixed to `logs/application.log` in YAML; operators need env-driven paths for centralized logging and read-only app directories.
- **DB setup:** `backend/src/main/resources/db/setup.sh` supports `SETUP_MODE` (`full`, `sys_only`, `pb_only`), split PB database (`DB_PB_*`), and ImageLog database **B** (`DB_B_NAME`, etc.)—suitable for **independent** sys vs PB init when invoked with documented env (see script header comments).
- **Linux install helper:** `scripts/install_linux.sh` is **interactive** (menu + `prompt` / `prompt_secret` / multiple `read` calls) and invokes `setup.sh`; it can write a generated env file but does not today offer a **.env-sourced non-interactive** primary path.
- **Dev service control:** `scripts/dev-services.sh` supports `frontend|backend|db|all` with `start|stop|restart` only; **no `status`** subcommand; macOS/Homebrew-oriented for `db`.
- **Deploy bin:** `bin/backend/run.sh` runs the fat JAR with `JAVA_OPTS` / `SPRING_*` / `APP_*` but does not implement lifecycle status; `bin/frontend/run.sh` serves static UI (deploy pattern differs from `npm start`).
- **Docs context:** `docs/QUICK_START.md`, `docs/DEPLOY.md`, `backend/DB_SETUP_GUIDE.md`, and offline bundle scripts (`scripts/build-offline-bundle.sh`, `scripts/offline-bundle/install-offline.sh`, closed-network variants) describe install paths; they must be updated to describe **non-interactive** and **three-DB** flows consistently.
- **Contract:** `docs/contract.md` environment table should list new or consolidated env keys for log paths and install-time DB variables so Backend and ops docs stay aligned.

#### Problem analysis

1. **Interactive install** blocks automation and air-gapped “single command” runbooks; repeated superuser password prompts are error-prone.
2. **Three physical databases** in production must be **first-class** in templates and docs, not only discoverable via optional `read` branches in `install_linux.sh`.
3. **Service lifecycle** for deploy lacks a consistent **`status`** contract for backend/frontend across dev and deploy scripts.
4. **Log paths** tied to relative `logs/` under CWD break systemd/`WorkingDirectory` expectations unless overridable by env.
5. **Split init** exists in `setup.sh` modes but the **operator journey** (which env to set, order of runs, idempotency) must be explicit for logmng vs pb_fep.

#### Solution approach

Structure by scope for handoff.

**Architecture:**

- Treat **`.env` + non-interactive CLI** as the **canonical production install story**; keep optional interactive mode only as secondary (e.g. `INSTALL_INTERACTIVE=1` or a differently named script) if product agrees—otherwise remove interactive prompts from the primary entrypoint.
- Align **three database names** with backend property namespaces: document mapping **logmng (sys/primary A)**, **pb_fep (PB pool / `DB_PB_*` + `APP_DATASOURCE_PB_*`)**, **imagelog (`DB_B_*` + `APP_DATASOURCE_IMAGELOG_*`)** so operators do not confuse schema-only split with physical DB split.

**Scripts / bin / packaging:**

- Extend or add wrapper(s) under `scripts/` (and align `bin/` deploy README) so that:
  - Install/DB bootstrap: `set -a && source .env && set +a && ./scripts/...` runs **without** `read` when required variables are set; missing required vars exit with **clear non-zero message** (variable name listed).
  - **Backend** and **frontend**: `start`, `stop`, **`status`** (status should report running/stopped, optionally PID and port, exit code convention documented: e.g. 0 = running, 1 = stopped, 2 = misconfigured).
- Reuse patterns from `scripts/dev-services.sh` where sensible; **document** differences between **dev** (macOS/Homebrew DB) and **Linux deploy** (systemd, `bin/` JAR).
- Update **offline / closed-network** install scripts to accept the same `.env` contract where applicable (reference only in §2; implementer confirms file list).

**Backend:**

- Externalize **log file path** (and optionally logback location if introduced) via standard Spring Boot env keys, e.g. `LOGGING_FILE_NAME` / `LOG_FILE` (Spring Boot 2.7 convention) or documented `SPRING_*` mapping; ensure **default** remains backward compatible for local dev.
- Confirm no code logs full JDBC URLs or passwords when adjusting startup diagnostics (align with existing multi-datasource requirement).

**DB:**

- Document **independent** invocation sequences: e.g. logmng/sys-only run (`SETUP_MODE=sys_only` on DB A) vs pb_fep-only run (`SETUP_MODE=pb_only` with `DB_PB_*`) vs ImageLog on DB B; include **idempotency** and “safe re-run” notes in `DB_SETUP_GUIDE.md`.
- Ensure `setup.sh` env variable names used for three-DB production are **listed in `.env.example`** (Korean comments).

**Documentation:**

- Update `docs/QUICK_START.md`, `docs/DEPLOY.md`, and `backend/README.md` / `backend/DB_SETUP_GUIDE.md` with the **non-interactive** path first; reference `scripts/install_linux.sh`, `scripts/dev-services.sh`, and offline installers **as context** only—implementation updates the actual commands.
- Add **`.env.example`** at a repo-root or `backend/` path (product decision: single root template vs split); Korean comments mandatory.

**Contract:**

- Extend `docs/contract.md` **Environment · Ports** (or equivalent) with log-path env vars and install-time DB variables for three-DB layout.

**Cursor tool update targets**

- After implementation, update `.cursor/skills/db-domain/SKILL.md` (and any ops-oriented doc in skills) if the documented DB bootstrap env or modes change materially.

### Affected scopes and change targets (verification)

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|------------|-----------------------------------------------|
| Backend | Yes | Yes |
| Frontend (config UI + view screen) | No | N/A |
| DB | Yes | Yes |
| Contract / Spec | Yes | Yes |
| Cursor tools (skills, specs) | Optional | Yes (db-domain if DB bootstrap contract changes) |

### Planned change file list (expected change targets)

**(Planned at authoring. Implementing agent (Step 4) confirms or amends this list when implementation is complete.)**

#### Scripts / bin / packaging

- `scripts/install_linux.sh` (or new `scripts/install_linux_noninteractive.sh` + thin wrapper)
  - Non-interactive mode: source `.env`, validate required vars, call `setup.sh` with exported env; optional `--help` listing variables.
- `scripts/dev-services.sh`
  - Add `status` for `backend` and `frontend` (and document `db` status behavior if applicable).
- `scripts/build-offline-bundle.sh`, `scripts/offline-bundle/install-offline.sh`, `scripts/build-offline-closed-network-bundle.sh` (as applicable)
  - Align env contract and non-interactive behavior with root install story.
- `bin/README.md`, `bin/backend/MODULES.md`, `bin/backend/run.sh` (if lifecycle wrappers or documented env for logs are added)
  - Document `start`/`stop`/`status` or point to systemd unit examples.

#### Backend

- `backend/src/main/resources/application.yml`
  - Log file path via env placeholder (e.g. `${LOGGING_FILE_NAME:logs/application.log}` or Spring-documented key).
- Any `logback-spring.xml` (if present or added) — only if YAML alone is insufficient for the required log path behavior.

#### DB

- `backend/src/main/resources/db/setup.sh`
  - Verify and document three-DB + independent `SETUP_MODE` flows; adjust only if gaps block non-interactive three-DB install.

#### Documentation / template

- `.env.example` (new; path TBD by implementer—prefer repo root or `backend/.env.example`)
- `docs/QUICK_START.md`, `docs/DEPLOY.md`
- `backend/DB_SETUP_GUIDE.md`, `backend/README.md`
- `docs/contract.md` (environment table)

## 3. Test approach

### Test case list (required)

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification (unit / integration / manual) |
|----|-------|------|------------------------------|-----------------|--------------------------------------------|
| TC-01 | Integration | Normal | `.env` defines all required DB hosts, names, users, passwords, `PGPASSWORD` or superuser password var; run non-interactive install command | `setup.sh` completes without prompt; databases/schemas exist as configured | Manual or CI script (documented bash invocation) |
| TC-02 | DB | Normal | `SETUP_MODE=sys_only` on DB A with PB already provisioned elsewhere | System DDL/migrations apply to A only; PB DDL not required on same run | Manual (psql inspection) + documented log |
| TC-03 | DB | Normal | `SETUP_MODE=pb_only` with `DB_PB_NAME` set | PB database receives PB DDL/migrations; A/ImageLog steps skipped per script contract | Manual (psql inspection) |
| TC-04 | Backend | Normal | Set `LOGGING_FILE_NAME` (or documented env) to a writable absolute path; start JAR | Application log file created at configured path; default path still works when env unset | Integration (mvn spring-boot or jar) or Manual |
| TC-05 | Integration | Edge | Non-interactive install with **missing** required env var | Script exits non-zero; stderr lists missing variable name(s); no partial secret echo | Manual |
| TC-06 | Integration | Normal | `dev-services.sh backend status` / `frontend status` | When process listening on configured port: exit 0 and clear message; when not: non-zero and clear message | Manual |
| TC-07 | Integration | Normal | Deploy-style wrapper `stop` then `start` backend; `status` | Status reflects stopped then running; no orphan listeners on port | Manual |
| TC-08 | Documentation | Normal | New operator follows only `docs/QUICK_START.md` / `docs/DEPLOY.md` non-interactive section | Completes install without interactive prompts | Manual review walkthrough |
| TC-09 | Security | Normal | `.env.example` committed; `.env` in `.gitignore` | No real passwords in example; Korean comments present for each variable | Manual + grep review |

### Test scenarios

#### Scenario 1: Three-DB production bootstrap (non-interactive)

1. Prepare PostgreSQL with three empty databases (or hosts) per `.env.example`.
2. Copy `.env.example` → `.env`, fill values using Korean comments as guide.
3. Run documented non-interactive install.
4. Verify connectivity from backend to each JDBC URL (health or `DbTestController` as documented).

#### Scenario 2: Independent PB re-init

1. Assume logmng/sys DB already provisioned.
2. Run PB-only setup with `pb_only` mode and PB env vars.
3. Confirm PB tables exist on pb_fep DB and application PB search works.

### Test data

- Three PostgreSQL databases (may be one instance, three DB names) with superuser and app roles per `DB_SETUP_GUIDE.md` (implementer provides exact SQL or env-driven creation order).

### Test environment

- Linux deploy target (primary); macOS optional for `dev-services.sh` parity checks.
- Backend: `http://localhost:9200` (or contract port)
- Frontend: static server or dev port per deploy path under test

## 4. Checklist

### Frontend verification

- [x] N/A for core requirement (no UI change); confirm no accidental frontend scope — QA scope: no `frontend/src` changes in 20260410 commit set.

### Backend verification

- [x] Log path env honored; tests added/updated for TC-04 where feasible — `application.yml` uses `${LOGGING_FILE_NAME:logs/application.log}` (Spring Boot 2.7 binding); TC-04 documented as config/static verification in §5.
- [ ] No secret leakage in startup logs when validating config — Not formally audited in this pass (no failing TC).

### Integration

- [ ] Non-interactive install documented and exercised (TC-01, TC-05) — TC-05 executed; TC-01 not run (needs real PostgreSQL + filled `.env`).
- [x] Backend/frontend status commands behave per TC-06, TC-07 — TC-06 pass; TC-07 `status` only (full stop/start skipped; see §5).

### Documentation

- [x] Requirement doc completed — §5 filled 2026-04-10.
- [x] `.env.example` with Korean comments; contract and deploy docs updated — TC-09 pass.

## 5. Test results

### Test run date

- 2026-04-10 (QA verification)

### Test results

| TC / check | Result | Notes |
|------------|--------|--------|
| Backend unit tests | **Pass** | `cd backend && mvn test` — exit 0 (branch `feat/cursor-commit-on-complete`). |
| Verify (backend health) | **Pass** | `./scripts/dev-services.sh backend restart`; after ~15s `curl -s http://localhost:9200/api/health` → HTTP 200, JSON `success:true`. |
| Frontend dev (3001) | **N/A** | Dev frontend not running; not in scope for this requirement verification. |
| **TC-04** `LOGGING_FILE_NAME` | **Pass (static)** | `backend/src/main/resources/application.yml`: `logging.file.name: ${LOGGING_FILE_NAME:logs/application.log}` with comment referencing Spring Boot 2.7 / `docs/contract.md`. Runtime JAR log file path not started in this pass. |
| **TC-05** missing env → non-zero, names only | **Pass** | (1) `INSTALL_NONINTERACTIVE=1 INSTALL_ENV_FILE=/tmp/nonexistent ./scripts/install_linux.sh` → stderr lines: `SETUP_MODE`, `DB_HOST`, `DB_PORT`, `DB_USER`, `DB_PASSWORD`, `DB_NAME`, `DB_A_NAME`; exit 1. (2) `env -i PATH="$PATH" HOME="$HOME" INSTALL_NONINTERACTIVE=1 SETUP_MODE=full bash backend/src/main/resources/db/setup.sh` → stderr: `Error: non-interactive install: missing or empty required variables: DB_HOST DB_PORT DB_USER DB_PASSWORD DB_ETL_USER DB_ETL_PASSWORD`; exit 2. No password values printed. |
| **TC-06** `dev-services.sh` status | **Pass** | `./scripts/dev-services.sh backend status` → running message, exit 0. `./scripts/dev-services.sh frontend status` → stopped message, exit 1 (expected when nothing on 3001). |
| **TC-07** `bin/.../run.sh` lifecycle | **Partial** | `./bin/backend/run.sh status` → running on 9200, exit 0. `./bin/frontend/run.sh status` → stopped on 3001, exit 1. **Skipped** full `stop`/`start` cycle: deploy `run.sh stop` uses `lsof` + `kill -9` on the service port and would terminate the dev backend listening on 9200; document for operators on isolated deploy hosts. |
| **TC-09** `.env.example` + `.gitignore` | **Pass** | `.env.example` present with Korean comment blocks (e.g. header L1–18, variable sections). `.gitignore` includes `.env`, `.env.local`, `.env.*.local` (lines 34–36). |

**Commands (reference):**

```bash
cd backend && mvn test
cd .. && ./scripts/dev-services.sh backend restart && sleep 15 && curl -s -o /dev/null -w "%{http_code}\n" http://localhost:9200/api/health

INSTALL_NONINTERACTIVE=1 INSTALL_ENV_FILE=/tmp/nonexistent ./scripts/install_linux.sh

env -i PATH="$PATH" HOME="$HOME" INSTALL_NONINTERACTIVE=1 SETUP_MODE=full bash backend/src/main/resources/db/setup.sh

./scripts/dev-services.sh backend status
./scripts/dev-services.sh frontend status

./bin/backend/run.sh status
./bin/frontend/run.sh status
```

### Issues found and resolution

- None for executed cases. TC-01 / TC-02 / TC-03 / TC-08 remain manual / environment-dependent.

### Next steps

- Run TC-01–TC-03 and TC-08 against a three-DB or split-mode PostgreSQL environment when available.
- Optional: add an integration test or scripted check that starts Spring with `LOGGING_FILE_NAME` pointing at a temp file and asserts file creation (TC-04 runtime).

---

**Author**: Requirements (subagent)  
**Date**: 2026-04-10  
**Status**: QA verification recorded (partial automation; TC-01/02/03/08 outstanding)
