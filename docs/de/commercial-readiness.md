# Kommerzielle Freigabe und offene Voraussetzungen

[English](../en/commercial-readiness.md) · [فارسی](../fa/commercial-readiness.md).

Version 0.1.0 ist eine funktionierende Engineering-Vorschau und Grundlage für ein Produkt. Sie ist nicht universell kompatibel, produktionszertifiziert, penetrationstestet oder hochverfügbar und wird nicht als uneingeschränkt verkaufs-/einsatzbereit bezeichnet. Quellcode und lokale Tests beweisen solche Eigenschaften nicht.

Geliefert sind Java-Server, dreisprachige Oberfläche, Versionierung, deklarative Parameter, CRUD/Query/Serviceadapter, API-Schlüssel mit Scopes, erzeugte URLs/OpenAPI, konservative Wiederholungen, persistente Idempotenz/Jobs, Verschlüsselung, Auditmetadaten, Tests, Beispiele und Deployment-Dateien. Verifizierte Teilmengen sind separat dokumentiert; keine erfundenen Benchmarks oder CI-Badges.

| Bereich | Voraussetzung vor Kundenfreigabe | Stand |
|---|---|---|
| Kompatibilität | Live-Abnahme je Produkt/Version/Vertrag | Überwiegend offen |
| Identität | OIDC/SSO und Admin-/Bootstrap-Rotation | Nicht implementiert |
| Zustand/HA | Transaktionale Metadatenbank, Migrationen, verteilte Claims | Einzelknoten-Datei |
| Verbindungen | Pools, Rotation, Treiber-Timeouts | Pro Operation |
| Netzwerk | Egress, TLS, DNS-Rebinding-Grenze | Betreiberprüfung |
| Qualität | Nativer Browser/CSP, Windows/Container, Last, Accessibility | Teilweise offen |
| Sicherheit | Unabhängiger Pentest, SBOM, Schwachstellenprozess | Nicht abgeschlossen |
| Betrieb | Restore-Übung, Retention, Monitoring, Support | Baseline vorhanden |
| Compliance | Vertrags-/Datenschutz-/Rechtsprüfung | Keine Zertifizierung |
| Produkt | Upgradepfad, Onboarding, Support/Vertrieb | Nicht abgeschlossen |

Ein Pilot sollte eine enge Matrix festlegen, etwa eine PostgreSQL-Version und einen REST-Vertrag, statt alle Systeme zu versprechen. Sensibilität, Last, Fehlerbehandlung und Abgleichverantwortung vereinbaren. Destruktive Tests nur auf Wegwerfinfrastruktur.

Zuerst konkrete Connectoren abnehmen, schwere Sicherheitslücken schließen und Restore/Abgleich beweisen. Danach Zustandsplattform, Identität und Verbindungslifecycle verbessern. Vollständige SOAP-/Camel-Protokolle folgen mit eigenen Tests. Abrechnung, Marketplace und Mehrregionenbetrieb sind spätere Produktarbeit, keine verdeckt vorhandenen Funktionen.

Vor GitHub-Veröffentlichung MIT-Lizenzentscheidung bestätigen, Geheimnisse/Kundendaten/Kataloge/JARs ausschließen, Skripte ausführen und Nachweise ansehen. Vorgeschlagene Actions aktivieren, geprüfte Digests festlegen und privaten Sicherheitsmeldeweg einrichten. Es wurde kein GitHub-Repository angelegt, nichts gepusht und kein Remote-Release veröffentlicht. Interview-, Umsatz- oder Verkaufserfolg wird nicht garantiert.
