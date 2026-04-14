#!/usr/bin/env bash
#
# LogMng — single offline installer & launcher (no npm/mvn/internet on this host).
# Run from the root of the extracted offline bundle (same directory as bin/, db/).
#
# Usage: ./install-offline.sh check|install-psql|db|configure|start|stop|status|all|start-frontend|stop-frontend
#
# Java: if `java` is not on PATH, set either
#   export JAVA_HOME=/path/to/jdk-17
#   export JAVA_CMD=/path/to/jdk-17/bin/java
# or answer the java path prompt in `configure` (saved to var/logmng.env).
#
# psql: on the app server, needed for ./install-offline.sh db against a remote PostgreSQL.
#       install-psql installs the client from tools/psql-deb/*.deb (dpkg) when missing.
#       Rebuild the tarball with scripts/download-psql-for-bundle.sh to include those .debs.
#       configure/start do not need psql if the DB was already provisioned.
#
# PB FEP on a separate PostgreSQL database: db step prompts, or pre-export DB_PB_NAME,
# DB_PB_HOST, DB_PB_PORT, DB_PB_SUPERUSER (optional; default superuser same as DB_SUPERUSER).
# See README-OFFLINE.md — align APP_DATASOURCE_PB_URL in configure when split.
#
# Bundled psql:
#   • tools/psql-deb/*.deb — Debian/Ubuntu (dpkg). From download-psql-for-bundle.sh (PG 16).
#   • tools/psql-rpm-el9/*.rpm — RHEL/Rocky/Alma 9.6 x86_64 (dnf/yum/rpm). From download-psql-rpm-el9.sh.
#   Set SKIP_BUNDLE_PSQL=1 to disable automatic install.
#
# DB seed: export SKIP_INIT_DATA=1 before db (or all → db) to run DDL/migrations only — no INIT_DATA_FILE
#   (no bundled admin/sample rows). Or choose [n] at the db-step prompt when mode is full (1).
#
set -euo pipefail

BUNDLE_ROOT="$(cd "$(dirname "$0")" && pwd)"
VAR_DIR="$BUNDLE_ROOT/var"
LOG_DIR="$VAR_DIR/log"
RUN_DIR="$VAR_DIR/run"
ENV_FILE="$VAR_DIR/logmng.env"

BACKEND_JAR="$BUNDLE_ROOT/bin/backend/logmng-backend-1.0.2.jar"
STATIC_JAR="$BUNDLE_ROOT/bin/frontend/logmng-static-server-1.0.0.jar"
WWW_DIR="$BUNDLE_ROOT/bin/frontend/www"
DB_SETUP="$BUNDLE_ROOT/db/setup.sh"

lc() {
  printf '%s' "$1" | tr '[:upper:]' '[:lower:]'
}

# INSTALL_NONINTERACTIVE=1: source repo/bundle .env (if present) + var/logmng.env; skip prompts; stderr lists missing var names only.
noninteractive_active() {
  local v="${INSTALL_NONINTERACTIVE:-0}"
  [[ "$v" == "1" ]] || [[ "$(lc "$v")" == "true" ]] || [[ "$(lc "$v")" == "yes" ]]
}

source_operator_env_noninteractive() {
  local dotenv="$BUNDLE_ROOT/.env"
  if [[ -f "$dotenv" ]]; then
    set -a
    # shellcheck disable=SC1090
    source "$dotenv"
    set +a
  fi
  if [[ -f "$ENV_FILE" ]]; then
    set -a
    # shellcheck disable=SC1090
    source "$ENV_FILE"
    set +a
  fi
}

# Sets JAVA_BIN for backend/static-server processes. Honors JAVA_CMD, then PATH java, then JAVA_HOME.
resolve_java() {
  if [[ -n "${JAVA_CMD:-}" ]]; then
    if [[ -x "$JAVA_CMD" || -f "$JAVA_CMD" ]]; then
      JAVA_BIN="$JAVA_CMD"
      return 0
    fi
    echo "[WARN] JAVA_CMD is set but not executable: $JAVA_CMD" >&2
  fi
  if command -v java >/dev/null 2>&1; then
    JAVA_BIN="$(command -v java)"
    return 0
  fi
  if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]]; then
    JAVA_BIN="$JAVA_HOME/bin/java"
    return 0
  fi
  JAVA_BIN=""
  return 1
}

