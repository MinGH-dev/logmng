#!/usr/bin/env bash
# Deploy: fat JAR in this directory. Foreground: ./run.sh [spring-args...]
# Lifecycle (TC-07): ./run.sh start | stop | status
# Env: JAVA_OPTS, SPRING_*, APP_*, SERVER_PORT (default 9200), LOGGING_FILE_NAME — see ../README.md and docs/contract.md
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
JAR="$DIR/logmng-backend-1.0.2.jar"
PID_FILE="$DIR/.logmng-backend.pid"
BACKEND_PORT="${SERVER_PORT:-9200}"
LOG_DIR="${LOG_DIR:-$DIR/logs}"

ensure_jar() {
  if [[ ! -f "$JAR" ]]; then
    echo "Missing: $JAR" >&2
    echo "On a build machine run: ./scripts/package-airgap-bin.sh" >&2
    exit 1
  fi
}

cmd_start() {
  ensure_jar
  mkdir -p "$LOG_DIR"
  if command -v lsof >/dev/null 2>&1 && lsof -ti ":$BACKEND_PORT" >/dev/null 2>&1; then
    echo "Backend already listening on port $BACKEND_PORT."
    return 0
  fi
  # Nohup log is process stdout/stderr; Spring file log follows LOGGING_FILE_NAME / logging.file.name.
  nohup env SERVER_PORT="${SERVER_PORT:-$BACKEND_PORT}" java ${JAVA_OPTS:-} -jar "$JAR" >>"$LOG_DIR/deploy-nohup.log" 2>&1 &
  echo $! >"$PID_FILE"
  echo "Backend started pid=$(cat "$PID_FILE") port=${SERVER_PORT:-$BACKEND_PORT} (nohup log: $LOG_DIR/deploy-nohup.log)"
}

cmd_stop() {
  local did=false
  if [[ -f "$PID_FILE" ]]; then
    local pid
    pid="$(cat "$PID_FILE")"
    if kill -0 "$pid" 2>/dev/null; then
      kill "$pid" 2>/dev/null || true
      did=true
    fi
    rm -f "$PID_FILE"
  fi
  if command -v lsof >/dev/null 2>&1; then
    local p
    p=$(lsof -ti ":$BACKEND_PORT" 2>/dev/null) || true
    if [[ -n "$p" ]]; then
      echo "$p" | xargs kill -9 2>/dev/null || true
      did=true
    fi
  fi
  if [[ "$did" == true ]]; then
    echo "Backend stopped."
  else
    echo "Backend was not running."
  fi
}

cmd_status() {
  if command -v lsof >/dev/null 2>&1 && lsof -ti ":$BACKEND_PORT" >/dev/null 2>&1; then
    echo "Backend: running (listening on port $BACKEND_PORT)."
    exit 0
  fi
  echo "Backend: stopped (port $BACKEND_PORT)."
  exit 1
}

case "${1:-}" in
  start)
    cmd_start
    ;;
  stop)
    cmd_stop
    ;;
  status)
    cmd_status
    ;;
  *)
    ensure_jar
    exec java ${JAVA_OPTS:-} -jar "$JAR" "$@"
    ;;
esac
