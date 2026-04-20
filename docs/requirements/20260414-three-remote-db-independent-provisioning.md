# 20260414 - Three remote hosts: independent DB provisioning (split Primary vs ImageLog cluster)

## 1. User requirement

### Requirement description

Operations must be able to **install, reinstall, or provision** each of the three PostgreSQL-backed domains **independently** when they run on **three different remote hosts**:

1. **Primary (logmng / system “A”)** — system schema, user activity, search history, decryption-related tables, etc.
2. **PB FEP (“pb_fep” / split-PB)** — `pb_send` / `pb_recv` and PB-only migrations when `DB_PB_NAME` is distinct from `DB_A_NAME`.
3. **ImageLog (“B”)** — `imagelog` / Java FW ImageLog DDL and ImageLog-only migrations and optional seeds.

Runtime already supports **three JDBC URLs** via `SPRING_DATASOURCE_*`, `APP_DATASOURCE_PB_*`, and `APP_DATASOURCE_IMAGELOG_*` (see `docs/contract.md`). **Provisioning tooling** must catch up so that **ImageLog on a different host than Primary** is a first-class, documented, and scriptable case—today `backend/src/main/resources/db/setup.sh` uses a single `psql_admin` target (`DB_HOST` / `DB_PORT` / `DB_SUPERUSER`) for **both** `DB_A_NAME` and `DB_B_NAME`, which matches **same-cluster A+B** but does **not** fully support **remote ImageLog only** (separate host/port/superuser), unlike split-PB which already has `DB_PB_HOST`, `DB_PB_PORT`, `DB_PB_SUPERUSER`.

This requirement **extends** multi-datasource and non-interactive install themes in `docs/requirements/20260320-multi-datasource-schema-configuration.md` and `docs/requirements/20260410-install-deploy-three-db-noninteractive.md`, and focuses on **install-time cluster split for DB B** and **SETUP_MODE** coverage for **independent** runs.

**Note:** Numeric JDBC and schema values remain governed by `docs/contract.md` and `backend/DB_SETUP_GUIDE.md`; this document defines **provisioning behavior and env contract**, not application UI.

### User scenario

1. An operator has three remote PostgreSQL instances (e.g. logmng-sys, pbfep, imagelog) on **different** host:port endpoints.
2. They need to **re-run or bootstrap only Primary** (system DDL and migrations that belong to A) **without** touching the ImageLog host, and vice versa **provision only ImageLog** on the ImageLog host.
3. They already use or plan to use **`SETUP_MODE=pb_only`** with **`DB_PB_*`** for PB-only provisioning on the PB host.
4. They run installs **non-interactively** (`.env` + `INSTALL_NONINTERACTIVE=1` / `SETUP_NONINTERACTIVE=1`) and expect **validation** to fail fast with **variable names only** on stderr when required values are missing—consistent with existing `setup.sh` rules.
5. **Problem:** ImageLog cannot be provisioned on a **different** host than Primary using the current `setup.sh` connection model for B; documentation (`docs/contract.md`, `backend/DB_SETUP_GUIDE.md`) still states that B is created via the same Primary cluster client. **`check-db.sh`** also assumes ImageLog is reachable via `DB_HOST`/`DB_PORT` only.

### Expected outcome

