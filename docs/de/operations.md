# Betrieb und Wiederherstellung

[English](../en/operations.md) · [فارسی](../fa/operations.md).

## Grundbetrieb

Nur eine Instanz je Katalogverzeichnis. Bei externen JARs den Classpath-Starter verwenden. `/healthz` zeigt Prozess-Liveness, keine Erreichbarkeit aller Ziele. Das Dashboard ist eine Auswertung begrenzter Auditdaten, kein SLA-Monitor. Strg+C/SIGTERM beendet den Prozess geordnet. Teilweise fehlgeschlagener Start schließt bereits angelegte Ressourcen. Ein harter Abbruch kann dennoch ungewisse Zieleffekte hinterlassen.

## Sicherung

Prozess stoppen, Freigabe der Dateisperre prüfen und das gesamte Verzeichnis konsistent sichern. Master-Key getrennt geschützt aufbewahren. Ein daneben kopierter lokaler Entwicklungsschlüssel bietet bei Sicherungsdiebstahl keine Trennung. Version und Betreiberkonfiguration dokumentieren. Wiederherstellung in einem neuen isolierten Verzeichnis mit richtigem Schlüssel tatsächlich testen.

Kein unterstütztes Hot-Backup durch Kopieren eines aktiven Katalogs; kein gemeinsames Volume für aktive Replikate. Konfigurationsexport enthält weder Geheimnisse noch Jobs/Ergebnisse/Geschäftsdaten und ist kein Backup. Ein alter gültiger Snapshot kann jüngere Idempotenznachweise verlieren, obwohl das Ziel bereits geschrieben wurde. Nach Rücksicherung externe Effekte abgleichen, bevor Verbraucher fortfahren.

## UNKNOWN-Abgleich

Neue Aufrufe der betroffenen Veröffentlichung gegebenenfalls stoppen oder Verbraucherschlüssel widerrufen. Den dauerhaften Eintrag nicht löschen. Request-ID, Version, Principal und Zeitpunkt sichern; keine vertraulichen Inhalte veröffentlichen. Das Ziel über einen unabhängigen autorisierten Weg anhand einer fachlichen Kennung prüfen.

Bei bestätigtem Erfolg CONFIRMED_SUCCESS mit überprüftem Ergebnis speichern. Bei sicher ausbleibender Wirkung CONFIRMED_NO_EFFECT wählen. Bei Ungewissheit UNKNOWN belassen. Eine No-effect-Entscheidung bewahrt den alten Schlüssel als fehlgeschlagenen Eintrag; ein neuer Versuch braucht eine explizite neue Aktion. Keine Entscheidung wiederholt oder storniert automatisch einen externen Schreibvorgang. Auch lokale Fehler nach Adaptereintritt können konservativ UNKNOWN werden.

## Jobs und Aufbewahrung

QUEUED kann abgebrochen werden; RUNNING nicht zuverlässig. SUCCEEDED/FAILED behalten Ergebnisse. Unterbrochene RUNNING-Jobs werden UNKNOWN. Wiederholung ist auf adapterseitig sichere Reads begrenzt. Abgelaufene/widerrufene Ursprungsschlüssel werden vor späterer Ausführung erneut geprüft.

Grenzen: 64 MiB Snapshot, 5.000 Auditereignisse über alle Mandanten, 1.000 Jobs, 5.000 Idempotenzeinträge, 10.000 LOCAL-Datensätze pro Sammlung. Admin-Pruning entfernt terminale Historie nach sieben Tagen und behält UNKNOWN. Danach endet der Schutz alter Schlüssel vor Duplikaten. Diese Limits sind keine empfohlenen Lastziele. Payloads können vertrauliche Daten enthalten und benötigen eine abgestimmte Aufbewahrungsstrategie.

## Änderungen und Störungen

Geheimnisse nur in vorgesehenen Feldern ändern. Leere Eingaben erhalten bestehende Werte; explizites Löschen entfernt sie. Verbindungsänderungen wirken auch auf veröffentlichte Provider. Snapshots frieren Providerdefinitionen ein, nicht Zugangsdaten oder Zielverfügbarkeit.

Bootstrap- und Master-Key-Rotation sind nicht als vollständiger Betriebsablauf implementiert. Ein geänderter Master-Key ohne Neuverschlüsselung macht den Katalog unlesbar. Bei Verlust nicht destruktiv neu initialisieren. Für DRIVER_MISSING Classpath und Abhängigkeiten prüfen; bei Destination-Fehlern keine pauschale Freigabe setzen. VERSION_CONFLICT verlangt Neuladen und fachliche Prüfung. CAPACITY erfordert Untersuchung von Datenmenge/Queue vor Pruning. Verschlüsselungsfehler: Datei sichern, passenden Schlüssel und getestetes Backup verwenden. Externe Überwachung von Speicher, Platte, Queue und Fehlern bleibt Betreiberaufgabe; Prometheus/OpenTelemetry ist nicht eingebaut.
