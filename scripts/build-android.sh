#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../android"
if [ -z "${JAVA_HOME:-}" ] && [ -x /workspace/toolchains/jdk-21.0.13+9/bin/javac ]; then
  export JAVA_HOME=/workspace/toolchains/jdk-21.0.13+9
fi
export ANDROID_HOME="${ANDROID_HOME:-/workspace/android-sdk}"
export GRADLE_USER_HOME="${GRADLE_USER_HOME:-/workspace/.cache/folio-gradle}"
export ANDROID_USER_HOME="${ANDROID_USER_HOME:-/workspace/.cache/folio-android}"
mkdir -p "$ANDROID_USER_HOME"
# Java does not automatically use HTTPS_PROXY. Forward the proxy endpoint only.
readarray -t proxy_args < <(python3 - <<'PY'
import os,urllib.parse
p=urllib.parse.urlsplit(os.environ.get('HTTPS_PROXY',''))
if p.hostname:
 for scheme in ('http','https'):
  print(f'-D{scheme}.proxyHost={p.hostname}')
  print(f'-D{scheme}.proxyPort={p.port or 80}')
PY
)
if [ -f /etc/ssl/certs/java/cacerts ]; then
  proxy_args+=("-Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts")
fi
./gradlew "${proxy_args[@]}" --no-daemon --max-workers=2 assembleDebug lintDebug
