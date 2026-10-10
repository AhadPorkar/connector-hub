# Sicherheitsrichtlinie

[English](SECURITY.md) · [فارسی](SECURITY.fa.md).

Version 0.1.0 ist eine technische Vorabversion ohne unabhängigen Penetrationstest oder Sicherheitszertifizierung. Ein Support-SLA oder eine Wartungsgarantie wird nicht zugesagt. Die [Sicherheitsdokumentation](docs/de/security.md) ist vor Bereitstellung zu lesen.

Der Eigentümer muss vor Veröffentlichung private Sicherheitsmeldungen auf GitHub aktivieren und einen betreuten Kontakt festlegen. Dieses Archiv richtet keinen aktiven Meldekanal ein. Nach Aktivierung vertrauliche Schwachstellen über die private Sicherheitsfunktion melden. Keine Zugangsdaten, Kundendaten oder sensiblen Infrastrukturdetails in öffentliche Issues stellen.

API-Schlüssel ersetzen kein OIDC/SSO. Der nicht ablaufende Bootstrap-Schlüssel hat noch keinen vollständigen Rotationsprozess. Verschlüsselte Verbindungsgeheimnisse bedeuten nicht, dass alle Ausführungsergebnisse verschlüsselt sind. Dateisystem und Sicherungen schützen. Externe TLS-Terminierung und Egress-Regeln sind erforderlich; DNS-Prüfungen sind nicht an die tatsächliche Verbindung gebunden.

JDBC ist keine SQL-Sandbox. Datenbankrechte beschränken. Treiber und Plugins sind vertrauenswürdiger JVM-Code und dürfen nicht von unberechtigten Benutzern hochgeladen werden. Unklare Schreibvorgänge bleiben UNKNOWN, bis ihre tatsächliche Wirkung untersucht wurde. Das Bereinigen der Idempotenzhistorie beendet das jeweilige Deduplizierungsfenster.

Die Demo nur lokal betreiben, den Simulator außerhalb der Entwicklung deaktivieren und kommerzielle Freigabekriterien erfüllen, bevor Kundendaten verarbeitet werden.
