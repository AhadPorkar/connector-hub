# Verification, reports and coverage boundaries

[Deutsch](../de/testing.md) · [فارسی](../fa/testing.md).

The release evidence is captured from actual commands, not inferred from test source or a green-looking badge. See [verification.json](../evidence/verification.json) and the JUnit XML files under `docs/evidence/reports/` for the final recorded counts and environment. Rerunning tests produces fresh reports under `build/reports/`; historical evidence is not automatically overwritten.

## Suites

The core suite exercises strict JSON parsing, Unicode, canonical fingerprints, typed inputs, source resolution, output mappings, SQL lexical binding, encryption, file locking, detached snapshots, restart behavior, concurrent mutations, roles, retries, circuits and rate limits.

Connector tests exercise XML/XXE and SOAP faults, WSDL inspection, destination restrictions, RESP2 framing and malformed replies, LOCAL CRUD/version checks/isolation, missing-driver behavior and retry classification. HTTP tests start the real Java process and a loopback simulator. They cover authentication, tenant isolation, roles/scopes, configuration lifecycle, CRUD, published revisions, OpenAPI, all implemented HTTP methods/body modes, static upstream authentication, redirects/body limits, transient reads, ambiguous writes, reconciliation, queueing and restart persistence. Server tests cover key lifecycle, secret patching, snapshot behavior and the recovery regression that must preserve DONE/FAILED records.

```bash
bash scripts/test.sh
# Optional browser suite in a connected development environment:
python -m pip install -r tests/browser/requirements.txt
python -m playwright install chromium
python tests/browser/ui_test.py
```

## Browser evidence

The supplied Playwright suite defaults to normal browser navigation. The authoring browser had an organization-managed block-all-navigation policy. That policy was not changed. For local verification, `HUB_UI_TRANSPORT=harness` rendered the actual HTML/CSS/JavaScript assets in an isolated page and bridged fetch calls to the running Java server. It checks actual interface interactions, translations, RTL, layout and persisted server changes, but does not verify native navigation, browser CSP enforcement or network interception semantics. This limitation is part of the evidence, not hidden behind an end-to-end claim.

The repository includes screenshots of real rendered states. They are not design mockups. No automated accessibility certification or exhaustive assistive-technology testing is claimed.

## Database acceptance

The JDBC acceptance runner creates and drops its own `CH_ACCEPTANCE` table. It requires explicit destructive-test permission and must run only against a disposable database.

```bash
export HUB_ALLOW_DESTRUCTIVE_TESTS=true
export HUB_TEST_JDBC_URL='jdbc:postgresql://127.0.0.1:5432/hub_acceptance'
export HUB_TEST_JDBC_DRIVER='org.postgresql.Driver'
export HUB_TEST_JDBC_USER='hub_test'
export HUB_TEST_JDBC_PASSWORD='a-disposable-test-secret'
bash scripts/test-jdbc.sh
```

Local acceptance used the environment's existing HSQLDB 1.8.0 JAR for twelve actual metadata/CRUD/negative cases. That legacy engine is neither shipped nor recommended. It does not prove PostgreSQL, SQL Server, Oracle, MySQL, MariaDB, SQLite, H2, DB2 or SAP HANA behavior. A manual GitHub workflow provides a disposable PostgreSQL acceptance path, but that hosted workflow was not executed here. Stored procedures, driver-specific timeouts, TLS and every vendor's metadata behavior still need dedicated tests.

## Not measured or not run

No line/branch coverage percentage was measured. No live Redis or official-driver MongoDB acceptance was available. No real external SOAP supplier or accounting API was used. Docker, Maven dependency resolution, Windows, Java 25 and hosted GitHub Actions were not run locally. No penetration test, sustained load/soak test, HA/failover test or commercial compliance assessment was completed. These are explicit release gates, not silently passing modules.

## Adding tests

Every new connector should cover success, missing credentials, unsupported operations, invalid inputs, timeout, partial/ambiguous write, duplicate key behavior, tenant/destination restrictions and cancellation/restart boundaries. A new UI action needs both server authorization tests and browser interaction tests. Update all three locales and guides. Use synthetic credentials and disposable databases only. A high test count does not substitute for acceptance of the exact deployed driver, service contract and failure model.
