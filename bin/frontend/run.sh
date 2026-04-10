#!/usr/bin/env bash
# Deploy: static UI + JDK static-server JAR. Foreground: ./run.sh
# Lifecycle (TC-07): ./run.sh start | stop | status
# Env: PORT (default 3001), JAVA_OPTS, LOGMNG_API_BASE_URL — see ../README.md and docs/contract.md
set -euo pipefail
DIR="$(cd "$(dirname "$0")" && pwd)"
JAR="$DIR/logmng-static-server-1.0.0.jar"
WWW="$DIR/www"
PORT="${PORT:-3001}"
PID_FILE="$DIR/.logmng-frontend.pid"
LOG_DIR="${LOG_DIR:-$DIR/logs}"

ensure_files() {
  if [[ ! -f "$JAR" ]]; then
    echo "Missing: $JAR" >&2
    echo "On a build machine run: ./scripts/package-airgap-bin.sh" >&2
    exit 1
  fi
  if [[ ! -f "$WWW/index.html" ]]; then
    echo "Missing: $WWW/index.html (static build output)" >&2
    exit 1
  fi
}

cmd_start() {
  ensure_files
  mkdir -p "$LOG_DIR"
  if command -v lsof >/dev/null 2>&1 && lsof -ti ":$PORT" >/dev/null 2>&1; then
    echo "Frontend already listening on port $PORT."
    return 0
  fi
  nohup java ${JAVA_OPTS:-} -jar "$JAR" "$WWW" "$PORT" >>"$LOG_DIR/deploy-frontend-nohup.log" 2>&1 &
  echo $! >"$PID_FILE"
  echo "Frontend started pid=$(cat "$PID_FILE") port=$PORT (log: $LOG_DIR/deploy-frontend-nohup.log)"
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
    p=$(lsof -ti ":$PORT" 2>/dev/null) || true
    if [[ -n "$p" ]]; then
      echo "$p" | xargs kill -9 2>/dev/null || true
      did=true
    fi
  fi
  if [[ "$did" == true ]]; then
    echo "Frontend stopped."
  else
    echo "Frontend was not running."
  fi
}

cmd_status() {
  if command -v lsof >/dev/null 2>&1 && lsof -ti ":$PORT" >/dev/null 2>&1; then
    echo "Frontend: running (listening on port $PORT)."
    exit 0
  fi
  echo "Frontend: stopped (port $PORT)."
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
    ensure_files
    exec java ${JAVA_OPTS:-} -jar "$JAR" "$WWW" "$PORT"
    ;;
esac
