#!/usr/bin/env bash
# Configure git to use this repository's .githooks/ directory.
# Run once after cloning:
#
#   bash scripts/install-hooks.sh

set -euo pipefail

REPO_ROOT=$(git rev-parse --show-toplevel 2>/dev/null) || {
  echo "ERROR: not inside a git repository." >&2
  exit 1
}

HOOKS_DIR="$REPO_ROOT/.githooks"

if [ ! -d "$HOOKS_DIR" ]; then
  echo "ERROR: $HOOKS_DIR not found. Is the repository fully cloned?" >&2
  exit 1
fi

# Make all hooks executable
chmod +x "$HOOKS_DIR"/*

# Point git at the project hooks directory (requires git >= 2.9)
git config core.hooksPath "$HOOKS_DIR"

echo "Git hooks installed successfully."
echo ""
echo "Hooks directory : $HOOKS_DIR"
echo "Active hooks    :"
ls -1 "$HOOKS_DIR" | sed 's/^/  /'
echo ""
echo "Note: GNU grep (grep -P) is required for pattern matching."
echo "Linux users: already available."
echo "macOS users: brew install grep && echo 'export PATH=\"/opt/homebrew/opt/grep/libexec/gnubin:\$PATH\"' >> ~/.zshrc"
