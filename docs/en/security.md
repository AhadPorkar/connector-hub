# Security model and deployment boundaries

[Deutsch](../de/security.md) · [فارسی](../fa/security.md).

This is a reviewed engineering preview, not a penetration-tested or certified product. The following controls are implemented and exercised where reports state so; they do not justify exposing the default process directly to the public internet.

## Trust boundaries

The operator controls environment settings, master keys, driver/plugin installation and network policy. Administrators control tenant connections and keys. Designers are trusted integration authors who can define requests and SQL. Invokers supply only declared inputs to authorized published providers. Catalog tenant isolation is checked independently of browser visibility. A malicious driver or plugin has JVM process privileges; no sandbox is claimed.

## Identity and secrets

Inbound authentication uses high-entropy bearer API keys. Only hashes are persisted; generated raw keys are displayed once. Scope, role, expiry and revocation are server-enforced. The bootstrap key is special and non-expiring; it cannot be revoked by the normal API. Changing `HUB_ADMIN_TOKEN` after catalog initialization does not rotate it. A commercial deployment needs a reviewed administrative recovery/rotation mechanism and preferably OIDC/SSO. Never delete the catalog merely to rotate a credential containing business history.

The browser keeps the bearer token in memory. Language preference alone uses local storage. There are no authentication cookies or wildcard CORS. This reduces cookie-based CSRF exposure but does not eliminate XSS, stolen-token or malicious-designer risks. A strict same-origin CSP and escaped display text are implemented. The UI harness cannot certify browser enforcement of that CSP; native browser testing remains a release gate.

Connection secrets and catalog contents are encrypted using AES-256-GCM. A local `master.key` is convenient for development but provides no separation against theft of the entire data directory. Use an externally managed `HUB_MASTER_KEY` for controlled deployments, protect filesystem access and keep backups/key custody separate. Memory, crash dumps and administrator access can still expose plaintext. No KMS/HSM integration or key-rotation service is implemented.

## Outbound network security

HTTP, Redis and MongoDB destinations must match an exact operator-approved origin. Private destinations additionally require explicit opt-in. Loopback/private demo access is enabled only for the simulated demo setup. Link-local/cloud metadata, unspecified and multicast addresses are rejected. Redirect following is disabled. Default JDK certificate and hostname verification are not disabled. JDBC destinations use separately approved URL prefixes and a denylist of risky options.

**Important residual risk:** hostname resolution validation and the actual client connection are not pinned to one resolved address. DNS rebinding/time-of-check/time-of-use attacks therefore require a real egress firewall or a restricted outbound proxy. Application URL checking alone is not enough. Custom plugins can bypass application network checks and must be reviewed as trusted code. Configure connection-level TLS according to each official driver, not by accepting every certificate.

These boundaries follow the defense-in-depth approach in the [OWASP SSRF prevention guidance](https://cheatsheetseries.owasp.org/cheatsheets/Server_Side_Request_Forgery_Prevention_Cheat_Sheet.html).

## SQL, XML and payloads

SQL values are bound through PreparedStatement/CallableStatement. CRUD identifiers are validated against metadata and a conservative identifier syntax. Updates/deletes require a metadata-confirmed primary key. Authored QUERY/EXECUTE/PROCEDURE/FUNCTION operations can still do anything allowed to their database account. Use distinct least-privilege accounts and review every publication. No shared global transaction across the hub and a destination is claimed.

XML parsing rejects DTDs, external entities, XInclude and external schema access. Self-contained WSDL inspection does not resolve imports. Payload depth, strings, collections, HTTP bodies, pages and concurrency are bounded. Follow the [OWASP XXE guidance](https://cheatsheetseries.owasp.org/cheatsheets/XML_External_Entity_Prevention_Cheat_Sheet.html) when changing parsing behavior. XML namespace-local-name collisions remain a mapping limitation, not a general schema-validation implementation.

## Data retention and audit

Audit entries contain metadata rather than request bodies or secret values. However, queued jobs, durable idempotency results and LOCAL records can retain business payloads inside the encrypted catalog. Configuration exports are not backups and are not an automatic DLP system. Sensitive values placed into arbitrary query literals, descriptions, headers or unmarked constants may remain in exports. Review them before sharing.

Audit history is bounded to 5,000 entries across tenants and is not an immutable external audit log. Local timestamps rely on the host clock. Pruning terminal execution history older than seven days ends deduplication protection for those keys. UNKNOWN records are retained for reconciliation. Define your own lawful retention and deletion policy with qualified organizational review; no GDPR or other compliance certification is asserted.

## Before external exposure

Terminate HTTPS at a controlled reverse proxy; bind the JVM to loopback or a protected private network. Restrict egress. Install reviewed driver versions, scan dependencies, test backups and restore, prove isolation against your actual schemas, and run independent penetration and load tests. Do not forward arbitrary authentication headers between users and destinations. Do not enable demo mode in a customer environment. Current security coverage and gaps are listed in [testing](testing.md) and [commercial readiness](commercial-readiness.md).
