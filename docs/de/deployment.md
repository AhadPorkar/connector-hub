# Build und Bereitstellung

[English](../en/deployment.md) · [فارسی](../fa/deployment.md).

## Offline-Start

Zum Kompilieren ist JDK 21 oder neuer erforderlich. Python 3 führt HTTP- und Repository-Tests aus; Node prüft optional JavaScript-Syntax. Für die Kerndemo werden weder Maven noch npm, CDN, Datenbank oder Internet benötigt.

```bash
bash scripts/build.sh
bash scripts/run-demo.sh --port 8080 --data var
bash scripts/test.sh
```

PowerShell: `./scripts/build.ps1`, `./scripts/run-demo.ps1 --port 8080`, `./scripts/test.ps1`. Windows-Skripte und CI sind vorbereitet, lokal aber nicht ausgeführt. `java -jar build/connector-hub.jar --demo` startet das eigenständige JAR. Externe Treiber/Plugins benötigen stattdessen die Classpath-Skripte. Bei belegtem Port verwenden Sie `--port 18080`; der Demo-Zielport 9091 ist über `HUB_DEMO_PORT` änderbar. Pro Instanz ist ein eigenes Datenverzeichnis nötig.

## Konfiguration

| Variable | Standard | Bedeutung |
|---|---|---|
| HUB_BIND | 127.0.0.1 | Listeneradresse |
| HUB_PORT | 8080 | Listenerport |
| HUB_DATA_DIR | var | Katalogverzeichnis |
| HUB_DEMO_PORT | 9091 | Simulatorport |
| HUB_ALLOWED_ORIGINS | leer | Exakte Origins, kommasepariert |
| HUB_ALLOW_PRIVATE | false | Zusätzliche Freigabe privater Ziele |
| HUB_JDBC_PREFIXES | leer | Enge JDBC-Präfixe |
| HUB_MASTER_KEY | leer | Base64-kodierte 32 Zufallsbytes |
| HUB_ADMIN_TOKEN | leer | Initialer zufälliger Bootstrap-Schlüssel |
| HUB_MAX_CONCURRENT | 32 | Parallelität 1–256 |
| JAVA_OPTS | leer | JVM-Optionen im POSIX-Starter |

CLI `--bind`, `--port`, `--data` überschreiben entsprechende Umgebungswerte. `--demo` aktiviert den Simulator; unbekannte Optionen werden abgewiesen. Der Demo-Modus erlaubt sein lokales Origin und private Ziele, entfernt aber nicht die Origin-Liste. Ohne externen Master-Key wird ein lokaler Entwicklungsschlüssel erzeugt. Der Bootstrap-Wert gilt nur beim ersten Initialisieren.

Erzeugen Sie Schlüssel außerhalb von Git mit kryptografischem Zufall, beispielsweise Python `secrets.token_bytes(32)` plus Base64 und `secrets.token_urlsafe(32)`. Verwenden Sie einen Deployment-Secret-Manager. `.env`, `var/`, Token-Startlogs und Treiber-Lizenzschlüssel dürfen nicht veröffentlicht werden.

## Maven und Container

`mvn verify` baut die Module und startet Java-Tests über exec-maven-plugin. Surefire ist bewusst übersprungen, weil der Offline-Runner keine JUnit-Annotationen nutzt. Maven-Auflösung wurde lokal nicht ausgeführt. Das kanonische Testskript führt zusätzlich echte HTTP- und Repository-Tests aus. Das Server-JAR bündelt keine optionalen Herstellertreiber.

`docker compose -f compose.demo.yml up --build` bietet eine lokal gebundene Demo. `compose.yml` verlangt externe Geheimnisse, deaktiviert Demo und bindet Treiber/Plugins schreibgeschützt ein. Der Prozess läuft als UID 10001 mit Datenvolume, schreibgeschütztem Root-Dateisystem und Healthcheck. Docker-Build und Containerbetrieb wurden hier nicht getestet.

Die JVM spricht bewusst HTTP. Ein überprüfter HTTPS-Reverse-Proxy ist Pflicht vor externer Nutzung; eine Caddy-Beispielkonfiguration liegt bei. Hostname, Zertifikate, Netzwerk und Firewall müssen angepasst werden. Image-Tags sind keine unveränderlichen Attestierungen; für Releases geprüfte Digests festlegen.

## Grenzen

Ein JVM-Prozess, ein verschlüsselter 64-MiB-Snapshot im Speicher, ein Queue-Worker, keine Datenbankpools und keine HA. Treiber kommen geprüft unter `drivers/`, Plugins unter `plugins/`; danach ist ein Neustart nötig. Durchsatz, Speicherverbrauch und Wiederherstellung müssen mit der eigenen Last gemessen werden. Keine SLA-, Benchmark- oder Zero-Downtime-Zusage.
