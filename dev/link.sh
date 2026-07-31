#!/usr/bin/env bash
# Symlink both packs into a local test world so you edit here and /reload in-game.
#
#   ./dev/link.sh <world-folder-name> [minecraft-dir]
#
# Default minecraft dir is the macOS location. Symlinks, never copies — copying is how
# you end up debugging a version of the pack you already fixed.
set -euo pipefail

WORLD="${1:-}"
MC="${2:-$HOME/Library/Application Support/minecraft}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

[ -n "$WORLD" ] || { echo "usage: ./dev/link.sh <world-folder-name> [minecraft-dir]" >&2; exit 1; }

SAVE="$MC/saves/$WORLD"
[ -d "$SAVE" ] || { echo "no such world: $SAVE" >&2; echo "create it in-game first (cheats on)." >&2; exit 1; }

mkdir -p "$SAVE/datapacks" "$MC/resourcepacks"
ln -sfn "$ROOT/datapack"     "$SAVE/datapacks/heartstead"
ln -sfn "$ROOT/resourcepack" "$MC/resourcepacks/heartstead"

cat <<EOF
linked:
  $SAVE/datapacks/heartstead -> $ROOT/datapack
  $MC/resourcepacks/heartstead -> $ROOT/resourcepack

in-game:
  /gamerule sendCommandFeedback true
  /reload                          picks up functions, loot tables, recipes, advancements
  restart the world                required for dialog changes — they do NOT hot-reload
EOF
