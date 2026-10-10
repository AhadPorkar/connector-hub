# Benutzerhandbuch

[English](../en/user-guide.md) · [فارسی](../fa/user-guide.md) · [README](../../README.de.md).

## Start und Anmeldung

Voraussetzung ist ein JDK ab Version 21. Im Projektverzeichnis startet `bash scripts/run-demo.sh` unter Linux/macOS die Anwendung; unter PowerShell verwenden Sie `./scripts/run-demo.ps1`. Öffnen Sie `http://127.0.0.1:8080` und melden Sie sich mit dem beim ersten Start ausgegebenen `BOOTSTRAP_TOKEN` an. Der Klartext wird nur einmal angezeigt; gespeichert wird ausschließlich sein Hash. Das Token gehört weder in Git noch in Fehlerberichte. Die Sprache lässt sich vor und nach der Anmeldung umstellen. Persisch aktiviert RTL; Code und technische Kennungen bleiben linksbündig. Das Zugriffstoken liegt nur im Browserspeicher, nicht im Local Storage. Nach einem Neuladen ist eine erneute Anmeldung erforderlich.

Die Demo enthält fünf Verbindungen und elf veröffentlichte Provider. Die externen Dienste sind ausdrücklich lokale Simulationen; Unternehmensnamen und Datensätze sind fiktiv. Mit Strg+C wird die Anwendung beendet. Der verschlüsselte Katalog in `var/` bleibt bei einem Neustart erhalten.

## Fachmodell

Eine Verbindung enthält den Connector-Typ, Zielkonfiguration und Zugangsdaten. Ein Provider beschreibt Operation, Eingabeparameter, Anfrage beziehungsweise SQL, Ergebnisauswahl und Ausführungsregeln. Eine Veröffentlichung erzeugt einen unveränderlichen Snapshot der Provider-Version und eine aufrufbare URL. Spätere Entwurfsänderungen verändern vorhandene Veröffentlichungen nicht. Zugangsdaten werden jedoch zur Laufzeit über die Verbindung aufgelöst und können unabhängig geändert werden.

Administratoren verwalten Verbindungen und Schlüssel. Designer bearbeiten und veröffentlichen Provider. Invoker dürfen nur explizit freigegebene Provider aufrufen. Auditoren lesen erlaubte Betriebsinformationen. Der Bootstrap-Administrator kann Administratoren für weitere Mandanten anlegen. Selbstregistrierung und Abrechnung sind nicht implementiert.

## Verbindung anlegen

Wählen Sie Verbindungen → Neue Verbindung. LOCAL ist der vollständig offline nutzbare Beispielspeicher. REST, SOAP, GRAPHQL und ODATA verwenden HTTP. JDBC benötigt einen passenden Datenbanktreiber. REDIS und MONGODB bieten die im [Connector-Handbuch](connectors.md) beschriebenen begrenzten Funktionen. Tragen Sie Geheimnisse ausschließlich in die vorgesehenen Geheimnisfelder ein. Leere Felder behalten vorhandene Werte bei; explizites Löschen entfernt sie. Antworten enthalten nur die Namen konfigurierter Geheimnisse.

Der Betreiber muss externe Ziele mit `HUB_ALLOWED_ORIGINS` freigeben. Private Adressen benötigen zusätzlich `HUB_ALLOW_PRIVATE=true`. Für JDBC gelten `HUB_JDBC_PREFIXES` und installierte Treiber. Die Oberfläche kann diese Betreibergrenzen nicht aufheben. Kennwörter in URLs, Konstanten oder beliebigen JSON-Feldern werden nicht automatisch als Geheimnisse erkannt.

Die Inspektion liest JDBC-Metadaten beziehungsweise LOCAL-Sammlungen. Für SOAP kann ein eigenständiges WSDL-1.1-Dokument eingefügt werden. Die HTTP-Inspektion ist keine erfolgreiche Erreichbarkeitsprüfung und lädt keine entfernten Spezifikationen automatisch.

## CRUD erzeugen

Bei LOCAL fragt Inspektion → CRUD erzeugen nach einer Sammlung. Bei JDBC werden der exakte Tabellenname und optional das Schema benötigt. READ, CREATE, UPDATE und DELETE entstehen als Entwürfe. JDBC-Generierung verlangt genau einen durch Metadaten bestätigten Primärschlüssel. Zusammengesetzte Schlüssel oder Tabellen ohne Primärschlüssel benötigen bewusst entworfene Provider. Prüfen, testen und veröffentlichen Sie jeden Entwurf. Die Generierung erstellt keine fachliche Zeilenberechtigung für Ihre Datenbank.

