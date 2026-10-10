#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
bash scripts/build.sh
mkdir -p build/test-classes
find hub-core/src/test/java hub-connectors/src/test/java hub-server/src/test/java -name '*.java' -print | LC_ALL=C sort > build/test-sources.txt
javac --release 21 -encoding UTF-8 -cp build/classes -d build/test-classes @build/test-sources.txt
for suite in core.CoreTest connectors.ConnectorTest server.ServerTest; do
  java -cp 'build/classes:build/test-classes' "io.ahad.connectorhub.$suite"
done
if command -v python3 >/dev/null; then PYTHON=python3; else PYTHON=python; fi
"$PYTHON" tests/http_test.py
"$PYTHON" scripts/check_repository.py
if command -v node >/dev/null; then node --check hub-server/src/main/resources/public/app.js; fi
printf '\nCore, connector, server and HTTP reports: build/reports/\n'
