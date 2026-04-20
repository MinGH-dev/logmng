#!/usr/bin/env bash
# dev 서비스 start/stop/restart/status (frontend | backend | db | all)
# 사용법: ./scripts/dev-services.sh <frontend|backend|db|all> <start|stop|restart|status>
# db: PostgreSQL (Homebrew postgresql@16, 포트 5432)
#
# Host CRA는 기본 FRONTEND_PORT=3002 — **3001은 Docker Compose 정적 UI 전용**으로 비워 두는 것을 권장한다.
# 로컬에서 검증 UI는 http://localhost:3001 (docker-dev-sync 후). CRA: http://localhost:3002
#
# 승인 흐름 진단 로그([diag-approval])를 켜려면 백엔드 재시작 시:
#   BACKEND_DIAGNOSTIC_APPROVAL=1 ./scripts/dev-services.sh backend restart
# (또는 미리 export APP_DIAGNOSTIC_APPROVAL_FLOW=true)

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
DEV_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
FRONTEND_PORT="${FRONTEND_PORT:-3002}"
BACKEND_PORT="${BACKEND_PORT:-9200}"
DB_PORT="${DB_PORT:-5432}"
POSTGRES_SERVICE="${POSTGRES_SERVICE:-postgresql@16}"
JAR_NAME="logmng-backend-1.0.2.jar"
LOGS_DIR="$DEV_ROOT/logs"

mkdir -p "$LOGS_DIR"

# Stale backend jar caused wrong API behavior (e.g. statistics KPIs) while DB had current data; rebuild when jar lags sources.
ensure_backend_jar_current() {
  local backend_dir="$DEV_ROOT/backend"
  local jar="$backend_dir/target/$JAR_NAME"
  local pom="$backend_dir/pom.xml"
  local src_java="$backend_dir/src/main/java"
  local need_build=false

  if [ ! -f "$jar" ]; then
    need_build=true
  elif [ -f "$pom" ] && [ "$pom" -nt "$jar" ]; then
    need_build=true
  elif [ -d "$src_java" ] && [ -n "$(find "$src_java" -name '*.java' -newer "$jar" 2>/dev/null | head -n 1)" ]; then
    need_build=true
  fi

  if [ "$need_build" = true ]; then
    echo "[...] Building backend (mvn package -DskipTests; jar missing or older than pom/src)..."
    mvn package -DskipTests -q -f "$pom"
  fi
}

kill_port() {
  local port=$1
  local name=$2
  local pids
  pids=$(lsof -ti ":$port" 2>/dev/null) || true
  if [ -n "$pids" ]; then
    echo "$pids" | xargs kill -9 2>/dev/null || true
    echo "[OK] $name (port $port) stopped."
  else
    echo "[--] $name (port $port) was not running."
  fi
}

start_backend() {
  if lsof -ti ":$BACKEND_PORT" >/dev/null 2>&1; then
    echo "[!!] Backend (port $BACKEND_PORT) is already running."
    return 0
  fi
  ensure_backend_jar_current
  cd "$DEV_ROOT/backend"
  mkdir -p logs
  local approval_diag="${APP_DIAGNOSTIC_APPROVAL_FLOW:-false}"
  if [ "${BACKEND_DIAGNOSTIC_APPROVAL:-}" = "1" ] || [ "${BACKEND_DIAGNOSTIC_APPROVAL:-}" = "true" ]; then
    approval_diag=true
    echo "[OK] APP_DIAGNOSTIC_APPROVAL_FLOW=true (BACKEND_DIAGNOSTIC_APPROVAL)"
  fi
  # Local dev: enable HR Sync PoC API by default (preview-only; apply stays off in application.yml).
  nohup env APP_DIAGNOSTIC_APPROVAL_FLOW="$approval_diag" HR_SYNC_POC_ENABLED="${HR_SYNC_POC_ENABLED:-true}" java -jar "target/$JAR_NAME" >> logs/backend-stdout.log 2>&1 &
  echo "[OK] Backend starting (port $BACKEND_PORT). Logs: backend/logs/"
}

start_frontend() {
  if lsof -ti ":$FRONTEND_PORT" >/dev/null 2>&1; then
    echo "[!!] Frontend (port $FRONTEND_PORT) is already running."
    return 0
  fi
  cd "$DEV_ROOT/frontend"
  export BROWSER=none
  export PORT="$FRONTEND_PORT"
  nohup npm start >> "$LOGS_DIR/frontend-stdout.log" 2>&1 &
  echo "[OK] Frontend starting (port $FRONTEND_PORT). Logs: $LOGS_DIR/frontend-stdout.log"
}

