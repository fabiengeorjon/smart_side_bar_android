#!/usr/bin/env bash
# Edge Bar on-device gesture test (Phase 1 spike, and the post-release retest).
#
#   scripts/phase1-gesture-test.sh [path/to.apk]
#
# Needs: adb, exactly one connected device, gesture navigation enabled.
# What it measures (all from the device's own logcat, nothing estimated):
#   1. N inward swipes (default 20) on the RIGHT edge and on the LEFT edge -> opens / N
#   2. gesture-to-first-visible-panel-frame latency for every successful open
#   3. Back gesture started on the same edge but OUTSIDE the handle -> still navigates back
#   4. after the panel closes, a tap in the former panel area reaches the app below
# Swipes are injected with `adb shell input swipe`. Injected events go through the same
# system gesture handling as a finger, but finish with a manual check by hand (see docs/TESTING.md).
set -euo pipefail

PKG=com.local.edgebar
N=${N:-20}
APK=${1:-}
OUT_DIR="$(cd "$(dirname "$0")/.." && pwd)/docs/results"
mkdir -p "$OUT_DIR"
REPORT="$OUT_DIR/device-test-$(date +%Y%m%d-%H%M%S).md"

die() { echo "ERROR: $*" >&2; exit 1; }
sh_() { adb shell "$@" | tr -d '\r'; }
elog() { adb logcat -d -s EdgeBar:I EdgeBar:W 2>/dev/null | tr -d '\r'; }
top_activity() { local d; d=$(sh_ dumpsys activity activities); grep -E 'topResumedActivity|mResumedActivity' <<<"$d" | head -1 | sed 's/^ *//' || true; }
panel_windows() { local d; d=$(sh_ dumpsys window windows); grep -c -E 'EdgeBarPanel|EdgeBarScrim' <<<"$d" || true; }

command -v adb >/dev/null || die "adb not found (install platform-tools: sdkmanager 'platform-tools')"
[ "$(adb devices | awk 'NR>1 && $2=="device"' | wc -l)" = 1 ] || die "need exactly one authorised device (adb devices)"

echo "Device: $(sh_ getprop ro.product.manufacturer) $(sh_ getprop ro.product.model), Android $(sh_ getprop ro.build.version.release) (SDK $(sh_ getprop ro.build.version.sdk)), build $(sh_ getprop ro.build.display.id)"
NAV=$(sh_ settings get secure navigation_mode)
[ "$NAV" = 2 ] || echo "WARNING: navigation_mode=$NAV (2 = gesture navigation). Switch to gesture navigation for a valid test."

if [ -n "$APK" ]; then
  echo "Installing $APK"
  adb install -r "$APK"
fi

if ! sh_ settings get secure enabled_accessibility_services | grep -q "$PKG/"; then
  echo
  echo ">>> Enable the Edge Bar accessibility service on the phone now:"
  echo "    Settings > Accessibility > Edge Bar > Use Edge Bar"
  echo "    (installed with adb, so 'Allow restricted settings' should not be needed)"
  sh_ am start -a android.settings.ACCESSIBILITY_SETTINGS >/dev/null
  read -r -p "Press Enter when it is enabled... " _
  sh_ settings get secure enabled_accessibility_services | grep -q "$PKG/" || die "service still not enabled"
fi

configure() { # side mode -> sets L T R B W H
  adb logcat -c
  sh_ am broadcast -n "$PKG/.service.TestControlReceiver" -a com.local.edgebar.TEST_CONFIGURE --es side "$1" --es mode "$2" >/dev/null
  local line=""
  for _ in 1 2 3 4 5 6 7 8 9 10; do
    sleep 0.5
    line=$(elog | grep HANDLE_LAYOUT | grep "side=$1" | tail -1 || true)
    [ -n "$line" ] && break
  done
  [ -n "$line" ] || die "no HANDLE_LAYOUT log for side=$1 (is the service running?)"
  L=$(sed -E 's/.* left=([0-9]+).*/\1/' <<<"$line"); T=$(sed -E 's/.* top=([0-9]+).*/\1/' <<<"$line")
  R=$(sed -E 's/.* right=([0-9]+).*/\1/' <<<"$line"); B=$(sed -E 's/.* bottom=([0-9]+).*/\1/' <<<"$line")
  W=$(sed -E 's/.* screen=([0-9]+)x([0-9]+).*/\1/' <<<"$line"); H=$(sed -E 's/.* screen=([0-9]+)x([0-9]+).*/\2/' <<<"$line")
}

close_panel() { # tap the scrim on the far side, then verify the windows are gone
  local x=$1 y=$2
  for _ in 1 2 3; do
    [ "$(panel_windows)" = 0 ] && return 0
    sh_ input tap "$x" "$y"; sleep 0.5
  done
  [ "$(panel_windows)" = 0 ]
}

LAT_ALL=()
{
  echo "# Edge Bar device test — $(date -Iseconds)"
  echo
  echo "Device: $(sh_ getprop ro.product.model), Android $(sh_ getprop ro.build.version.release), build $(sh_ getprop ro.build.display.id), navigation_mode=$NAV"
  echo
} > "$REPORT"

