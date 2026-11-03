#!/usr/bin/env bash
# Copies the Liquid LSD web TV (the visuals behind the radio) into app/src/main/assets/visuals/.
# The copy is committed, so builds never depend on the liquid-lsd checkout; re-run this and commit
# whenever the web app changes.  Usage: scripts/sync_visuals.sh [path/to/liquid-lsd]
set -euo pipefail
here="$(cd "$(dirname "$0")/.." && pwd)"
src="${1:-${LIQUID_LSD:-$here/../../projects/liquid-lsd}}"
web="$src/web"
dest="$here/app/src/main/assets/visuals"
[ -f "$web/index.html" ] || { echo "no web/index.html under $src (pass the liquid-lsd path)" >&2; exit 1; }

mkdir -p "$dest"
# Only what embed mode loads: no checks, tools, service worker, manifest or icons, and no fonts (only the
# web TV's chat blackboard uses them, and embed never shows it). The chat's .js files stay: ui.js imports them.
rsync -a --delete --checksum \
  --exclude 'checks/' --exclude 'tools/' --exclude 'sw.js' --exclude 'manifest.webmanifest' \
  --exclude '*.png' --exclude '*.jpg' --exclude '*.ico' --exclude 'fonts/' \
  "$web/" "$dest/"
# The photo gallery background (gallery.js): liquid-lsd's built web/gallery/ at phone size (768 px), bundled so it
# works offline. The rsync above leaves *.jpg alone; this writes gallery/ and gallery.json and drops stale photos.
python3 "$src/scripts/gallery_curate.py" export "$dest" 768
git -C "$src" rev-parse HEAD > "$dest/VERSION"
echo "synced $(find "$dest" -type f | wc -l) files, $(du -sh "$dest" | cut -f1), liquid-lsd $(cut -c1-9 "$dest/VERSION")"
