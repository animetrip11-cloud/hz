#!/usr/bin/env bash
set -euo pipefail
JDK_DIR=/workspace/toolchains/jdk-21.0.13+9
if [ -x "$JDK_DIR/bin/javac" ]; then exit 0; fi
mkdir -p /workspace/toolchains
archive=$(mktemp /tmp/folio-jdk-XXXXXX.tar.gz)
checksum=$(mktemp /tmp/folio-jdk-XXXXXX.sha256)
base='https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.13%2B9/OpenJDK21U-jdk_x64_linux_hotspot_21.0.13_9.tar.gz'
curl -fL --retry 2 "$base" -o "$archive"
curl -fL --retry 2 "$base.sha256.txt" -o "$checksum"
python3 - "$archive" "$checksum" <<'PY'
import hashlib,sys,tarfile
archive,checksum=sys.argv[1:]
expected=open(checksum).read().split()[0]
assert hashlib.sha256(open(archive,'rb').read()).hexdigest()==expected,'JDK checksum mismatch'
with tarfile.open(archive) as t:t.extractall('/workspace/toolchains',filter='data')
PY
rm -f "$archive" "$checksum"
