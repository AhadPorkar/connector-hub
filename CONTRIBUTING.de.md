# Mitwirken

[English](CONTRIBUTING.md) · [فارسی](CONTRIBUTING.fa.md).

JDK 21 und Python 3 verwenden und `bash scripts/test.sh` ausführen. Zusätzliche Prüfungen stehen in der [Testdokumentation](docs/de/testing.md). Neue Laufzeitabhängigkeiten benötigen eine Architekturentscheidung, Lizenzprüfung und einen nachvollziehbaren Build.

Eine Änderung beschreibt Geschäftsfall, Fehlermodell, Berechtigungsgrenzen und Kompatibilität. Tests müssen Erfolg, Fehler, Wiederholungen und Mandantentrennung abdecken. Unterstützung eines Datenbankanbieters darf nicht allein aus erfolgreichem Kompilieren abgeleitet werden. Tatsächlich ausgeführte und nicht ausgeführte Prüfungen getrennt nennen.

Alle drei Sprachdateien und betroffenen Anleitungen aktualisieren. Maschinenlesbare API-Bezeichner bleiben unverändert. `python scripts/check_repository.py` prüft Konsistenz. Neue Oberflächenaktionen benötigen Browserprüfungen und zugängliche Bedienung. Keine Katalogdateien, Schlüssel, Zugangsdaten, Kundendaten oder heruntergeladenen Treiber einchecken.

Plugins sind vertrauenswürdiger Anwendungscode. Keine beliebigen Skripte, unbeschränkten Ziele oder Wiederholung unklarer Schreibvorgänge einführen. Serverberechtigungen dürfen nicht umgangen werden. Änderungen an Wiederherstellung und Migration gehören ins Änderungsprotokoll.

Vor Veröffentlichung muss der Eigentümer Rechte und MIT-Lizenz bestätigen, private Sicherheitsmeldungen und Branch-Schutz einrichten sowie Anbieter- und native Browserabnahmen ausführen. Dieses Archiv erstellt kein entferntes Repository und keine CI-Historie. Wartung und Support müssen gesondert vereinbart werden.
