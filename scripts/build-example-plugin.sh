#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
if [ ! -f build/connector-hub.jar ]; then bash scripts/build.sh; fi
mkdir -p build/example-plugin plugins
javac --release 21 -encoding UTF-8 -cp build/connector-hub.jar -d build/example-plugin examples/echo-plugin/src/io/ahad/connectorhub/example/EchoConnector.java
cp -R examples/echo-plugin/resources/. build/example-plugin/
jar --create --file plugins/echo-example.jar -C build/example-plugin .
echo 'Trusted example built. Restart with scripts/run.sh or run.ps1, then choose ECHO in a new connection.'