- **New install-time variables** for the ImageLog cluster when it is split from Primary, **mirroring the split-PB pattern** (exact names listed in §2): e.g. dedicated host/port/superuser for B-side DDL so operators do not misuse Primary `psql` for a remote B database.
- **New or extended `SETUP_MODE` values** (or an explicitly documented combination of modes) so operators can run **Primary-only** (system path on A **without** B steps), **ImageLog-only** (B path on the ImageLog host), retain **`pb_only`**, and understand how **`full`** / **`sys_only`** behave when **Primary, PB, and ImageLog** use **different** hosts (single orchestrated run vs. ordered separate runs—§2 must specify).
- **Non-interactive validation** rules updated for the new modes and variables (`INSTALL_NONINTERACTIVE` / `SETUP_NONINTERACTIVE`) so CI and air-gapped runbooks fail deterministically without secret leakage.
- **Documentation and contract alignment:** `backend/DB_SETUP_GUIDE.md`, **`docs/contract.md`** (install / bootstrap tables—remove or qualify “same host·port for B” where split is supported), optional **`check-db.sh`** alignment, and **`.env.example`** (or documented template) if present—so generated JDBC examples for `APP_DATASOURCE_IMAGELOG_*` match provisioning endpoints.
- **Backward compatibility:** When the new B-cluster variables are **unset**, behavior must remain **equivalent** to today (single Primary cluster for both A and B names)—so existing single-host and same-cluster multi-database deployments keep working without mandatory new env keys.

**Note:** This requirement does **not** invoke search/filter UI pattern §2.4 (forms-and-filters).

## 2. Design

### 2.1 Security review (optional; when PII / decryption / access control)

- [ ] Security review performed (recommended: operational secrets concentration in `.env`, superuser vs app roles on **three** hosts—extend §2.1 from `docs/requirements/20260410-install-deploy-three-db-noninteractive.md` if needed)

This change **touches** bootstrap credentials and possibly additional superuser endpoints (ImageLog host). Implementers must **not** print passwords, `PGPASSWORD`, or full JDBC URLs to stdout/stderr; missing-variable errors list **names only** (existing project rule).

### Technical design

#### Codebase summary (verified)

- **`setup.sh`**: `psql_admin` uses `DB_HOST`, `DB_PORT`, `DB_SUPERUSER` for both creating/connecting to `DB_A_NAME` and `DB_B_NAME` (e.g. database creation loop, `ensure_schema`, `run_sql_file_sp` for ImageLog). Split-PB uses **`psql_pb_admin`** with `DB_PB_HOST`, `DB_PB_PORT`, `DB_PB_SUPERUSER`. There is **no** `psql_b_admin` today.
- **`docs/contract.md`**: Documents **`APP_DATASOURCE_*`** for three pools and install vars including **`DB_PB_*`** for split-PB; states Primary/ImageLog install target cluster as **same `DB_HOST`·`DB_PORT`** for B via `psql_admin`.
- **`backend/DB_SETUP_GUIDE.md`**: Describes split-PB and same-host A/B; independent run order examples; non-interactive rules for `full` / `sys_only` / `pb_only`.
- **`check-db.sh`**: Uses `DB_HOST`/`DB_PORT` for superuser and app `psql` to both A and B; no `DB_B_HOST` concept.

#### Problem analysis

1. **Remote ImageLog** requires DDL and grants on the **ImageLog host**, not on the Primary `psql_admin` target—current script would create `DB_B_NAME` on the wrong cluster or fail to reach the remote instance.
2. **Independent reinstall** per host is **partially** supported (`pb_only` for PB) but not symmetric for **Primary-only** vs **ImageLog-only** with clear, validated modes.
3. **Documentation** still encodes the “B on same host as Primary” assumption in contract/guide; operators following **three JDBC URLs** at runtime may **misconfigure** install if docs imply B is always created via Primary.
4. **`check-db.sh`** would report a false success/failure mix if B is remote but checks still use Primary host only.

#### Solution approach (by scope)

**Backend:**

- **Typically no Java code change** if runtime already uses `APP_DATASOURCE_IMAGELOG_*` from env; **verify** that no bootstrap code assumes `DB_HOST` constructs ImageLog JDBC URL at install time. If any helper generates JDBC from Primary-only vars, align with **Contract** after DB/Contract updates.

**Frontend:**

- **None** for this requirement (provisioning and docs only).

**DB (scripts under `backend/src/main/resources/db/`):**

- Introduce **ImageLog cluster variables** (exact names—implementers must use these unless amended after review):
  - **`DB_B_HOST`** — default **`DB_HOST`** when unset (backward compatible).
  - **`DB_B_PORT`** — default **`DB_PORT`** when unset.
  - **`DB_B_SUPERUSER`** — default **`DB_SUPERUSER`** when unset.
