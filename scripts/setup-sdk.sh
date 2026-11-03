#!/usr/bin/env bash
# Install the Android SDK pieces Edge Bar needs (needs network access to dl.google.com).
#
#   scripts/setup-sdk.sh            # latest stable platform + build-tools
#   PLATFORM=36 scripts/setup-sdk.sh
#
# Writes local.properties (sdk.dir=...) in the repository root.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
CMDLINE_ZIP="commandlinetools-linux-11076708_latest.zip"

mkdir -p "$SDK/cmdline-tools"
if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
  tmp="$(mktemp -d)"
  echo "Downloading Android command-line tools…"
  curl -fL --retry 3 -o "$tmp/cmdline.zip" "https://dl.google.com/android/repository/$CMDLINE_ZIP"
  unzip -q "$tmp/cmdline.zip" -d "$tmp"
  mv "$tmp/cmdline-tools" "$SDK/cmdline-tools/latest"
  rm -rf "$tmp"
fi
SDKM="$SDK/cmdline-tools/latest/bin/sdkmanager"
yes | "$SDKM" --sdk_root="$SDK" --licenses >/dev/null || true
"$SDKM" --sdk_root="$SDK" "cmdline-tools;latest" "platform-tools" >/dev/null

LIST="$("$SDKM" --sdk_root="$SDK" --list 2>/dev/null)"
# Stable only: integer platforms and x.y.z build-tools without -rc suffixes.
PLATFORM="${PLATFORM:-$(grep -oE 'platforms;android-[0-9]+ ' <<<"$LIST" | grep -oE '[0-9]+' | sort -n | tail -1)}"
BUILD_TOOLS="${BUILD_TOOLS:-$(grep -oE 'build-tools;[0-9]+\.[0-9]+\.[0-9]+ ' <<<"$LIST" | grep -oE '[0-9.]+' | sort -V | tail -1)}"
echo "Installing platforms;android-$PLATFORM and build-tools;$BUILD_TOOLS"
"$SDKM" --sdk_root="$SDK" "platforms;android-$PLATFORM" "build-tools;$BUILD_TOOLS"

echo "sdk.dir=$SDK" > "$ROOT/local.properties"
echo "Done. local.properties -> $SDK"
echo "Add platform-tools to PATH for adb:  export PATH=\"$SDK/platform-tools:\$PATH\""
