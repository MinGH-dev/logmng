#!/bin/bash
# Pre-commit hook: regenerate treemap HTML when tool files change.
# Installed by scripts/setup-hooks.sh

set -e

TOOL_DIRS=".cursor/rules/ .cursor/skills/ .cursor/commands/ .cursor/agents/ docs/workflow/ docs/template/"

changed=0
for dir in $TOOL_DIRS; do
  if git diff --cached --name-only | grep -q "^${dir}"; then
    changed=1
    break
  fi
done

if [ "$changed" -eq 1 ]; then
  echo "[pre-commit] Tool files changed — regenerating treemap..."
  if command -v node >/dev/null 2>&1; then
    PROJECT_ROOT="$(git rev-parse --show-toplevel)"
    node "$PROJECT_ROOT/scripts/generate-treemap.js"
    git add "$PROJECT_ROOT/docs/cursor-tools-treemap.html"
    echo "[pre-commit] treemap updated and staged."
  else
    echo "[pre-commit] WARNING: node not found — skipping treemap generation."
  fi
fi
