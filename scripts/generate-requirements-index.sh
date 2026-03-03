#!/bin/bash
# List requirement docs that are NOT yet in TOPIC-INDEX.md.
# Run after adding new requirements to see what needs to be added.
# Usage: ./scripts/generate-requirements-index.sh

set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REQ_DIR="$SCRIPT_DIR/../docs/requirements"
INDEX="$REQ_DIR/TOPIC-INDEX.md"

echo "Requirements not in TOPIC-INDEX.md:"
echo ""

missing=0
for f in "$REQ_DIR"/*.md; do
  [ -f "$f" ] || continue
  base=$(basename "$f" .md)
  [ "$base" = "TOPIC-INDEX" ] && continue
  if ! grep -q "$base" "$INDEX" 2>/dev/null; then
    title=$(grep -m1 "^# " "$f" 2>/dev/null | sed 's/^# //' || echo "(no title)")
    echo "  - $base | $title"
    missing=$((missing + 1))
  fi
done

if [ "$missing" -eq 0 ]; then
  echo "  (all docs are in TOPIC-INDEX)"
fi
echo ""
echo "Add the above to docs/requirements/TOPIC-INDEX.md under the appropriate ## topic section."
