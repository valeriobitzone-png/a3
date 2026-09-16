#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
# SPDX-FileCopyrightText: 2026 A3 contributors

# Harvest dumpsys gfxinfo framestats from a physical Android device.
# Does not invent numbers. Exits non-zero if no device is attached.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/review-assets/perf"
ADB="${ADB:-$(command -v adb || true)}"
PKG="${PKG:-a3.showcase}"
ACTIVITY="${ACTIVITY:-a3.showcase.MainActivity}"
PROFILE="${1:-HIGH}"
SCENE="${2:-catalog}"

mkdir -p "$OUT"
if [[ -z "$ADB" ]]; then
  echo "adb not found" >&2
  exit 2
fi
DEVICES="$("$ADB" devices | awk 'NR>1 && $2=="device" {print $1}')"
if [[ -z "$DEVICES" ]]; then
  echo "NO_DEVICE" | tee "$OUT/android-device-missing.txt"
  echo "Nothing Phone (3) not attached. Refusing to invent gfxinfo." >&2
  exit 3
fi

MODEL="$("$ADB" shell getprop ro.product.model | tr -d '\r')"
echo "device_model=$MODEL" | tee "$OUT/android-device.txt"
echo "device_id=<redacted-device-id>" | tee -a "$OUT/android-device.txt"

"$ADB" shell am force-stop "$PKG" || true
"$ADB" shell dumpsys gfxinfo "$PKG" reset >/dev/null || true
"$ADB" shell am start -n "$PKG/$ACTIVITY" \
  --es a3_profile "$PROFILE" \
  --es a3_level "$( [[ "$SCENE" == overlay ]] && echo overlay || echo all )" \
  >/dev/null

# Operator must scroll catalog or expand overlay during this window.
echo "Interact now (${SCENE}, profile=${PROFILE}) for ~4s…" >&2
sleep 4

DUMP="$OUT/android-${SCENE}-${PROFILE}-gfxinfo.txt"
{
  echo "model=$MODEL"
  echo "profile=$PROFILE"
  echo "scene=$SCENE"
  echo "method=dumpsys gfxinfo framestats"
  "$ADB" shell dumpsys gfxinfo "$PKG"
} | tee "$DUMP" >/dev/null
"$ADB" shell dumpsys gfxinfo "$PKG" framestats >> "$DUMP"
echo "wrote $DUMP"
