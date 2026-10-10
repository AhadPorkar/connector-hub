# Recherche und Technologieentscheidungen

[English](../en/research.md) · [فارسی](../fa/research.md).

Stand: 2026-10-10. Primärquellen begründen Entscheidungen, bedeuten aber weder eingebaute Bibliotheken noch vollständige Standardimplementierung. Optionale Versionsbindungen sind vor Einsatz erneut zu prüfen.

| Quelle | Verwendung beziehungsweise Entscheidung |
|---|---|
| [JEP 444](https://openjdk.org/jeps/444) | Virtuelle Threads unter Java 21; Parallelität bleibt begrenzt. |
| [JDK HttpClient](https://docs.oracle.com/en/java/javase/21/docs/api/java.net.http/java/net/http/HttpClient.html) | Tatsächlicher HTTP-Transport ohne Redirects. |
| [CallableStatement](https://docs.oracle.com/en/java/javase/21/docs/api/java.sql/java/sql/CallableStatement.html) | Prozeduren/Funktionen; Herstellerabnahme bleibt nötig. |
| [Spring Boot](https://docs.spring.io/spring-boot/system-requirements.html) | Untersucht, nicht im Offline-Kern enthalten. |
| [Spring Security JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html) | Referenz für späteres OIDC, nicht implementiert. |
| [Apache Camel](https://camel.apache.org/components/4.22.x/index.html) | Kandidat für zusätzliche Protokolle, nicht gebündelt. |
| [Apache CXF](https://cxf.apache.org/) | Kandidat für vollständige SOAP-/WS-*-Funktionen. |
| [Resilience4j](https://resilience4j.readme.io/docs/retry) | Richtlinienreferenz; eigener begrenzter Runner. |
| [OpenAPI 3.1.1](https://spec.openapis.org/oas/v3.1.1.html) | Veröffentlichte Verbraucherverträge, begrenzter Import. |
| [SOAP 1.2](https://www.w3.org/TR/soap12-part1/) | Envelope/Action/Fault, kein vollständiges WS-Security. |
| [GraphQL](https://spec.graphql.org/September2025/) | Dokument/Variablen, keine Subscriptions. |
| [OWASP SSRF](https://cheatsheetseries.owasp.org/cheatsheets/Server_Side_Request_Forgery_Prevention_Cheat_Sheet.html) | Allowlist plus externe Egress-/DNS-Sicherung. |
| [OWASP XXE](https://cheatsheetseries.owasp.org/cheatsheets/XML_External_Entity_Prevention_Cheat_Sheet.html) | DTD/Entity/Import-Sperren. |
| [MongoDB Java](https://www.mongodb.com/docs/drivers/java/sync/current/) | Optionale offizielle Treiberbrücke, keine Live-Abnahme. |
| [MongoDB Pools](https://www.mongodb.com/docs/drivers/java/sync/current/connection/specify-connection-options/connection-pools/) | Verbesserungsbedarf beim Client-Lifecycle. |
| [Redis/Jedis](https://redis.io/docs/latest/develop/clients/jedis/) | Reifer Client untersucht; geliefert ist begrenztes RESP2. |
| [pgJDBC](https://jdbc.postgresql.org/download/) | Optionales gepinntes Profil, keine Latest-Zusage. |
| [MySQL](https://dev.mysql.com/doc/relnotes/connector-j/en/) | Treiber-/Lizenz-/Kompatibilitätsprüfung. |
| [MariaDB](https://mariadb.com/docs/release-notes/connectors/java/) | Optionales Treiberprofil. |
| [Microsoft JDBC](https://learn.microsoft.com/en-us/sql/connect/jdbc/release-notes-for-the-jdbc-driver) | Manuelle SQL-Server-Abnahme. |
| [Testcontainers](https://testcontainers.com/guides/testing-spring-boot-rest-api-using-testcontainers/) | Abnahmeansatz; Docker lokal nicht verfügbar. |
| [Checkout Releases](https://github.com/actions/checkout/releases) | Workflow-Familien geprüft; Actions nicht ausgeführt. |
| [Artifact Releases](https://github.com/actions/upload-artifact/releases) | Paketnachweise statt erfundener CI-Ergebnisse. |

JDBC standardisiert Schnittstellen, nicht jede Metadaten-, Typ-, Transaktions-, Authentifizierungs- und Fehlereigenschaft. HTTP definiert Transport, nicht die fachliche Bedeutung jedes API-Aufrufs. Deshalb müssen Kompatibilitätsaussagen genaue Teilmengen nennen.

Ein Timeout kann nach einem Commit auftreten. Wiederholung kann dann eine irreversible Aktion duplizieren. Der Hub trennt sichere Reads, speichert Claims und isoliert unklare Writes. Zielseitige Idempotenz und unabhängiger Abgleich bleiben entscheidend. Beliebige KI-generierte Skripte würden die Vertrauensgrenze stark erweitern; diese Version nutzt deshalb deklarative Mappings und überprüfte Startup-Plugins.
