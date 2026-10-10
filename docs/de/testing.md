# Tests und Nachweisgrenzen

[English](../en/testing.md) · [فارسی](../fa/testing.md).

Die Nachweise stammen aus tatsächlich ausgeführten Befehlen. [verification.json](../evidence/verification.json) und `docs/evidence/reports/` enthalten die endgültigen Zähler und Umgebung. Neue Läufe schreiben nach `build/reports/`, ohne historische Nachweise automatisch zu ersetzen. Es wird keine nicht gemessene Code-Coverage-Prozentzahl behauptet.

## Testbereiche

Core prüft JSON/Unicode, Fingerprints, Parametertypen und Quellen, Mappings, SQL-Lexer, Verschlüsselung, Sperren, Kopien, Neustart, konkurrierende Änderungen, Rollen, Wiederholungen, Circuit Breaker und Rate-Limits. Connector-Tests prüfen XXE/SOAP/WSDL, Ziele, RESP2, LOCAL-CRUD/Versionen/Isolation, fehlende Treiber und Retry-Einstufung. Server-Tests prüfen Schlüssel, Geheimnisänderungen, Veröffentlichungen und Wiederherstellung; insbesondere dürfen DONE/FAILED nicht durch Neustart in UNKNOWN umgewandelt werden.

HTTP-Tests starten den echten Java-Prozess und lokale Zielsimulatoren. Sie prüfen Mandanten/Rollen/Scopes, Konfigurationslebenszyklus, CRUD, veröffentlichte Versionen, OpenAPI, HTTP-Methoden und Body-Modi, Authentifizierung am Ziel, Redirect-/Größenlimits, unklare Schreibvorgänge, Abgleich, Jobs und Neustartpersistenz.

```bash
bash scripts/test.sh
python -m pip install -r tests/browser/requirements.txt
python -m playwright install chromium
python tests/browser/ui_test.py
```

## Browser

Die Playwright-Suite nutzt standardmäßig normale Navigation. In der Erstellungsumgebung blockierte eine verwaltete Browserrichtlinie sämtliche Navigation. Diese Richtlinie wurde nicht verändert. `HUB_UI_TRANSPORT=harness` rendert deshalb reale HTML/CSS/JS-Ressourcen in einer isolierten Seite und leitet Fetch-Aufrufe an den tatsächlichen Java-Server weiter. Das prüft Interaktionen, Übersetzungen, RTL, Layout und gespeicherte Änderungen, aber nicht native Navigation oder CSP-Durchsetzung. Screenshots zeigen echte gerenderte Zustände, keine Mockups. Eine vollständige Accessibility-Zertifizierung wird nicht behauptet.

## Datenbankabnahme

Der JDBC-Runner erzeugt und löscht `CH_ACCEPTANCE`. Er benötigt `HUB_ALLOW_DESTRUCTIVE_TESTS=true` und darf nur eine Wegwerfdatenbank verwenden. Setzen Sie `HUB_TEST_JDBC_URL`, `HUB_TEST_JDBC_DRIVER`, `HUB_TEST_JDBC_USER` und `HUB_TEST_JDBC_PASSWORD`, installieren Sie den passenden Treiber und starten Sie `bash scripts/test-jdbc.sh`.

Lokal wurden zwölf echte JDBC-Metadaten-/CRUD-/Negativtests mit einem vorhandenen HSQLDB-1.8.0-JAR ausgeführt. Das ist keine Abnahme für PostgreSQL, SQL Server, Oracle, MySQL, MariaDB, SQLite, H2, DB2 oder SAP HANA. Ein manueller PostgreSQL-GitHub-Workflow ist vorbereitet, hier aber nicht gelaufen. Prozeduren, TLS und Treiber-Timeouts brauchen eigene Tests.

## Offen

Live-Redis, MongoDB mit offiziellem Treiber, externe SOAP-Anbieter, Docker, Maven-Auflösung, Windows, Java 25 und gehostete Actions waren lokal nicht verfügbar. Penetration, Dauerlast, HA/Failover und Compliance sind nicht abgeschlossen. Neue Connectoren benötigen neben Erfolg auch falsche Eingaben, fehlende Rechte, Zeitüberschreitung, unklare Writes, Duplikate, Mandantengrenzen und Neustarttests. Viele Tests ersetzen nicht die Abnahme der exakten Kundenkonfiguration.
