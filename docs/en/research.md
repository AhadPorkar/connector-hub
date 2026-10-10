# Research register and implementation decisions

[Deutsch](../de/research.md) · [فارسی](../fa/research.md).

Research date: 2026-10-10. Primary documentation informed the design. A source being listed does not mean its library is included or its entire standard is implemented. Version-family examples and optional pins must be reviewed again before deployment.

| Primary source | Decision and actual use |
|---|---|
| [OpenJDK JEP 444](https://openjdk.org/jeps/444) | Java 21 virtual threads are used for blocking integration work; concurrency is still bounded. |
| [JDK HttpClient](https://docs.oracle.com/en/java/javase/21/docs/api/java.net.http/java/net/http/HttpClient.html) | Actual HTTP transport, bounded responses and no redirect following. |
| [JDBC CallableStatement](https://docs.oracle.com/en/java/javase/21/docs/api/java.sql/java/sql/CallableStatement.html) | Actual stored-procedure/function adapter; vendor semantics remain acceptance requirements. |
| [Spring Boot requirements](https://docs.spring.io/spring-boot/system-requirements.html) | Evaluated framework option, not a dependency of the offline-verified baseline. |
| [Spring Security resource-server JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html) | Reference for future OIDC/JWT integration; inbound JWT support is not implemented. |
| [Apache Camel component catalog](https://camel.apache.org/components/4.22.x/index.html) | Candidate expansion path for many protocols; Camel is not secretly bundled. |
| [Apache CXF](https://cxf.apache.org/) | Candidate for full SOAP bindings and WS-* features beyond explicit envelopes. |
| [Resilience4j retry](https://resilience4j.readme.io/docs/retry) | Policy reference; actual bounded retry/circuit implementation is in hub-core. |
| [OpenAPI 3.1.1](https://spec.openapis.org/oas/v3.1.1.html) | Published service contracts use 3.1.1; import supports a documented subset. |
| [W3C SOAP 1.2](https://www.w3.org/TR/soap12-part1/) | Envelope/action/fault behavior; no full WS-Security or arbitrary binding claim. |
| [GraphQL September 2025](https://spec.graphql.org/September2025/) | HTTP document/variables and error distinction; no subscription server. |
| [OWASP SSRF prevention](https://cheatsheetseries.owasp.org/cheatsheets/Server_Side_Request_Forgery_Prevention_Cheat_Sheet.html) | Destination restrictions plus explicit residual DNS/egress requirements. |
| [OWASP XXE prevention](https://cheatsheetseries.owasp.org/cheatsheets/XML_External_Entity_Prevention_Cheat_Sheet.html) | DTD/external entity/import restrictions in SafeXml. |
| [Official MongoDB Java sync driver](https://www.mongodb.com/docs/drivers/java/sync/current/) | Optional official-driver bridge; live acceptance was unavailable. |
| [MongoDB connection pools](https://www.mongodb.com/docs/drivers/java/sync/current/connection/specify-connection-options/connection-pools/) | Identifies a lifecycle improvement: this preview creates clients per operation. |
| [Redis client documentation](https://redis.io/docs/latest/develop/clients/jedis/) | Evaluated mature client option; delivered adapter is a limited native RESP2 implementation. |
| [pgJDBC downloads](https://jdbc.postgresql.org/download/) | Optional explicitly pinned PostgreSQL driver profile, not a latest-version claim. |
| [MySQL Connector/J release notes](https://dev.mysql.com/doc/relnotes/connector-j/en/) | Optional driver compatibility/licensing review input. |
| [MariaDB Connector/J releases](https://mariadb.com/docs/release-notes/connectors/java/) | Optional driver profile review input. |
| [Microsoft JDBC release notes](https://learn.microsoft.com/en-us/sql/connect/jdbc/release-notes-for-the-jdbc-driver) | Manual SQL Server driver/version validation path. |
| [Testcontainers guide](https://testcontainers.com/guides/testing-spring-boot-rest-api-using-testcontainers/) | Real-vendor acceptance approach; Docker/Testcontainers were unavailable locally. |
| [GitHub checkout releases](https://github.com/actions/checkout/releases) | Current workflow families reviewed; hosted workflows were not executed here. |
| [GitHub upload-artifact releases](https://github.com/actions/upload-artifact/releases) | Artifact workflow configuration reference; no fake hosted CI result. |

## Why not promise universal connectivity?

JDBC standardizes an interface, not every vendor's metadata, types, transactions, generated keys, procedure calls, authentication and failure behavior. HTTP standardizes transport, not the business semantics of every API. SOAP, GraphQL and OData also have substantial optional capabilities. A credible compatibility statement therefore names the exact implemented subset and tested products.

## Why conservative retries?

A timeout can occur after a destination commits. Repeating the request can duplicate an irreversible action. The hub classifies safe reads separately, records durable claims and quarantines ambiguous writes. Destination-native idempotency and reconciliation remain stronger evidence than optimistic retries.

## Why no embedded AI-generated code execution?

Arbitrary runtime scripting would broaden the trust boundary substantially. This version instead uses typed declarative mappings and reviewed startup plugins. A later expression engine must define sandboxing, resource limits and secret/network permissions before becoming a product feature.
