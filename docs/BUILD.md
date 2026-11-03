# Building Edge Bar

## Status: no APK was produced in the authoring environment

The source was written in a Claude Code cloud container whose network policy returns
**HTTP 403 for `dl.google.com`**. `maven.google.com` redirects to that host, so it is
blocked too. Everything Android-specific comes only from there:

| Needed | Host | Result in the container |
|---|---|---|
| Android SDK (`platforms;android-N`, `build-tools`, `platform-tools`/adb) | dl.google.com | 403 |
| Android Gradle Plugin `com.android.tools.build:gradle` | maven.google.com → dl.google.com | 403 |
| AndroidX / Jetpack Compose / DataStore | maven.google.com → dl.google.com | 403 |
| Kotlin, kotlinx-coroutines, JUnit (Maven Central) | repo.maven.apache.org | OK |
| Gradle 8.14.3 distribution | services.gradle.org | OK |

So `./gradlew test`, `./gradlew lint` and `./gradlew assembleRelease` could not run there,
and there is no `dist/edge-bar.apk` yet.

## Fix: build on a machine that can reach Google's Maven (recommended: your own computer)

Running locally is also what the Phase 1 device test needs, since the phone connects over USB.

Requirements: JDK 17 or 21, `curl`, `unzip`. Android Studio is optional.

```bash
scripts/setup-sdk.sh
```
Installs the command-line tools, platform-tools (adb), and the latest stable
`platforms;android-N` and `build-tools`, then writes `local.properties`.
If you already have an SDK, just set `ANDROID_HOME`.

```bash
scripts/make-keystore.sh
```
Creates the release key in `~/.edgebar/` (outside the repo) and the git-ignored
`keystore.properties`. Run this once and **back up `~/.edgebar/`**.

```bash
scripts/build-release.sh
```
Runs `./gradlew test`, `./gradlew lint` and `./gradlew assembleRelease`, copies the APK to
`dist/edge-bar.apk`, runs `apksigner verify`, and prints the version, size and SHA-256.

### Alternative: build in a Claude Code cloud session

Allow the host in the environment's network settings: open the cloud environment menu in
the session title bar, choose **Edit → Network access**, and add `dl.google.com` and
`maven.google.com` under *Allowed domains* (or pick a broader access level). See
<https://code.claude.com/docs/en/cloud-environments#network-access>. Then run the three
scripts above in a **new** session. A cloud session can never reach a USB phone, so
device testing still has to happen locally. The signing key would also be created inside
an ephemeral container, so copy `~/.edgebar/` out of it before the container is reclaimed,
or create the key locally instead.

## compileSdk / targetSdk

`app/build.gradle.kts` reads the SDK directory (`local.properties` or `ANDROID_HOME`) and
uses the highest **stable** platform installed (`platforms/android-<integer>` that
contains `android.jar`). Previews and minor platforms are ignored. Override with
`-Pedgebar.sdk=NN`. If no SDK is found it falls back to 36.

## Dependency versions (unverified against Google Maven)

`gradle/libs.versions.toml` pins AGP 8.12.0, Kotlin 2.2.10, Compose BOM 2025.08.00,
activity-compose 1.10.1, core-ktx 1.16.0, lifecycle 2.9.2 and DataStore 1.1.7.
Kotlin and coroutines were confirmed to resolve from Maven Central. The Google-hosted
versions could not be checked from the container. If the newest SDK on your machine is
newer than AGP 8.12 knows about, AGP prints a warning (not an error). Bump `agp` in
the catalog if you want it gone.

## Release configuration

- `isMinifyEnabled = true` (R8) and `isShrinkResources = true`.
- The release build fails at `preReleaseBuild` with a clear message if the signing key is
  missing, so an unsigned APK is never produced silently.
- No INTERNET permission. `dist/` and all key material are git-ignored.