stop_backend() {
  kill_port "$BACKEND_PORT" "Backend"
}

stop_frontend() {
  kill_port "$FRONTEND_PORT" "Frontend"
}

# Exit 0 if something is listening on port; 1 otherwise (TC-06).
status_port() {
  local port=$1
  local name=$2
  if lsof -ti ":$port" >/dev/null 2>&1; then
    echo "[OK] $name is running (listening on port $port)."
    return 0
  fi
  echo "[--] $name is stopped (nothing listening on port $port)."
  return 1
}

status_backend() {
  status_port "$BACKEND_PORT" "Backend"
}

status_frontend() {
  status_port "$FRONTEND_PORT" "Frontend"
}

status_db() {
  if command -v pg_isready >/dev/null 2>&1 && pg_isready -h localhost -p "$DB_PORT" >/dev/null 2>&1; then
    echo "[OK] DB (PostgreSQL) is accepting connections on localhost:$DB_PORT."
    return 0
  fi
  echo "[--] DB (PostgreSQL) is not ready on localhost:$DB_PORT."
  return 1
}

# PostgreSQL (Homebrew). macOS + brew 전제.
start_db() {
  if command -v pg_isready >/dev/null 2>&1 && pg_isready -h localhost -p "$DB_PORT" >/dev/null 2>&1; then
    echo "[!!] DB (PostgreSQL port $DB_PORT) is already running."
    return 0
  fi
  if ! command -v brew >/dev/null 2>&1; then
    echo "[!!] 'brew' not found. Start PostgreSQL manually (e.g. pg_ctl or systemd)."
    return 1
  fi
  brew services start "$POSTGRES_SERVICE"
  echo "[OK] DB (PostgreSQL $POSTGRES_SERVICE, port $DB_PORT) starting. Check: pg_isready -h localhost -p $DB_PORT"
}

stop_db() {
  if ! command -v brew >/dev/null 2>&1; then
    echo "[!!] 'brew' not found. Stop PostgreSQL manually."
    return 1
  fi
  brew services stop "$POSTGRES_SERVICE"
  echo "[OK] DB (PostgreSQL $POSTGRES_SERVICE) stopped."
}

restart_db() {
  if ! command -v brew >/dev/null 2>&1; then
    echo "[!!] 'brew' not found. Restart PostgreSQL manually."
    return 1
  fi
  brew services restart "$POSTGRES_SERVICE"
  echo "[OK] DB (PostgreSQL $POSTGRES_SERVICE) restarted."
}

case "${1:-}" in
  backend)
    case "${2:-}" in
      start)   start_backend ;;
      stop)    stop_backend ;;
      restart) stop_backend; start_backend ;;
      status)  status_backend ;;
      *)       echo "Usage: $0 backend {start|stop|restart|status}"; exit 1 ;;
    esac
    ;;
  frontend)
    case "${2:-}" in
      start)   start_frontend ;;
      stop)    stop_frontend ;;
      restart) stop_frontend; start_frontend ;;
      status)  status_frontend ;;
      *)       echo "Usage: $0 frontend {start|stop|restart|status}"; exit 1 ;;
    esac
    ;;
  db)
    case "${2:-}" in
      start)   start_db ;;
      stop)    stop_db ;;
      restart) restart_db ;;
      status)  status_db ;;
      *)       echo "Usage: $0 db {start|stop|restart|status}"; exit 1 ;;
    esac
    ;;
  all)
    case "${2:-}" in
      start)   start_db; start_backend; start_frontend ;;
      stop)    stop_backend; stop_frontend; stop_db ;;
      restart) stop_backend; stop_frontend; stop_db; start_db; start_backend; start_frontend ;;
      status)
        rc=0
        status_db || rc=1
        status_backend || rc=1
        status_frontend || rc=1
        exit $rc
        ;;
      *)       echo "Usage: $0 all {start|stop|restart|status}"; exit 1 ;;
    esac
    ;;
  *)
    echo "Usage: $0 <frontend|backend|db|all> <start|stop|restart|status>"
    exit 1
    ;;
esac
