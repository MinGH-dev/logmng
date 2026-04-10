#!/usr/bin/env bash
#
# 배포용 산출물 생성 (인터넷·Maven·npm 있는 빌드 PC에서 실행).
#
# Usage (repo root):
#   ./scripts/release-build.sh              # 기본: 폐쇄망 번들 dist/logmng-offline-${VERSION}.tar.gz (바이너리는 번들 내부 bin/)
#   ./scripts/release-build.sh bin          # bin/ 만 (tar 생략, 빠른 반복 빌드용)
#
# Environment (optional):
#   REACT_APP_API_BASE_URL — 프론트 빌드 시 기본 API 베이스 (런타임은 LOGMNG_API_BASE_URL 로 덮어쓰기 가능)
#   VERSION — offline 번들 이름/폴더 (기본 1.0.1)
#   NO_TAR=1 — offline 모드에서 디렉터리만 생성, tar 생략
#
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

MODE="${1:-offline}"

case "$MODE" in
  bin|package)
    exec "$ROOT/scripts/package-airgap-bin.sh"
    ;;
  offline|tarball|airgap)
    exec "$ROOT/scripts/build-offline-bundle.sh"
    ;;
  -h|--help|help)
    echo "Usage: $0 [offline|bin]"
    echo "  (default) offline — Full air-gap tarball under dist/ (bin/ inside bundle only; npm + mvn + tar)"
    echo "  bin              — Fill bin/ only (no dist tarball)"
    exit 0
    ;;
  *)
    echo "Unknown mode: $MODE" >&2
    echo "Use: $0 [offline|bin]   default: offline   (or $0 --help)" >&2
    exit 1
    ;;
esac
