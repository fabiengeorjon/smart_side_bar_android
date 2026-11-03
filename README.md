# Edge Bar

A Nothing-style edge sidebar for Android 13+ (built for the Nothing Phone 3 with gesture
navigation). Swipe inward from a narrow handle on the screen edge to open a black,
dot-matrix sidebar with pinned apps and quick tools: calculator, torch, screenshot and
lock screen. It's for personal sideloading, not Google Play.

> **Status:** the source is complete, but **no APK has been built or device-tested yet**.
> See [docs/REPORT.md](docs/REPORT.md) for the exact state, and
> [docs/BUILD.md](docs/BUILD.md) for how to build.

## Build and install

```bash
scripts/setup-sdk.sh
```
Installs the Android SDK; needs access to dl.google.com.

```bash
scripts/make-keystore.sh
```
Creates the signing key once, in `~/.edgebar/`. Back it up.

```bash
scripts/build-release.sh
```
Runs test, lint and assembleRelease, writes `dist/edge-bar.apk`, then verifies it.

```bash
adb install -r dist/edge-bar.apk
```

Then open **Edge Bar → SETUP → Enable Edge Bar** (Settings → Accessibility → Edge Bar).
If you installed by opening the APK file on the phone and the toggle is greyed out:
App info → ⋮ → **Allow restricted settings**, then enable it again.

## Design

| | |
|---|---|
| Only long-running component | `EdgeBarAccessibilityService` (`canRetrieveWindowContent=false`) |
| Windows | `TYPE_ACCESSIBILITY_OVERLAY` only: no `SYSTEM_ALERT_WINDOW`, foreground service or notification |
| Handle | plain `View`, 16 × 160 dp default (200 dp cap), gesture-exclusion rects, opens after a 40 dp inward swipe, haptic on grab |
| Back fallback | *Visible pill* trigger: moved inward, visible, opens on swipe or tap |
| Auto-hide | 1×1 non-touchable `WindowInsets` probe (keyboard, full-screen) plus an optional per-app hide list |
| Panel | Compose, created on open and destroyed on close, together with its scrim; 25–30 % width, min 88 dp |
| Settings | Preferences DataStore; survives service restarts and reboots |
| Permissions | none beyond the accessibility binding; no INTERNET |

Code map:

- `core/`: pure Kotlin settings model, codec, pin logic, gesture maths and geometry (unit-tested)
- `overlay/`: `OverlayController` (all windows), `EdgeHandleView`, `PanelSession`, `OverlayLifecycleOwner`, `InsetsProbe`
- `ui/`: the companion app (setup, handle with live preview, apps, tools, hide list, diagnostics) and the panel's Compose UI
- `scripts/`: SDK setup, signing key, release build, device gesture test, 24 h battery test

Fonts: [Doto](https://github.com/oliverlalan/Doto) © The Doto Project Authors, SIL Open
Font License 1.1 (`third_party/doto/OFL.txt`), downloaded from the official Google Fonts
repository. No Nothing fonts are bundled.
