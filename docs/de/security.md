# Sicherheitsmodell

[English](../en/security.md) · [فارسی](../fa/security.md).

Die Version ist eine Engineering-Vorschau, kein unabhängig penetrationstestetes oder zertifiziertes Produkt. Der Standardprozess darf nicht allein aufgrund lokaler Tests öffentlich exponiert werden.

## Vertrauensgrenzen und Identität

Betreiber kontrollieren Schlüssel, Netzwerkpolitik und installierte JARs. Administratoren verwalten Verbindungen/Schlüssel; Designer sind vertrauenswürdige SQL- und Integrationsautoren. Invoker liefern ausschließlich deklarierte Eingaben für freigegebene Provider. Mandantengrenzen gelten serverseitig. Treiber und Plugins besitzen JVM-Prozessrechte; eine Sandbox wird nicht behauptet.

API-Schlüssel werden mit hoher Entropie erzeugt, nur gehasht gespeichert und einmal angezeigt. Rolle, Scope, Ablauf und Widerruf werden geprüft. Der Bootstrap-Schlüssel ist nicht ablaufend und über die normale API nicht widerrufbar. Eine nachträgliche Änderung von `HUB_ADMIN_TOKEN` rotiert ihn nicht. Ein geprüftes Wiederherstellungs-/Rotationsverfahren und OIDC/SSO sind kommerzielle Freigabevoraussetzungen. Löschen Sie dafür keinen Katalog mit Geschäftshistorie.

Der Browser speichert das Zugriffstoken nur im Arbeitsspeicher; Local Storage enthält lediglich die Sprache. Keine Auth-Cookies und kein Wildcard-CORS. Das reduziert bestimmte CSRF-Risiken, beseitigt aber weder XSS noch gestohlene Tokens. CSP und escapte Textausgabe sind implementiert. Der UI-Harness beweist keine native CSP-Durchsetzung.

## Verschlüsselung und Daten

Der Katalog wird mit AES-256-GCM verschlüsselt. Eine lokale `master.key` dient der Entwicklung und schützt nicht bei Diebstahl des gesamten Verzeichnisses. Für kontrollierte Bereitstellung ist ein extern verwalteter `HUB_MASTER_KEY` mit geschütztem Dateisystem und getrennten Sicherungen nötig. Prozessspeicher und Crash-Dumps können Klartext enthalten. KMS/HSM und Schlüsselrotation sind nicht eingebaut.

Audit-Einträge enthalten Metadaten statt Aufrufinhalte. Jobs, Idempotenzresultate und LOCAL-Datensätze können jedoch Geschäftsdaten im verschlüsselten Katalog speichern. Ein Export ist weder Backup noch DLP-System. Geheimnisse in freien SQL-Literalen, Beschreibungen oder nicht markierten Konstanten können sichtbar bleiben. Prüfen Sie Exporte vor Weitergabe. Audit ist auf 5.000 Einträge über alle Mandanten begrenzt und kein externes unveränderliches Auditarchiv.

## Netzwerksicherheit

HTTP-, Redis- und MongoDB-Ziele benötigen exakte freigegebene Origins; private Adressen zusätzlich Opt-in. Link-Local/Cloud-Metadaten, unspecified und Multicast werden abgewiesen. Redirects werden nicht verfolgt. JDK-Zertifikats-/Hostnameprüfung wird nicht deaktiviert. JDBC verwendet gesonderte URL-Präfixe und verbotene gefährliche Optionen.

**Restgefahr:** DNS-Prüfung und tatsächlicher Verbindungsaufbau sind nicht an dieselbe aufgelöste IP gebunden. DNS-Rebinding/TOCTOU erfordert daher eine echte Egress-Firewall oder einen kontrollierten Proxy. URL-Validierung allein genügt nicht. Eigene Plugins können die Anwendungsprüfung umgehen und müssen als vertrauenswürdiger Code geprüft werden. Siehe [OWASP SSRF](https://cheatsheetseries.owasp.org/cheatsheets/Server_Side_Request_Forgery_Prevention_Cheat_Sheet.html).

## SQL, XML und Ressourcen

PreparedStatement/CallableStatement binden Werte. CRUD-Bezeichner werden gegen Metadaten geprüft. Frei verfasstes SQL kann weiterhin alles ausführen, was das Datenbankkonto erlaubt. Verwenden Sie minimale Rechte, getrennte Konten und geprüfte Veröffentlichungen. Keine globale Transaktion über Hub und Zielsystem.

XML verbietet DTDs, externe Entities, XInclude und externe Schemazugriffe. WSDL-Imports werden nicht aufgelöst. Tiefen, Größen, Seiten und Parallelität sind begrenzt. [OWASP XXE](https://cheatsheetseries.owasp.org/cheatsheets/XML_External_Entity_Prevention_Cheat_Sheet.html) beschreibt relevante Schutzprinzipien. Namespace-Kollisionen bleiben eine Mapping-Grenze.

## Vor externer Bereitstellung

HTTPS am kontrollierten Reverse Proxy terminieren, JVM nur über Loopback/geschütztes Netz anbinden, Egress einschränken, Treiber prüfen, Backups und Wiederherstellung testen sowie Isolation gegen echte Schemas nachweisen. Unabhängige Penetrations-, Last- und native Browsertests sind offen. Demo-Modus gehört nicht in Kundenumgebungen. Nach dem Pruning endet der Idempotenzschutz alter Einträge. Datenschutz-/Aufbewahrungsanforderungen benötigen qualifizierte organisatorische Prüfung; eine DSGVO-Zertifizierung wird nicht behauptet.
