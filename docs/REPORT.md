# Edge Bar: build report

Date: 2026-11-03. Environment: Claude Code cloud container (Linux, JDK 21, Gradle 8.14.3,
no Android SDK, no USB).

## Bottom line

**No signed APK was produced, and nothing was tested on a phone.** The container's
network policy blocks `dl.google.com` (HTTP 403), and `maven.google.com` redirects there.
That host is the only source of the Android SDK, the Android Gradle Plugin and AndroidX.
The container also has no adb device. The full source, build configuration and scripts
are complete; see [BUILD.md](BUILD.md) for the commands that produce the APK.

## Deliverable

| Item | Value |
|---|---|
| APK path | `dist/edge-bar.apk`: **not produced** |
| Version | 1.0.0 (versionCode 1), `com.local.edgebar` |
| Size / SHA-256 | n/a (written to `dist/BUILD-INFO.txt` by `scripts/build-release.sh`) |

## Results

| Step | Result |
|---|---|
| `./gradlew test` | **Not run** (AGP not downloadable). Instead, the 28 unit tests in `app/src/test` (settings codec, pin logic, gesture maths, geometry) were compiled and run with the real `core/` sources in a plain Kotlin/JVM harness: **28 passed, 0 failed**. |
| `./gradlew lint` | **Not run** (needs AGP and the SDK) |
| `./gradlew assembleRelease` | **Not run** (same blocker) |
| `apksigner verify` | **Not run** (no APK) |
| Android source compile check | Not possible without android.jar/AndroidX. A Kotlin parse of the whole tree found no syntax errors, and the `core/` package compiled cleanly. Everything else is unverified until the first real build. |
| Release signing script | Tested in a sandbox: it creates a PKCS12 key, refuses to overwrite an existing one, and the key reads back with keytool. That test key was deleted. |

## Phase 1 gesture spike

Built (commit "Phase 1: gesture spike"), **untested on a device**. Details in
[PHASE1.md](PHASE1.md).

- 20/20 right-edge opens: **not measured**
- 20/20 left-edge opens: **not measured**
- Back still works outside the handle: **not measured**
- Final trigger method: **edge swipe (EDGE) remains the default**, with gesture
  exclusion on a 16 × 160 dp handle. The fallback **visible inward pill (PILL)** is built
  and one toggle away. Because EDGE is unverified, the decision rule stands: if the device
  test shows fewer than 20/20 on either edge, make PILL the default and retest.

## Measured acceptance criteria

| Target | Status |
|---|---|
| Gesture → first visible panel frame ≤ 100 ms | **Not measured.** Instrumented in-app (logcat `latency_ms`, Diagnostics screen) and collected by `scripts/phase1-gesture-test.sh`. |
| Touches reach the app in the former panel area | **Not measured.** By design both panel windows are removed on close; the script checks this. |
| 24 h idle battery | **Not measured, and not started** (needs the phone and your approval). Use `scripts/battery-24h.sh`. |
| Full-screen detection in YouTube and a game | **Not tested.** The per-app hide list is built pre-emptively because detection on Nothing OS is unverified. |

## Tested on a real device

Nothing. Every on-device behaviour (gestures, Back, panel, launches, torch, screenshot,
lock, keyboard/full-screen hiding, rotation, reboot restore, Allow-restricted-settings
flow) is untested. Checklist: [TESTING.md](TESTING.md).

## Installation (once built)

1. Build `dist/edge-bar.apk` (see BUILD.md), then run `adb install -r dist/edge-bar.apk`.
   Or copy the APK to the phone and open it.
2. Open Edge Bar → SETUP → **Enable Edge Bar** → turn on *Use Edge Bar*.
3. Installed from a file and the switch is greyed out? Tap it once, then
   App info → ⋮ → **Allow restricted settings**, and enable it again.
   (`adb install` is not restricted.)
4. Swipe inward from the right edge. Configure pins under APPS and the handle under HANDLE.

## Signing key

Not created in the container on purpose: a key made there would disappear when the
container is recycled, and every later update would be unable to install over it.
`scripts/make-keystore.sh` creates it on your build machine at
**`~/.edgebar/edge-bar-release.jks`**, with the passwords in `~/.edgebar/keystore.properties`
and a git-ignored copy at `./keystore.properties`.

> **Warning:** every future update must be signed with this same key. Android refuses to
> install an update signed with a different key; you would have to uninstall Edge Bar and
> lose its settings. Back up `~/.edgebar/`.

## Known limitations and risks

- **Never compiled against Android.** Expect possible first-build fixes (API signatures,
  lint findings). Google-hosted dependency versions (AGP 8.12.0, Compose BOM 2025.08.00,
  AndroidX) could not be checked against the repository.
- Edge trigger, gesture exclusion for `TYPE_ACCESSIBILITY_OVERLAY` windows, and whether
  inset-based IME and full-screen detection works for an accessibility overlay on Nothing OS
  are all unverified. The PILL trigger and per-app hide list are the fallbacks.
- Back delivery to the panel relies on the focusable overlay window getting focus
  (OnBackInvokedCallback plus KEYCODE_BACK). Scrim tap and swipe-to-close always work.
- Background-activity-launch rules: launches go straight from the service (accessibility
  services are exempt). A silently blocked launch cannot be detected; no trampoline
  activity was added because no test showed one is needed.
- If the default calculator does not declare `CATEGORY_APP_CALCULATOR`, the Calculator tool
  reports "No calculator app found".
- `TestControlReceiver` (adb test hook) ships in the release build, guarded by
  `android.permission.DUMP` (shell and system only).
- The Gradle wrapper has no `distributionSha256Sum`: the checksum file's download host is
  also blocked in the container.