run_side() {
  local side=$1 mode=$2
  configure "$side" "$mode"
  local y=$(( (T + B) / 2 )) x1 x2 farx
  if [ "$side" = RIGHT ]; then x1=$(( W - 2 )); x2=$(( W - 2 - W / 3 )); farx=$(( W / 10 ))
  else x1=1; x2=$(( W / 3 )); farx=$(( W - W / 10 )); fi
  [ "$mode" = PILL ] && x1=$(( (L + R) / 2 ))
  sh_ input keyevent KEYCODE_HOME; sleep 1

  local ok=0 fails=() lats=()
  for i in $(seq 1 "$N"); do
    adb logcat -c
    sh_ input swipe "$x1" "$y" "$x2" "$y" 180
    sleep 0.8
    local log; log=$(elog)
    if grep -q "GESTURE OPEN" <<<"$log"; then
      ok=$((ok + 1))
      local l; l=$(grep -o 'latency_ms=[0-9.]*' <<<"$log" | head -1 | cut -d= -f2 || true)
      [ -n "$l" ] && lats+=("$l") && LAT_ALL+=("$l")
    else
      fails+=("#$i: $(grep -m1 'NO_OPEN' <<<"$log" | sed 's/.*NO_OPEN //' || echo 'no handle event (touch never reached handle)')")
    fi
    close_panel "$farx" "$y" || fails+=("#$i: panel did not close")
  done

  # Back gesture on the same edge, outside the handle's vertical span.
  local yout
  if [ "$T" -gt $(( H / 2 )) ]; then yout=$(( H / 4 )); else yout=$(( H * 7 / 10 )); fi
  [ "$yout" -ge "$T" ] && [ "$yout" -le "$B" ] && yout=$(( B + (H - B) / 2 ))
  local bx1 bx2
  if [ "$side" = RIGHT ]; then bx1=$(( W - 2 )); bx2=$(( W - 2 - W / 3 )); else bx1=1; bx2=$(( W / 3 )); fi
  local back_ok=0
  for i in 1 2 3 4 5; do
    sh_ am start -W -a android.settings.SETTINGS >/dev/null; sleep 0.7
    sh_ am start -W -a android.settings.DISPLAY_SETTINGS >/dev/null; sleep 0.9
    local before after; before=$(top_activity)
    adb logcat -c
    sh_ input swipe "$bx1" "$yout" "$bx2" "$yout" 180; sleep 1
    after=$(top_activity)
    local blog; blog=$(elog)
    if [ "$before" != "$after" ] && ! grep -q "GESTURE OPEN" <<<"$blog"; then back_ok=$((back_ok + 1)); fi
    close_panel "$farx" "$yout" || true
  done

  # Touch pass-through in the former panel area.
  local tp="FAIL"
  sh_ am start -W -a android.settings.SETTINGS >/dev/null; sleep 1
  sh_ input swipe "$x1" "$y" "$x2" "$y" 180; sleep 0.8
  close_panel "$farx" "$y" || true
  local before; before=$(top_activity)
  local tx; if [ "$side" = RIGHT ]; then tx=$(( W - W / 7 )); else tx=$(( W / 7 )); fi
  sh_ input tap "$tx" $(( H * 2 / 5 )); sleep 1.2
  [ "$(panel_windows)" = 0 ] && [ "$before" != "$(top_activity)" ] && tp="PASS"
  sh_ input keyevent KEYCODE_HOME

  {
    echo "## $side edge, $mode mode"
    echo "- Handle bounds: left=$L top=$T right=$R bottom=$B (screen ${W}x${H})"
    echo "- Inward swipes that opened the panel: **$ok / $N**"
    for f in "${fails[@]:-}"; do [ -n "$f" ] && echo "  - $f"; done
    if [ "${#lats[@]}" -gt 0 ]; then
      echo "- Gesture → first visible panel frame (ms): $(printf '%s\n' "${lats[@]}" | sort -n | awk '{a[NR]=$1} END {printf "min %.1f / median %.1f / max %.1f (n=%d)", a[1], a[int((NR+1)/2)], a[NR], NR}')"
    fi
    echo "- Back gesture outside the handle navigated back: **$back_ok / 5**"
    echo "- Tap in former panel area reached the app after close: **$tp**"
    echo
  } | tee -a "$REPORT"
}

MODE=${MODE:-EDGE}
run_side RIGHT "$MODE"
run_side LEFT "$MODE"

if [ "${#LAT_ALL[@]}" -gt 0 ]; then
  over=$(printf '%s\n' "${LAT_ALL[@]}" | awk '$1>100' | wc -l)
  echo "Latency samples over 100 ms: $over of ${#LAT_ALL[@]}" | tee -a "$REPORT"
fi
echo
echo "Report written to $REPORT"
echo "Pass criteria: 20/20 opens on each edge, 5/5 Back outside the handle, PASS for pass-through."
echo "If EDGE mode fails, rerun with MODE=PILL $0"
