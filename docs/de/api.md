# HTTP-API-Referenz

[English](../en/api.md) · [فارسی](../fa/api.md).

## Konventionen

Mit Ausnahme von `/healthz`, statischen Dateien und Dokumentation ist `Authorization: Bearer <key>` erforderlich. Requests mit Inhalt benötigen `Content-Type: application/json`. `Accept-Language` wählt en/de/fa für Fehlermeldungen; maschinenlesbare Fehlercodes bleiben stabil. Jede Antwort enthält eine Request-ID. Kein Wildcard-CORS, keine Authentifizierungscookies und keine anonyme Service-Ausführung.

Konfigurationsobjekte haben eine Ganzzahlversion und ETag. PUT sendet die aktuelle `version`. Geschützte Löschoperationen verwenden `If-Match: "1"`. Mandantenfremde Ressourcen erscheinen bewusst als nicht gefunden. Berechtigungen gelten serverseitig, unabhängig von sichtbaren Schaltflächen.

| Methode | Pfad | Zweck |
|---|---|---|
| GET | `/api/me`, `/api/dashboard` | Identität/Funktionen und begrenzte Statistik |
| GET, POST | `/api/connections` | Verbindungen auflisten/anlegen |
| GET, PUT, DELETE | `/api/connections/{id}` | Verbindung verwalten |
| POST | `/api/connections/{id}/discover`, `/test` | Metadaten/Deskriptor; kein universeller Healthcheck |
| POST | `/api/connections/{id}/generate-crud` | CRUD-Entwürfe erzeugen |
| GET, POST | `/api/providers` | Provider-Entwürfe |
| GET, PUT, DELETE | `/api/providers/{id}` | Versionierter Provider |
| POST | `/api/providers/{id}/execute` | Entwurf ausführen |
| POST | `/api/providers/{id}/publish` | `{"version":1}` veröffentlichen |
| GET | `/api/publications` | Veröffentlichungsmetadaten |
| DELETE | `/api/publications/{id}` | Veröffentlichung zurückziehen |
| POST | `/api/services/{slug}/v{revision}` | Freigegebenen Service aufrufen |
| GET | `/api/openapi` | Scope-gefiltertes OpenAPI 3.1.1 |
| POST | `/api/import/openapi` | Begrenzter JSON-Import |
| GET | `/api/jobs`, `/api/jobs/{id}` | Jobs und Ergebnisse |
| POST | `/api/jobs/{id}/cancel`, `/retry` | Wartenden Job abbrechen/sicheren Lesejob wiederholen |
| GET, POST | `/api/keys` | Schlüssel verwalten |
| DELETE | `/api/keys/{id}` | Nicht-Bootstrap-Schlüssel widerrufen |
| GET | `/api/audit` | Mandantenbezogene Metadaten |
| GET, PUT | `/api/settings` | Mandantenkonfiguration |
| GET | `/api/export`, `/api/idempotency` | Redigierter Export/Abgleichmetadaten |
| POST | `/api/idempotency/{id}/resolve` | Unabhängig bestätigtes Ergebnis dokumentieren |
| POST | `/api/maintenance/prune` | Alte terminale Historie entfernen |

Das generierte OpenAPI beschreibt veröffentlichte Verbraucherservices, nicht sämtliche Verwaltungsrouten.

## Aufruf

```json
{
  "input": {"customerId":42},
  "page": {"index":0,"size":25,"filters":[{"field":"status","op":"eq","value":"active"}],"orders":[{"field":"id","direction":"asc"}]},
  "async": false
}
```

Nur deklarierte INPUT-Parameter sind erlaubt. Filter/Paging sind connectorabhängig. Generisches REST übersetzt keine beliebigen Filter automatisch in den Vertrag eines fremden Dienstes. Alle erzeugten URLs sind POST-Endpunkte mit diesem Umschlag, keine automatisch anonymen GET- oder SOAP-Endpunkte.

Schreibende beziehungsweise nicht wiederholungssichere Operationen verlangen `Idempotency-Key`. Erfolg enthält Status, abgebildete Daten und Request-/Zeitmetadaten. Async liefert HTTP 202 mit Job-ID und QUEUED. Abfrage erfolgt autorisiert über `/api/jobs/{id}`. Mögliche Endzustände sind SUCCEEDED, FAILED, UNKNOWN und CANCELLED.

## Fehler

Fehler besitzen `{"error":{"code":"VERSION_CONFLICT","message":"lokalisierter Text","requestId":"..."}}`. Typische Grenzen: 400 ungültige Eingabe, 401 Authentifizierung, 403 Berechtigung, 404 Ressource, 409 Version/Idempotenz/Ungewissheit, 412 fehlender Treiber, 413 Größenlimit, 415 Medientyp, 429 Rate-Limit, 502/504 Zielsystem. Verarbeiten Sie Codes, nicht übersetzte Texte.

UNKNOWN kann nach externer Prüfung mit CONFIRMED_SUCCESS und Ergebnis oder CONFIRMED_NO_EFFECT geklärt werden. Die zweite Entscheidung hinterlässt einen FAILED-Eintrag unter dem alten Schlüssel. Sie wiederholt keine Aktion. Beispiele unter `examples/` und `publish-local.py` zeigen vollständige Erstellung und Nutzung. Exporte entfernen bekannte Geheimnisfelder, sind aber kein allgemeiner DLP-Scanner.
