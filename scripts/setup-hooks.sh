#!/bin/bash
# Install git hooks for the project.
# Usage: ./scripts/setup-hooks.sh

set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
HOOKS_DIR="$PROJECT_ROOT/.git/hooks"

if [ ! -d "$HOOKS_DIR" ]; then
  echo "Error: .git/hooks directory not found. Is this a git repository?"
  exit 1
fi

PRE_COMMIT="$HOOKS_DIR/pre-commit"
TREEMAP_HOOK="$SCRIPT_DIR/pre-commit-treemap.sh"

install_hook() {
  if [ -f "$PRE_COMMIT" ] && ! grep -q "pre-commit-treemap" "$PRE_COMMIT"; then
    echo "" >> "$PRE_COMMIT"
    echo "# Treemap auto-generation hook" >> "$PRE_COMMIT"
    echo ". \"$TREEMAP_HOOK\"" >> "$PRE_COMMIT"
    echo "Appended treemap hook to existing pre-commit."
  elif [ ! -f "$PRE_COMMIT" ]; then
    cat > "$PRE_COMMIT" << EOF
#!/bin/bash
# Treemap auto-generation hook
. "$TREEMAP_HOOK"
EOF
    echo "Created pre-commit hook with treemap generation."
  else
    echo "Treemap hook already installed in pre-commit."
  fi
  chmod +x "$PRE_COMMIT"
  chmod +x "$TREEMAP_HOOK"
}

install_hook
echo "Done. Hooks installed in $HOOKS_DIR"
