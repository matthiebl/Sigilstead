#!/usr/bin/env bash
# Build both packs into dist/ as independent zips.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DIST="$ROOT/dist"

rm -rf "$DIST"
mkdir -p "$DIST"

pack() {
  local src="$1" name="$2"
  [ -f "$ROOT/$src/pack.mcmeta" ] || { echo "missing $src/pack.mcmeta" >&2; exit 1; }
  ( cd "$ROOT/$src" && zip -qr "$DIST/$name.zip" . -x '.*' -x '**/.*' )
  echo "  $name.zip"
}

echo "building into dist/"
pack datapack     heartstead-datapack
pack resourcepack heartstead-resourcepack

# Reminder, because these are numbered independently and copying one into the other
# silently produces an incompatible pack. See docs/REFERENCES.md.
echo
echo "formats — datapack: $(grep -o '"min_format":[^,]*' "$ROOT/datapack/pack.mcmeta")" \
     "| resourcepack: $(grep -o '"min_format":[^,]*' "$ROOT/resourcepack/pack.mcmeta")"
