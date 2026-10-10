#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
if [ "${HUB_ALLOW_DESTRUCTIVE_TESTS:-}" != true ]; then
  echo 'Use a disposable database and set HUB_ALLOW_DESTRUCTIVE_TESTS=true.' >&2; exit 1
fi
if [ ! -f build/test-classes/io/ahad/connectorhub/connectors/JdbcAcceptance.class ]; then bash scripts/test.sh; fi
exec java -cp "build/classes:build/test-classes:drivers/*:${HUB_TEST_DRIVER_CLASSPATH:-}" io.ahad.connectorhub.connectors.JdbcAcceptance
