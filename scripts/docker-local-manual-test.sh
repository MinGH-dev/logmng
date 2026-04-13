#!/usr/bin/env bash
#
# 로컬에서 Docker 스택을 빌드·기동해 브라우저/curl로 직접 테스트할 때 사용합니다.
# LDAP·브라우저 E2E 자동화는 포함하지 않습니다.
#
# 중요 — 소스 코드 반영:
#   - backend/·frontend/ 를 수정한 뒤 **실행 중인 컨테이너**에 반영하려면 **반드시 오프라인 번들(dist) 재빌드**가 필요합니다.
#   - ./scripts/docker-dev-sync.sh 또는 본 스크립트의 **sync** 가 그 작업입니다(mvn/npm 빌드 → dist → Docker 이미지 재빌드).
#   - **restart** 는 **이미 있는 dist/** 만으로 이미지를 다시 빌드합니다. dist를 갱신하지 않았다면 **이전 JAR·정적 파일**이 그대로 들어갑니다.
#
# 사전: Docker + Compose(docker compose 또는 docker-compose), 인터넷 있는 환경에서 번들 빌드 시 npm/mvn
#
# 사용법 (저장소 루트에서):
#   chmod +x scripts/docker-local-manual-test.sh
#   ./scripts/docker-local-manual-test.sh up          # 번들 생성 + DB 초기화 + 백엔드/프론트 기동
#   VERSION=1.0.1 SKIP_BUNDLE_BUILD=1 ./scripts/docker-local-manual-test.sh up   # 이미 dist 있을 때
#   SKIP_DB_INIT=1 SKIP_BUNDLE_BUILD=1 ./scripts/docker-local-manual-test.sh up  # DB 볼륨 유지·이미지만 재빌드
#   ./scripts/docker-local-manual-test.sh sync      # ★ 소스 변경 후 Docker 반영: 번(dist) 재빌드 + backend/frontend 컨테이너 재생성 (= docker-dev-sync.sh)
#   ./scripts/docker-local-manual-test.sh restart   # 기존 dist만으로 이미지 재빌드·재기동(소스 수정 직후에는 sync 권장)
#   RESTART_DB_INIT=1 ./scripts/docker-local-manual-test.sh restart  # 드물게 db-init까지 다시 돌릴 때
#   ./scripts/docker-local-manual-test.sh down        # 컨테이너 중지
#   ./scripts/docker-local-manual-test.sh smoke     # 헬스 확인(curl)
#   ./scripts/docker-local-manual-test.sh test-backend   # Docker 컨테이너에서 mvn test (프로필 mvn-test)
#   MVN_ARGS='-Dtest=LogDbServiceTest' ./scripts/docker-local-manual-test.sh test-backend   # 테스트 클래스만(Compose가 MVN_ARGS 전달)
#   ./scripts/docker-local-manual-test.sh test-frontend # Docker 컨테이너에서 npm ci && npm test (프로필 npm-test)
#   ./scripts/docker-local-manual-test.sh test-all      # 백엔드 테스트 후 프론트 테스트(순차, 하나라도 실패 시 비정상 종료)
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