- Add a **`psql_b_admin`** (or equivalent) that connects with **`DB_B_SUPERUSER`** / **`DB_B_HOST`** / **`DB_B_PORT`**, analogous to `psql_pb_admin`.
- Refactor **all** operations that target **only** `DB_B_NAME` / `SCHEMA_IMAGELOG` (database create, `ensure_schema`, ImageLog DDL/migrations, ImageLog init-data paths, `LOAD_LOCAL_DECRYPT_TEST_DATA` ImageLog files) to use the B admin client when **`DB_B_HOST`/`DB_B_PORT`/`DB_B_SUPERUSER` denote a distinct cluster** from Primary—or more simply, always use `psql_b_admin` for B operations with defaults folding to Primary (cleaner than branching).
- **`SETUP_MODE` extension** (names are **requirements**; implementers may adjust naming only with Contract/doc sync):
  - **`primary_only`** (or **`sys_primary_only`**): run **Primary A** path—system DDL, migrations, and grants on A that belong to the current `full`/`sys_only` contract for A—**do not** execute ImageLog-on-B steps (no `schema_imagelog.sql` on B, no B-only seeds). **Must** still respect split-PB rules: either document that PB is **excluded** here and **`pb_only`** remains the PB path, or define interaction with `SPLIT_PB` explicitly in §3 TCs.
  - **`imagelog_only`**: run **ImageLog B** path only—database `DB_B_NAME`, schema, ImageLog migrations, optional imagelog seeds per flags—**no** Primary A DDL, **no** PB steps.
  - Preserve **`pb_only`**, **`full`**, **`sys_only`** with documented semantics when **three hosts** differ: e.g. **`full`** orchestrates Primary+ImageLog+PB in one invocation **if** all endpoints are reachable, or document **mandatory sequence** of `primary_only` → `imagelog_only` → `pb_only` when clusters are isolated—**pick one** and test (§3).
- **Non-interactive validation:** extend `_validate_noninteractive_env` (or successor) so each mode lists **required** variables (e.g. `imagelog_only` requires B cluster reachability vars; when B host differs from Primary, require **`DB_B_HOST`** and **`DB_B_PORT`** explicitly or follow the same “either pair” rule as `pb_only` for PB). **Never** print secret values.
- **Idempotency:** preserve idempotent DDL/migration behavior for split runs; document **`SKIP_INIT_DATA`** / **`CLOSED_NETWORK_MINIMAL`** interaction for partial modes.

**Contract / Spec:**

- Update **`docs/contract.md`**: Environment·Ports table and **DB 설치·부트스트랩** section—replace “B same host as Primary only” with **split-B** vars and defaults; document **`SETUP_MODE`** values once finalized; keep **`APP_DATASOURCE_IMAGELOG_*`** as runtime source of truth for JDBC.
- Align **`backend/DB_SETUP_GUIDE.md`**: three-host independent provisioning, run order, examples for non-interactive env files.

**Optional tooling:**

- **`scripts/install_linux.sh`** (and offline installer if applicable): extend **non-interactive validation** and exported `setup.sh` env passthrough so new vars are **forwarded** and validated consistently with `docs/contract.md`.
- **`check-db.sh`**: use **`DB_B_HOST`** / **`DB_B_PORT`** (and app-role test) for B when split; keep Primary checks for A.

**Cursor tools:**

- If **`db-domain`** skill documents install env vars, update **`.cursor/skills/db-domain/SKILL.md`** after variable names stabilize (Contract agent / DB agent ownership per file rules).

### Affected scopes and change targets (verification)

Per `docs/workflow/REQUIREMENTS-CHANGE-TARGET-CHECKLIST.md` §1:

| Scope | Affected? | §2 subsection and change file list complete? |
|-------|------------|-----------------------------------------------|
| Backend | Yes (verify only) | [x] |
| Frontend | No | [x] |
| DB | Yes | [x] |
| Contract / Spec | Yes | [x] |
| Cursor tools (skills) | Optional | [x] |

