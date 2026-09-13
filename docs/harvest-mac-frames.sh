#!/usr/bin/env bash
# On-screen Mac harvest: overlay NSVisualEffectView CADisplayLink + catalog Compose Metal.
# Writes review-assets/perf/mac-{overlay-expanded,catalog}-{HIGH,MID,BLUR_OFF}.txt
# MUST NOT use OverlayCompositor CPU wall time as the display dump.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
OUT="$ROOT/review-assets/perf"
mkdir -p "$OUT"
FRAMES="${FRAMES:-320}"

./gradlew :overlay:mac:compileNative --no-daemon -Pkotlin.compiler.execution.strategy=in-process
BIN="$ROOT/overlay/mac/build/overlay-mac"
if [[ ! -x "$BIN" ]]; then
  echo "missing native overlay $BIN" >&2
  exit 2
fi

for profile in HIGH MID BLUR_OFF; do
  echo "harvest overlay expanded $profile ($FRAMES frames)…" >&2
  "$BIN" --expanded --profile "$profile" --scene overlay --frames "$FRAMES" \
    --log "$OUT/mac-overlay-expanded-${profile}.txt"
done

for profile in HIGH MID BLUR_OFF; do
  echo "harvest catalog $profile ($FRAMES frames)…" >&2
  ./gradlew :renderers:mac-compose:harvestMacCatalog --no-daemon \
    -Pkotlin.compiler.execution.strategy=in-process \
    -PharvestProfile="$profile" \
    -PharvestFrames="$FRAMES" \
    -PharvestLog="$OUT/mac-catalog-${profile}.txt"
done

echo "wrote $OUT/mac-overlay-expanded-*.txt and mac-catalog-*.txt"
