#!/usr/bin/env bash
#
# 로컬에서 Docker 스택을 빌드·기동해 브라우저/curl로 직접 테스트할 때 사용합니다.
# LDAP·브라우저 E2E 자동화는 포함하지 않습니다.
#
# 사전: Docker + Compose(docker compose 또는 docker-compose), 인터넷 있는 환경에서 번들 빌드 시 npm/mvn
#
# 사용법 (저장소 루트에서):
#   chmod +x scripts/docker-local-manual-test.sh
#   ./scripts/docker-local-manual-test.sh up          # 번들 생성 + DB 초기화 + 백엔드/프론트 기동
#   VERSION=1.0.1 SKIP_BUNDLE_BUILD=1 ./scripts/docker-local-manual-test.sh up   # 이미 dist 있을 때
#   SKIP_DB_INIT=1 SKIP_BUNDLE_BUILD=1 ./scripts/docker-local-manual-test.sh up  # DB 볼륨 유지·이미지만 재빌드
#   ./scripts/docker-local-manual-test.sh down        # 컨테이너 중지
#   ./scripts/docker-local-manual-test.sh smoke     # 헬스 확인(curl)
#
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

VERSION="${VERSION:-1.0.1}"
DIST_DIR="dist/logmng-offline-${VERSION}"

compose() {
  # Compose file interpolation (${POSTGRES_PUBLISH_PORT}, ${OFFLINE_ROOT}, …) reads the process
  # environment. Also pass --env-file so standalone docker-compose matches `docker compose` behavior.
  local envfile=( )
  if [[ -f "$ROOT/.env.docker" ]]; then
    envfile=( --env-file "$ROOT/.env.docker" )
  fi
  if docker compose version >/dev/null 2>&1; then
    docker compose "${envfile[@]}" -f docker/docker-compose.yml --project-directory "$ROOT" "$@"
  else
    docker-compose "${envfile[@]}" -f docker/docker-compose.yml --project-directory "$ROOT" "$@"
  fi
}

cmd="${1:-up}"

ensure_env_file() {
  if [[ ! -f .env.docker ]]; then
    if [[ ! -f .env.docker.example ]]; then
      echo "[docker-local-manual-test] 오류: .env.docker.example 이 없습니다."
      exit 1
    fi
    cp .env.docker.example .env.docker
    echo "[docker-local-manual-test] 생성: .env.docker (.env.docker.example 복사, 로컬 개발용 플레이스홀더)"
    echo "[docker-local-manual-test] 프로덕션 비밀번호·ENCRYPTION_KEY 는 반드시 교체하세요."
  fi
}

load_env_docker() {
  if [[ -f .env.docker ]]; then
    set -a
    # shellcheck disable=SC1091
    . ./.env.docker
    set +a
  fi
}

export_for_compose() {
  export DIST_VERSION="$VERSION"
  export OFFLINE_ROOT="./dist/logmng-offline-${VERSION}"
}

run_up() {
  if [[ "${SKIP_BUNDLE_BUILD:-0}" != "1" ]]; then
    echo "[docker-local-manual-test] 오프라인 번들 빌드 중 (VERSION=${VERSION})..."
    VERSION="$VERSION" ./scripts/build-offline-bundle.sh
  else
    echo "[docker-local-manual-test] SKIP_BUNDLE_BUILD=1 — 번들 빌드 생략"
  fi

  if [[ ! -d "$DIST_DIR" ]]; then
    echo "[docker-local-manual-test] 오류: $DIST_DIR 가 없습니다. ./scripts/build-offline-bundle.sh 를 실행하거나 VERSION을 맞추세요."
    exit 1
  fi

  ensure_env_file
  load_env_docker
  export_for_compose

  echo "[docker-local-manual-test] PostgreSQL 기동..."
  compose up -d postgres
  if [[ "${SKIP_DB_INIT:-0}" == "1" ]]; then
    echo "[docker-local-manual-test] SKIP_DB_INIT=1 — db-init 생략 (이미 초기화된 볼륨에서 이미지만 재빌드할 때)"
  else
    echo "[docker-local-manual-test] DB 초기화 (logmng / pbfep / imagelog) — 최초 1회. 이미 스키마가 있으면 실패할 수 있습니다."
    compose --profile init run --rm db-init
  fi

  echo "[docker-local-manual-test] 백엔드·프론트 이미지 빌드 및 기동..."
  compose up -d --build backend frontend

  echo ""
  echo "[docker-local-manual-test] 완료. 수동 테스트 URL:"
  echo "  - UI:    http://localhost:3001/"
  echo "  - API:   http://localhost:9200/api/health"
  echo "  - DB체크: http://localhost:9200/api/db/test"
  echo ""
  echo "로그: compose logs -f backend   또는   ./scripts/docker-local-manual-test.sh logs"
}

run_down() {
  ensure_env_file
  load_env_docker
  export_for_compose
  compose down
  echo "[docker-local-manual-test] 중지됨. Postgres 데이터까지 지우려면: docker volume rm logmng-local_pgdata (이름은 docker volume ls 로 확인)"
}

run_logs() {
  ensure_env_file
  load_env_docker
  export_for_compose
  if [[ -n "${2:-}" ]]; then
    compose logs -f "$2"
  else
    compose logs -f
  fi
}

run_smoke() {
  echo "[docker-local-manual-test] smoke (curl)..."
  curl -sf "http://localhost:9200/api/health" | head -c 200 || echo " — /api/health 실패"
  echo ""
  curl -sf "http://localhost:9200/api/db/test" | head -c 200 || echo " — /api/db/test 실패"
  echo ""
  code="$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:3001/")"
  echo "프론트 HTTP 상태 코드: ${code}"
}

case "$cmd" in
  up) run_up ;;
  down) run_down ;;
  logs) run_logs ;;
  smoke) run_smoke ;;
  *)
    echo "사용법: $0 up | down | logs [서비스명] | smoke"
    exit 1
    ;;
esac
