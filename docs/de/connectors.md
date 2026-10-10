# Connector-Funktionen und Verifikationsmatrix

[English](../en/connectors.md) · [فارسی](../fa/connectors.md).

Implementierung, ladbarer Treiber und Abnahme gegen ein konkretes Live-System sind unterschiedliche Nachweise. Diese Version verspricht nicht alle Datenbanken und Dienste.

| Typ | Implementiert | Hier geprüft | Wesentliche Grenzen |
|---|---|---|---|
| LOCAL | Persistentes Dokument-CRUD, Filter, Paging, Versionsprüfung | Unit und echte Hub-HTTP-Tests | Beispielspeicher, keine relationale DB |
| REST | Sieben HTTP-Methoden, JSON/Text/Form/Multipart, Binärantworten | Lokale Netzwerkfixtures | Kein OAuth-Tokenabruf, Streaming oder WebSocket |
| SOAP | 1.1/1.2, Actions/Faults, eigenständiges WSDL 1.1 | Parser und Netzwerkfixtures | Keine Imports, WS-Security oder komplette Binding-Generierung |
| GRAPHQL | Dokument/Variablen über HTTP, Fehlerbehandlung | Netzwerkfixtures | Keine Subscriptions/Federation |
| ODATA | HTTP-Operationen, top/skip | Netzwerkfixtures | Keine vollständigen Batch-/Delta-Semantiken |
| JDBC | Metadaten, gebundenes CRUD/SQL, IN/OUT/INOUT, Funktionen | Zwölf Fälle mit lokalem HSQLDB 1.8 | Keine universelle Herstellerabnahme |
| REDIS | Standalone RESP2, GET/SET NX/SET XX/DEL, Auth, TTL, TLS-Option | Protokoll-/Unit-Tests | Kein Live-Nachweis, Cluster/Sentinel/Streams |
| MONGODB | Optionale offizielle Treiberbrücke, begrenztes CRUD | Nur Pfad ohne Treiber | Keine Live-Abnahme, SRV/Multi-Host/Transaktionen |

Die Netzwerkfixtures sind echte lokale Transporttests, aber keine beliebigen Herstellerdienste. Das vorhandene alte HSQLDB-JAR wurde nur als Testinfrastruktur benutzt, nicht mitgeliefert und nicht als Produktionsdatenbank empfohlen.

## JDBC

Legen Sie geprüfte Treiber-JARs samt Abhängigkeiten unter `drivers/` ab. Starten Sie über das Classpath-Skript; `java -jar` allein lädt diese Dateien nicht. Optional kopiert `mvn -f drivers/pom.xml -Ppostgres dependency:copy-dependencies` ausdrücklich gewählte Abhängigkeiten. Weitere Profile: mysql, mariadb, mongodb. Dieser Maven-Pfad benötigt Netzwerk und wurde lokal nicht ausgeführt.

Treiberklassen: PostgreSQL `org.postgresql.Driver`, MySQL `com.mysql.cj.jdbc.Driver`, MariaDB `org.mariadb.jdbc.Driver`, SQL Server `com.microsoft.sqlserver.jdbc.SQLServerDriver`, Oracle `oracle.jdbc.OracleDriver`. SQLite, H2, DB2, SAP HANA und andere JDBC-Produkte sind mögliche Integrationswege, keine bestätigten Supportzusagen. Version, Lizenz, Java-Kompatibilität, TLS, Metadaten, Prozeduren und Timeouts müssen je Hersteller geprüft werden.

```json
{
  "name":"PostgreSQL-Labor","type":"JDBC",
  "config":{"url":"jdbc:postgresql://db.internal:5432/integration?sslmode=verify-full","driverClass":"org.postgresql.Driver"},
  "secrets":{"username":"least_privileged_account","password":"nicht-im-quellcode-speichern"}
}
```

Freigeben wird ein enger JDBC-Präfix, nicht pauschal `jdbc:`. Präfixprüfung ist kein vollständiger URL-Parser und ersetzt keine Egress-Firewall. CRUD prüft Bezeichner gegen Metadaten. UPDATE/DELETE benötigen einen einzelnen echten Primärschlüssel. Ein optionales Versionsfeld ermöglicht Compare-and-swap. QUERY/EXECUTE binden Werte, vertrauen aber dem SQL-Autor. Es gibt keinen Connection Pool; Verbindungen entstehen pro Operation.

## HTTP und Dokumentdatenbanken

NONE, BASIC, BEARER und API_KEY sind verfügbar. Statisches Bearer-Token bedeutet keinen OAuth2-Client-Credentials-Ablauf und keine automatische Erneuerung. Pfad/Query werden kodiert, Header geprüft, Redirects unterbunden und Antworten begrenzt. SOAP-Ersetzungen werden XML-escaped.

Redis-Schlüssel enthalten `ch:<tenant>:<namespace>:`. Gleiche Zieladresse und gleicher Namespace teilen innerhalb eines Mandanten bewusst den Schlüsselraum. Server-ACLs bleiben notwendig. MongoDB nutzt explizite Datenbank/Sammlung, String-IDs und begrenzte Gleichheitsfilter. Externe Datenzeilen werden nicht automatisch mandantenisoliert: separate Konten/Datenbanken oder überprüfte serverseitige Filter sind erforderlich.

gRPC, Kafka, JMS, AMQP, SFTP, FTP, MQTT, LDAP, Elasticsearch, Cassandra, DynamoDB und WCF-Spezialbindungen sind nicht implementiert. Das Startup-SPI und ein Beispielplugin ermöglichen Erweiterungen. Camel beziehungsweise offizielle SDKs sind Kandidaten, nicht bereits vorhandene Unterstützung.
