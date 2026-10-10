# Connector capability and acceptance matrix

[Deutsch](../de/connectors.md) · [فارسی](../fa/connectors.md).

A connector implementation, a driver being loadable, and acceptance against a particular live system are different claims. This release intentionally does not advertise support for every database or service.

| Type | Implemented capability | Verified here | Important exclusions |
|---|---|---|---|
| LOCAL | Persistent document CRUD, filtering, paging, version checks | Unit + real hub HTTP | Demo store, not relational DB |
| REST | GET/HEAD/POST/PUT/PATCH/DELETE/OPTIONS; JSON/text/form/multipart; binary output | Loopback transport fixtures | OAuth token acquisition, streaming, WebSocket |
| SOAP | 1.1/1.2 envelopes/actions/faults; self-contained WSDL 1.1 inspection | Parser + loopback HTTP | Imported WSDL/XSD resolution, WS-Security, full binding generation |
| GRAPHQL | HTTP document/variables, error handling | Loopback transport fixtures | Subscriptions, schema federation, automatic schema discovery |
| ODATA | HTTP CRUD-style requests, bounded top/skip | Loopback transport fixtures | Full batch/delta/metadata semantics |
| JDBC | Metadata, prepared CRUD, named SQL, callable IN/OUT/INOUT and function returns | Twelve acceptance cases on local HSQLDB 1.8 only | Universal vendor compatibility, composite-key generator, distributed transactions |
| REDIS | Standalone RESP2 string GET/SET NX/SET XX/DEL, auth, TTL, optional TLS | Protocol/unit tests | Live acceptance, cluster/sentinel, streams/pubsub |
| MONGODB | Optional official sync-driver bridge, bounded CRUD and equality filters | Missing-driver path only | Live driver acceptance, SRV/multi-host URI, transactions/change streams |

REST/SOAP/GraphQL/OData fixture verification is real network testing against a controlled simulator, not acceptance against arbitrary external vendors. The old local HSQLDB engine is test infrastructure only, is not bundled, and is not a deployment recommendation.

## JDBC installation

Put a compatible vendor driver JAR and required dependencies in `drivers/`, restart, and use the classpath launch script. `java -jar` alone does not add those JARs. The core distribution deliberately bundles no vendor driver. Optional connected Maven profiles copy explicitly selected dependencies:

```bash
mvn -f drivers/pom.xml -Ppostgres dependency:copy-dependencies
# Other explicit profiles: mysql, mariadb, mongodb.
```

Examples: PostgreSQL `org.postgresql.Driver`; MySQL `com.mysql.cj.jdbc.Driver`; MariaDB `org.mariadb.jdbc.Driver`; SQL Server `com.microsoft.sqlserver.jdbc.SQLServerDriver`; Oracle `oracle.jdbc.OracleDriver`. SQLite, H2, DB2, SAP HANA and other JDBC products require a compatible driver, URL, privilege policy and independent acceptance. This list describes possible JDBC integration paths, not verified support. Driver licensing, Java compatibility, TLS and timeout behavior must be reviewed individually.

```json
{
  "name": "PostgreSQL integration lab",
  "type": "JDBC",
  "config": {
    "url": "jdbc:postgresql://db.internal:5432/integration?sslmode=verify-full",
    "driverClass": "org.postgresql.Driver"
  },
  "secrets": {"username":"least_privileged_account","password":"supply-outside-source-control"}
}
```

The operator must allow a narrow prefix such as `jdbc:postgresql://db.internal:5432/integration`. JDBC prefixes are textual guards, not a complete parser or egress firewall. Denied dangerous URL options are defense-in-depth. Avoid broad `jdbc:` allowlists.

READ/CREATE/UPDATE/DELETE use discovered and validated identifiers. UPDATE/DELETE require a single real primary key. Optional version columns enable compare-and-swap behavior. QUERY/EXECUTE bind named values but authored SQL remains trusted code. Stored-procedure parameter order matters, and each vendor's callable behavior needs separate tests. No database pool is included; connections open per execution. Query timeout and network timeout support are driver-dependent.

## HTTP authentication and request bodies

NONE, BASIC, BEARER and API_KEY are implemented. Store credentials in the connection secret bag. A static Bearer token is not an OAuth2 client-credentials flow and is not automatically refreshed. Path and query values are encoded, header names/values are checked, redirects are disabled, and response size is capped. SOAP input substitution escapes XML text. Imported specifications cannot cause arbitrary network fetches.

## Redis and MongoDB

Redis physical keys include `ch:<tenant>:<namespace>:`. This does not replace server ACLs; two connections to the same destination and namespace intentionally address the same tenant keyspace. Values are strings or JSON-serialized values. MongoDB uses explicit database/collection configuration, string IDs, safe equality filters and rejects dangerous document key shapes. Tenant isolation in external databases is not automatic: use tenant-specific credentials/databases or reviewed server-side filters. Do not confuse catalog isolation with downstream row isolation.

## Additional protocols

gRPC, Kafka, JMS, AMQP, SFTP, FTP, MQTT, LDAP, Elasticsearch, Cassandra, DynamoDB, WCF-specific bindings and arbitrary service buses are not implemented. A trusted ServiceLoader connector SPI and example plugin are included. Apache Camel or specialized official SDKs are appropriate candidates for reviewed extensions. Do not relabel an extension point as working support.