## Parameter und Abbildung

Parameternamen müssen eindeutig sein. Typen: STRING, INTEGER, DECIMAL, BOOLEAN, JSON, DATE und DATETIME. DATE verwendet `YYYY-MM-DD`; DATETIME benötigt einen Offset, etwa `2026-10-10T12:00:00Z`. INTEGER ist auf einen vorzeichenbehafteten Java-64-Bit-Wert begrenzt. Für präzisionskritische Zahlen sollte der Aufrufer Zeichenketten mit passendem deklariertem Zahlentyp senden, da der Browser JavaScript-Zahlen verwendet.

| Quelle | Auflösung | Durch Aufrufer überschreibbar |
|---|---|---|
| INPUT | Aufruf oder Standardwert | Ja |
| CONSTANT | Provider-Konstante | Nein |
| SYSTEM | tenant, principal, correlationId, now | Nein |
| CONFIG | Mandantenkonfiguration | Nein |
| SECRET | Geheimnis der Verbindung | Nein |

Nicht deklarierte Eingaben werden abgewiesen. Ein vollständiges `${customerId}` in einer JSON-Vorlage erhält den Datentyp. Eingebettete Ersetzungen erzeugen Text. SOAP-Werte werden XML-escaped. SQL verwendet gebundene `:customerId`-Parameter statt Textersetzung. Die automatische Bindungserkennung ersetzt nicht die fachliche Prüfung von Typ und Pflichtfeldstatus.

REST unterstützt Pfad, Query, Header sowie JSON-, Text-, Formular- und Multipart-Inhalte. Binäre Ergebnisse werden begrenzt und als Base64 mit Metadaten ausgegeben. SOAP nutzt explizite Envelope/Version/Action. GraphQL nutzt Dokument und Variablen; Mutationen gelten als Schreibvorgänge. OData bietet HTTP und begrenztes `$top`/`$skip`, keinen vollständigen OData-Client.

`fieldsRoot` ist ein JSON Pointer, beispielsweise `/data/items`, und wird vor der Ausgabemapping-Liste ausgewertet. Mapping-Pointer beziehen sich auf das ausgewählte Objekt beziehungsweise jede Zeile. Beispiel: `{"name":"customerName","pointer":"/name","type":"STRING"}`. XML verwendet lokale Elementnamen, Arrays bei Wiederholungen, `@` für Attribute und `#text` für Text. Gleiche lokale Namen aus verschiedenen Namespaces können mehrdeutig sein. Text/Binärdaten werden nicht automatisch zu Datensätzen.

## Testen und veröffentlichen

Testen Sie Entwürfe mit synthetischen Eingaben. Synchrone und eingereihte Ausführung sind möglich. Für dieselbe logische Wiederholung bleibt der Idempotenzschlüssel gleich; eine neue fachliche Aktion erhält einen neuen Schlüssel. Ein veränderter Inhalt mit gleichem Schlüssel verursacht einen Konflikt. Nach der Veröffentlichung stehen die versionierte URL und ein OpenAPI-3.1.1-Dokument bereit. Alle generierten Verbraucherendpunkte sind authentifizierte POST-Endpunkte, auch bei lesenden Zieloperationen.

```bash
curl -X POST http://127.0.0.1:8080/api/services/customer-directory/v1 \
  -H "Authorization: Bearer $HUB_TOKEN" \
  -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: customer-read-example-001' \
  --data '{"input":{},"page":{"index":0,"size":25}}'
```

Erzeugen Sie kurzlebige INVOKER-Schlüssel mit den benötigten Provider-IDs. Geben Sie niemals den Bootstrap-Schlüssel an Verbraucher weiter. Das Zurückziehen einer Veröffentlichung macht bereits ausgeführte Zielaktionen nicht rückgängig. OpenAPI-Import erzeugt Entwürfe aus einer begrenzten JSON-Teilmenge und löst keine beliebigen entfernten Referenzen auf.

## Fehler und Wiederherstellung

Automatische Wiederholungen sind ausschließlich für vom Adapter als sicher eingestufte Leseoperationen erlaubt. Eine PUT-Methode oder eine READ-Bezeichnung beweist allein keine Sicherheit. Unklare Schreibvorgänge werden dauerhaft als UNKNOWN markiert. Prüfen Sie das Ziel unabhängig und dokumentieren Sie anschließend CONFIRMED_SUCCESS oder CONFIRMED_NO_EFFECT. Keine dieser Entscheidungen wiederholt den Schreibvorgang automatisch. Details zu Jobs, Aufbewahrung und Abgleich enthält das [Betriebshandbuch](operations.md).
