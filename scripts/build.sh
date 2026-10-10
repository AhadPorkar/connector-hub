#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
command -v javac >/dev/null || { echo 'JDK 21 or newer is required.' >&2; exit 1; }
rm -rf build/classes
mkdir -p build/classes build/reports
find hub-core/src/main/java hub-connectors/src/main/java hub-server/src/main/java -name '*.java' -print | LC_ALL=C sort > build/main-sources.txt
javac --release 21 -encoding UTF-8 -d build/classes @build/main-sources.txt
cp -R hub-server/src/main/resources/. build/classes/
mkdir -p build/classes/public/docs
for lang in en de fa; do
  if [ -d "docs/$lang" ]; then cp -R "docs/$lang" build/classes/public/docs/; fi
done
jar --create --file build/connector-hub.jar --main-class io.ahad.connectorhub.server.Main -C build/classes .
printf '\nBuilt %s/build/connector-hub.jar\n' "$ROOT"
