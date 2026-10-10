# Security policy

[Deutsch](SECURITY.de.md) · [فارسی](SECURITY.fa.md).

Version 0.1.0 is a pre-production engineering preview. It has not received an independent penetration test or security certification. No support SLA or maintained-release guarantee is implied. Read the [threat model and limitations](docs/en/security.md) before deployment.

## Reporting

The source archive does not establish an active vulnerability-reporting channel. Before publishing, the repository owner must enable GitHub private vulnerability reporting and identify a monitored security contact. Once enabled, use the repository's private security-reporting interface. Do not put secrets, customer records, working exploit credentials or sensitive infrastructure details into public issues. No email address or response SLA is invented here.

## Important boundaries

The administration API is privileged. Built-in hashed API keys are not OIDC/SSO; the non-expiring bootstrap key has an incomplete rotation lifecycle. Encrypting catalog secrets does not encrypt all stored execution results. Control filesystem and backup access. External TLS termination and independent egress restrictions are required. DNS checks are not connection-pinned and do not alone eliminate DNS rebinding.

JDBC queries use parameters but are not a SQL sandbox. Use least-privileged database users. Plugins and JDBC drivers execute inside the JVM and must be trusted. Do not let untrusted users upload JARs. The WSDL inspector is intentionally limited; remote schema imports and unsafe XML entity processing are not supported.

Uncertain writes remain UNKNOWN for explicit investigation. Never reconcile them merely to make the dashboard green. Idempotency history pruning ends the corresponding deduplication window. LOCAL storage is single-node and not a substitute for a production distributed database.

Keep demonstration ports on loopback, disable the simulator outside development, use synthetic test data, inspect logs and backups for sensitive values, and complete the documented commercial release gates before customer use.