### Planned change file list (expected change targets)

#### Frontend

- (none)

#### Backend

- (none expected — **verify** no hardcoded Primary-only construction for ImageLog URL; if found, amend per Contract)

#### DB

- `backend/src/main/resources/db/setup.sh` — B-cluster vars, `psql_b_admin`, SETUP_MODE extensions, non-interactive validation, refactor B-only steps.
- `backend/src/main/resources/db/check-db.sh` — optional connectivity checks for split B host.
- **Optional:** `scripts/install_linux.sh`, `scripts/offline-bundle/install-offline.sh` or related — passthrough/validation only if install entrypoints must know new vars.

#### Contract / documentation

- `docs/contract.md` — install table, SETUP_MODE row(s), ImageLog bootstrap host wording.
- `backend/DB_SETUP_GUIDE.md` — three remote hosts, mode matrix, examples.
- `.env.example` (repository root, if present) — document new variables with **Korean comments** per project policy.

## 3. Test approach

### Test case list (required)

**Scope tags** for handoff per `HANDOFF-CHECKLIST.md`.

| ID | Scope | Type | Scenario (input / condition) | Expected result | Verification |
|----|-------|------|------------------------------|-----------------|--------------|
| TC-01 | DB | Normal | `SETUP_NONINTERACTIVE=1`, mode **`imagelog_only`**, all required B-cluster and app-role vars set; ImageLog host reachable | Script completes ImageLog DDL (and configured seeds) on **B** endpoint; no Primary-only DDL executed | Manual or integration (script run against test containers) |
| TC-02 | DB | Normal | `SETUP_NONINTERACTIVE=1`, mode **`primary_only`**, Primary vars set; B not targeted | Script completes A-side steps per mode definition; **no** `schema_imagelog.sql` on B | Manual or integration |
| TC-03 | DB | Regression | New B-cluster vars **unset**; `DB_A_NAME` / `DB_B_NAME` same or different **on same Primary host** | Behavior **matches pre-change** (single `psql_admin` target for both A and B names) | Manual or integration |
| TC-04 | DB | Edge | `DB_B_HOST`/`DB_B_PORT` point to remote host; `primary_only` | No connection attempt to B for ImageLog DDL (per design); Primary operations succeed | Manual or integration |
| TC-05 | DB | Exception | `SETUP_NONINTERACTIVE=1`, **`imagelog_only`**, missing required var (e.g. empty `DB_B_HOST` when rule requires explicit split) | Exit non-zero; stderr lists **variable names only**; no secrets | Manual |
| TC-06 | DB | Exception | `SETUP_NONINTERACTIVE=1`, **`primary_only`**, missing required Primary var | Exit non-zero; names-only stderr | Manual |
| TC-07 | DB | Normal | `SETUP_MODE=pb_only` with existing **`DB_PB_*`** | Unchanged PB-only behavior (regression) | Manual or integration |
| TC-08 | DB | Normal | `SETUP_MODE=full` (or documented combo) with **three distinct** host:port targets | All required DDL/migrations run on **correct** cluster per step; order documented in §5 commands | Integration |
| TC-09 | DB | Normal | `SETUP_MODE=sys_only` with split PB and/or split B per §2 | Documented skips (e.g. init-data, PB on split) respected; no partial apply on wrong host | Manual or integration |
| TC-10 | Integration | Normal | `check-db.sh` with split B host | Validates ImageLog on **`DB_B_HOST`:`DB_B_PORT`** when set; Primary checks unchanged | Manual |
| TC-11 | Contract | Normal | `docs/contract.md` install section | Describes **`DB_B_HOST`/`DB_B_PORT`/`DB_B_SUPERUSER`**, defaults, and SETUP_MODE values; consistent with `APP_DATASOURCE_IMAGELOG_*` | Doc review |
| TC-12 | Contract | Normal | `backend/DB_SETUP_GUIDE.md` | Operator runbook for **three remote hosts** + independent reinstall; cross-links contract | Doc review |
| TC-13 | Contract | Normal | `.env.example` | New/updated vars documented (Korean comments); placeholders only | Doc review |
| TC-14 | DB | Security | Any failure path | No password, `PGPASSWORD`, or credential-bearing JDBC URL printed | Manual / code review |

