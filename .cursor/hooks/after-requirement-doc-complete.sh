#!/usr/bin/env bash
# Cursor afterFileEdit hook: run requirement-finalization maintenance only when a
# single requirement doc transitions from incomplete to complete.

set -euo pipefail

input=$(cat)
file_path=$(printf '%s' "$input" | sed -n 's/.*"file_path"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')

if [ -z "$file_path" ]; then
  exit 0
fi

script_dir=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
project_root=$(cd "$script_dir/../.." && pwd)
requirements_dir="${CURSOR_REQUIREMENTS_DIR:-$project_root/docs/requirements}"
topic_index_path="${CURSOR_TOPIC_INDEX_PATH:-$requirements_dir/TOPIC-INDEX.md}"
index_script="${CURSOR_REQUIREMENT_INDEX_SCRIPT:-$project_root/scripts/generate-requirements-index.sh}"

case "$file_path" in
  "$requirements_dir"/*.md) ;;
  *) exit 0 ;;
esac

if [ "$file_path" = "$topic_index_path" ]; then
  exit 0
fi

file_name=$(basename "$file_path")
case "$file_name" in
  [0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]-*.md) ;;
  *) exit 0 ;;
esac

if [ ! -r "$file_path" ]; then
  exit 0
fi

completion_line_count=$(grep -Ec '^[[:space:]]*-[[:space:]]*\[[ xX]\][[:space:]]+Requirement doc completed([[:space:]].*)?$' "$file_path" || true)
if [ "$completion_line_count" -ne 1 ]; then
  exit 0
fi

current_state=""
if grep -Eq '^[[:space:]]*-[[:space:]]*\[[xX]\][[:space:]]+Requirement doc completed([[:space:]].*)?$' "$file_path"; then
  current_state="complete"
elif grep -Eq '^[[:space:]]*-[[:space:]]*\[[[:space:]]\][[:space:]]+Requirement doc completed([[:space:]].*)?$' "$file_path"; then
  current_state="incomplete"
else
  exit 0
fi

state_dir="${CURSOR_REQUIREMENT_HOOK_STATE_DIR:-$project_root/.git/.cursor-hook-state/requirement-doc-complete}"
if ! mkdir -p "$state_dir" 2>/dev/null; then
  exit 0
fi

if command -v shasum >/dev/null 2>&1; then
  state_key=$(printf '%s' "$file_path" | shasum -a 256 | awk '{print $1}')
else
  state_key=$(printf '%s' "$file_path" | cksum | awk '{print $1}')
fi
state_file="$state_dir/$state_key.state"

previous_state=""
if [ -r "$state_file" ]; then
  previous_state=$(tr -d '\r\n' < "$state_file")
fi

printf '%s\n' "$current_state" > "$state_file"

if [ "$current_state" != "complete" ] || [ "$previous_state" = "complete" ]; then
  exit 0
fi

if [ -n "${CURSOR_REQUIREMENT_HOOK_LOG_PATH:-}" ]; then
  printf '%s\n' "$file_path" >> "$CURSOR_REQUIREMENT_HOOK_LOG_PATH"
fi

if [ -n "${CURSOR_REQUIREMENT_HOOK_ACTION:-}" ]; then
  HOOK_REQUIREMENT_DOC_PATH="$file_path" sh -c "$CURSOR_REQUIREMENT_HOOK_ACTION" || true
  exit 0
fi

REQUIREMENTS_DIR_OVERRIDE="$requirements_dir" TOPIC_INDEX_PATH_OVERRIDE="$topic_index_path" bash "$index_script" --doc "$file_path" || true
exit 0
