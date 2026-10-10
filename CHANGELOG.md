# Changelog

[Deutsch](CHANGELOG.de.md) · [فارسی](CHANGELOG.fa.md).

## 0.1.0 — 2026-10-10

Initial independently authored engineering preview. Added core contracts, encrypted credential fields, single-node persistent catalog, typed parameter sources, output mapping and connector SPI. Implemented LOCAL, JDBC, REST, SOAP, GraphQL, OData, standalone Redis string operations and an optional MongoDB driver bridge.

Added provider drafts, immutable publication revisions, generated invocation URLs, scoped keys, OpenAPI export/limited import, guarded retries, circuit breaker, durable idempotency, ambiguous-write reconciliation and persistent jobs. Added English/German/Persian administration, RTL/mobile layout, eleven guides per language, offline build scripts, optional Maven builds, container templates and GitHub workflows.

Regression fixes include preserving committed idempotency outcomes during recovery and audit failures, verifying primary keys for generated JDBC writes, bounding JDBC result materialization, closing resources after partial startup failure, and secure UUID fallback in the browser.

See [verification](docs/evidence/verification.json) for executed tests and explicit exclusions. This entry is not a claim that the version was published to GitHub or approved for commercial operation.
