#!/usr/bin/env bash
# Phase 3: test, lint, build the signed release APK, copy it to dist/edge-bar.apk and
# verify the signature. Prints version, size and SHA-256.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
die() { echo "ERROR: $*" >&2; exit 1; }

[ -f keystore.properties ] || die "keystore.properties missing: run scripts/make-keystore.sh"
SDK="$(sed -n 's/^sdk.dir=//p' local.properties 2>/dev/null || true)"
SDK="${SDK:-${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}}"
[ -n "$SDK" ] && [ -d "$SDK" ] || die "Android SDK not found: run scripts/setup-sdk.sh"

echo "== 1/3 ./gradlew test";            ./gradlew --no-daemon test
echo "== 2/3 ./gradlew lint";            ./gradlew --no-daemon lint
echo "== 3/3 ./gradlew assembleRelease"; ./gradlew --no-daemon assembleRelease

SRC=app/build/outputs/apk/release/app-release.apk
[ -f "$SRC" ] || die "release APK not produced (unsigned builds are named app-release-unsigned.apk)"
mkdir -p dist
cp "$SRC" dist/edge-bar.apk

BT="$(ls -d "$SDK"/build-tools/*/ | sort -V | tail -1)"
echo "== apksigner verify ($BT)"
"$BT/apksigner" verify --verbose --print-certs dist/edge-bar.apk | tee dist/apksigner-verify.txt

BADGE="$("$BT/aapt2" dump badging dist/edge-bar.apk | head -1)"
SHA="$(sha256sum dist/edge-bar.apk | cut -d' ' -f1)"
SIZE="$(stat -c %s dist/edge-bar.apk)"
echo "$SHA  edge-bar.apk" > dist/edge-bar.apk.sha256
{
  echo "apk: dist/edge-bar.apk"
  echo "$BADGE"
  echo "size_bytes: $SIZE"
  echo "sha256: $SHA"
  echo "built: $(date -Iseconds)"
} | tee dist/BUILD-INFO.txt