### Test scenarios

#### Scenario 1: Independent reinstall on three remotes

1. Configure `.env` with three distinct endpoints (Primary, PB, ImageLog) per contract.
2. Run **`primary_only`** (or documented equivalent) on Primary host credentials only.
3. Run **`pb_only`** on PB host with `DB_PB_*`.
4. Run **`imagelog_only`** on ImageLog host with `DB_B_*`.
5. Run **`check-db.sh`** (or equivalent) with the same env; confirm green checks for A, PB, and B.

#### Scenario 2: Backward-compatible single cluster

1. Leave **`DB_B_HOST`/`DB_B_PORT`/`DB_B_SUPERUSER`** unset; set `DB_HOST`/`DB_PORT` only.
2. Run legacy **`full`** install; confirm databases A/B created on same cluster as today (TC-03).

### Test data

- Three PostgreSQL test instances **or** container network with three published ports; app role and superuser test passwords via env (not committed).

### Test environment

- Script execution host with `psql` and network access to test DB hosts; optional alignment with `docs/contract.md` dev ports.

### 3.5 Browser automation verification

- **Not applicable** (no UI change).

## 4. Checklist

### Frontend verification

- [ ] N/A

### Backend verification

- [ ] Runtime JDBC validation only if code touched (see §2)

### Integration

- [ ] End-to-end provisioning scenarios in §3 pass on representative three-host layout

### Documentation

- [ ] Requirement doc completed
- [ ] `docs/contract.md` and `backend/DB_SETUP_GUIDE.md` updated in same change wave as scripts (per `docs/workflow/DOC-CODE-SYNC.md` where applicable)

## 5. Test results

### Test run date

- (pending — QA)

### Test results

- (pending — QA)

## 6. Error remedy result (cause and action) — for error/bug fix requirements only

- N/A (feature/requirements change).

---

## 7. Final version (Korean)

### 요약

- **요구사항 요약**: logmng(Primary), pbfep(PB), imagelog(ImageLog)가 **서로 다른 원격 호스트**에 있을 때, 각각을 **독립적으로 재설치·프로비저닝**할 수 있어야 한다. 현재 `setup.sh`는 Primary와 ImageLog(DB B)에 동일한 `psql_admin`( `DB_HOST`/`DB_PORT`)을 사용하므로, ImageLog만 **별도 호스트**에 두는 구성은 설치 스크립트 관점에서 완전히 지원되지 않는다. split-PB와 같이 ImageLog 클러스터용 **`DB_B_HOST` / `DB_B_PORT` / `DB_B_SUPERUSER`**(명칭은 구현 시 §2와 계약 문서에 맞출 것)와 **`primary_only` / `imagelog_only`** 등 **SETUP_MODE** 확장, 비대화형 검증·문서·계약(`docs/contract.md`, `DB_SETUP_GUIDE.md`, 선택 `check-db.sh`, `.env.example`) 정렬이 필요하다.

- **기대 결과**: 런타임의 세 JDBC URL(`APP_DATASOURCE_*`)과 일치하도록, 설치 단계에서도 Primary / PB / ImageLog를 **올바른 엔드포인트**에 적용할 수 있고, 각 호스트에서 **필요한 모드만** 비대화형으로 실행할 수 있다. 기존 단일 클러스터 구성은 **새 변수 미설정 시** 기존과 동일하게 동작해야 한다.

- **검증 결과**: QA 수행 후 §5에 기록한다.

---

**Author**: Requirements (subagent)  
**Date**: 2026-04-14  
**Status**: In progress
