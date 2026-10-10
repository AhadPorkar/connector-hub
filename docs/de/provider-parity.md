# GraphBPMS-Provider-Konzepte und unabhängiger Umfang

[English](../en/provider-parity.md) · [فارسی](../fa/provider-parity.md).

Dieses Projekt implementiert das Integrationsprovider-Muster unabhängig. Es enthält keinen kopierten GraphBPMS-Code, proprietäre Schemas oder Kundendaten und ist kein kompatibler Austausch des Originals.

| Konzept | Connector Hub | Grenze |
|---|---|---|
| Provider | Versionierter Entwurf | Eigenes Schema/API |
| Datenbank-/Servicekonfiguration | Mandantenverbindung und Geheimnisse | Treiber und Allowlist erforderlich |
| Normal | INPUT | Nur deklarierte Namen |
| Constant | CONSTANT | Serverseitig |
| Systemic | SYSTEM | tenant, principal, correlationId, now |
| Systemkonfiguration | CONFIG | Mandantenwerte |
| Vertrauliche Konfiguration | SECRET | Referenz auf Verbindungsgeheimnis |
| Query | JDBC QUERY/EXECUTE | Gebundene Werte, vertrauenswürdiges SQL |
| Prozedur/Funktion | PROCEDURE/FUNCTION | IN/OUT/INOUT, Herstellerprüfung |
| REST/WSDL | HTTP-Familien/WSDL-Inspektion | Keine universellen SOAP-Bindings |
| FieldsRoot | JSON Pointer | Vor Mapping ausgewertet |
| Ausgabefelder | outputMappings | Relative Pointer und Typen |
| Paging/Filter/Sortierung | page-Umschlag | Connectorabhängige Teilmenge |
| Verbrauch | Versionierte POST-URL | Scope und Snapshot |
| Async | Persistente Jobs | Ein Worker, kein verteilter Bus |

Aufrufer senden ein Objekt unter `input`, nicht die originale Name/FieldType/Value-Liste. Konstanten/System-/Konfigurations-/Geheimniswerte werden nicht vom Aufrufer überschrieben. Beliebige Server-Konfigurationsdateien können nicht gelesen werden. JSON Pointer ersetzt keine frei ausführbare Ausdruckssprache. `${name}` erhält als vollständiger Wert den Typ; eingebettet entsteht Text. SQL verwendet `:name`.

CRUD-Generierung ist absichtlich konservativ: verifizierter einzelner Primärschlüssel, geprüfte Bezeichner und optionaler Versionsvergleich. OptionalItems, AdvancedEditor, SystemEntity, dynamische Formulare/Berichte und proprietäre Providerkategorien werden nicht nachgebildet. `absoluteMethodHelper.runProvider` existiert nicht; Anwendungen können stattdessen den veröffentlichten HTTP-Service serverseitig aufrufen.

Nicht implementiert sind beliebiger C#/Java/Script-Code aus der Oberfläche, WCF-Spezialbindungen, GraphBPMS-Servicebus, Form-Submit, Prozessvariablen, Berichtsdesigner, hierarchische Entity-Rechte und automatische Bestandsmigration. Diese Einschränkung vermeidet eine als Flexibilität getarnte Remote-Code-Ausführung. Geprüfte Java-Plugins werden nur beim Start installiert.

Für Migration zuerst Ziel, Rechte, Quellen, Nullsemantik, Timeouts, Ausgabe und Verbraucher inventarisieren. Einen Provider mit synthetischen Daten neu erstellen, Erfolg/Fehler/Duplikate auf isoliertem Ziel prüfen, Ausgabe/Paging vergleichen und Verbraucher schrittweise umstellen. Ein alter Katalog darf nicht ungeprüft als verhaltensgleich importiert werden. Die Parität betrifft den Arbeitsablauf Verbinden → Parametrisieren → Ausführen → Abbilden → Veröffentlichen, nicht jeden Funktionsumfang eines ausgereiften BPMS.
