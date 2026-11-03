#!/usr/bin/env bash
# Create the release signing key OUTSIDE the repository and write the git-ignored
# keystore.properties that app/build.gradle.kts reads.
#
#   Key:        ~/.edgebar/edge-bar-release.jks   (override with EDGEBAR_KEY_DIR)
#   Passwords:  ~/.edgebar/keystore.properties (backup copy) and ./keystore.properties
#
# BACK UP ~/.edgebar. Android only installs an update over an existing install when it
# is signed with the same key; losing it means uninstalling Edge Bar (and its settings).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
KEYDIR="${EDGEBAR_KEY_DIR:-$HOME/.edgebar}"
KS="$KEYDIR/edge-bar-release.jks"
PROPS="$ROOT/keystore.properties"

if [ -e "$KS" ]; then
  echo "Signing key already exists: $KS (not overwritten; updates must use this key)."
  if [ ! -f "$PROPS" ] && [ -f "$KEYDIR/keystore.properties" ]; then
    cp "$KEYDIR/keystore.properties" "$PROPS" && chmod 600 "$PROPS"
    echo "Restored $PROPS from $KEYDIR/keystore.properties"
  fi
  exit 0
fi

command -v keytool >/dev/null || { echo "keytool not found (install a JDK)" >&2; exit 1; }
mkdir -p "$KEYDIR" && chmod 700 "$KEYDIR"
PASS="$(od -An -tx1 -N24 /dev/urandom | tr -d ' \n')"   # 48 hex chars; no early-closing pipe
[ "${#PASS}" -eq 48 ] || { echo "password generation failed" >&2; exit 1; }

keytool -genkeypair -v \
  -keystore "$KS" -storetype PKCS12 \
  -alias edgebar -keyalg RSA -keysize 4096 -validity 10000 \
  -storepass "$PASS" -keypass "$PASS" \
  -dname "CN=Edge Bar (personal sideload), O=Local"
chmod 600 "$KS"

umask 077
cat > "$KEYDIR/keystore.properties" <<PROPS
# Edge Bar release signing. Do not commit. Keep a backup of this folder.
storeFile=$KS
storePassword=$PASS
keyAlias=edgebar
keyPassword=$PASS
PROPS
cp "$KEYDIR/keystore.properties" "$PROPS"
chmod 600 "$PROPS" "$KEYDIR/keystore.properties"
echo
echo "Created $KS"
echo "Wrote $PROPS (git-ignored) and a backup in $KEYDIR/keystore.properties"
echo "BACK UP $KEYDIR — future updates must be signed with this exact key."
