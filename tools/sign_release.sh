#!/usr/bin/env bash
set -euo pipefail
# Passwords are read by apksigner from environment, never command-line literals.
: "${ANDROID_HOME:?Set ANDROID_HOME}"
: "${KOMA_KEYSTORE:?Set path to your private release keystore}"
: "${KOMA_KEY_ALIAS:?Set your key alias}"
: "${KOMA_STORE_PASSWORD:?Set keystore password}"
: "${KOMA_KEY_PASSWORD:?Set key password}"
cd "$(dirname "$0")/.."
build_tools="$ANDROID_HOME/build-tools/35.0.0"
mkdir -p downloads
"$build_tools/zipalign" -P 16 -f 4 app/build/outputs/apk/release/app-release-unsigned.apk app/build/outputs/apk/release/app-release-aligned.apk
"$build_tools/apksigner" sign --ks "$KOMA_KEYSTORE" --ks-key-alias "$KOMA_KEY_ALIAS" --ks-pass env:KOMA_STORE_PASSWORD --key-pass env:KOMA_KEY_PASSWORD --out downloads/KOMA-release.apk app/build/outputs/apk/release/app-release-aligned.apk
"$build_tools/apksigner" verify --verbose --print-certs downloads/KOMA-release.apk
(cd downloads && sha256sum KOMA-release.apk > KOMA-release.apk.sha256)
