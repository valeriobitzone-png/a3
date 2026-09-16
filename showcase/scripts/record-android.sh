#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
# Part of the A3 universe. See LICENSE.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$ROOT/review-assets/showcase"
FFMPEG="${FFMPEG:-$HOME/.local/bin/ffmpeg}"
ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
cd "$ROOT"
mkdir -p "$OUT"

echo "== assemble showcase APK =="
./gradlew :showcase:assembleDebug

APK="$ROOT/showcase/android/build/outputs/apk/debug/showcase-debug.apk"
if [[ ! -f "$APK" ]]; then
  APK="$(find "$ROOT/showcase/android/build/outputs/apk" -name "*.apk" | head -n 1)"
fi
echo "apk=$APK"
"$ADB" install -r "$APK"
"$ADB" shell svc power stayon true || true
"$ADB" shell input keyevent KEYCODE_WAKEUP || true

echo "== per-level screenshots (Phone 3) =="
for level in all glass axis motion dynamic shaders sensory; do
  "$ADB" shell am force-stop a3.showcase
  sleep 0.4
  "$ADB" shell am start -n a3.showcase/.MainActivity --es a3_level "$level"
  sleep 2.2
  "$ADB" exec-out screencap -p > "$OUT/${level}.png"
  echo "wrote $OUT/${level}.png ($(wc -c < "$OUT/${level}.png") bytes)"
done

echo "== full session screenrecord =="
"$ADB" shell rm -f /sdcard/showcase-android.mp4 || true
"$ADB" shell am force-stop a3.showcase
sleep 0.4
"$ADB" shell screenrecord --time-limit 40 /sdcard/showcase-android.mp4 &
REC_PID=$!
sleep 1
"$ADB" shell am start -n a3.showcase/.MainActivity --ez a3_record true
wait "$REC_PID" || true
"$ADB" pull /sdcard/showcase-android.mp4 "$OUT/showcase-android.mp4"
echo "video $(wc -c < "$OUT/showcase-android.mp4") bytes"

echo "== pull session logs =="
sleep 1
EXT="/sdcard/Android/data/a3.showcase/files"
"$ADB" pull "$EXT/showcase-haptic.json" "$OUT/showcase-haptic.json" || true
"$ADB" pull "$EXT/showcase-announce.json" "$OUT/showcase-announce.json" || true
"$ADB" pull "$EXT/showcase-session.wav" "$OUT/showcase-session.wav" || true

AUDIO_NOTE="$OUT/AUDIO.txt"
if "$ADB" shell screenrecord --help 2>&1 | grep -qi microphone; then
  echo "screenrecord may support --microphone; this capture used default (no mic)." > "$AUDIO_NOTE"
else
  echo "adb screenrecord on this device does not capture internal playback or microphone." > "$AUDIO_NOTE"
fi
echo "Session tones are the PCM the showcase actually emitted (audio.json morph token)." >> "$AUDIO_NOTE"
echo "Converted to showcase-audio.m4a when a session WAV exists." >> "$AUDIO_NOTE"

if [[ -f "$OUT/showcase-session.wav" ]]; then
  "$FFMPEG" -y -i "$OUT/showcase-session.wav" -c:a aac -b:a 128k "$OUT/showcase-audio.m4a"
  echo "wrote showcase-audio.m4a from session PCM tape"
else
  echo "No session WAV on device; synthesizing mapping tape from tokens (same oscillator, not a live mic)." >> "$AUDIO_NOTE"
fi

"$ADB" shell svc power stayon false || true
echo "android recording done"
