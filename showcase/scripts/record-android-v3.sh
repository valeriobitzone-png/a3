#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="$ROOT/review-assets/showcase-v3"
FFMPEG="${FFMPEG:-$HOME/.local/bin/ffmpeg}"
ADB="${ADB:-$HOME/Library/Android/sdk/platform-tools/adb}"
SERIAL="${SERIAL:-<redacted-device-id>}"
cd "$ROOT"
mkdir -p "$OUT"

echo "== assemble showcase APK =="
./gradlew :showcase:assembleDebug --offline

APK="$ROOT/showcase/android/build/outputs/apk/debug/showcase-debug.apk"
echo "apk=$APK"
"$ADB" -s "$SERIAL" install -r "$APK"
"$ADB" -s "$SERIAL" shell svc power stayon true || true
"$ADB" -s "$SERIAL" shell input keyevent KEYCODE_WAKEUP || true

shot() {
  local extras="$1"
  local name="$2"
  "$ADB" -s "$SERIAL" shell am force-stop a3.showcase
  sleep 0.4
  "$ADB" -s "$SERIAL" shell am start -n a3.showcase/.MainActivity $extras
  sleep 2.4
  "$ADB" -s "$SERIAL" exec-out screencap -p > "$OUT/$name"
  echo "wrote $OUT/$name ($(wc -c < "$OUT/$name") bytes)"
}

echo "== v3 screenshots (Phone 3) =="
shot "--ez a3_ambient true" "scena-completa.png"
shot "" "provenance.png"
shot "--ez a3_reduced_motion true" "reduced-motion.png"
shot "--ez a3_glass false" "blur-off.png"

echo "== v3 screenrecord =="
"$ADB" -s "$SERIAL" shell rm -f /sdcard/showcase-v3-android.mp4 || true
"$ADB" -s "$SERIAL" shell am force-stop a3.showcase
sleep 0.4
"$ADB" -s "$SERIAL" shell screenrecord --time-limit 18 /sdcard/showcase-v3-android.mp4 &
REC_PID=$!
sleep 1
"$ADB" -s "$SERIAL" shell am start -n a3.showcase/.MainActivity --ez a3_ambient true
sleep 4
"$ADB" -s "$SERIAL" shell input tap 180 2400 || true
sleep 2
"$ADB" -s "$SERIAL" shell am force-stop a3.showcase
sleep 0.3
"$ADB" -s "$SERIAL" shell am start -n a3.showcase/.MainActivity --ez a3_reduced_motion true
sleep 4
"$ADB" -s "$SERIAL" shell am force-stop a3.showcase
sleep 0.3
"$ADB" -s "$SERIAL" shell am start -n a3.showcase/.MainActivity --ez a3_glass false --ez a3_silent true
wait "$REC_PID" || true
"$ADB" -s "$SERIAL" pull /sdcard/showcase-v3-android.mp4 "$OUT/showcase-v3-android.mp4"
echo "video $(wc -c < "$OUT/showcase-v3-android.mp4") bytes"

"$ADB" -s "$SERIAL" shell svc power stayon false || true
echo "android v3 recording done"
