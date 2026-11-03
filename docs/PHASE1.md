# Phase 1: gesture spike

## What the spike contains

- `EdgeBarAccessibilityService`: the only long-running component.
- `EdgeHandleView`: a plain `View` in a `TYPE_ACCESSIBILITY_OVERLAY` window, 16dp × 160dp by
  default (200dp hard cap). It sets `setSystemGestureExclusionRects` on its full bounds,
  fires a light haptic on grab, and opens after a 40dp inward, mostly horizontal swipe.
- **Back-gesture fallback (`TriggerMode.PILL`)**: moves the touch strip inward (default 12dp)
  and draws a visible thin pill that opens on a swipe *or* a tap.
- A plain-View test panel with a full-screen scrim. Both windows are added on open and
  removed on close. The panel logs gesture-to-first-frame latency.
- `scripts/phase1-gesture-test.sh`: the automated device test (20 swipes per edge, Back
  outside the handle, touch pass-through, latency).
- `TestControlReceiver`: an adb-only hook (protected by `android.permission.DUMP`) the
  script uses to switch edges and modes.

## Result: NOT TESTED ON A DEVICE

The spike was written in a cloud build container that has no USB and no `adb` device,
so the on-device steps could not be run:

| Step | Status |
|---|---|
| Install spike on phone | **Not run**: no device reachable from the build container |
| Enable accessibility service | **Not run** |
| 20/20 inward swipes, right edge | **Untested** |
| 20/20 inward swipes, left edge | **Untested** |
| Back gesture still works outside the handle | **Untested** |
| Fallback pill mode | **Built** (`TriggerMode.PILL`, settings toggle), **untested** |

The container also could not compile the APK, because its network policy blocks
`dl.google.com` / `maven.google.com` (Android SDK, Android Gradle Plugin, AndroidX).
See `docs/BUILD.md`.

The pure gesture and geometry logic (`core/GestureMath.kt`, `core/Geometry.kt`) is
covered by JVM unit tests that pass, but those do not prove behaviour on Nothing OS.

### Default trigger decision

The edge-only trigger stays the default because Android honours up to 200dp of
gesture exclusion per edge and the handle is 160dp. The visible inward pill is one
toggle away (Settings → HANDLE → Trigger → Pill). **If the device test shows fewer
than 20/20 opens on either edge in EDGE mode, make PILL the default**
(`EdgeBarSettings.triggerMode`) and rerun with `MODE=PILL scripts/phase1-gesture-test.sh`.

## How to run Phase 1 on the phone

```bash
scripts/phase1-gesture-test.sh app/build/outputs/apk/release/app-release.apk
```

The script stops and asks you to enable the service, then writes a report to
`docs/results/`. Finish with a short manual check using your thumb: open from both
edges, and do a Back swipe above and below the handle.
