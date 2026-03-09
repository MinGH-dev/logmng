#!/usr/bin/env bash
# dev 서비스 start/stop/restart (frontend | backend | db | all)
# 사용법: ./scripts/dev-services.sh <frontend|backend|db|all> <start|stop|restart>
# db: PostgreSQL (Homebrew postgresql@16, 포트 5432)

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
DEV_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
FRONTEND_PORT="${FRONTEND_PORT:-3001}"
BACKEND_PORT="${BACKEND_PORT:-9200}"
DB_PORT="${DB_PORT:-5432}"
POSTGRES_SERVICE="${POSTGRES_SERVICE:-postgresql@16}"
JAR_NAME="logmng-backend-1.0.0.jar"
LOGS_DIR="$DEV_ROOT/logs"

mkdir -p "$LOGS_DIR"

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
  cd "$DEV_ROOT/backend"
  if [ ! -f "target/$JAR_NAME" ]; then
    echo "[...] Building backend (mvn clean package -DskipTests)..."
    mvn clean package -DskipTests -q
  fi
  mkdir -p logs
  nohup java -jar "target/$JAR_NAME" >> logs/backend-stdout.log 2>&1 &
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
      *)       echo "Usage: $0 backend {start|stop|restart}"; exit 1 ;;
    esac
    ;;
  frontend)
    case "${2:-}" in
      start)   start_frontend ;;
      stop)    stop_frontend ;;
      restart) stop_frontend; start_frontend ;;
      *)       echo "Usage: $0 frontend {start|stop|restart}"; exit 1 ;;
    esac
    ;;
  db)
    case "${2:-}" in
      start)   start_db ;;
      stop)    stop_db ;;
      restart) restart_db ;;
      *)       echo "Usage: $0 db {start|stop|restart}"; exit 1 ;;
    esac
    ;;
  all)
    case "${2:-}" in
      start)   start_db; start_backend; start_frontend ;;
      stop)    stop_backend; stop_frontend; stop_db ;;
      restart) stop_backend; stop_frontend; stop_db; start_db; start_backend; start_frontend ;;
      *)       echo "Usage: $0 all {start|stop|restart}"; exit 1 ;;
    esac
    ;;
  *)
    echo "Usage: $0 <frontend|backend|db|all> <start|stop|restart>"
    exit 1
    ;;
esac