run_restart() {
  if [[ ! -d "$DIST_DIR" ]]; then
    echo "[docker-local-manual-test] 오류: $DIST_DIR 가 없습니다. ./scripts/build-offline-bundle.sh 를 실행하거나 VERSION을 맞추세요."
    exit 1
  fi

  ensure_env_file
  load_env_docker
  export_for_compose

  # backend depends_on postgres health — 항상 postgres를 먼저 기동(스택 전체가 내려간 뒤에도 동일하게 복구).
  echo "[docker-local-manual-test] Postgres 기동(healthy 대기)..."
  compose up -d postgres

  # 기본: DB 초기화 생략(Postgres 볼륨·데이터 유지). 드물게 스키마/시드를 다시 넣을 때만 RESTART_DB_INIT=1.
  if [[ "${RESTART_DB_INIT:-0}" == "1" ]]; then
    echo "[docker-local-manual-test] RESTART_DB_INIT=1 — db-init 실행..."
    compose --profile init run --rm db-init
  else
    echo "[docker-local-manual-test] SKIP_DB_INIT=1 (기본) — db-init 생략, backend·frontend만 재빌드·재기동"
  fi

  echo "[docker-local-manual-test] 백엔드·프론트 이미지 빌드 및 재기동 (docker compose … up -d --build backend frontend)..."
  compose up -d --build backend frontend

  echo ""
  echo "[docker-local-manual-test] restart 완료. 수동 테스트 URL:"
  echo "  - UI:    http://localhost:3001/"
  echo "  - API:   http://localhost:9200/api/health"
  echo "  - DB체크: http://localhost:9200/api/db/test"
  echo ""
  echo "로그: ./scripts/docker-local-manual-test.sh logs"
  echo ""
  echo "[docker-local-manual-test] 참고: restart는 dist/를 다시 만들지 않습니다. 방금 backend·frontend 소스를 고쳤다면:"
  echo "         ./scripts/docker-local-manual-test.sh sync   (또는 ./scripts/docker-dev-sync.sh)"
}

run_sync() {
  echo "[docker-local-manual-test] sync — 소스→dist→Docker 이미지 동기화 (./scripts/docker-dev-sync.sh 호출)..."
  VERSION="$VERSION" NO_TAR="${NO_TAR:-1}" "$ROOT/scripts/docker-dev-sync.sh"
}

run_test_backend() {
  ensure_env_file
  load_env_docker
  export_for_compose
  export MVN_ARGS="${MVN_ARGS:-}"
  echo "[docker-local-manual-test] test-backend: docker compose … --profile mvn-test build mvn-test && run --rm mvn-test (호스트 mvn 아님)"
  if [[ -n "${MVN_ARGS}" ]]; then
    echo "[docker-local-manual-test] MVN_ARGS=${MVN_ARGS}"
  fi
  compose --profile mvn-test build mvn-test
  compose --profile mvn-test run --rm mvn-test
}

run_test_frontend() {
  ensure_env_file
  load_env_docker
  export_for_compose
  echo "[docker-local-manual-test] test-frontend: docker compose … --profile npm-test build npm-test && run --rm npm-test (호스트 npm test 아님)"
  compose --profile npm-test build npm-test
  compose --profile npm-test run --rm npm-test
}

run_test_all() {
  run_test_backend
  run_test_frontend
}

case "$cmd" in
  up) run_up ;;
  down) run_down ;;
  logs) run_logs ;;
  smoke) run_smoke ;;
  restart) run_restart ;;
  sync) run_sync ;;
  test-backend) run_test_backend ;;
  test-frontend) run_test_frontend ;;
  test-all) run_test_all ;;
  *)
    echo "사용법: $0 up | down | sync | restart | logs [서비스명] | smoke | test-backend | test-frontend | test-all"
    echo ""
    echo "  sync — backend/frontend 소스 변경 후 Docker에 반영: build-offline-bundle + compose up --build (db-init 생략)."
    echo "         (= ./scripts/docker-dev-sync.sh)  VERSION·NO_TAR 환경변수 동일하게 전달됩니다."
    echo "  restart — 기존 dist/만으로 이미지 재빌드·재기동. 소스만 바꾸고 sync 안 하면 이전 바이너리가 그대로입니다."
    echo "           RESTART_DB_INIT=1 이면 postgres 기동 후 db-init 한 번 실행한 뒤 backend+frontend 기동."
    echo "  test-backend / test-frontend — Compose 프로필(mvn-test / npm-test)로 컨테이너에서 테스트 실행."
    echo "  test-all — test-backend 후 test-frontend 순차 실행(어느 한 단계라도 실패 시 종료 코드 비0)."
    exit 1
    ;;
esac