# Install bundled .deb PostgreSQL client when psql is missing (Debian/Ubuntu + dpkg only).
ensure_psql_from_bundle() {
  if command -v psql >/dev/null 2>&1; then
    return 0
  fi
  if [[ "${SKIP_BUNDLE_PSQL:-0}" == "1" ]]; then
    return 1
  fi
  local d="$BUNDLE_ROOT/tools/psql-deb"
  shopt -s nullglob
  local deb=( "$d"/*.deb )
  shopt -u nullglob
  if [[ ${#deb[@]} -eq 0 ]]; then
    return 1
  fi
  if ! command -v dpkg >/dev/null 2>&1; then
    return 1
  fi
  echo "psql not found — installing bundled packages from tools/psql-deb/ (requires root or sudo) ..."
  run_pkg_as_root() {
    if [[ "$(id -u)" -eq 0 ]]; then
      "$@"
    elif command -v sudo >/dev/null 2>&1; then
      sudo "$@"
    else
      echo "Root or sudo is required to install .deb files. Install postgresql-client manually." >&2
      return 1
    fi
  }
  # Install order: libpq5 → client-common → postgresql-client-NN (dpkg dependency order)
  local ordered=()
  local f
  for f in "$d"/libpq5_*.deb; do [[ -f "$f" ]] && ordered+=( "$f" ); done
  for f in "$d"/postgresql-client-common_*.deb; do [[ -f "$f" ]] && ordered+=( "$f" ); done
  for f in "$d"/postgresql-client-[0-9]*_*.deb; do [[ -f "$f" ]] && ordered+=( "$f" ); done
  for f in "${deb[@]}"; do
    local seen=0
    local o
    for o in "${ordered[@]}"; do [[ "$f" == "$o" ]] && seen=1 && break; done
    [[ "$seen" -eq 0 ]] && ordered+=( "$f" )
  done
  if ! run_pkg_as_root dpkg -i "${ordered[@]}"; then
    echo "[WARN] dpkg -i failed — install missing dependencies from the same OS release (offline mirror) or add psql to PATH." >&2
    echo "       See tools/psql-deb/README.txt and README-OFFLINE.md." >&2
    return 1
  fi
  command -v psql >/dev/null 2>&1
}

# RHEL / Rocky / Alma 9.x (RPM) — PGDG builds for rhel-9.6-x86_64; typically works on 9.5–9.7.
ensure_psql_from_bundle_rpm_el9() {
  if command -v psql >/dev/null 2>&1; then
    return 0
  fi
  if [[ "${SKIP_BUNDLE_PSQL:-0}" == "1" ]]; then
    return 1
  fi
  local d="$BUNDLE_ROOT/tools/psql-rpm-el9"
  shopt -s nullglob
  local rpms=( "$d"/*.rpm )
  shopt -u nullglob
  if [[ ${#rpms[@]} -eq 0 ]]; then
    return 1
  fi
  if ! command -v rpm >/dev/null 2>&1; then
    return 1
  fi
  echo "psql not found — installing bundled RPMs from tools/psql-rpm-el9/ (RHEL 9.x / PG 16 client, sudo/root) ..."
  run_pkg_as_root() {
    if [[ "$(id -u)" -eq 0 ]]; then
      "$@"
    elif command -v sudo >/dev/null 2>&1; then
      sudo "$@"
    else
      echo "Root or sudo is required to install RPMs." >&2
      return 1
    fi
  }
  if command -v dnf >/dev/null 2>&1; then
    if ! run_pkg_as_root dnf install -y "${rpms[@]}"; then
      echo "[WARN] dnf install failed — see tools/psql-rpm-el9/README.txt" >&2
      return 1
    fi
  elif command -v yum >/dev/null 2>&1; then
    if ! run_pkg_as_root yum localinstall -y "${rpms[@]}"; then
      echo "[WARN] yum localinstall failed — see tools/psql-rpm-el9/README.txt" >&2
      return 1
    fi
  else
    local ordered=()
    local f
    for f in "$d"/postgresql16-libs-*.rpm; do [[ -f "$f" ]] && ordered+=( "$f" ); done
    for f in "$d"/postgresql16-*.rpm; do
      [[ -f "$f" ]] || continue
      [[ "$f" == *postgresql16-libs* ]] && continue
      [[ "$f" == *server* ]] && continue
      ordered+=( "$f" )
    done
    if [[ ${#ordered[@]} -eq 0 ]]; then
      ordered=( "${rpms[@]}" )
    fi
    if ! run_pkg_as_root rpm -Uvh "${ordered[@]}"; then
      echo "[WARN] rpm -Uvh failed — missing OS deps? Use offline mirror for RHEL 9 base libs." >&2
      return 1
    fi
  fi
  command -v psql >/dev/null 2>&1
}

# Ensure psql exists for db/setup.sh (remote DB from app server). Tries .deb (dpkg) or EL9 .rpm (dnf/yum).
ensure_psql_client() {
  if command -v psql >/dev/null 2>&1; then
    echo "[OK] psql already on PATH: $(command -v psql)"
    return 0
  fi
  if [[ "${SKIP_BUNDLE_PSQL:-0}" == "1" ]]; then
    echo "[FAIL] psql 없음 — SKIP_BUNDLE_PSQL=1 이라 번들 설치를 건너뜁니다. PATH에 psql을 두거나 SKIP을 해제하세요." >&2
    return 1
  fi
  echo ""
  echo "=== PostgreSQL client (psql) ==="
  echo "원격 DB DDL 적용을 위해 psql이 필요합니다. 번들에 포함된 클라이언트 패키지를 설치합니다."
  if ensure_psql_from_bundle; then
    echo "[OK] psql 설치됨 (.deb): $(command -v psql)"
    return 0
  fi
  if ensure_psql_from_bundle_rpm_el9; then
    echo "[OK] psql 설치됨 (RPM, EL9 PGDG): $(command -v psql)"
    return 0
  fi
  echo "" >&2
  echo "[FAIL] psql을 사용할 수 없습니다." >&2
  echo "  • Debian/Ubuntu: 빌드 PC에서 ./scripts/download-psql-for-bundle.sh 후 tarball 재생성 → tools/psql-deb/*.deb" >&2
  echo "  • RHEL/Rocky/Alma 9.x(예: 9.6): ./scripts/download-psql-rpm-el9.sh 후 재생성 → tools/psql-rpm-el9/*.rpm (PGDG rhel-9.6-x86_64)" >&2
  echo "  • 또는 사내 미러에서 postgresql 클라이언트 설치 후 PATH에 psql" >&2
  echo "  • 또는 DDL만 DB 서버/다른 호스트에서 실행" >&2
  return 1
}

prompt() {
  local label="$1"
  local def="$2"
  local val
  read -r -p "${label} [${def}]: " val || true
  if [[ -z "${val}" ]]; then
    echo "$def"
  else
    echo "$val"
  fi
}

prompt_secret() {
  local label="$1"
  local val
  read -r -s -p "${label}: " val || true
  echo ""
  echo "${val:-}"
}

ensure_dirs() {
  mkdir -p "$LOG_DIR" "$RUN_DIR"
}

write_env_file() {
  local path="$1"
  umask 077
  cat >"$path" <<EOF
# Generated by install-offline.sh — contains secrets.
export SERVER_PORT="${SERVER_PORT}"
export SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL}"
export SPRING_DATASOURCE_USERNAME="${SPRING_DATASOURCE_USERNAME}"
export SPRING_DATASOURCE_PASSWORD="${SPRING_DATASOURCE_PASSWORD}"
export APP_DB_SCHEMA_SYS="${APP_DB_SCHEMA_SYS}"
export APP_DB_SCHEMA_PB="${APP_DB_SCHEMA_PB}"
export APP_DB_SCHEMA_IMAGELOG="${APP_DB_SCHEMA_IMAGELOG}"
export APP_DATASOURCE_PB_URL="${APP_DATASOURCE_PB_URL:-}"
export APP_DATASOURCE_PB_USERNAME="${APP_DATASOURCE_PB_USERNAME:-}"
export APP_DATASOURCE_PB_PASSWORD="${APP_DATASOURCE_PB_PASSWORD:-}"
export APP_DATASOURCE_IMAGELOG_URL="${APP_DATASOURCE_IMAGELOG_URL:-}"
export APP_DATASOURCE_IMAGELOG_USERNAME="${APP_DATASOURCE_IMAGELOG_USERNAME:-}"
export APP_DATASOURCE_IMAGELOG_PASSWORD="${APP_DATASOURCE_IMAGELOG_PASSWORD:-}"
export CORS_ALLOWED_ORIGINS="${CORS_ALLOWED_ORIGINS}"
export FRONTEND_PORT="${FRONTEND_PORT}"
export LOGMNG_API_BASE_URL="${LOGMNG_API_BASE_URL:-}"
# AES-256 암·복호화 (LogDbService/CryptoUtil). 운영에서는 반드시 고유 키(UTF-8 32바이트 권장).
export ENCRYPTION_KEY="${ENCRYPTION_KEY}"
export DECRYPTION_ENABLED="${DECRYPTION_ENABLED:-true}"
export AUTO_DECRYPT_ON_KEYWORD_SEARCH="${AUTO_DECRYPT_ON_KEYWORD_SEARCH:-true}"
export FAILURE_HANDLING="${FAILURE_HANDLING:-fallback}"
export LOGGING_FILE_NAME="${LOGGING_FILE_NAME:-}"
EOF
  if [[ -n "${JAVA_CMD_LINE:-}" ]]; then
    printf 'export JAVA_CMD="%s"\n' "${JAVA_CMD_LINE//\"/\\\"}" >>"$path"
  fi
  chmod 600 "$path"
  echo "Wrote $path (mode 600)"
}

cmd_check() {
  echo "=== Offline bundle check ==="
  echo "Root: $BUNDLE_ROOT"
  local ok=1
  resolve_java || true
  if [[ -z "${JAVA_BIN:-}" ]]; then
    echo "[FAIL] java not found on PATH and JAVA_HOME/bin/java missing."
    echo "      Fix: export JAVA_HOME=/path/to/jdk-17   (then re-run check)"
    echo "      or:  export JAVA_CMD=/path/to/jdk-17/bin/java"
    echo "      or:  ./install-offline.sh configure — java path prompt saves JAVA_CMD into var/logmng.env"
    ok=0
  else
    echo "[OK] java: $JAVA_BIN"
    "$JAVA_BIN" -version 2>&1 | head -1 || true
  fi
  if ! command -v psql >/dev/null 2>&1; then
    echo "[INFO] psql not on PATH — db(1·2) 또는 install-psql 시 번들 포함 클라이언트 설치 시도:"
    echo "       Debian/Ubuntu: tools/psql-deb/*.deb (download-psql-for-bundle.sh)"
    echo "       RHEL/Rocky/Alma 9.x: tools/psql-rpm-el9/*.rpm (download-psql-rpm-el9.sh, PGDG rhel-9.6)"
  else
    echo "[OK] psql: $(command -v psql)"
  fi
  [[ -f "$BACKEND_JAR" ]] && echo "[OK] backend JAR" || { echo "[FAIL] missing $BACKEND_JAR"; ok=0; }
  [[ -f "$STATIC_JAR" ]] && echo "[OK] static server JAR" || { echo "[FAIL] missing $STATIC_JAR"; ok=0; }
  [[ -f "$WWW_DIR/index.html" ]] && echo "[OK] frontend www/index.html" || { echo "[FAIL] missing $WWW_DIR/index.html"; ok=0; }
  [[ -f "$DB_SETUP" ]] && echo "[OK] db/setup.sh" || { echo "[FAIL] missing $DB_SETUP"; ok=0; }
  return $((1 - ok))
}

cmd_db_noninteractive() {
  source_operator_env_noninteractive
  if [[ "${INSTALL_DB_SKIP:-0}" == "1" ]]; then
    echo "DB step skipped (INSTALL_DB_SKIP=1)."
    return 0
  fi
  local missing=()
  [[ -n "${DB_HOST:-}" ]] || missing+=("DB_HOST")
  [[ -n "${DB_PORT:-}" ]] || missing+=("DB_PORT")
  [[ -n "${DB_USER:-}" ]] || missing+=("DB_USER")
  [[ -n "${DB_PASSWORD:-}" ]] || missing+=("DB_PASSWORD")
  if [[ -z "${DB_A_NAME:-}" && -z "${DB_NAME:-}" ]]; then
    missing+=("DB_NAME")
    missing+=("DB_A_NAME")
  fi
  local sm="${SETUP_MODE:-}"
  [[ -n "$sm" ]] || missing+=("SETUP_MODE")
  if [[ -n "$sm" && "$sm" != "full" && "$sm" != "sys_only" && "$sm" != "pb_only" ]]; then
    printf '%s\n' "SETUP_MODE" >&2
    exit 1
  fi
  if [[ "$sm" == "pb_only" ]]; then
    [[ -n "${DB_PB_NAME:-}" ]] || missing+=("DB_PB_NAME")
  fi
  if [[ ${#missing[@]} -gt 0 ]]; then
    printf '%s\n' "${missing[@]}" >&2
    exit 1
  fi

  export DB_SUPERUSER="${DB_SUPERUSER:-postgres}"
  if [[ -z "${DB_A_NAME:-}" ]]; then
    export DB_A_NAME="${DB_NAME}"
  fi
  if [[ -z "${DB_NAME:-}" ]]; then
    export DB_NAME="${DB_A_NAME}"
  fi
  export DB_B_NAME="${DB_B_NAME:-$DB_A_NAME}"
  export SCHEMA_SYS="${SCHEMA_SYS:-public}"
  export SCHEMA_PB="${SCHEMA_PB:-public}"
  export SCHEMA_IMAGELOG="${SCHEMA_IMAGELOG:-public}"
  export SETUP_MODE="$sm"
  [[ -n "${PGPASSWORD_SUPER:-}" ]] && export PGPASSWORD_SUPER
  [[ -n "${PGPASSWORD:-}" ]] && export PGPASSWORD

  if [[ -n "${DB_PB_NAME:-}" ]]; then
    export DB_PB_HOST="${DB_PB_HOST:-$DB_HOST}"
    export DB_PB_PORT="${DB_PB_PORT:-$DB_PORT}"
    export DB_PB_SUPERUSER="${DB_PB_SUPERUSER:-$DB_SUPERUSER}"
  else
    unset DB_PB_NAME DB_PB_HOST DB_PB_PORT DB_PB_SUPERUSER 2>/dev/null || true
  fi

  if [[ -f "$BUNDLE_ROOT/CLOSED-NETWORK-BUNDLE" ]]; then
    if [[ -z "${INIT_DATA_FILE+x}" ]]; then
      export INIT_DATA_FILE=init-data-closed-network-admin-only.sql
    fi
    if [[ -z "${CLOSED_NETWORK_MINIMAL+x}" ]]; then
      export CLOSED_NETWORK_MINIMAL=1
    fi
  fi

  ensure_psql_client || exit 1
  echo "[install-offline] Non-interactive DB: SETUP_MODE=$SETUP_MODE" >&2
  (cd "$BUNDLE_ROOT/db" && bash ./setup.sh)
  echo "DB step done."
}

cmd_configure_noninteractive() {
  source_operator_env_noninteractive
  ensure_dirs
  local missing=()
  [[ -n "${SERVER_PORT:-}" ]] || missing+=("SERVER_PORT")
  [[ -n "${FRONTEND_PORT:-}" ]] || missing+=("FRONTEND_PORT")
  [[ -n "${SPRING_DATASOURCE_URL:-}" ]] || missing+=("SPRING_DATASOURCE_URL")
  [[ -n "${SPRING_DATASOURCE_USERNAME:-}" ]] || missing+=("SPRING_DATASOURCE_USERNAME")
  [[ -n "${SPRING_DATASOURCE_PASSWORD:-}" ]] || missing+=("SPRING_DATASOURCE_PASSWORD")
  [[ -n "${APP_DB_SCHEMA_SYS:-}" ]] || missing+=("APP_DB_SCHEMA_SYS")
  [[ -n "${APP_DB_SCHEMA_PB:-}" ]] || missing+=("APP_DB_SCHEMA_PB")
  [[ -n "${APP_DB_SCHEMA_IMAGELOG:-}" ]] || missing+=("APP_DB_SCHEMA_IMAGELOG")
  [[ -n "${CORS_ALLOWED_ORIGINS:-}" ]] || missing+=("CORS_ALLOWED_ORIGINS")
  [[ -n "${ENCRYPTION_KEY:-}" ]] || missing+=("ENCRYPTION_KEY")
  if [[ ${#missing[@]} -gt 0 ]]; then
    printf '%s\n' "${missing[@]}" >&2
    exit 1
  fi
  export APP_DATASOURCE_PB_URL="${APP_DATASOURCE_PB_URL:-}"
  export APP_DATASOURCE_PB_USERNAME="${APP_DATASOURCE_PB_USERNAME:-}"
  export APP_DATASOURCE_PB_PASSWORD="${APP_DATASOURCE_PB_PASSWORD:-}"
  export APP_DATASOURCE_IMAGELOG_URL="${APP_DATASOURCE_IMAGELOG_URL:-}"
  export APP_DATASOURCE_IMAGELOG_USERNAME="${APP_DATASOURCE_IMAGELOG_USERNAME:-}"
  export APP_DATASOURCE_IMAGELOG_PASSWORD="${APP_DATASOURCE_IMAGELOG_PASSWORD:-}"
  export LOGMNG_API_BASE_URL="${LOGMNG_API_BASE_URL:-}"
  export DECRYPTION_ENABLED="${DECRYPTION_ENABLED:-true}"
  export AUTO_DECRYPT_ON_KEYWORD_SEARCH="${AUTO_DECRYPT_ON_KEYWORD_SEARCH:-true}"
  export FAILURE_HANDLING="${FAILURE_HANDLING:-fallback}"
  write_env_file "$ENV_FILE"
}

cmd_db() {
  echo "=== Database setup (bundled db/setup.sh) ==="
  if [[ ! -f "$DB_SETUP" ]]; then
    echo "Missing $DB_SETUP" >&2
    exit 1
  fi
  if noninteractive_active; then
    cmd_db_noninteractive
    return
  fi
  if [[ "$(uname -s)" != "Linux" ]]; then
    echo "Warning: expected Linux server (found: $(uname -s))"
  fi
  echo ""
  echo "  1) Full DDL + migrations (+ optional seed SQL; can skip rows only)"
  echo "  2) sys_only (PB already in SCHEMA_PB; new SCHEMA_SYS only)"
  echo "  3) Skip DB script (already provisioned)"
  echo ""
  local dchoice
  read -r -p "Select [1-3]: " dchoice || true
  if [[ "$dchoice" == "3" ]]; then
    echo "Skipped."
    return 0
  fi
  ensure_psql_client || exit 1
  export DB_SUPERUSER="$(prompt "PostgreSQL superuser" "postgres")"
  echo "Superuser password (empty if peer/trust):"
  local sp
  sp="$(prompt_secret "PGPASSWORD_SUPER")"
  [[ -n "$sp" ]] && export PGPASSWORD_SUPER="$sp"

  export DB_HOST="$(prompt "DB host" "localhost")"
  export DB_PORT="$(prompt "DB port" "5432")"
  export DB_NAME="$(prompt "Database name (A)" "logmng")"
  export DB_USER="$(prompt "App DB user" "logmng")"
  export DB_PASSWORD="$(prompt "App DB password" "logmng123")"
  export DB_A_NAME="$DB_NAME"
  export DB_B_NAME="$(prompt "ImageLog DB (B, same as A for single DB)" "$DB_NAME")"
  export SCHEMA_SYS="$(prompt "SCHEMA_SYS" "public")"
  export SCHEMA_PB="$(prompt "SCHEMA_PB" "public")"
  export SCHEMA_IMAGELOG="$(prompt "SCHEMA_IMAGELOG (on B)" "public")"

  # Optional: PB FEP in a separate PostgreSQL database (non-empty DB_PB_NAME → setup.sh split-PB mode).
  # See README-OFFLINE.md / backend/DB_SETUP_GUIDE.md. Align APP_DATASOURCE_PB_URL in configure when split.
  if [[ -n "${DB_PB_NAME:-}" ]]; then
    echo "[INFO] DB_PB_NAME is set (${DB_PB_NAME}) — provisioning PB in a separate DB (see README-OFFLINE.md)."
    export DB_PB_HOST="${DB_PB_HOST:-$DB_HOST}"
    export DB_PB_PORT="${DB_PB_PORT:-$DB_PORT}"
    export DB_PB_SUPERUSER="${DB_PB_SUPERUSER:-$DB_SUPERUSER}"
  else
    local pb_split=""
    read -r -p "Place PB FEP in a different PostgreSQL database than system DB (A)? [y/N]: " pb_split || true
    pb_split="$(lc "${pb_split:-}")"
    if [[ "$pb_split" == "y" ]]; then
      export DB_PB_NAME="$(prompt "PB database name" "logmng_pb")"
      local _pbh _pbp
      read -r -p "PB DB host [${DB_HOST}]: " _pbh || true
      export DB_PB_HOST="${_pbh:-$DB_HOST}"
      read -r -p "PB DB port [${DB_PORT}]: " _pbp || true
      export DB_PB_PORT="${_pbp:-$DB_PORT}"
      export DB_PB_SUPERUSER="${DB_PB_SUPERUSER:-$DB_SUPERUSER}"
    else
      unset DB_PB_NAME DB_PB_HOST DB_PB_PORT DB_PB_SUPERUSER 2>/dev/null || true
    fi
  fi

  if [[ "$dchoice" == "2" ]]; then
    echo ""
    echo "sys_only: PB DDL skipped. See README-OFFLINE.md / DB_SETUP_GUIDE if unsure."
    read -r -p "Continue? [y/N]: " ok || true
    [[ "$(lc "${ok:-}")" == "y" ]] || exit 1
    export SETUP_MODE=sys_only
  else
    export SETUP_MODE=full
  fi

  # Closed-network bundles ship CLOSED-NETWORK-BUNDLE at the bundle root; setup.sh then uses
  # INIT_DATA_FILE + CLOSED_NETWORK_MINIMAL for minimal seed and skipped dev-only migrations.
  # Do not override if the operator already exported these (e.g. custom mirror or tests).
  if [[ -f "$BUNDLE_ROOT/CLOSED-NETWORK-BUNDLE" ]]; then
    if [[ -z "${INIT_DATA_FILE+x}" ]]; then
      export INIT_DATA_FILE=init-data-closed-network-admin-only.sql
    fi
    if [[ -z "${CLOSED_NETWORK_MINIMAL+x}" ]]; then
      export CLOSED_NETWORK_MINIMAL=1
    fi
  fi

  # full(1): optional skip of INIT_DATA_FILE only (DDL/migrations unchanged). Unset SKIP_INIT_DATA → prompt.
  if [[ "$dchoice" == "1" ]] && [[ -z "${SKIP_INIT_DATA+x}" ]]; then
    read -r -p "Apply seed SQL (${INIT_DATA_FILE})? [Y/n]: " seed_ok || true
    if [[ "$(lc "${seed_ok:-y}")" == "n" ]]; then
      export SKIP_INIT_DATA=1
      echo "[INFO] SKIP_INIT_DATA=1 — setup.sh will skip step 5 (no seed rows). Override: unset SKIP_INIT_DATA and re-run db."
    fi
  fi

  (cd "$BUNDLE_ROOT/db" && bash ./setup.sh)
  echo "DB step done."
}

cmd_configure() {
  echo "=== Application environment ($ENV_FILE) ==="
  if noninteractive_active; then
    cmd_configure_noninteractive
    return
  fi
  ensure_dirs
  local db_host db_port db_name db_user db_password
  db_host="$(prompt "DB host (for JDBC)" "localhost")"
  db_port="$(prompt "DB port" "5432")"
  db_name="$(prompt "Database name (primary A)" "logmng")"
  db_user="$(prompt "JDBC username" "logmng")"
  db_password="$(prompt "JDBC password" "logmng123")"

  export SERVER_PORT="$(prompt "Backend HTTP port" "9200")"
  export FRONTEND_PORT="$(prompt "Frontend static port" "3001")"
  export SPRING_DATASOURCE_URL="jdbc:postgresql://${db_host}:${db_port}/${db_name}"
  export SPRING_DATASOURCE_USERNAME="$db_user"
  export SPRING_DATASOURCE_PASSWORD="$db_password"
  export APP_DB_SCHEMA_SYS="$(prompt "APP_DB_SCHEMA_SYS" "public")"
  export APP_DB_SCHEMA_PB="$(prompt "APP_DB_SCHEMA_PB" "public")"
  export APP_DB_SCHEMA_IMAGELOG="$(prompt "APP_DB_SCHEMA_IMAGELOG" "public")"

  read -r -p "PB FEP JDBC URL (empty = share primary pool + search_path sys+pb) []: " pb_url || true
  export APP_DATASOURCE_PB_URL="${pb_url:-}"
  if [[ -n "${APP_DATASOURCE_PB_URL}" ]]; then
    export APP_DATASOURCE_PB_USERNAME="$(prompt "PB FEP JDBC user" "$db_user")"
    export APP_DATASOURCE_PB_PASSWORD="$(prompt "PB FEP JDBC password" "$db_password")"
  else
    export APP_DATASOURCE_PB_USERNAME=""
    export APP_DATASOURCE_PB_PASSWORD=""
  fi

  read -r -p "ImageLog JDBC URL (empty = use primary pool) []: " img_url || true
  export APP_DATASOURCE_IMAGELOG_URL="${img_url:-}"
  if [[ -n "${APP_DATASOURCE_IMAGELOG_URL}" ]]; then
    export APP_DATASOURCE_IMAGELOG_USERNAME="$(prompt "ImageLog JDBC user" "$db_user")"
    export APP_DATASOURCE_IMAGELOG_PASSWORD="$(prompt "ImageLog JDBC password" "$db_password")"
  else
    export APP_DATASOURCE_IMAGELOG_USERNAME=""
    export APP_DATASOURCE_IMAGELOG_PASSWORD=""
  fi

  local def_cors="http://127.0.0.1:${FRONTEND_PORT},http://localhost:${FRONTEND_PORT}"
  export CORS_ALLOWED_ORIGINS="$(prompt "CORS_ALLOWED_ORIGINS (comma-separated UI origins)" "$def_cors")"

  local def_api="http://127.0.0.1:${SERVER_PORT}/api"
  echo ""
  echo "브라우저가 호출할 백엔드 API 베이스 URL (정적 UI용; 비우면 빌드 시 REACT_APP 값 또는 localhost 기본)."
  export LOGMNG_API_BASE_URL="$(prompt "LOGMNG_API_BASE_URL (empty = build default)" "$def_api")"

  echo ""
  echo "암·복호화 키 (AES-256, UTF-8로 32바이트 권장). 운영은 임의 32바이트 이상으로 변경하세요."
  export ENCRYPTION_KEY="$(prompt "ENCRYPTION_KEY" "12345678901234567890123456789012")"
  export DECRYPTION_ENABLED="$(prompt "DECRYPTION_ENABLED (true/false)" "true")"
  export AUTO_DECRYPT_ON_KEYWORD_SEARCH="$(prompt "AUTO_DECRYPT_ON_KEYWORD_SEARCH (true/false)" "true")"
  export FAILURE_HANDLING="$(prompt "FAILURE_HANDLING (fallback|skip|error)" "fallback")"

  echo ""
  echo "Java: if PATH에 java가 없으면 JDK 설치 경로의 bin/java 전체 경로를 입력하세요."
  JAVA_CMD_LINE="$(prompt "java full path (empty if java is already on PATH)" "")"
  export JAVA_CMD_LINE

  write_env_file "$ENV_FILE"
}

cmd_start() {
  ensure_dirs
  if [[ ! -f "$ENV_FILE" ]]; then
    echo "Missing $ENV_FILE — run: $0 configure" >&2
    exit 1
  fi
  # shellcheck disable=SC1090
  set -a
  source "$ENV_FILE"
  set +a

  resolve_java || true
  if [[ -z "${JAVA_BIN:-}" ]]; then
    echo "Cannot find java. Set JAVA_HOME, or JAVA_CMD in $ENV_FILE, or PATH." >&2
    exit 1
  fi

  if [[ -f "$RUN_DIR/backend.pid" ]] && kill -0 "$(cat "$RUN_DIR/backend.pid")" 2>/dev/null; then
    echo "Backend already running (pid $(cat "$RUN_DIR/backend.pid"))"
  else
    nohup "$JAVA_BIN" ${JAVA_OPTS:-} -jar "$BACKEND_JAR" >>"$LOG_DIR/backend.log" 2>&1 &
    echo $! >"$RUN_DIR/backend.pid"
    echo "Backend started pid=$(cat "$RUN_DIR/backend.pid") port=${SERVER_PORT:-9200}"
    sleep 2
  fi

  if [[ -f "$RUN_DIR/frontend.pid" ]] && kill -0 "$(cat "$RUN_DIR/frontend.pid")" 2>/dev/null; then
    echo "Frontend already running (pid $(cat "$RUN_DIR/frontend.pid"))"
  else
    export PORT="${FRONTEND_PORT:-3001}"
    nohup "$JAVA_BIN" ${JAVA_OPTS:-} -jar "$STATIC_JAR" "$WWW_DIR" "$PORT" >>"$LOG_DIR/frontend.log" 2>&1 &
    echo $! >"$RUN_DIR/frontend.pid"
    echo "Frontend started pid=$(cat "$RUN_DIR/frontend.pid") port=$PORT"
  fi
  echo "Logs: $LOG_DIR"
}

# 정적 UI만 기동 (백엔드 없음). var/logmng.env 가 있으면 로드해 FRONTEND_PORT·LOGMNG_API_BASE_URL·JAVA_CMD 반영.
cmd_start_frontend() {
  ensure_dirs
  if [[ -f "$ENV_FILE" ]]; then
    # shellcheck disable=SC1090
    set -a
    source "$ENV_FILE"
    set +a
  else
    echo "[INFO] No $ENV_FILE — using shell env only (FRONTEND_PORT, LOGMNG_API_BASE_URL, JAVA_HOME, PATH)." >&2
  fi
  resolve_java || true
  if [[ -z "${JAVA_BIN:-}" ]]; then
    echo "Cannot find java. Set JAVA_HOME, JAVA_CMD, PATH, or run: $0 configure" >&2
    exit 1
  fi
  if [[ ! -f "$STATIC_JAR" || ! -f "$WWW_DIR/index.html" ]]; then
    echo "Missing static UI: $STATIC_JAR or $WWW_DIR/index.html" >&2
    exit 1
  fi
  if [[ -f "$RUN_DIR/frontend.pid" ]] && kill -0 "$(cat "$RUN_DIR/frontend.pid")" 2>/dev/null; then
    echo "Frontend already running (pid $(cat "$RUN_DIR/frontend.pid"))"
    return 0
  fi
  export PORT="${FRONTEND_PORT:-3001}"
  nohup "$JAVA_BIN" ${JAVA_OPTS:-} -jar "$STATIC_JAR" "$WWW_DIR" "$PORT" >>"$LOG_DIR/frontend.log" 2>&1 &
  echo $! >"$RUN_DIR/frontend.pid"
  echo "Frontend only: pid=$(cat "$RUN_DIR/frontend.pid") port=$PORT (backend not started)"
  echo "Log: $LOG_DIR/frontend.log"
}

cmd_stop_frontend() {
  local f="$RUN_DIR/frontend.pid"
  if [[ -f "$f" ]]; then
    local pid
    pid="$(cat "$f")"
    if kill -0 "$pid" 2>/dev/null; then
      kill "$pid" && echo "Stopped frontend (pid $pid)" || true
    else
      echo "Frontend: stale pid file"
    fi
    rm -f "$f"
  else
    echo "Frontend: not started (no pid file)"
  fi
}

cmd_stop() {
  for name in backend frontend; do
    local f="$RUN_DIR/${name}.pid"
    if [[ -f "$f" ]]; then
      local pid
      pid="$(cat "$f")"
      if kill -0 "$pid" 2>/dev/null; then
        kill "$pid" && echo "Stopped $name (pid $pid)" || true
      fi
      rm -f "$f"
    fi
  done
}

cmd_status() {
  echo "=== Status ==="
  for name in backend frontend; do
    local f="$RUN_DIR/${name}.pid"
    if [[ -f "$f" ]]; then
      local pid
      pid="$(cat "$f")"
      if kill -0 "$pid" 2>/dev/null; then
        echo "$name: running pid=$pid"
      else
        echo "$name: stale pid file"
      fi
    else
      echo "$name: not started (no pid file)"
    fi
  done
  [[ -f "$ENV_FILE" ]] && echo "Env: $ENV_FILE" || echo "Env: not configured"
}

cmd_all() {
  if noninteractive_active; then
    source_operator_env_noninteractive
    cmd_check || exit 1
    if [[ "${INSTALL_RUN_DB:-1}" == "1" ]]; then
      cmd_db_noninteractive
    fi
    if [[ "${INSTALL_RUN_CONFIGURE:-1}" == "1" ]]; then
      cmd_configure_noninteractive
    fi
    if [[ "${INSTALL_RUN_START:-1}" == "1" ]]; then
      sleep 1
      cmd_start
    fi
    echo ""
    echo "Done (INSTALL_NONINTERACTIVE). Env: $ENV_FILE — ./install-offline.sh start|stop|status"
    echo "README: $BUNDLE_ROOT/README-OFFLINE.md"
    return
  fi
  if ! cmd_check; then
    read -r -p "Check reported issues. Continue anyway? [y/N]: " c || true
    [[ "$(lc "${c:-}")" == "y" ]] || exit 1
  fi
  read -r -p "Run DB setup now? [y/N]: " rdb || true
  if [[ "$(lc "${rdb:-}")" == "y" ]]; then
    cmd_db
  fi
  cmd_configure
  read -r -p "Start backend + frontend now? [Y/n]: " rst || true
  if [[ "$(lc "${rst:-y}")" != "n" ]]; then
    sleep 1
    cmd_start
  fi
  echo ""
  echo "Done. Next: source $ENV_FILE && ... or ./install-offline.sh start"
  echo "README: $BUNDLE_ROOT/README-OFFLINE.md"
}

cmd_install_psql() {
  echo "=== Install PostgreSQL client (psql) only ==="
  ensure_psql_client
}

usage() {
  echo "Usage: $0 check|install-psql|db|configure|start|stop|status|all|start-frontend|stop-frontend"
  exit 1
}

main() {
  local sub="${1:-}"
  [[ -n "$sub" ]] || usage
  case "$(lc "$sub")" in
    check) cmd_check ;;
    install-psql) cmd_install_psql ;;
    db) cmd_db ;;
    configure) cmd_configure ;;
    start) cmd_start ;;
    stop) cmd_stop ;;
    status) cmd_status ;;
    all) cmd_all ;;
    start-frontend|frontend-start) cmd_start_frontend ;;
    stop-frontend|frontend-stop) cmd_stop_frontend ;;
    *) usage ;;
  esac
}

main "${1:-}"
