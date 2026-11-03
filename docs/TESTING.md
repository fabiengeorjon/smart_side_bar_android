# Device testing checklist (Nothing Phone 3, gesture navigation)

None of this has been run yet. Record real results in `docs/results/`, and do not tick a
box you did not observe.

## Automated (adb)

```bash
scripts/phase1-gesture-test.sh dist/edge-bar.apk
```
Measures, for each edge: 20 inward swipes, open latency, 5 Back gestures outside
the handle, and touch pass-through after close.

If EDGE mode is below 20/20 on either edge, run the PILL fallback, then make PILL the default:
```bash
MODE=PILL scripts/phase1-gesture-test.sh
```

## Manual (use your thumb, not adb)

- [ ] Right edge: 20 inward swipes open the panel
- [ ] Left edge: 20 inward swipes open the panel
- [ ] Back swipe above and below the handle still goes back (both edges)
- [ ] Haptic tick when grabbing the handle
- [ ] Panel slides in fast with no bounce; scrim tap closes it; swipe toward the edge closes it
- [ ] Back gesture while the panel is open closes the panel
- [ ] After closing, tapping where the panel was reaches the app underneath
- [ ] Pinned app launches; panel is gone before the app appears
- [ ] Uninstall a pinned app → its pin disappears on the next panel open
- [ ] Calculator opens the default calculator
- [ ] Torch toggles and the tile shows ON/OFF state
- [ ] Screenshot does not contain the panel or the handle
- [ ] Lock screen locks
- [ ] Keyboard open (e.g. Messages) → handle hidden; keyboard closed → handle back
- [ ] YouTube full-screen video → handle hidden
- [ ] A full-screen game → handle hidden (if not, add the game under HIDE and retest)
- [ ] Rotate to landscape and back: handle stays on the chosen edge, clear of the camera cutout
- [ ] Change settings (side, height, width, position, pill) → the live handle updates
- [ ] Reboot → service comes back with the same settings
- [ ] Disable the service → handle disappears, nothing crashes; re-enable → handle returns
- [ ] Diagnostics shows service state, handle bounds/size, last gesture, latency, failures

## Measurements to report (never estimate)

| Target | How | Result |
|---|---|---|
| Gesture → first visible panel frame ≤ 100 ms | logcat `latency_ms`, or Diagnostics | not measured |
| Touches reach app in former panel area | script pass-through step plus manual check | not measured |
| 24 h idle battery (only if approved) | `scripts/battery-24h.sh start` / `report` | not measured |

Latency is measured in-app from the moment the 40dp threshold is crossed to the
`registerFrameCommitCallback` of the first frame in which the panel is on screen (frame
submitted to the compositor). Display scan-out adds up to one refresh interval
(about 8 ms at 120 Hz).
