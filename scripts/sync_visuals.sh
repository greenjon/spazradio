#!/usr/bin/env bash
# Refreshes lsd-visuals/ (the Liquid LSD visuals module and the web TV it bundles) from a liquid-lsd checkout.
# The copy is committed, so builds never depend on the liquid-lsd checkout; re-run this and commit whenever
# liquid-lsd's web/ or android/lsd-visuals/ changes.  Usage: scripts/sync_visuals.sh [path/to/liquid-lsd]
set -euo pipefail
here="$(cd "$(dirname "$0")/.." && pwd)"
src="${1:-${LIQUID_LSD:-$here/../../projects/liquid-lsd}}"
[ -x "$src/scripts/sync_lsd_visuals.sh" ] || { echo "no scripts/sync_lsd_visuals.sh under $src (pass the liquid-lsd path)" >&2; exit 1; }
exec "$src/scripts/sync_lsd_visuals.sh" "$here"
