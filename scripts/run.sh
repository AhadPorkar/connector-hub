#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
if [ ! -f build/connector-hub.jar ]; then bash scripts/build.sh; fi
exec java ${JAVA_OPTS:-} -cp 'build/connector-hub.jar:drivers/*:plugins/*' io.ahad.connectorhub.server.Main "$@"
