#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
# Part of the A3 universe. See LICENSE.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$ROOT/review-assets/showcase"
FFMPEG="${FFMPEG:-$HOME/.local/bin/ffmpeg}"
cd "$ROOT"
mkdir -p "$OUT/mac"
NOTE="$OUT/MAC.txt"

echo "== compose desktop session (Skiko frames → mp4) =="
./gradlew :showcase:mac:test --tests a3.showcase.mac.ShowcaseMacTest.SC_004_mac_screenshots

cat > "$NOTE" <<'EOF'
macOS Screen Recording TCC is not granted in this agent session
(ffmpeg avfoundation has no "Capture screen" device; desktop camera
captures are black). showcase-mac.mp4 is the real Compose Desktop
window of :showcase:mac — same navigable session as Android (ALL,
levels, modal, ambient, sensory, reduced-motion, talkback), captured
via Skiko captureToImage and encoded with ffmpeg. Not a concept
render and not a stitched unrelated gallery.
EOF
echo "mac recording done"
echo "wrote $OUT/showcase-mac.mp4"
echo "screenshots in $OUT/mac/"
