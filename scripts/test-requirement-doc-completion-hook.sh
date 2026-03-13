#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
HOOK_SCRIPT="$PROJECT_ROOT/.cursor/hooks/after-requirement-doc-complete.sh"
TEST_ROOT="$(mktemp -d)"
TEST_REQ_DIR="$TEST_ROOT/docs/requirements"
TOPIC_INDEX="$TEST_REQ_DIR/TOPIC-INDEX.md"
TEMP_DOC="$TEST_REQ_DIR/99999999-hook-test-temp.md"
AUTH_DOC="$TEST_REQ_DIR/99999998-login-hook-test.md"
UNRELATED_DOC="$TEST_ROOT/docs/template/REQUIREMENT_TEMPLATE.md"
STATE_DIR="$(mktemp -d)"
LOG_PATH="$(mktemp)"

cleanup() {
  rm -f "$LOG_PATH"
  rm -rf "$STATE_DIR" "$TEST_ROOT"
}
trap cleanup EXIT

run_hook() {
  local target_path="$1"
  printf '{"file_path":"%s"}' "$target_path" |
    CURSOR_REQUIREMENTS_DIR="$TEST_REQ_DIR" \
    CURSOR_TOPIC_INDEX_PATH="$TOPIC_INDEX" \
    CURSOR_REQUIREMENT_HOOK_STATE_DIR="$STATE_DIR" \
    CURSOR_REQUIREMENT_HOOK_LOG_PATH="$LOG_PATH" \
    bash "$HOOK_SCRIPT" >/dev/null
}

assert_log_count() {
  local expected_count="$1"
  local actual_count

  actual_count=$(wc -l < "$LOG_PATH" | tr -d '[:space:]')
  if [ "$actual_count" != "$expected_count" ]; then
    echo "Expected $expected_count trigger(s), got $actual_count." >&2
    exit 1
  fi
}

assert_index_contains_once() {
  local expected_line="$1"
  local actual_count

  actual_count=$(python3 - "$TOPIC_INDEX" "$expected_line" <<'PY'
from pathlib import Path
import sys
path = Path(sys.argv[1])
needle = sys.argv[2]
count = sum(1 for line in path.read_text().splitlines() if line == needle)
print(count)
PY
)
  if [ "$actual_count" != "1" ]; then
    echo "Expected TOPIC-INDEX to contain exactly one line: $expected_line" >&2
    exit 1
  fi
}

mkdir -p "$TEST_REQ_DIR" "$(dirname "$UNRELATED_DOC")"

cat > "$TOPIC_INDEX" <<'EOF'
# Requirements Topic Index

For test coverage only.

## auth | logout | 로그인 | 로그아웃

## misc | bugfix | pretty | highlighting

EOF

cat > "$TEMP_DOC" <<'EOF'
# 99999999 - Hook test temp

## 1. User requirement

### Requirement description
Manual test summary for requirement completion hook.

## 4. Checklist

### Documentation
- [ ] Requirement doc completed
EOF

cat > "$AUTH_DOC" <<'EOF'
# 99999998 - Login hook test

## 1. User requirement

### Requirement description
Login flow hook summary for auth topic matching.

## 4. Checklist

### Documentation
- [ ] Requirement doc completed
EOF

cat > "$UNRELATED_DOC" <<'EOF'
# Unrelated template
EOF

run_hook "$TEMP_DOC"
assert_log_count 0

run_hook "$UNRELATED_DOC"
assert_log_count 0

python3 - "$TEMP_DOC" <<'PY'
from pathlib import Path
path = Path(__import__("sys").argv[1])
text = path.read_text()
path.write_text(text.replace("- [ ] Requirement doc completed", "- [x] Requirement doc completed"))
PY

run_hook "$TEMP_DOC"
assert_log_count 1
assert_index_contains_once "- 99999999-hook-test-temp | Manual test summary for requirement completion hook."

run_hook "$TEMP_DOC"
assert_log_count 1
assert_index_contains_once "- 99999999-hook-test-temp | Manual test summary for requirement completion hook."

python3 - "$TEMP_DOC" <<'PY'
from pathlib import Path
path = Path(__import__("sys").argv[1])
text = path.read_text()
path.write_text(text.replace("- [x] Requirement doc completed", "- [ ] Requirement doc completed"))
PY

run_hook "$TEMP_DOC"
assert_log_count 1

python3 - "$TEMP_DOC" <<'PY'
from pathlib import Path
path = Path(__import__("sys").argv[1])
text = path.read_text()
path.write_text(text.replace("- [ ] Requirement doc completed", "- [x] Requirement doc completed"))
PY

run_hook "$TEMP_DOC"
assert_log_count 2
assert_index_contains_once "- 99999999-hook-test-temp | Manual test summary for requirement completion hook."

python3 - "$AUTH_DOC" <<'PY'
from pathlib import Path
path = Path(__import__("sys").argv[1])
text = path.read_text()
path.write_text(text.replace("- [ ] Requirement doc completed", "- [x] Requirement doc completed"))
PY

run_hook "$AUTH_DOC"
assert_log_count 3
assert_index_contains_once "- 99999998-login-hook-test | Login flow hook summary for auth topic matching."

printf '{}' | CURSOR_REQUIREMENT_HOOK_STATE_DIR="$STATE_DIR" CURSOR_REQUIREMENT_HOOK_LOG_PATH="$LOG_PATH" bash "$HOOK_SCRIPT" >/dev/null
assert_log_count 3

echo "requirement-doc completion hook tests passed"
