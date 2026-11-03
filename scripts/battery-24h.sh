#!/usr/bin/env bash
# 24-hour idle battery measurement for Edge Bar. Only run this if you have approved it.
#
#   scripts/battery-24h.sh start    # resets batterystats; then UNPLUG and leave idle 24 h
#   scripts/battery-24h.sh report   # after 24 h, reconnect and collect Edge Bar's usage
#
# Battery stats only accumulate while the phone is on battery, so USB must stay
# unplugged during the test (wireless adb also keeps the radio awake: avoid it).
set -euo pipefail
PKG=com.local.edgebar
OUT="$(cd "$(dirname "$0")/.." && pwd)/docs/results"
mkdir -p "$OUT"
case "${1:-}" in
  start)
    adb shell settings get secure enabled_accessibility_services | grep -q "$PKG/" \
      || { echo "Enable the Edge Bar accessibility service first." >&2; exit 1; }
    adb shell dumpsys batterystats --reset
    date -Iseconds > "$OUT/.battery-start"
    echo "Stats reset at $(cat "$OUT/.battery-start")."
    echo "Now unplug USB, lock the phone and leave it idle for 24 hours, then run: $0 report"
    ;;
  report)
    UID_NUM="$(adb shell cmd package list packages -U "$PKG" | tr -d '\r' | sed -n 's/.*uid:\([0-9]*\).*/\1/p' | head -1)"
    [ -n "$UID_NUM" ] || { echo "$PKG not installed" >&2; exit 1; }
    APPID=$((UID_NUM % 100000)); U="u0a$((APPID - 10000))"
    F="$OUT/battery-$(date +%Y%m%d-%H%M%S)"
    adb shell dumpsys batterystats > "$F-full.txt"
    adb shell dumpsys batterystats "$PKG" > "$F-pkg.txt"
    {
      echo "# Edge Bar 24 h idle battery result"
      echo "Started: $(cat "$OUT/.battery-start" 2>/dev/null || echo unknown)   Collected: $(date -Iseconds)"
      echo "UID $UID_NUM ($U)"
      echo '```'
      grep -E "Time on battery|Discharge|Computed drain|actual drain" "$F-full.txt" | head -8 || true
      echo "--- Edge Bar estimated power use:"
      grep -E "^\s+(UID )?$U[: ]" "$F-full.txt" | head -5 || echo "(no $U line: usage below reporting threshold)"
      echo "--- Edge Bar CPU / wakelocks:"
      grep -iE "cpu|wake lock|wakeup" "$F-pkg.txt" | head -15 || true
      echo '```'
    } | tee "$F.md"
    echo "Full dumps: $F-full.txt, $F-pkg.txt"
    ;;
  *) echo "usage: $0 start|report" >&2; exit 2 ;;
esac
