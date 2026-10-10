# Contributing

[Deutsch](CONTRIBUTING.de.md) · [فارسی](CONTRIBUTING.fa.md).

Use JDK 21 and Python 3. Run `bash scripts/test.sh` before submitting a change. Browser and disposable-database instructions are in [testing](docs/en/testing.md). The project intentionally supports an offline JDK-only baseline; a new runtime dependency requires an architectural decision, license review and a reproducible build path.

## Pull request expectations

Explain the business scenario, failure model, authorization boundary and compatibility impact. Add tests for success and failure, concurrent or repeated requests where relevant, and tenant isolation. Do not claim vendor support from compilation alone. Include a disposable acceptance case for the exact driver and vendor. Report commands actually executed and clearly mark unexecuted checks.

Update all three locale files and relevant English/German/Persian guides. Never translate machine-readable API identifiers. Run `python scripts/check_repository.py`. Keep UI behavior keyboard accessible and cover new actions with the browser suite. Do not commit runtime catalogs, master keys, access tokens, database dumps, customer schemas or downloaded vendor JARs. Use synthetic fixtures.

Treat connectors and ServiceLoader plugins as trusted code. Do not introduce arbitrary user scripts, unrestricted URLs, retries of ambiguous writes, or a bypass of server-side authorization. An Idempotency-Key is not a distributed transaction. Record operational limits instead of hiding them.

Use small reviewable changes. Describe migrations and recovery implications in the changelog. Contribution acceptance, maintenance commitments and commercial support must be established by the repository owner; this source bundle does not promise them. Contributions must be original or correctly licensed.

## Before first GitHub publication

Confirm ownership and the MIT license, configure private vulnerability reporting, review workflows and third-party versions, run native-browser and vendor acceptance, and set branch protection in the actual repository. No remote repository or hosted CI history is created by extracting this archive.
