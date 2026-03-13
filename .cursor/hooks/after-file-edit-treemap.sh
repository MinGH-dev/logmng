#!/usr/bin/env bash
# Cursor afterFileEdit hook: regenerate treemap when tool/config files that affect the treemap are edited.
# Triggered by .cursor/hooks.json "afterFileEdit". Cursor may run from an arbitrary cwd; we cd to project root.
# See .cursor/rules/treemap-consistency.mdc.

set -e
input=$(cat)
# Extract file_path from JSON (e.g. {"file_path": "/abs/path/to/file", "edits": [...]})
file_path=$(printf '%s' "$input" | sed -n 's/.*"file_path"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')
if [ -z "$file_path" ]; then
  exit 0
fi

# Paths that affect treemap output (same as pre-commit-treemap.sh + script/template/i18n)
case "$file_path" in
  */.cursor/rules/*|*/.cursor/agents/*|*/.cursor/commands/*|*/.cursor/skills/*|\
  */docs/workflow/*|*/docs/template/*|\
  */scripts/generate-treemap.js|*/scripts/treemap-template.html|*/scripts/treemap-i18n.json)
    if command -v node >/dev/null 2>&1; then
      # Resolve project root from this script's path: .cursor/hooks/this-file -> project root
      script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
      project_root=$(cd "$script_dir/../.." && pwd)
      (cd "$project_root" && node scripts/generate-treemap.js) 2>/dev/null || true
    fi
    ;;
esac
exit 0
