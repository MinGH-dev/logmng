#!/usr/bin/env bash
# List untracked files under .cursor/, docs/, specs/ so they can be added if intended.
# Usage: ./scripts/check-untracked-docs.sh

set -e
cd "$(dirname "$0")/.."

echo "Untracked files under .cursor/, docs/, specs/:"
echo "---"
untracked=$(git status -u --short .cursor docs specs 2>/dev/null | grep '^??' || true)
if [ -z "$untracked" ]; then
  echo "(none)"
  exit 0
fi
echo "$untracked"
echo "---"
echo "If any should be committed, run: git add <path> then commit."
