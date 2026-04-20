#!/usr/bin/env bash
#
# Run on a machine WITH internet (or with images already present). Produces a single tar for docker load on an air-gapped host.
# After transfer: docker load -i logmng-docker-airgap-*.tar
# Then: docker compose --env-file .env.docker -f docker/docker-compose.yml --project-directory . up -d --pull never …
#
# Usage (repo root):
#   ./scripts/docker-export-images-for-airgap.sh
#   OUT=./dist/my-images.tar SKIP_PULL=1 ./scripts/docker-export-images-for-airgap.sh
#
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

DIST_VERSION="${DIST_VERSION:-1.0.2}"
OUT="${OUT:-$ROOT/dist/logmng-docker-airgap-${DIST_VERSION}-$(date +%Y%m%d).tar}"
SKIP_PULL="${SKIP_PULL:-0}"
SKIP_COMPOSE_BUILD="${SKIP_COMPOSE_BUILD:-0}"

ENVF=( )
if [[ -f "$ROOT/.env.docker" ]]; then
  ENVF=( --env-file "$ROOT/.env.docker" )
elif [[ -f "$ROOT/.env.docker.example" ]]; then
  ENVF=( --env-file "$ROOT/.env.docker.example" )
fi

compose() {
  if docker compose version >/dev/null 2>&1; then
    docker compose "${ENVF[@]}" -f docker/docker-compose.yml --project-directory "$ROOT" "$@"
  elif command -v docker-compose >/dev/null 2>&1; then
    docker-compose "${ENVF[@]}" -f docker/docker-compose.yml --project-directory "$ROOT" "$@"
  else
    echo "[docker-export-airgap] ERROR: docker compose or docker-compose not found"
    exit 1
  fi
}

IMAGES=( "postgres:16" "eclipse-temurin:17-jre" )

if [[ "$SKIP_PULL" != "1" ]]; then
  echo "[docker-export-airgap] Pulling base images..."
  for img in "${IMAGES[@]}"; do
    docker pull "$img"
  done
else
  echo "[docker-export-airgap] SKIP_PULL=1 — not pulling (images must exist locally)"
fi

if [[ "$SKIP_COMPOSE_BUILD" != "1" ]]; then
  DIST_DIR="dist/logmng-offline-${DIST_VERSION}"
  if [[ ! -d "$DIST_DIR" ]]; then
    echo "[docker-export-airgap] ERROR: $DIST_DIR missing. Run: VERSION=${DIST_VERSION} ./scripts/build-offline-bundle.sh"
    exit 1
  fi
  export DIST_VERSION
  export OFFLINE_ROOT="./dist/logmng-offline-${DIST_VERSION}"
  echo "[docker-export-airgap] Building backend + frontend images (requires network for Dockerfile apt-get unless base layers cached)..."
  compose build backend frontend
else
  echo "[docker-export-airgap] SKIP_COMPOSE_BUILD=1 — not building application images"
fi

# Compose project name is `logmng-local` (see docker-compose.yml). Image names: logmng-local-backend, logmng-local-frontend
for svc in backend frontend; do
  if docker image inspect "logmng-local-${svc}:latest" >/dev/null 2>&1; then
    IMAGES+=( "logmng-local-${svc}:latest" )
  else
    echo "[docker-export-airgap] WARN: image logmng-local-${svc}:latest not found — run without SKIP_COMPOSE_BUILD=1 or build manually"
  fi
done

mkdir -p "$(dirname "$OUT")"
echo "[docker-export-airgap] Saving ${#IMAGES[@]} image(s) to: $OUT"
docker save -o "$OUT" "${IMAGES[@]}"
echo "[docker-export-airgap] Done. On air-gapped host: docker load -i $(basename "$OUT")"
echo "[docker-export-airgap] Then use: docker compose --env-file .env.docker -f docker/docker-compose.yml --project-directory . up -d --pull never postgres postgres-pb postgres-imagelog"
echo "[docker-export-airgap]        … --profile init run --rm db-init  …  up -d --pull never backend frontend"
