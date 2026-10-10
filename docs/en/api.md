# HTTP API reference

[Deutsch](../de/api.md) · [فارسی](../fa/api.md).

## Authentication and conventions

Except `/healthz`, static UI files and localized documentation, routes require `Authorization: Bearer <key>`. API requests with bodies require `Content-Type: application/json`. `Accept-Language: en`, `de` or `fa` selects error messages; stable machine error codes do not change. No wildcard CORS, browser-cookie authentication or anonymous service execution is enabled. Requests return an `X-Request-ID`. Configuration objects use integer versions and an ETag. PUT includes the current `version`; guarded deletion uses `If-Match: "1"`.

## Control-plane routes

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/me`, `/api/dashboard` | Identity/capabilities and retained-window statistics |
| GET, POST | `/api/connections` | List/create connections |
| GET, PUT, DELETE | `/api/connections/{id}` | Read/update/delete a connection |
| POST | `/api/connections/{id}/discover` | Metadata/descriptor inspection |
| POST | `/api/connections/{id}/test` | Alias for inspection, not universal health checking |
| POST | `/api/connections/{id}/generate-crud` | Generate reviewed-later CRUD drafts |
| GET, POST | `/api/providers` | List/create drafts |
| GET, PUT, DELETE | `/api/providers/{id}` | Versioned draft management |
| POST | `/api/providers/{id}/execute` | Designer/admin draft execution |
| POST | `/api/providers/{id}/publish` | Publish `{"version":1}` |
| GET | `/api/publications` | Publication metadata |
| DELETE | `/api/publications/{id}` | Unpublish without undoing effects |
| POST | `/api/services/{slug}/v{revision}` | Scoped published invocation |
| GET | `/api/openapi` | Scope-filtered consumer OpenAPI 3.1.1 |
| POST | `/api/import/openapi` | Limited JSON contract import into drafts |
| GET | `/api/jobs`, `/api/jobs/{id}` | Authorized queue/result inspection |
| POST | `/api/jobs/{id}/cancel`, `/retry` | Cancel queued work; retry eligible reads |
| GET, POST | `/api/keys` | Admin key management |
| DELETE | `/api/keys/{id}` | Revoke a non-bootstrap key |
| GET | `/api/audit` | Tenant-visible retained audit metadata |
| GET, PUT | `/api/settings` | Admin tenant configuration |
| GET | `/api/export` | Redacted configuration export |
| GET | `/api/idempotency` | Admin recovery metadata |
| POST | `/api/idempotency/{id}/resolve` | Record independently verified outcome |
| POST | `/api/maintenance/prune` | Remove terminal history older than seven days |

The generated OpenAPI describes published consumer services, not every administrative route. Permissions are enforced server-side. Unknown cross-tenant resources deliberately appear as not found.

## Invocation contract

```json
{
  "input": {"customerId": 42},
  "page": {
    "index": 0,
    "size": 25,
    "filters": [{"field":"status","op":"eq","value":"active"}],
    "orders": [{"field":"id","direction":"asc"}]
  },
  "async": false
}
```

Only INPUT definitions may appear in `input`. Pagination behavior is connector-specific; JDBC and LOCAL implement the documented filter subset, while generic REST does not magically translate arbitrary page filters into a remote API contract. Use explicit request mappings for that service. All generated URLs use POST with this envelope. They are not automatically anonymous GET endpoints or new SOAP servers.

Supply `Idempotency-Key` for writes and non-retry-safe operations. Keys are bounded and scoped to the invoking principal and provider revision. A synchronous success includes `status`, mapped `data`, request metadata and timing. Async submission returns HTTP 202 with a job ID and QUEUED status. Poll `/api/jobs/{id}` with the same authorized principal. Job outcomes include SUCCEEDED, FAILED, UNKNOWN and CANCELLED.

## Errors and recovery

```json
{
  "error": {
    "code": "VERSION_CONFLICT",
    "message": "Localized explanation",
    "requestId": "correlation identifier"
  }
}
```

Typical boundaries are 400 invalid/undeclared input, 401 missing or expired credentials, 403 forbidden action, 404 absent/inaccessible resource, 409 version/idempotency/unknown-outcome conflict, 412 missing driver, 413 body limit, 415 unsupported media, 429 rate limit, and 502/504 upstream errors. Never parse localized text as application logic.

An administrator can resolve an UNKNOWN record with `{"decision":"CONFIRMED_SUCCESS","result":{...}}` or `{"decision":"CONFIRMED_NO_EFFECT"}` after inspecting the external system. This records a decision only; it neither reverses nor repeats the action. The second decision retains a FAILED tombstone under the old key. A new execution must be an explicit new decision.

## Examples and export

See `examples/` for complete connection/provider definitions and `examples/publish-local.py` for an executable create → publish → invoke workflow. Replace connection IDs after creation. Export omits connection secret values and redacts marked sensitive constants, but it is not a general data-loss-prevention scanner. Review descriptions, query literals and request templates before sharing an export.
