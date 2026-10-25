#!/usr/bin/env bash
set -euo pipefail
SDK_ROOT="${ANDROID_HOME:-/workspace/android-sdk}"
TOOLS_URL=https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip
TOOLS_SHA1=5fdcc763663eefb86a5b8879697aa6088b041e70
export ANDROID_USER_HOME="${ANDROID_USER_HOME:-/workspace/.cache/folio-android}"
mkdir -p "$SDK_ROOT" "$ANDROID_USER_HOME"
if [ ! -x "$SDK_ROOT/cmdline-tools/19.0/bin/sdkmanager" ]; then
  archive=$(mktemp /tmp/folio-sdk-XXXXXX.zip)
  tools_dir=$(mktemp -d /tmp/folio-sdk-XXXXXX)
  curl -fL --retry 2 "$TOOLS_URL" -o "$archive"
  echo "$TOOLS_SHA1  $archive" | sha1sum -c -
  unzip -q "$archive" -d "$tools_dir"
  mkdir -p "$SDK_ROOT/cmdline-tools"
  mv "$tools_dir/cmdline-tools" "$SDK_ROOT/cmdline-tools/19.0"
  rm -f "$archive"
  rmdir "$tools_dir"
fi
export ANDROID_HOME="$SDK_ROOT"
export SDK_MANAGER="$SDK_ROOT/cmdline-tools/19.0/bin/sdkmanager"
python3 - <<'PY'
import os, subprocess, urllib.parse
sdk=os.environ['SDK_MANAGER'];root=os.environ['ANDROID_HOME']
proxy=urllib.parse.urlsplit(os.environ.get('HTTPS_PROXY',''))
proxy_args=['--proxy=http','--proxy_host='+proxy.hostname,'--proxy_port='+str(proxy.port or 80)] if proxy.hostname else []
subprocess.run([sdk,'--sdk_root='+root,*proxy_args,'--licenses'],input='y\n'*100,text=True,check=True)
subprocess.run([sdk,'--sdk_root='+root,*proxy_args,'platforms;android-35','build-tools;35.0.0','build-tools;34.0.0','platform-tools'],input='y\n'*100,text=True,check=True)
PY
